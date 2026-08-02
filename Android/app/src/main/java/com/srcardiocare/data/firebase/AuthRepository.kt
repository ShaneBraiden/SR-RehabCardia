// AuthRepository.kt — Authentication, registration, password changes, login auditing.
package com.srcardiocare.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Authentication state, sign-in/up, password changes, and login-log auditing. */
object AuthRepository {

    /** Upper bound on how long an audit write may delay the sign-in flow. */
    private const val LOG_WRITE_TIMEOUT_MS = 5_000L

    val currentUID: String? get() = FirebaseClients.auth.currentUser?.uid
    val isAuthenticated: Boolean get() = FirebaseClients.auth.currentUser != null

    // ── Authorization source ────────────────────────────────────────────────
    // Authorization is read from the caller's `users/{uid}` document, matching
    // firestore.rules revision 3. It used to be read from Firebase Auth custom
    // claims, which is the stronger design — but that depends on the
    // `syncUserClaims` Cloud Function being deployed to write the claims, and
    // while it is not, every new account has an empty token and is denied
    // everywhere. Reading the document keeps client behaviour and server
    // enforcement in agreement without that dependency.
    //
    // If `syncUserClaims` is ever deployed, move both this and the rules back
    // to claims — the token check costs no read and cannot be raced.

    /** Cached authorization fields for the signed-in user, keyed by UID. */
    private var authFieldsUid: String? = null
    private var authFieldsRole: String = ""
    private var authFieldsDoctorId: String = ""

    /**
     * Reads the caller's authorization fields from their own user document.
     *
     * Cached per UID for the life of the session, because the values are
     * written by an admin or doctor and cannot change under the user's own
     * feet. [forceRefresh] re-reads, and is what to call after an operation
     * that alters the caller's own role or doctor assignment.
     */
    private suspend fun authFields(forceRefresh: Boolean = false) {
        val uid = currentUID
        if (uid == null) {
            authFieldsUid = null
            authFieldsRole = ""
            authFieldsDoctorId = ""
            return
        }
        if (!forceRefresh && authFieldsUid == uid) return

        runCatching {
            FirebaseClients.db.collection("users").document(uid).get().await()
        }.onSuccess { doc ->
            authFieldsUid = uid
            authFieldsRole = doc.getString("role")?.lowercase() ?: ""
            authFieldsDoctorId = doc.getString("assignedDoctorId") ?: ""
        }
    }

    /** The caller's role, per their user document. */
    suspend fun claimedRole(forceRefresh: Boolean = false): String {
        authFields(forceRefresh)
        return authFieldsRole
    }

    /**
     * The doctor this patient is assigned to, per their user document.
     *
     * Clinical documents are stamped with this value so doctor-scoped list
     * queries can be authorised per document. The rules verify the stamp
     * against this same field, read server-side, so a client cannot write
     * itself onto another clinician's caseload by lying here.
     */
    suspend fun assignedDoctorId(forceRefresh: Boolean = false): String {
        authFields(forceRefresh)
        return authFieldsDoctorId
    }

    /**
     * Re-reads the caller's authorization fields. Call after any operation
     * that changes the caller's own role, block state, or doctor assignment.
     */
    suspend fun refreshClaims(): String = claimedRole(forceRefresh = true)

    suspend fun login(email: String, password: String): Map<String, Any?> {
        var loginHandled = false
        var attemptedUid: String? = null
        val normalizedEmail = email.trim().lowercase()

        try {
            val result = FirebaseClients.auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            val uid = result.user?.uid ?: throw Exception("Login failed: no user")
            attemptedUid = uid

            val userData = UserRepository.fetchUser(uid)
            val role = (userData["role"] as? String ?: "").lowercase()
            val isBlocked = userData["isBlocked"] as? Boolean ?: false

            if (isBlocked) {
                // Record before signing out: the loginLogs rule requires an
                // authenticated writer, so a log written after signOut() is
                // rejected by the server and the block goes unaudited.
                recordLoginLog(
                    userId = uid,
                    email = normalizedEmail,
                    role = role,
                    status = "blocked",
                    message = "Blocked by admin"
                )
                FirebaseClients.auth.signOut()
                loginHandled = true
                throw Exception("Your account access has been blocked by admin. Please contact support.")
            }

            val accessSettings = SettingsRepository.fetchAccessControlSettings()
            val roleBlockedByPolicy = (role == "patient" && accessSettings.blockAllPatients) ||
                (role == "doctor" && accessSettings.blockAllDoctors)
            if (roleBlockedByPolicy) {
                // Same ordering constraint as the isBlocked branch above.
                recordLoginLog(
                    userId = uid,
                    email = normalizedEmail,
                    role = role,
                    status = "blocked",
                    message = "Blocked by admin policy"
                )
                FirebaseClients.auth.signOut()
                loginHandled = true
                throw Exception("Your account access is temporarily blocked by admin settings.")
            }

            // There is deliberately no custom-claim gate here any more.
            //
            // It used to block sign-in until the `role` claim appeared in the
            // ID token, because the rules read authorization from the token and
            // a claimless session was denied everywhere. With firestore.rules
            // revision 3 reading authorization from `users/{uid}` instead,
            // there is nothing to wait for — the document was fetched above and
            // is the same value the server will check.
            //
            // Prime the authorization cache from the document we already hold
            // so the first clinical write of the session does not pay for a
            // second read.
            authFieldsUid = uid
            authFieldsRole = role
            authFieldsDoctorId = userData["assignedDoctorId"] as? String ?: ""

            // Update last seen on login
            UserRepository.updateLastSeen()
            recordLoginLog(
                userId = uid,
                email = normalizedEmail,
                role = role,
                status = "success",
                message = null
            )
            loginHandled = true
            return userData
        } catch (e: Exception) {
            // Only attempt an audit write if we actually hold a session.
            //
            // A wrong password or unknown address fails with no signed-in user,
            // and the loginLogs rule requires an authenticated writer — so the
            // write could never succeed. It is not merely wasted: Firestore
            // writes only settle once the server acknowledges them, so with no
            // connection the await here never returned and the caller sat on a
            // spinner instead of being told the credentials were wrong.
            // Failed-credential attempts have to be audited server-side (a
            // blocking Auth trigger) to be recorded at all.
            if (!loginHandled && isAuthenticated) {
                recordLoginLog(
                    userId = attemptedUid,
                    email = normalizedEmail,
                    role = null,
                    status = "failed",
                    message = e.message
                )
            }
            throw e
        }
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: String
    ): Map<String, Any?> {
        val result = FirebaseClients.auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Registration failed")

        // Update display name
        result.user?.updateProfile(
            userProfileChangeRequest { displayName = "$firstName $lastName" }
        )?.await()

        // Create user doc in Firestore
        val userData = hashMapOf<String, Any?>(
            "email" to email,
            "firstName" to firstName,
            "lastName" to lastName,
            "role" to role,
            "isBlocked" to false,
            "apiAccessBlocked" to false,
            "phone" to null,
            "profileImageUrl" to null,
            "autoToursEnabled" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )
        FirebaseClients.db.collection("users").document(uid).set(userData).await()
        return userData
    }

