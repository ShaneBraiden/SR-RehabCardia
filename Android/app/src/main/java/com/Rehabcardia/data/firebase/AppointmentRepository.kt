// AppointmentRepository.kt — Appointments between patients and doctors.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.srcardiocare.data.model.Appointment
import kotlinx.coroutines.tasks.await

/** CRUD over the `appointments` collection. */
object AppointmentRepository {

    suspend fun fetchAppointments(userId: String, role: String): List<Pair<String, Map<String, Any?>>> {
        val field = if (role == "doctor" || role == "admin") "doctorId" else "patientId"
        val snapshot = FirebaseClients.db.collection("appointments")
            .whereEqualTo(field, userId)
            .orderBy("dateTime", Query.Direction.ASCENDING)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fallback when composite index is missing — fetches without ordering. */
    suspend fun fetchAppointmentsUnordered(userId: String, role: String): List<Pair<String, Map<String, Any?>>> {
        val field = if (role == "doctor" || role == "admin") "doctorId" else "patientId"
        val snapshot = FirebaseClients.db.collection("appointments")
            .whereEqualTo(field, userId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    suspend fun createAppointment(data: Map<String, Any>): String {
        val ref = FirebaseClients.db.collection("appointments").document()
        val mutableData = data.toMutableMap()
        mutableData["id"] = ref.id
        mutableData["createdAt"] = FieldValue.serverTimestamp()
        ref.set(mutableData).await()
        return ref.id
    }

    suspend fun updateAppointment(id: String, fields: Map<String, Any>) {
        FirebaseClients.db.collection("appointments").document(id).update(fields).await()
    }

    // ── Typed reads ─────────────────────────────────────────────────────

    suspend fun getAppointments(userId: String, role: String): List<Appointment> =
        fetchAppointments(userId, role).map { (id, data) -> data.toAppointment(id) }

    suspend fun getAppointmentsUnordered(userId: String, role: String): List<Appointment> =
        fetchAppointmentsUnordered(userId, role).map { (id, data) -> data.toAppointment(id) }
}
