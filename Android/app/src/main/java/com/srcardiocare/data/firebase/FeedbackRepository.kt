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

    /**
     * Submit post-workout feedback to a top-level collection.
     *
     * `patientId` and `doctorId` are stamped here rather than taken from the
     * caller's map: both are authorization-relevant, and the security rules
     * check them against the caller's own user document. Stamping them
     * centrally means a screen cannot accidentally omit or contradict them.
     *
     * (Before this change no `doctorId` was written at all, which is why
     * fetchDoctorFeedbacks() has always returned an empty list.)
     */
    suspend fun submitPostWorkoutFeedback(data: Map<String, Any?>) {
        val patientId = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val ref = FirebaseClients.db.collection("postWorkoutFeedback").document()
        val mutableData = data.toMutableMap()
        mutableData["id"] = ref.id
        mutableData["patientId"] = patientId
        mutableData["doctorId"] = AuthRepository.assignedDoctorId()
        ref.set(mutableData).await()
    }

    /**
     * Every feedback [patientId] has submitted.
     *
     * Satisfies the patient's own list rule and the admin one. A doctor must
     * use [fetchPatientFeedbacksAsDoctor] instead — their branch of the rule
     * authorises on the denormalised `doctorId`, so a query that does not
     * constrain it is rejected wholesale rather than filtered down.
     */
    suspend fun fetchPatientFeedbacks(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("postWorkoutFeedback")
            .whereEqualTo("patientId", patientId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
            .sortedByDescending { (it.second["submittedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
    }

    /**
     * As [fetchPatientFeedbacks], but for a doctor reading a patient's
     * feedback. Without the `doctorId` constraint the query was denied, the
     * clinician screens caught the denial, and every patient showed
     * "No feedbacks recorded yet" no matter how much they had submitted.
     */
    suspend fun fetchPatientFeedbacksAsDoctor(
        patientId: String,
        doctorId: String
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("postWorkoutFeedback")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("doctorId", doctorId)
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

    /**
     * [patientId]'s feedback as seen by [viewerId] in [viewerRole]. Admins and
     * the patient themselves may read the whole set; a doctor is scoped to the
     * feedback stamped with their own id.
     */
    suspend fun getPatientFeedbacksFor(
        patientId: String,
        viewerId: String,
        viewerRole: String
    ): List<PostWorkoutFeedback> =
        if (viewerRole == "doctor" && viewerId != patientId) {
            fetchPatientFeedbacksAsDoctor(patientId, viewerId)
                .map { (id, data) -> data.toPostWorkoutFeedback(id) }
        } else {
            getPatientFeedbacks(patientId)
        }

    suspend fun getDoctorFeedbacks(doctorId: String): List<PostWorkoutFeedback> =
        fetchDoctorFeedbacks(doctorId).map { (id, data) -> data.toPostWorkoutFeedback(id) }

    suspend fun getAllFeedbacks(): List<PostWorkoutFeedback> =
        fetchAllFeedbacks().map { (id, data) -> data.toPostWorkoutFeedback(id) }
}
