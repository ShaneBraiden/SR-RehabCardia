// WorkoutMappers.kt — Firestore document -> WorkoutSession mapping (legacy workouts).
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.WorkoutSession
import java.time.Instant

/** Builds a [WorkoutSession] from a Firestore `workouts` document. */
fun Map<String, Any?>.toWorkoutSession(id: String): WorkoutSession = WorkoutSession(
    id = id,
    patientId = this["patientId"] as? String ?: "",
    planId = this["planId"] as? String ?: "",
    startedAtMs = workoutMillisOf(this["startedAt"]),
    completedAtMs = workoutMillisOf(this["completedAt"]),
    exercisesCompleted = (this["exercisesCompleted"] as? Number)?.toInt() ?: 0,
    totalExercises = (this["totalExercises"] as? Number)?.toInt() ?: 0
)

private fun workoutMillisOf(raw: Any?): Long? = when (raw) {
    is Timestamp -> raw.toDate().time
    is String -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    else -> null
}
