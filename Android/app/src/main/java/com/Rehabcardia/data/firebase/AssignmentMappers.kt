// AssignmentMappers.kt — Firestore document -> Assignment / SessionLog mapping.
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.Assignment
import com.srcardiocare.data.model.SessionLog
import com.srcardiocare.data.model.SessionStatus
import com.srcardiocare.data.model.SetLog
import java.time.LocalDate

/** Builds an [Assignment] from a Firestore `assignments` document (id + raw field map). */
fun Map<String, Any?>.toAssignment(id: String): Assignment = Assignment(
    id = id,
    patientId = this["patientId"] as? String ?: "",
    doctorId = this["doctorId"] as? String ?: "",
    exerciseId = this["exerciseId"] as? String ?: "",
    exerciseName = this["exerciseName"] as? String ?: "Exercise",
    exerciseVideoUrl = this["exerciseVideoUrl"] as? String,
    exerciseThumbnailUrl = this["exerciseThumbnailUrl"] as? String,
    exerciseCategory = this["exerciseCategory"] as? String,
    exerciseDifficulty = this["exerciseDifficulty"] as? String,
    startDate = this["startDate"] as? String ?: LocalDate.now().toString(),
    endDate = this["endDate"] as? String ?: LocalDate.now().plusDays(7).toString(),
    dailyFrequency = ((this["dailyFrequency"] as? Number)?.toInt() ?: 3).coerceIn(1, 3),
    sets = (this["sets"] as? Number)?.toInt() ?: 3,
    reps = (this["reps"] as? Number)?.toInt() ?: 10,
    restSeconds = (this["restSeconds"] as? Number)?.toInt() ?: 45,
    instructions = this["instructions"] as? String,
    completionThreshold = (this["completionThreshold"] as? Number)?.toFloat() ?: 0.8f,
    isActive = this["isActive"] as? Boolean ?: true,
    createdAt = (this["createdAt"] as? Timestamp)?.toDate()?.toString()
)

/** Builds a [SessionLog] (with embedded [SetLog]s) from a Firestore `sessionLogs` document. */
fun Map<String, Any?>.toSessionLog(id: String): SessionLog {
    val setLogs = (this["setLogs"] as? List<*> ?: emptyList<Any?>()).mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        SetLog(
            setNumber = (map["setNumber"] as? Number)?.toInt() ?: 0,
            startedAt = map["startedAt"] as? String,
            completedAt = map["completedAt"] as? String,
            videoWatchedSeconds = (map["videoWatchedSeconds"] as? Number)?.toInt() ?: 0,
            repsCompleted = (map["repsCompleted"] as? Number)?.toInt()
        )
    }

    return SessionLog(
        id = id,
        assignmentId = this["assignmentId"] as? String ?: "",
        patientId = this["patientId"] as? String ?: "",
        sessionDate = this["sessionDate"] as? String ?: LocalDate.now().toString(),
        sessionNumber = (this["sessionNumber"] as? Number)?.toInt() ?: 1,
        startedAt = (this["startedAt"] as? Timestamp)?.toDate()?.toString(),
        completedAt = (this["completedAt"] as? Timestamp)?.toDate()?.toString(),
        setsCompleted = (this["setsCompleted"] as? Number)?.toInt() ?: 0,
        totalSets = (this["totalSets"] as? Number)?.toInt() ?: 3,
        setLogs = setLogs,
        status = SessionStatus.valueOf(this["status"] as? String ?: "IN_PROGRESS"),
        feedbackId = this["feedbackId"] as? String
    )
}
