package com.srcardiocare.core.push

/**
 * Closed taxonomy of every notification the app can send.
 *
 * Each variant is responsible for deriving the four things every push needs:
 * recipient ([userId]), user-visible [title] / [body], the [route] the tap
 * should open inside the app, and any extra [params] that route requires.
 *
 * The sealed-class shape means any new event must be declared here, keeping
 * deep-link wiring centralised instead of scattered across call-sites.
 */
sealed class NotificationEvent {
    abstract val userId: String
    abstract val title: String
    abstract val body: String
    abstract val type: String
    abstract val channelId: String
    abstract val route: String
    abstract val params: Map<String, String>

    data class ExerciseAssigned(
        val patientId: String,
        val exerciseName: String
    ) : NotificationEvent() {
        override val userId get() = patientId
        override val title get() = "New exercise assigned"
        override val body get() = "Your care team added \"$exerciseName\" to your plan."
        override val type get() = "exercise"
        override val channelId get() = PushChannels.GENERAL
        override val route get() = DeepLink.Routes.EXERCISES
        override val params get() = emptyMap<String, String>()
    }

    data class PrescriptionUpdated(
        val patientId: String,
        val exerciseName: String,
        val expiryDate: String
    ) : NotificationEvent() {
        override val userId get() = patientId
        override val title get() = "Prescription updated"
        override val body get() = "\"$exerciseName\" is active until $expiryDate."
        override val type get() = "prescription"
        override val channelId get() = PushChannels.GENERAL
        override val route get() = DeepLink.Routes.EXERCISES
        override val params get() = emptyMap<String, String>()
    }

    data class DoctorFeedback(
        val patientId: String,
        val preview: String
    ) : NotificationEvent() {
        override val userId get() = patientId
        override val title get() = "Feedback from your doctor"
        override val body get() = preview.ifBlank { "Tap to read your doctor's note." }
        override val type get() = "feedback"
        override val channelId get() = PushChannels.GENERAL
        override val route get() = DeepLink.Routes.CHAT_PATIENT
        override val params get() = emptyMap<String, String>()
    }

    data class ChatMessage(
        val recipientId: String,
        val senderName: String,
        val preview: String,
        val chatThreadId: String,
        val senderIsClinician: Boolean
    ) : NotificationEvent() {
        override val userId get() = recipientId
        override val title get() = senderName.ifBlank { "New message" }
        override val body get() = preview.ifBlank { "You have a new message." }
        override val type get() = "message"
        override val channelId get() = PushChannels.CHAT
        override val route get() =
            if (senderIsClinician) DeepLink.Routes.CHAT_PATIENT else DeepLink.Routes.CHAT_CLINICIAN
        override val params get() =
            if (senderIsClinician) emptyMap() else mapOf("patientId" to chatThreadId)
    }

    data class AppointmentUpdated(
        val recipientId: String,
        override val title: String,
        override val body: String,
        val appointmentId: String,
        val action: String
    ) : NotificationEvent() {
        override val userId get() = recipientId
        override val type get() = "appointment_update"
        override val channelId get() = PushChannels.APPOINTMENTS
        override val route get() = DeepLink.Routes.SCHEDULE
        override val params get() = mapOf(
            "appointmentId" to appointmentId,
            "action" to action
        )
    }

    data class AppointmentScheduled(
        val patientId: String,
        val appointmentType: String,
        val whenText: String,
        val appointmentId: String
    ) : NotificationEvent() {
        override val userId get() = patientId
        override val title get() = "Appointment scheduled"
        override val body get() = "$appointmentType at $whenText."
        override val type get() = "appointment"
        override val channelId get() = PushChannels.APPOINTMENTS
        override val route get() = DeepLink.Routes.SCHEDULE
        override val params get() = mapOf("appointmentId" to appointmentId)
    }

    data class AppointmentRequested(
        val doctorId: String,
        val appointmentType: String,
        val whenText: String,
        val appointmentId: String
    ) : NotificationEvent() {
        override val userId get() = doctorId
        override val title get() = "New appointment request"
        override val body get() = "$appointmentType requested for $whenText."
        override val type get() = "appointment_request"
        override val channelId get() = PushChannels.APPOINTMENTS
        override val route get() = DeepLink.Routes.SCHEDULE
        override val params get() = mapOf("appointmentId" to appointmentId)
    }

    data class DoctorAssigned(
        val patientId: String,
        val doctorName: String
    ) : NotificationEvent() {
        override val userId get() = patientId
        override val title get() = "Care team update"
        override val body get() = "$doctorName is now your doctor."
        override val type get() = "doctor_assigned"
        override val channelId get() = PushChannels.GENERAL
        override val route get() = DeepLink.Routes.PROFILE_PATIENT
        override val params get() = emptyMap<String, String>()
    }

    data class WorkoutReminder(
        val patientId: String
    ) : NotificationEvent() {
        override val userId get() = patientId
        override val title get() = "Don't forget your workout"
        override val body get() = "You still have exercises to finish today. Tap to view your plan."
        override val type get() = "workout_reminder"
        override val channelId get() = PushChannels.GENERAL
        override val route get() = DeepLink.Routes.EXERCISES
        override val params get() = emptyMap<String, String>()
    }
}
