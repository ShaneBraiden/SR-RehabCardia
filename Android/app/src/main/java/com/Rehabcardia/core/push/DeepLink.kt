package com.srcardiocare.core.push

import androidx.navigation.NavController
import com.srcardiocare.navigation.Route

/**
 * Converts an abstract notification [Routes] id + params map into a concrete
 * [NavController] navigation call.
 *
 * Keeping the mapping here means notification events can reference stable
 * symbolic names ([Routes.CHAT_PATIENT] etc.) without hard-coding NavGraph
 * path strings in every event, and lets role-specific divergence live in one
 * place.
 */
object DeepLink {

    object Routes {
        const val EXERCISES = "exercises"
        const val SCHEDULE = "schedule"
        const val NOTIFICATIONS = "notifications"
        const val CHAT_PATIENT = "chat_patient"
        const val CHAT_CLINICIAN = "chat_clinician"
        const val PROFILE_PATIENT = "profile_patient"
        const val PATIENT_PROFILE_DOCTOR = "patient_profile_doctor"
    }

    fun navigate(navController: NavController, route: String, params: Map<String, String>) {
        val path: String = when (route) {
            Routes.EXERCISES -> Route.AssignmentList.path
            Routes.SCHEDULE -> Route.Schedule.path
            Routes.NOTIFICATIONS -> Route.Notifications.path
            Routes.CHAT_PATIENT -> Route.PatientChat.path
            Routes.CHAT_CLINICIAN -> params["patientId"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Route.PatientFeedbackChat.createPath(it) }
                ?: Route.FeedbackDashboard.path
            Routes.PROFILE_PATIENT -> Route.PatientProfileSelf.path
            Routes.PATIENT_PROFILE_DOCTOR -> params["patientId"]
                ?.takeIf { it.isNotBlank() }
                ?.let { "doctor/patient/$it" }
                ?: Route.PatientList.path
            else -> Route.Notifications.path
        }
        navController.navigate(path) { launchSingleTop = true }
    }
}
