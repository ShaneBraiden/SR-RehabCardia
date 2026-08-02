// PatientTransferRepository.kt — Moving a patient between clinicians.
package com.srcardiocare.data.firebase

import android.util.Log
import kotlinx.coroutines.tasks.await

/**
 * Reassigns a patient to a different doctor.
 *
 * `users.assignedDoctorId` is only half the move. Every clinical collection
 * carries a denormalised `doctorId` so that list queries can be authorised per
 * document without a lookup, and those stamps are written once — at creation —
 * from whoever was signed in at the time. An admin who creates a patient,
 * prescribes for them, and *then* hands them to a doctor leaves records stamped
 * with the admin's uid.
 *
 * The consequence is not a partial read. A doctor querying `plans` or
 * `assignments` by `patientId` alone is authorised per returned document, so a
 * single foreign record fails the entire query — which is what "failing to add
 * exercise" looked like: the read at the top of the assign flow threw before
 * anything could be written.
 *
 * Restamping on transfer keeps the denormalised stamp in step with
 * `assignedDoctorId`, which is the invariant the rules were written against.
 * Run as an admin: `update` on these collections is admin-or-owner, and by
 * definition the records being repaired are not owned by the incoming doctor.
 */
object PatientTransferRepository {

    private const val TAG = "PatientTransfer"

    /**
     * Collections keyed by `patientId` whose `doctorId` identifies the
     * responsible clinician, and which should follow the patient on transfer.
     *
     * `feedback` is deliberately absent: those are doctor-authored messages,
     * and re-attributing them would put words in the new doctor's mouth.
     * `appointments` likewise — an appointment belongs to whoever booked it.
     */
    private val TRANSFERRED_COLLECTIONS = listOf(
        "plans",
        "assignments",
        "sessionLogs",
        "workouts",
        "postWorkoutFeedback"
    )

    /** Firestore caps a single batch at 500 writes. */
    private const val BATCH_LIMIT = 500

    /**
     * Points [patientId] at [newDoctorId] and restamps their clinical records.
     *
     * The `users` write happens first and is allowed to throw — if the patient
     * is not moved there is nothing to restamp. Restamping is best-effort per
     * collection: a partial repair still strictly improves the doctor's reach,
     * and failing the whole transfer over one stale sub-collection would leave
     * the patient in a worse state than before.
     *
     * @return the number of records restamped.
     */
    suspend fun reassignPatient(patientId: String, newDoctorId: String): Int {
        FirebaseClients.db.collection("users").document(patientId)
            .update("assignedDoctorId", newDoctorId).await()

        return restampPatientRecords(patientId, newDoctorId)
    }

    /**
     * Rewrites `doctorId` to [newDoctorId] on every transferable record
     * belonging to [patientId]. Safe to re-run: records already stamped
     * correctly are skipped, so this doubles as a repair for patients moved by
     * an older build that only wrote `assignedDoctorId`.
     */
    suspend fun restampPatientRecords(patientId: String, newDoctorId: String): Int {
        if (patientId.isBlank() || newDoctorId.isBlank()) return 0

        var restamped = 0
        for (collection in TRANSFERRED_COLLECTIONS) {
            restamped += runCatching { restampCollection(collection, patientId, newDoctorId) }
                .onFailure { Log.w(TAG, "Restamp of $collection failed for $patientId", it) }
                .getOrDefault(0)
        }
        return restamped
    }

    private suspend fun restampCollection(
        collection: String,
        patientId: String,
        newDoctorId: String
    ): Int {
        val snapshot = FirebaseClients.db.collection(collection)
            .whereEqualTo("patientId", patientId)
            .get().await()

        val stale = snapshot.documents.filter { it.getString("doctorId") != newDoctorId }
        if (stale.isEmpty()) return 0

        stale.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = FirebaseClients.db.batch()
            chunk.forEach { batch.update(it.reference, "doctorId", newDoctorId) }
            batch.commit().await()
        }
        return stale.size
    }
}
