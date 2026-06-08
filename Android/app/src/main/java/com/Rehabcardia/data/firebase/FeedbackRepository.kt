// FeedbackRepository.kt — Doctor feedback and post-workout feedback.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.srcardiocare.data.model.PostWorkoutFeedback
import kotlinx.coroutines.tasks.await

/** Doctor->patient feedback plus the post-workout feedback collection. */
object FeedbackRepository {

    /** Send feedback from doctor to patient. */
    suspend fun sendFeedback(patientId: String, message: String) {
        val doctorId = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val ref = FirebaseClients.db.collection("feedback").document()
        val data = hashMapOf<String, Any>(
            "id" to ref.id,
            "patientId" to patientId,
            "doctorId" to doctorId,
            "message" to message,
            "createdAt" to FieldValue.serverTimestamp()
        )
        ref.set(data).await()

        com.srcardiocare.core.push.Notifier.send(
            com.srcardiocare.core.push.NotificationEvent.DoctorFeedback(
                patientId = patientId,
                preview = message.take(120)
            )
        )
    }

    /** Submit post-workout feedback to a top-level collection. */
    suspend fun submitPostWorkoutFeedback(data: Map<String, Any?>) {
        val ref = FirebaseClients.db.collection("postWorkoutFeedback").document()
        val mutableData = data.toMutableMap()
        mutableData["id"] = ref.id
        ref.set(mutableData).await()
    }

    suspend fun fetchPatientFeedbacks(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("postWorkoutFeedback")
            .whereEqualTo("patientId", patientId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
            .sortedByDescending { (it.second["submittedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
    }

    suspend fun fetchDoctorFeedbacks(doctorId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("postWorkoutFeedback")
            .whereEqualTo("doctorId", doctorId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
            .sortedByDescending { (it.second["submittedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
    }

    suspend fun fetchAllFeedbacks(): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("postWorkoutFeedback")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    // ── Typed reads ─────────────────────────────────────────────────────

    suspend fun getPatientFeedbacks(patientId: String): List<PostWorkoutFeedback> =
        fetchPatientFeedbacks(patientId).map { (id, data) -> data.toPostWorkoutFeedback(id) }

    suspend fun getDoctorFeedbacks(doctorId: String): List<PostWorkoutFeedback> =
        fetchDoctorFeedbacks(doctorId).map { (id, data) -> data.toPostWorkoutFeedback(id) }

    suspend fun getAllFeedbacks(): List<PostWorkoutFeedback> =
        fetchAllFeedbacks().map { (id, data) -> data.toPostWorkoutFeedback(id) }
}
