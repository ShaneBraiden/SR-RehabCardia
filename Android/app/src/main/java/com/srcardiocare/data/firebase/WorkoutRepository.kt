// WorkoutRepository.kt — Legacy `workouts` sessions (read-only on Android).
package com.srcardiocare.data.firebase

import com.srcardiocare.data.model.WorkoutSession
import kotlinx.coroutines.tasks.await

/**
 * Reads `workouts` documents (the original plan-based workout flow). The write
 * path was retired when the patient flow moved to `assignments`/`sessionLogs`;
 * the doctor dashboard still reads historical workout sessions from here.
 */
object WorkoutRepository {

    suspend fun fetchWorkouts(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("workouts")
            .whereEqualTo("patientId", patientId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
            .sortedByDescending { (it.second["startedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
    }

    /** Typed read. Prefer this in new code. */
    suspend fun getWorkouts(patientId: String): List<WorkoutSession> =
        fetchWorkouts(patientId).map { (id, data) -> data.toWorkoutSession(id) }
}
