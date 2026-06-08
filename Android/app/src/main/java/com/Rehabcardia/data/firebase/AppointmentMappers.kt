// AppointmentMappers.kt — Firestore document -> Appointment mapping.
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.Appointment
import java.time.Instant

/** Builds an [Appointment] from a Firestore `appointments` document. */
fun Map<String, Any?>.toAppointment(id: String): Appointment = Appointment(
    id = id,
    patientId = this["patientId"] as? String,
    doctorId = this["doctorId"] as? String,
    dateTimeMs = appointmentMillisOf(this["dateTime"]),
    type = this["type"] as? String ?: "Appointment",
    status = (this["status"] as? String ?: "scheduled").lowercase(),
    notes = this["notes"] as? String ?: "",
    requestedByRole = (this["requestedByRole"] as? String ?: "doctor").lowercase()
)

private fun appointmentMillisOf(raw: Any?): Long? = when (raw) {
    is Timestamp -> raw.toDate().time
    is String -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    else -> null
}
