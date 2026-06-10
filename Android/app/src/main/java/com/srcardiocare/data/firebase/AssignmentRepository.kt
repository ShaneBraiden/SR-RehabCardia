// AssignmentRepository.kt — Exercise assignments (doctor -> patient prescriptions).
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import com.srcardiocare.data.model.Assignment
import kotlinx.coroutines.tasks.await

/** CRUD over the `assignments` collection. */
object AssignmentRepository {

    /** Create a new assignment (doctor assigns exercise to patient). */
    suspend fun createAssignment(data: Map<String, Any>): String {
        AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val ref = FirebaseClients.db.collection("assignments").document()
        val mutableData = data.toMutableMap()
        mutableData["id"] = ref.id
        mutableData["createdAt"] = FieldValue.serverTimestamp()
        ref.set(mutableData).await()
        return ref.id
    }

    /** Fetch all active assignments for a patient. */
    suspend fun fetchAssignments(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("assignments")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("isActive", true)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fetch active assignments created by a doctor. */
    suspend fun fetchDoctorAssignments(doctorId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("assignments")
            .whereEqualTo("doctorId", doctorId)
            .whereEqualTo("isActive", true)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fetch a single assignment by id, regardless of isActive. */
    suspend fun fetchAssignmentById(assignmentId: String): Map<String, Any?>? {
        val snap = FirebaseClients.db.collection("assignments").document(assignmentId).get().await()
        return if (snap.exists()) snap.data else null
    }

    /** Update an existing assignment. */
    suspend fun updateAssignment(assignmentId: String, updates: Map<String, Any>) {
        FirebaseClients.db.collection("assignments").document(assignmentId)
            .update(updates).await()
    }

    /** Deactivate an assignment (soft delete). */
    suspend fun deactivateAssignment(assignmentId: String) {
        FirebaseClients.db.collection("assignments").document(assignmentId)
            .update("isActive", false).await()
    }

    // ── Typed reads ─────────────────────────────────────────────────────

    suspend fun getAssignments(patientId: String): List<Assignment> =
        fetchAssignments(patientId).map { (id, data) -> data.toAssignment(id) }

    suspend fun getDoctorAssignments(doctorId: String): List<Assignment> =
        fetchDoctorAssignments(doctorId).map { (id, data) -> data.toAssignment(id) }

    suspend fun getAssignmentById(assignmentId: String): Assignment? =
        fetchAssignmentById(assignmentId)?.toAssignment(assignmentId)
}
