// UserRepository.kt — User documents: patients, doctors, profiles, and lifecycle.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctionsException
import com.srcardiocare.data.model.User
import kotlinx.coroutines.tasks.await

/** CRUD and queries over the `users` collection, plus patient data cleanup. */
object UserRepository {

    suspend fun fetchUser(uid: String): Map<String, Any?> {
        val doc = FirebaseClients.db.collection("users").document(uid).get().await()
        return doc.data ?: throw Exception("User not found: $uid")
    }

    suspend fun fetchCurrentUser(): Map<String, Any?> {
        val uid = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        return fetchUser(uid)
    }

    suspend fun updateUser(fields: Map<String, Any>) {
        val uid = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        FirebaseClients.db.collection("users").document(uid).update(fields).await()
    }

    /** Update lastSeen timestamp */
    suspend fun updateLastSeen() {
        val uid = AuthRepository.currentUID ?: return
        try {
            FirebaseClients.db.collection("users").document(uid)
                .update("lastSeen", FieldValue.serverTimestamp()).await()
        } catch (_: Exception) { }
    }

    /** Update another user's fields by their ID (for admin/doctor). */
    suspend fun updateUserById(uid: String, fields: Map<String, Any>) {
        FirebaseClients.db.collection("users").document(uid).update(fields).await()
    }

    /**
     * Block or unblock a user from app/API access.
     * When blocked, login is denied and active sessions are forced out by the doc guard.
     */
    suspend fun setUserAccessBlocked(
        uid: String,
        blocked: Boolean,
        reason: String? = null
    ) {
        val actorId = AuthRepository.currentUID
        val updates = mutableMapOf<String, Any?>(
            "isBlocked" to blocked,
            "apiAccessBlocked" to blocked,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (blocked) {
            updates["blockedAt"] = FieldValue.serverTimestamp()
            updates["blockedBy"] = actorId
            updates["blockReason"] = reason?.trim().orEmpty()
        } else {
            updates["blockedAt"] = null
            updates["blockedBy"] = null
            updates["blockReason"] = null
        }

        FirebaseClients.db.collection("users").document(uid).set(updates, SetOptions.merge()).await()
    }

    /**
     * Permanently deletes a user: Firestore documents *and* the Firebase Auth
     * account, via the `deleteUserAccount` callable.
     *
     * The client SDK can only delete the Auth account it is signed in as, so
     * the previous Firestore-only delete left the sign-in record behind and the
     * email stayed claimed — re-adding the same person failed with "email
     * already in use". The callable runs on the Admin SDK, which can actually
     * remove it.
     */
    suspend fun deleteUser(uid: String) {
        callDeleteUserAccount(mapOf("uid" to uid))
    }

    /**
     * Clears an Auth account that has no Firestore document left — the residue
     * of a delete performed before this went through the backend. Admin only.
     * Safe to call when nothing is there; the callable treats that as success.
     */
    suspend fun purgeOrphanedAccount(email: String) {
        callDeleteUserAccount(mapOf("email" to email.trim().lowercase()))
    }

    /**
     * Files the signed-in user's own request to have their account deleted.
     *
     * Deliberately a *request* rather than an immediate delete. Everything in
     * this app is a clinical record created under a clinician's supervision,
     * and record-retention law generally outlives a patient's wish to leave —
     * so a one-tap hard delete would either break that obligation or lie about
     * what it did. The callable blocks the account immediately, which is the
     * part the user actually feels, and hands the retention decision to the
     * clinic. The published deletion policy at /delete-account.html describes
     * exactly this, so the in-app path and the web path agree.
     */
    suspend fun requestOwnAccountDeletion(reason: String = ""): String {
        try {
            val result = FirebaseClients.functions
                .getHttpsCallable("requestAccountDeletion")
                .call(mapOf("reason" to reason.trim().take(500)))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any?>
            return data?.get("requestId") as? String ?: ""
        } catch (e: FirebaseFunctionsException) {
            throw Exception(e.message ?: "Could not submit the deletion request", e)
        }
    }

    /**
     * Stamps the user's own document with the disclosure version they accepted.
     *
     * Best-effort by design: the local record in
     * [com.srcardiocare.core.consent.ConsentManager] is what gates the UI, and
     * a patient who has just tapped "I understand" should not be held at a
     * blocking screen because the ward's wifi dropped. A failure here re-prompts
     * on a later launch, which is the safe direction to fail in.
     */
    suspend fun recordConsent(version: Int) {
        val uid = AuthRepository.currentUID ?: return
        runCatching {
            FirebaseClients.db.collection("users").document(uid).set(
                mapOf(
                    "consent" to mapOf(
                        "version" to version,
                        "acceptedAt" to FieldValue.serverTimestamp()
                    )
                ),
                SetOptions.merge()
            ).await()
        }
    }

    /**
     * Invokes the callable and unwraps its error. Callable failures surface as
     * [FirebaseFunctionsException] whose `message` carries the server's text,
     * which is already written for the clinician reading it.
     */
    private suspend fun callDeleteUserAccount(payload: Map<String, String>) {
        try {
            FirebaseClients.functions
                .getHttpsCallable("deleteUserAccount")
                .call(payload)
                .await()
        } catch (e: FirebaseFunctionsException) {
            throw Exception(e.message ?: "Could not delete the account", e)
        }
    }

    /** Fetch patients assigned to a specific doctor (for doctor role). */
    suspend fun fetchPatients(doctorId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("users")
            .whereEqualTo("role", "patient")
            .whereEqualTo("assignedDoctorId", doctorId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fetch ALL patients regardless of assigned doctor (for admin role). */
    suspend fun fetchAllPatients(): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("users")
            .whereEqualTo("role", "patient")
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fetch ALL users regardless of role (for admin role). */
    suspend fun fetchAllUsers(): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("users").get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fetch ALL doctors (for admin role). */
    suspend fun fetchAllDoctors(): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("users")
            .whereEqualTo("role", "doctor")
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /**
     * Permanently deletes a patient: user document, every clinical record that
     * references them, and their Firebase Auth account.
     *
     * All of it now runs in `deleteUserAccount` on the backend rather than as a
     * client batch. Two reasons the batch had to go:
     *
     *  - the client SDK cannot delete another user's Auth account, so the email
     *    stayed claimed and the patient could never be re-added;
     *  - notifications are admin-delete-only, so a doctor's batch either
     *    excluded them or rolled the whole deletion back. Running as the Admin
     *    SDK removes that split entirely.
     *
     * Authorization is enforced server-side: a doctor may only delete a patient
     * assigned to them.
     */
    suspend fun deletePatient(patientId: String) {
        callDeleteUserAccount(mapOf("uid" to patientId))
    }

    // ── Typed reads ─────────────────────────────────────────────────────
    // Same queries as above, mapped to the User model. Prefer these in new code.

    suspend fun getUser(uid: String): User = fetchUser(uid).toUser(uid)

    suspend fun getCurrentUser(): User {
        val uid = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        return getUser(uid)
    }

    suspend fun getPatients(doctorId: String): List<User> =
        fetchPatients(doctorId).map { (id, data) -> data.toUser(id) }

    suspend fun getAllPatients(): List<User> =
        fetchAllPatients().map { (id, data) -> data.toUser(id) }

    suspend fun getAllUsers(): List<User> =
        fetchAllUsers().map { (id, data) -> data.toUser(id) }

    suspend fun getAllDoctors(): List<User> =
        fetchAllDoctors().map { (id, data) -> data.toUser(id) }
}
