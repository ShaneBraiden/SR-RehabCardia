// UserRepository.kt — User documents: patients, doctors, profiles, and lifecycle.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
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

    /** Delete a user's Firestore document (for admin). Note: Auth account must be deleted via Admin SDK. */
    suspend fun deleteUser(uid: String) {
        FirebaseClients.db.collection("users").document(uid).delete().await()
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
     * Deletes a patient from Firestore: removes user doc + related plans,
     * workouts, appointments, and notifications. Firebase client SDK cannot delete
     * another user's Auth account, so only Firestore data is removed.
     */
    suspend fun deletePatient(patientId: String) {
        val batch = FirebaseClients.db.batch()

        // Delete related plans
        val plans = FirebaseClients.db.collection("plans")
            .whereEqualTo("patientId", patientId).get().await()
        for (doc in plans.documents) batch.delete(doc.reference)

        // Delete related workouts
        val workouts = FirebaseClients.db.collection("workouts")
            .whereEqualTo("patientId", patientId).get().await()
        for (doc in workouts.documents) batch.delete(doc.reference)

        // Delete related appointments
        val appointments = FirebaseClients.db.collection("appointments")
            .whereEqualTo("patientId", patientId).get().await()
        for (doc in appointments.documents) batch.delete(doc.reference)

        // Delete related notifications
        val notifications = FirebaseClients.db.collection("notifications")
            .whereEqualTo("userId", patientId).get().await()
        for (doc in notifications.documents) batch.delete(doc.reference)

        // Delete the user document
        batch.delete(FirebaseClients.db.collection("users").document(patientId))

        batch.commit().await()
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