    /**
     * Register a new user WITHOUT switching the current auth session.
     * Uses a temporary secondary FirebaseApp so the doctor/admin stays signed in.
     * Returns the new user's UID.
     *
     * [assignedDoctorId] must be supplied in the same write as the rest of the
     * document. The security rules require a doctor to name themselves as the
     * assigned clinician at creation time, so setting it in a follow-up
     * update() — as this flow used to — is rejected outright.
     */
    suspend fun registerOther(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: String,
        assignedDoctorId: String? = null
    ): String {
        val defaultApp = FirebaseApp.getInstance()
        // Get or create secondary app
        val secondaryApp = try {
            FirebaseApp.getInstance("accountCreator")
        } catch (_: Exception) {
            FirebaseApp.initializeApp(
                defaultApp.applicationContext,
                defaultApp.options,
                "accountCreator"
            )
        }

        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
        try {
            val result = secondaryAuth.createUserWithEmailAndPassword(email, password).await()
            val newUid = result.user?.uid ?: throw Exception("Registration failed")

            // Set display name on the secondary auth user
            result.user?.updateProfile(
                userProfileChangeRequest { displayName = "$firstName $lastName" }
            )?.await()

            // Sign out from secondary immediately
            secondaryAuth.signOut()

            // Write user doc using the PRIMARY Firestore (authenticated as doctor/admin)
            val userData = hashMapOf<String, Any?>(
                "email" to email,
                "firstName" to firstName,
                "lastName" to lastName,
                "role" to role,
                "isBlocked" to false,
                "apiAccessBlocked" to false,
                "phone" to null,
                "profileImageUrl" to null,
                "autoToursEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!assignedDoctorId.isNullOrBlank()) {
                userData["assignedDoctorId"] = assignedDoctorId
            }
            FirebaseClients.db.collection("users").document(newUid).set(userData).await()
            return newUid
        } catch (e: Exception) {
            secondaryAuth.signOut()
            throw e
        }
    }

    fun logout() {
        // Drop the cached authorization fields first: the object outlives the
        // session, so leaving them set would hand the next user to sign in on
        // this device the previous user's role and doctor assignment.
        authFieldsUid = null
        authFieldsRole = ""
        authFieldsDoctorId = ""
        FirebaseClients.auth.signOut()
    }

    /** Re-authenticate with old password and update to new password. */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val user = FirebaseClients.auth.currentUser ?: throw Exception("Not authenticated")
        val email = user.email ?: throw Exception("No email on account")

        // Re-authenticate
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, oldPassword)
        user.reauthenticate(credential).await()

        // Update password
        user.updatePassword(newPassword).await()
    }

    /** Fetch recent login logs (admin). */
    suspend fun fetchLoginLogs(limit: Int = 150): List<Pair<String, Map<String, Any?>>> {
        val safeLimit = limit.coerceIn(20, 500).toLong()
        val snapshot = FirebaseClients.db.collection("loginLogs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(safeLimit)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    private suspend fun recordLoginLog(
        userId: String?,
        email: String,
        role: String?,
        status: String,
        message: String?
    ) {
        try {
            val ref = FirebaseClients.db.collection("loginLogs").document()
            val payload = mutableMapOf<String, Any?>(
                "id" to ref.id,
                "userId" to userId,
                "email" to email,
                "role" to role,
                "status" to status,
                "message" to message,
                "platform" to "android",
                "createdAt" to FieldValue.serverTimestamp()
            )
            // A Firestore write settles only on server acknowledgement, so on a
            // poor connection this await can outlast the user's patience. Auditing
            // must never be the reason a sign-in appears to hang.
            withTimeoutOrNull(LOG_WRITE_TIMEOUT_MS) { ref.set(payload).await() }
        } catch (_: Exception) {
            // Logging must never block auth flow.
        }
    }
}
