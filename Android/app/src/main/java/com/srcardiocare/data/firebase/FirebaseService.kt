// FirebaseService.kt — Backward-compatible facade over the domain repositories.
//
// The Firebase data layer is split into focused repositories (AuthRepository,
// UserRepository, ExerciseRepository, PlanRepository, AssignmentRepository,
// SessionRepository, WorkoutRepository, AppointmentRepository,
// NotificationRepository, ChatRepository, FeedbackRepository, SettingsRepository).
// This object preserves the original FirebaseService API so existing callers keep
// working; each member simply delegates to the relevant repository. Prefer calling
// the repositories directly in new code.
package com.srcardiocare.data.firebase

import kotlinx.coroutines.flow.Flow

object FirebaseService {

    // ── Auth State ──────────────────────────────────────────────────────
    val currentUID: String? get() = AuthRepository.currentUID
    val isAuthenticated: Boolean get() = AuthRepository.isAuthenticated

    // ── Auth ────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): Map<String, Any?> =
        AuthRepository.login(email, password)

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: String
    ): Map<String, Any?> = AuthRepository.register(email, password, firstName, lastName, role)

    suspend fun registerOther(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: String
    ): String = AuthRepository.registerOther(email, password, firstName, lastName, role)

    fun logout() = AuthRepository.logout()

    suspend fun changePassword(oldPassword: String, newPassword: String) =
        AuthRepository.changePassword(oldPassword, newPassword)

    suspend fun fetchLoginLogs(limit: Int = 150): List<Pair<String, Map<String, Any?>>> =
        AuthRepository.fetchLoginLogs(limit)

    // ── Users ───────────────────────────────────────────────────────────
    suspend fun fetchUser(uid: String): Map<String, Any?> = UserRepository.fetchUser(uid)

    suspend fun fetchCurrentUser(): Map<String, Any?> = UserRepository.fetchCurrentUser()

    suspend fun updateUser(fields: Map<String, Any>) = UserRepository.updateUser(fields)

    suspend fun updateLastSeen() = UserRepository.updateLastSeen()

    suspend fun updateUserById(uid: String, fields: Map<String, Any>) =
        UserRepository.updateUserById(uid, fields)

    suspend fun setUserAccessBlocked(uid: String, blocked: Boolean, reason: String? = null) =
        UserRepository.setUserAccessBlocked(uid, blocked, reason)

    suspend fun deleteUser(uid: String) = UserRepository.deleteUser(uid)

    suspend fun fetchPatients(doctorId: String): List<Pair<String, Map<String, Any?>>> =
        UserRepository.fetchPatients(doctorId)

    suspend fun fetchAllPatients(): List<Pair<String, Map<String, Any?>>> =
        UserRepository.fetchAllPatients()

    suspend fun fetchAllUsers(): List<Pair<String, Map<String, Any?>>> =
        UserRepository.fetchAllUsers()

    suspend fun fetchAllDoctors(): List<Pair<String, Map<String, Any?>>> =
        UserRepository.fetchAllDoctors()

    suspend fun deletePatient(patientId: String) = UserRepository.deletePatient(patientId)

    // ── Exercises ───────────────────────────────────────────────────────
    suspend fun fetchExercises(category: String? = null): List<Pair<String, Map<String, Any?>>> =
        ExerciseRepository.fetchExercises(category)

    suspend fun createExercise(data: Map<String, Any>): String =
        ExerciseRepository.createExercise(data)

    suspend fun deleteExercise(exerciseId: String, videoUrl: String?) =
        ExerciseRepository.deleteExercise(exerciseId, videoUrl)

    suspend fun uploadVideo(data: ByteArray, mimeType: String = "video/mp4"): String =
        ExerciseRepository.uploadVideo(data, mimeType)

    // ── Plans ───────────────────────────────────────────────────────────
    suspend fun fetchPlans(patientId: String): List<Pair<String, Map<String, Any?>>> =
        PlanRepository.fetchPlans(patientId)

    suspend fun createPlan(data: Map<String, Any>): String = PlanRepository.createPlan(data)

    suspend fun assignExerciseToPatient(patientId: String, exerciseData: Map<String, Any>) =
        PlanRepository.assignExerciseToPatient(patientId, exerciseData)

    suspend fun assignExerciseToPatientWithPrescription(
        patientId: String,
        exerciseData: Map<String, Any>,
        expiryDays: Int,
        expiryDate: String
    ) = PlanRepository.assignExerciseToPatientWithPrescription(patientId, exerciseData, expiryDays, expiryDate)

    suspend fun removeExerciseFromPlan(patientId: String, exerciseData: Map<String, Any>) =
        PlanRepository.removeExerciseFromPlan(patientId, exerciseData)

    // ── Doctor Feedback ─────────────────────────────────────────────────
    suspend fun sendFeedback(patientId: String, message: String) =
        FeedbackRepository.sendFeedback(patientId, message)

    // ── Workouts ────────────────────────────────────────────────────────
    suspend fun fetchWorkouts(patientId: String): List<Pair<String, Map<String, Any?>>> =
        WorkoutRepository.fetchWorkouts(patientId)

    suspend fun startWorkout(planId: String, totalExercises: Int): String =
        WorkoutRepository.startWorkout(planId, totalExercises)

    suspend fun completeWorkout(id: String, exercisesCompleted: Int) =
        WorkoutRepository.completeWorkout(id, exercisesCompleted)

    suspend fun incrementExerciseProgress(patientId: String, planId: String, totalCount: Int): String? =
        WorkoutRepository.incrementExerciseProgress(patientId, planId, totalCount)

    suspend fun submitFeedback(workoutId: String, painLevel: Int, difficulty: Int, notes: String?) =
        WorkoutRepository.submitFeedback(workoutId, painLevel, difficulty, notes)

    suspend fun fetchWorkoutCompletionsToday(patientId: String): Int =
        WorkoutRepository.fetchWorkoutCompletionsToday(patientId)

    // ── Appointments ────────────────────────────────────────────────────
    suspend fun fetchAppointments(userId: String, role: String): List<Pair<String, Map<String, Any?>>> =
        AppointmentRepository.fetchAppointments(userId, role)

    suspend fun fetchAppointmentsUnordered(userId: String, role: String): List<Pair<String, Map<String, Any?>>> =
        AppointmentRepository.fetchAppointmentsUnordered(userId, role)

    suspend fun createAppointment(data: Map<String, Any>): String =
        AppointmentRepository.createAppointment(data)

    suspend fun updateAppointment(id: String, fields: Map<String, Any>) =
        AppointmentRepository.updateAppointment(id, fields)

    // ── Notifications ───────────────────────────────────────────────────
    suspend fun writeNotification(
        userId: String,
        title: String,
        body: String,
        type: String,
        route: String,
        params: Map<String, String> = emptyMap()
    ): String = NotificationRepository.writeNotification(userId, title, body, type, route, params)

    suspend fun fetchNotifications(userId: String): List<Pair<String, Map<String, Any?>>> =
        NotificationRepository.fetchNotifications(userId)

    fun observeNotifications(userId: String): Flow<List<Pair<String, Map<String, Any?>>>> =
        NotificationRepository.observeNotifications(userId)

    suspend fun markNotificationRead(id: String) = NotificationRepository.markNotificationRead(id)

    suspend fun markAllNotificationsRead(userId: String) =
        NotificationRepository.markAllNotificationsRead(userId)

    suspend fun markNotificationsReadByType(userId: String, type: String) =
        NotificationRepository.markNotificationsReadByType(userId, type)

    // ── Post-Workout Feedback ───────────────────────────────────────────
    suspend fun submitPostWorkoutFeedback(data: Map<String, Any?>) =
        FeedbackRepository.submitPostWorkoutFeedback(data)

    suspend fun fetchPatientFeedbacks(patientId: String): List<Pair<String, Map<String, Any?>>> =
        FeedbackRepository.fetchPatientFeedbacks(patientId)

    suspend fun fetchDoctorFeedbacks(doctorId: String): List<Pair<String, Map<String, Any?>>> =
        FeedbackRepository.fetchDoctorFeedbacks(doctorId)

    suspend fun fetchAllFeedbacks(): List<Pair<String, Map<String, Any?>>> =
        FeedbackRepository.fetchAllFeedbacks()

    // ── Chat Messaging ──────────────────────────────────────────────────
    suspend fun sendChatMessage(patientId: String, senderId: String, senderName: String, text: String) =
        ChatRepository.sendChatMessage(patientId, senderId, senderName, text)

    fun observeChatMessages(patientId: String): Flow<List<Map<String, Any?>>> =
        ChatRepository.observeChatMessages(patientId)

    // ── Global Settings ─────────────────────────────────────────────────
    suspend fun fetchAccessControlSettings(): AccessControlSettings =
        SettingsRepository.fetchAccessControlSettings()

    suspend fun updateAccessControlSettings(
        sessionLocksEnabled: Boolean? = null,
        blockAllPatients: Boolean? = null,
        blockAllDoctors: Boolean? = null
    ) = SettingsRepository.updateAccessControlSettings(sessionLocksEnabled, blockAllPatients, blockAllDoctors)

    suspend fun fetchSessionLocksEnabled(): Boolean = SettingsRepository.fetchSessionLocksEnabled()

    suspend fun updateSessionLocksEnabled(enabled: Boolean) =
        SettingsRepository.updateSessionLocksEnabled(enabled)

    // ── Exercise Assignments ────────────────────────────────────────────
    suspend fun createAssignment(data: Map<String, Any>): String =
        AssignmentRepository.createAssignment(data)

    suspend fun fetchAssignments(patientId: String): List<Pair<String, Map<String, Any?>>> =
        AssignmentRepository.fetchAssignments(patientId)

    suspend fun fetchDoctorAssignments(doctorId: String): List<Pair<String, Map<String, Any?>>> =
        AssignmentRepository.fetchDoctorAssignments(doctorId)

    suspend fun fetchAssignmentById(assignmentId: String): Map<String, Any?>? =
        AssignmentRepository.fetchAssignmentById(assignmentId)

    suspend fun updateAssignment(assignmentId: String, updates: Map<String, Any>) =
        AssignmentRepository.updateAssignment(assignmentId, updates)

    suspend fun deactivateAssignment(assignmentId: String) =
        AssignmentRepository.deactivateAssignment(assignmentId)

    // ── Session Logging ─────────────────────────────────────────────────
    suspend fun startSession(
        assignmentId: String,
        sessionDate: String,
        sessionNumber: Int,
        totalSets: Int
    ): String = SessionRepository.startSession(assignmentId, sessionDate, sessionNumber, totalSets)

    suspend fun logSetCompletion(
        sessionId: String,
        setNumber: Int,
        videoWatchedSeconds: Int,
        repsCompleted: Int?
    ) = SessionRepository.logSetCompletion(sessionId, setNumber, videoWatchedSeconds, repsCompleted)

    suspend fun completeSession(sessionId: String, feedbackId: String? = null) =
        SessionRepository.completeSession(sessionId, feedbackId)

    suspend fun abandonSession(sessionId: String) = SessionRepository.abandonSession(sessionId)

    suspend fun fetchSessionsForDate(
        assignmentId: String,
        sessionDate: String
    ): List<Pair<String, Map<String, Any?>>> =
        SessionRepository.fetchSessionsForDate(assignmentId, sessionDate)

    suspend fun fetchAllSessionsForAssignment(
        assignmentId: String
    ): List<Pair<String, Map<String, Any?>>> =
        SessionRepository.fetchAllSessionsForAssignment(assignmentId)

    suspend fun fetchTodaysSessions(patientId: String): List<Pair<String, Map<String, Any?>>> =
        SessionRepository.fetchTodaysSessions(patientId)

    suspend fun findInProgressSession(patientId: String): Pair<String, Map<String, Any?>>? =
        SessionRepository.findInProgressSession(patientId)

    suspend fun getCompletedSessionCount(assignmentId: String, sessionDate: String): Int =
        SessionRepository.getCompletedSessionCount(assignmentId, sessionDate)
}
