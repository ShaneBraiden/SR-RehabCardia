// FeedbackMappers.kt — Firestore document -> PostWorkoutFeedback mapping.
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.PostWorkoutFeedback

/** Builds a [PostWorkoutFeedback] from a Firestore `postWorkoutFeedback` document. */
fun Map<String, Any?>.toPostWorkoutFeedback(id: String): PostWorkoutFeedback = PostWorkoutFeedback(
    id = id,
    patientId = this["patientId"] as? String ?: "",
    workoutId = this["workoutId"] as? String,
    hadPain = this["hadPain"] as? Boolean ?: (((this["painIntensity"] as? Number)?.toInt() ?: 0) > 0),
    painIntensity = (this["painIntensity"] as? Number)?.toInt() ?: 0,
    painLocation = this["painLocation"] as? String,
    // `respiration` is the current field; fall back to the legacy `respiratoryDifficulty` for old docs.
    respiration = (this["respiration"] as? Number)?.toInt()
        ?: (this["respiratoryDifficulty"] as? Number)?.toInt() ?: 0,
    pulseRate = (this["pulseRate"] as? Number)?.toInt(),
    notes = this["notes"] as? String,
    submittedAtMs = (this["submittedAt"] as? Timestamp)?.toDate()?.time
)
