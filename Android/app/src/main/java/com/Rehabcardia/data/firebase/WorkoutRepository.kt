// WorkoutRepository.kt — Legacy workout sessions and their feedback.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import com.srcardiocare.data.model.WorkoutSession
import kotlinx.coroutines.tasks.await

/** Tracks `workouts` documents (the original plan-based workout flow). */
object WorkoutRepository {

    suspend fun fetchWorkouts(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("workouts")
            .whereEqualTo("patientId", patientId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
            .sortedByDescending { (it.second["startedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
    }

    suspend fun startWorkout(planId: String, totalExercises: Int): String {
        val uid = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val ref = FirebaseClients.db.collection("workouts").document()
        val data = hashMapOf<String, Any?>(
            "id" to ref.id,
            "patientId" to uid,
            "planId" to planId,
            "startedAt" to FieldValue.serverTimestamp(),
            "completedAt" to null,
            "exercisesCompleted" to 0,
            "totalExercises" to totalExercises
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun completeWorkout(id: String, exercisesCompleted: Int) {
        FirebaseClients.db.collection("workouts").document(id).update(
            mapOf(
                "completedAt" to FieldValue.serverTimestamp(),
                "exercisesCompleted" to exercisesCompleted
            )
        ).await()
    }

    suspend fun incrementExerciseProgress(patientId: String, planId: String, totalCount: Int): String? {
        val workouts = fetchWorkouts(patientId)
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        val latest = workouts.firstOrNull { (it.second["startedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0 >= todayStart }

        var workoutId = latest?.first
        var comp = (latest?.second?.get("exercisesCompleted") as? Number)?.toInt() ?: 0

        // If no workout today, or the latest one is already finished, start a new one
        if (workoutId == null || comp >= totalCount) {
            workoutId = startWorkout(planId, totalCount)
            comp = 0
        }

        if (comp < totalCount) {
            val newComp = comp + 1
            if (newComp >= totalCount) {
                completeWorkout(workoutId, newComp)
            } else {
                FirebaseClients.db.collection("workouts").document(workoutId).update("exercisesCompleted", newComp).await()
            }
            return workoutId
        }
        return null
    }

    suspend fun submitFeedback(workoutId: String, painLevel: Int, difficulty: Int, notes: String?) {
        val ref = FirebaseClients.db.collection("workouts").document(workoutId)
            .collection("feedback").document()
        val data = hashMapOf<String, Any?>(
            "painLevel" to painLevel,
            "difficulty" to difficulty,
            "notes" to notes,
            "submittedAt" to FieldValue.serverTimestamp()
        )
        ref.set(data).await()
    }

    /** Count how many workouts were completed today for a specific patient. */
    suspend fun fetchWorkoutCompletionsToday(patientId: String): Int {
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
        val timestamp = com.google.firebase.Timestamp(todayStart.epochSecond, 0)

        return try {
            val snapshot = FirebaseClients.db.collection("workouts")
                .whereEqualTo("patientId", patientId)
                .whereGreaterThanOrEqualTo("completedAt", timestamp)
                .get().await()
            snapshot.size()
        } catch (_: Exception) {
            // If composite index missing, count manually
            val snapshot = FirebaseClients.db.collection("workouts")
                .whereEqualTo("patientId", patientId)
                .get().await()
            snapshot.documents.count { doc ->
                val completedAt = doc.getTimestamp("completedAt")
                completedAt != null && completedAt.toDate().toInstant().isAfter(todayStart)
            }
        }
    }

    /** Typed read. Prefer this in new code. */
    suspend fun getWorkouts(patientId: String): List<WorkoutSession> =
        fetchWorkouts(patientId).map { (id, data) -> data.toWorkoutSession(id) }
}
