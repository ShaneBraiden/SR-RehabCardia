// Models.kt — Shared data classes for SR-Cardiocare Android
package com.srcardiocare.data.model

import java.util.UUID

/**
 * A user document from the `users` collection. Patient- and doctor-specific
 * profile fields are stored flat on the same document; only the ones relevant
 * to a given role are populated.
 */
data class User(
    val id: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val role: String = "",                  // "patient" | "doctor" | "admin"
    val phone: String? = null,
    val profileImageUrl: String? = null,
    val assignedDoctorId: String? = null,
    val isBlocked: Boolean = false,
    val apiAccessBlocked: Boolean = false,
    val blockReason: String? = null,
    val lastSeenMs: Long? = null,
    val hasCompletedOnboarding: Boolean = false,
    // Patient profile fields
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val injuries: List<String> = emptyList(),
    val primaryGoal: String? = null,
    // Doctor profile fields
    val speciality: String? = null,
    val licenseNumber: String? = null,
    val clinicName: String? = null
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    /** True if the user was active within the last 5 minutes (presence indicator). */
    val isOnline: Boolean
        get() = lastSeenMs?.let { System.currentTimeMillis() - it < 5 * 60 * 1000 } ?: false
}

data class Exercise(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val difficultyLevel: String = "",
    val durationSeconds: Int = 0,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val uploadedBy: String = "",
    val sets: Int = 3,
    val reps: Int = 10,
    val instructions: String? = null
)

data class ExercisePlan(
    val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val doctorId: String,
    val name: String,
    val exercises: List<PlanExercise>,
    val startDate: String,
    val endDate: String? = null,
    val isActive: Boolean = true
)

data class PlanExercise(
    val exerciseId: String,
    val order: Int,
    val customSets: Int? = null,
    val customReps: Int? = null,
    val notes: String? = null,
    val expiryDate: String? = null // ISO date string when this workout expires
)

data class WorkoutSession(
    val id: String = "",
    val patientId: String = "",
    val planId: String = "",
    val startedAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val exercisesCompleted: Int = 0,
    val totalExercises: Int = 0
)

data class WorkoutFeedback(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val painLevel: Int,      // 0-10
    val difficulty: Int,      // 1-5
    val notes: String? = null,
    val submittedAt: String? = null
)

data class Appointment(
    val id: String = "",
    val patientId: String? = null,
    val doctorId: String? = null,
    val dateTimeMs: Long? = null,
    val type: String = "Appointment",
    val status: String = "scheduled",         // stored lowercase
    val notes: String = "",
    val requestedByRole: String = "doctor"
)

enum class AppointmentStatus { SCHEDULED, PENDING, CONFIRMED, CANCELLED, COMPLETED }

data class AppNotification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "",
    val route: String = "",
    val params: Map<String, String> = emptyMap(),
    val isRead: Boolean = false,
    val createdAtMs: Long? = null
)

/** Post-workout cardiac feedback (collection: "postWorkoutFeedback"). */
data class PostWorkoutFeedback(
    val id: String = "",
    val patientId: String = "",
    val workoutId: String? = null,
    val respiratoryDifficulty: Int = 1,
    val stress: Boolean = false,
    val strain: Boolean = false,
    val notes: String? = null,
    val submittedAtMs: Long? = null
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestampMs: Long? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
// EXERCISE ASSIGNMENT SYSTEM
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Assignment: Doctor assigns an exercise to a patient with specific dates and frequency.
 * Each assignment has its own lifecycle (active → expired → history).
 */
data class Assignment(
    val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val doctorId: String,
    val exerciseId: String,
    val exerciseName: String,           // Denormalized for display
    val exerciseVideoUrl: String? = null,
    val exerciseThumbnailUrl: String? = null,
    val exerciseCategory: String? = null,
    val exerciseDifficulty: String? = null,
    val startDate: String,              // ISO date (yyyy-MM-dd) - inclusive
    val endDate: String,                // ISO date (yyyy-MM-dd) - inclusive
    val dailyFrequency: Int = 3,        // How many times per day (doctor sets this)
    val sets: Int = 3,                  // Sets per session
    val reps: Int = 10,                 // Reps per set
    val restSeconds: Int = 45,          // Rest between sets
    val instructions: String? = null,   // Doctor's notes for patient
    val completionThreshold: Float = 0.8f, // 0.0-1.0, what % = "fully completed"
    val isActive: Boolean = true,       // Soft delete / reassignment
    val createdAt: String? = null       // Server timestamp
)

/** Status of an assignment after it expires (for history) */
enum class AssignmentHistoryStatus {
    FULLY_COMPLETED,    // Met completion threshold
    PARTIALLY_COMPLETED, // Did some but below threshold
    MISSED              // Did nothing
}

/**
 * SessionLog: One entry per exercise session attempt.
 * Patient can do up to dailyFrequency sessions per day.
 */
data class SessionLog(
    val id: String = UUID.randomUUID().toString(),
    val assignmentId: String,
    val patientId: String,
    val sessionDate: String,            // ISO date (yyyy-MM-dd) - which day
    val sessionNumber: Int,             // 1, 2, or 3 for that day
    val startedAt: String? = null,      // ISO timestamp
    val completedAt: String? = null,    // ISO timestamp (null if abandoned)
    val setsCompleted: Int = 0,
    val totalSets: Int = 3,
    val setLogs: List<SetLog> = emptyList(),
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val feedbackId: String? = null      // Link to WorkoutFeedback if submitted
)

enum class SessionStatus {
    IN_PROGRESS,  // Currently doing this session
    COMPLETED,    // Finished all sets and clicked complete
    ABANDONED     // Started but didn't finish (partial)
}

/**
 * SetLog: Tracks individual set completion within a session.
 * Embedded within SessionLog.
 */
data class SetLog(
    val setNumber: Int,                 // 1, 2, 3...
    val startedAt: String? = null,      // ISO timestamp
    val completedAt: String? = null,    // ISO timestamp
    val videoWatchedSeconds: Int = 0,   // How much video they watched
    val repsCompleted: Int? = null      // Optional manual input
)

// ═══════════════════════════════════════════════════════════════════════════════
// UI STATE MODELS (for composables)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Active exercise item for today's list.
 * Computed from Assignment + today's SessionLogs.
 */
data class ActiveExerciseItem(
    val assignment: Assignment,
    val sessionsToday: Int,             // How many completed today
    val dailyTarget: Int,               // = assignment.dailyFrequency
    val isDoneToday: Boolean,           // sessionsToday >= dailyTarget
    val canStartSession: Boolean,       // !isDoneToday && no in-progress session
    val currentSession: SessionLog? = null, // If there's an in-progress session
    val expiryText: String? = null,     // "Expires in 3 days", "Expires today"
    val progressText: String = "0/3"    // "1/3", "2/3", "3/3"
)

/**
 * History item for expired assignments.
 */
data class HistoryExerciseItem(
    val assignment: Assignment,
    val status: AssignmentHistoryStatus,
    val completionRate: Float,          // 0.0-1.0
    val sessionsCompleted: Int,
    val sessionsPossible: Int,          // totalDays * dailyFrequency
    val endDate: String
)
