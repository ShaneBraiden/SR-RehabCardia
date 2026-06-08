// ExerciseMappers.kt — Firestore document -> Exercise mapping.
package com.srcardiocare.data.firebase

import com.srcardiocare.data.model.Exercise

/** Builds an [Exercise] from a Firestore `exercises` document. */
fun Map<String, Any?>.toExercise(id: String): Exercise = Exercise(
    id = id,
    name = this["name"] as? String ?: "",
    description = this["description"] as? String ?: "",
    category = this["category"] as? String ?: "",
    difficultyLevel = this["difficultyLevel"] as? String ?: "",
    durationSeconds = (this["durationSeconds"] as? Number)?.toInt() ?: 0,
    videoUrl = this["videoUrl"] as? String,
    thumbnailUrl = this["thumbnailUrl"] as? String,
    uploadedBy = this["uploadedBy"] as? String ?: "",
    sets = (this["sets"] as? Number)?.toInt() ?: 3,
    reps = (this["reps"] as? Number)?.toInt() ?: 10,
    instructions = this["instructions"] as? String
)
