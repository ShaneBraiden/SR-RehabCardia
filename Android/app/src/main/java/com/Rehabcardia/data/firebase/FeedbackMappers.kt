// FeedbackMappers.kt — Firestore document -> PostWorkoutFeedback mapping.
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.PostWorkoutFeedback

/** Builds a [PostWorkoutFeedback] from a Firestore `postWorkoutFeedback` document. */
fun Map<String, Any?>.toPostWorkoutFeedback(id: String): PostWorkoutFeedback = PostWorkoutFeedback(
    id = id,
    patientId = this["patientId"] as? String ?: "",
    workoutId = this["workoutId"] as? String,
    respiratoryDifficulty = (this["respiratoryDifficulty"] as? Number)?.toInt() ?: 1,
    stress = this["stress"] as? Boolean ?: false,
    strain = this["strain"] as? Boolean ?: false,
    notes = this["notes"] as? String,
    submittedAtMs = (this["submittedAt"] as? Timestamp)?.toDate()?.time
)
