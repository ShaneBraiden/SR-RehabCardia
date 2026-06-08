// AuthRepository.kt — Authentication, registration, password changes, login auditing.
package com.srcardiocare.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Authentication state, sign-in/up, password changes, and login-log auditing. */
object AuthRepository {

    val currentUID: String? get() = FirebaseClients.auth.currentUser?.uid
    val isAuthenticated: Boolean get() = FirebaseClients.auth.currentUser != null

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
                FirebaseClients.auth.signOut()
                recordLoginLog(
                    userId = uid,
                    email = normalizedEmail,
                    role = role,
                    status = "blocked",
                    message = "Blocked by admin"
                )
                loginHandled = true
                throw Exception("Your account access has been blocked by admin. Please contact support.")
            }

            val accessSettings = SettingsRepository.fetchAccessControlSettings()
            val roleBlockedByPolicy = (role == "patient" && accessSettings.blockAllPatients) ||
                (role == "doctor" && accessSettings.blockAllDoctors)
            if (roleBlockedByPolicy) {
                FirebaseClients.auth.signOut()
                recordLoginLog(
                    userId = uid,
                    email = normalizedEmail,
                    role = role,
                    status = "blocked",
                    message = "Blocked by admin policy"
                )
                loginHandled = true
                throw Exception("Your account access is temporarily blocked by admin settings.")
            }

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
            if (!loginHandled) {
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
            "createdAt" to FieldValue.serverTimestamp()
        )
        FirebaseClients.db.collection("users").document(uid).set(userData).await()
        return userData
    }

    /**
     * Register a new user WITHOUT switching the current auth session.
     * Uses a temporary secondary FirebaseApp so the doctor/admin stays signed in.
     * Returns the new user's UID.
     */
    suspend fun registerOther(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: String
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
                "createdAt" to FieldValue.serverTimestamp()
            )
            FirebaseClients.db.collection("users").document(newUid).set(userData).await()
            return newUid
        } catch (e: Exception) {
            secondaryAuth.signOut()
            throw e
        }
    }

    fun logout() {
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
            ref.set(payload).await()
        } catch (_: Exception) {
            // Logging must never block auth flow.
        }
    }
}
