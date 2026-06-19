// TutorialTours.kt — Central registry of tour keys, target ids, and copy.
//
// All guided-tour content lives here so wording is editable in one place.
// Screens annotate controls with the same ids they reference via
// `Modifier.tutorialTarget(TutorialIds.X)`, and wrap their root in
// `TutorialHost(tourKey = ..., steps = TutorialTours.<key>)`.
package com.srcardiocare.ui.components.tutorial

/** Stable tourKeys — one per screen. Persisted in `users/{uid}.toursSeen`. */
object TutorialKeys {
    // Patient
    const val PATIENT_HOME = "patient_home"
    const val ASSIGNMENT_LIST = "assignment_list"
    const val EXERCISE_LIST = "exercise_list"
    const val WORKOUT_PLAYER = "workout_player"
    const val ASSIGNMENT_WORKOUT = "assignment_workout"
    const val POST_WORKOUT_FEEDBACK = "post_workout_feedback"
    const val SCHEDULE = "schedule"
    const val ANALYTICS = "analytics"
    const val PATIENT_CHAT = "patient_chat"
    const val PATIENT_PROFILE = "patient_profile"
    const val PATIENT_HISTORY = "patient_history"
    const val NOTIFICATIONS = "notifications"

    // Doctor
    const val DOCTOR_DASHBOARD = "doctor_dashboard"
    const val PATIENT_LIST = "patient_list"
    const val DOCTOR_PATIENT_PROFILE = "doctor_patient_profile"
    const val PATIENT_FEEDBACK_CHAT = "patient_feedback_chat"
    const val EDIT_ASSIGNMENT = "edit_assignment"
    const val ADD_PATIENT = "add_patient"
    const val ADD_DOCTOR = "add_doctor"
    const val VIDEO_UPLOAD = "video_upload"
    const val EXERCISE_LIBRARY = "exercise_library"
    const val FEEDBACK_DASHBOARD = "feedback_dashboard"
    const val DOCTOR_PROFILE = "doctor_profile"

    // Admin
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_SETTINGS = "admin_settings"
    const val ADMIN_DOCTOR_PATIENTS = "admin_doctor_patients"
    const val ADMIN_DOCTOR_PROFILE = "admin_doctor_profile"
    const val ADMIN_LOGIN_LOGS = "admin_login_logs"
    const val ADMIN_PATIENT_ASSIGNMENTS = "admin_patient_assignments"
}

/** Stable ids for highlightable controls, grouped loosely by screen. */
object TutorialIds {
    // ── Patient home ──
    const val HOME_START = "home_start"
    const val HOME_SCHEDULE = "home_schedule"
    const val HOME_PROGRESS = "home_progress"
    const val HOME_CHAT = "home_chat"
    const val HOME_NOTIFICATIONS = "home_notifications"
    const val HOME_PROFILE = "home_profile"

    // ── Assignment list ──
    const val ASSIGNMENT_CARD = "assignment_card"
    const val ASSIGNMENT_START = "assignment_start"
    const val ASSIGNMENT_HISTORY = "assignment_history"

    // ── Exercise list ──
    const val EXERCISE_ROW = "exercise_row"
    const val EXERCISE_START = "exercise_start"

    // ── Workout player ──
    const val PLAYER_VIDEO = "player_video"
    const val PLAYER_FULLSCREEN = "player_fullscreen"
    const val PLAYER_COMPLETE = "player_complete"

    // ── Assignment workout ──
    const val AW_VIDEO = "aw_video"
    const val AW_REP_TARGET = "aw_rep_target"
    const val AW_PRIMARY = "aw_primary"

    // ── Post-workout feedback ──
    const val FEEDBACK_PAIN = "feedback_pain"
    const val FEEDBACK_BORG = "feedback_borg"
    const val FEEDBACK_PULSE = "feedback_pulse"
    const val FEEDBACK_SUBMIT = "feedback_submit"

    // ── Schedule ──
    const val SCHEDULE_CALENDAR = "schedule_calendar"
    const val SCHEDULE_ADD = "schedule_add"

    // ── Analytics ──
    const val ANALYTICS_RANGE = "analytics_range"
    const val ANALYTICS_CHART = "analytics_chart"
    const val ANALYTICS_METRICS = "analytics_metrics"

    // ── Chat (patient + doctor) ──
    const val CHAT_FIELD = "chat_field"
    const val CHAT_SEND = "chat_send"

    // ── Generic profile ──
    const val PROFILE_EDIT = "profile_edit"
    const val PROFILE_PASSWORD = "profile_password"
    const val PROFILE_LOGOUT = "profile_logout"

    // ── History ──
    const val HISTORY_LIST = "history_list"

    // ── Notifications ──
    const val NOTIFICATION_ITEM = "notification_item"
    const val NOTIFICATION_MARK_READ = "notification_mark_read"

    // ── Doctor dashboard ──
    const val DASH_PATIENTS = "dash_patients"
    const val DASH_ADD_PATIENT = "dash_add_patient"
    const val DASH_FEEDBACK = "dash_feedback"
    const val DASH_SCHEDULE = "dash_schedule"
    const val DASH_EXERCISES = "dash_exercises"
    const val DASH_PROFILE = "dash_profile"

    // ── Patient list ──
    const val LIST_SEARCH = "list_search"
    const val LIST_ROW = "list_row"
    const val LIST_ADD = "list_add"

    // ── Doctor's patient profile ──
    const val DPP_METRICS = "dpp_metrics"
    const val DPP_SEND_FEEDBACK = "dpp_send_feedback"
    const val DPP_ASSIGNMENTS = "dpp_assignments"
    const val DPP_HISTORY = "dpp_history"

    // ── Patient feedback chat (doctor) ──
    const val PFC_FEEDBACKS_TAB = "pfc_feedbacks_tab"
    const val PFC_CHAT_TAB = "pfc_chat_tab"

    // ── Edit assignment ──
    const val EA_SETS = "ea_sets"
    const val EA_FREQUENCY = "ea_frequency"
    const val EA_SAVE = "ea_save"

    // ── Add patient / add doctor ──
    const val ADD_FORM = "add_form"
    const val ADD_SAVE = "add_save"

    // ── Video upload ──
    const val UPLOAD_PICK = "upload_pick"
    const val UPLOAD_METADATA = "upload_metadata"
    const val UPLOAD_SUBMIT = "upload_submit"

    // ── Exercise library ──
    const val LIBRARY_ROW = "library_row"
    const val LIBRARY_UPLOAD = "library_upload"

    // ── Feedback dashboard ──
    const val FD_LIST = "fd_list"

    // ── Admin dashboard ──
    const val ADMIN_DOCTORS = "admin_doctors"
    const val ADMIN_PATIENTS = "admin_patients"
    const val ADMIN_SETTINGS_NAV = "admin_settings_nav"
    const val ADMIN_LOGS = "admin_logs"
    const val ADMIN_PROFILE = "admin_profile"

    // ── Admin settings ──
    const val ADMIN_SETTINGS_TOGGLE = "admin_settings_toggle"

    // ── Admin list/detail ──
    const val ADMIN_DOCTOR_PATIENT_ROW = "admin_doctor_patient_row"
    const val ADMIN_LOGIN_LOG_ROW = "admin_login_log_row"
    const val ADMIN_ASSIGNMENT_ROW = "admin_assignment_row"

    // ── Admin doctor profile (manage doctor) ──
    const val ADP_FORM = "adp_form"
    const val ADP_SAVE = "adp_save"
    const val ADP_DELETE = "adp_delete"
}

/** All tours keyed by tourKey. Copy is short, friendly, and action-oriented. */
object TutorialTours {

    // ───────────────────────────── Patient ─────────────────────────────

    val patientHome = listOf(
        TutorialStep(TutorialIds.HOME_START, "Today's session", "Tap here to begin the exercises your care team assigned for today."),
        TutorialStep(TutorialIds.HOME_SCHEDULE, "Schedule", "View appointments and set reminders so you never miss a session."),
        TutorialStep(TutorialIds.HOME_PROGRESS, "Your progress", "See trends and charts that show how your recovery is improving."),
        TutorialStep(TutorialIds.HOME_CHAT, "Message your doctor", "Have a question? Chat directly with your care team here."),
        TutorialStep(TutorialIds.HOME_NOTIFICATIONS, "Notifications", "Reminders and feedback from your doctor land here."),
        TutorialStep(TutorialIds.HOME_PROFILE, "Your profile", "Update your details, change your password, or sign out.")
    )

    val assignmentList = listOf(
        TutorialStep(TutorialIds.ASSIGNMENT_CARD, "Your exercises", "Each card is an exercise your doctor assigned, with its sets, reps, and how often to do it."),
        TutorialStep(TutorialIds.ASSIGNMENT_START, "Start a session", "Tap to begin a guided session for this exercise."),
        TutorialStep(TutorialIds.ASSIGNMENT_HISTORY, "History", "Review the exercises you've completed over time.")
    )

    val exerciseList = listOf(
        TutorialStep(TutorialIds.EXERCISE_ROW, "Your exercises", "Each row is an exercise in today's plan."),
        TutorialStep(TutorialIds.EXERCISE_START, "Start", "Tap an exercise to open its video and begin.")
    )

    val workoutPlayer = listOf(
        TutorialStep(TutorialIds.PLAYER_VIDEO, "Follow along", "Watch the demonstration and move at your own pace."),
        TutorialStep(TutorialIds.PLAYER_FULLSCREEN, "Fullscreen", "Tap to enlarge the video for a clearer view."),
        TutorialStep(TutorialIds.PLAYER_COMPLETE, "Finish the set", "When you've finished, tap here to record it and move on.")
    )

    val assignmentWorkout = listOf(
        TutorialStep(TutorialIds.AW_VIDEO, "Exercise demo", "Watch how the movement is performed before you start."),
        TutorialStep(TutorialIds.AW_REP_TARGET, "Your target", "This shows the reps and sets to aim for in this session."),
        TutorialStep(TutorialIds.AW_PRIMARY, "Begin & continue", "Use this button to start the exercise and move through each set.")
    )

    val postWorkoutFeedback = listOf(
        TutorialStep(TutorialIds.FEEDBACK_PAIN, "Any pain?", "Let us know if you felt pain — and where — during the session."),
        TutorialStep(TutorialIds.FEEDBACK_BORG, "How it felt", "Rate how hard the effort and breathing felt using these sliders."),
        TutorialStep(TutorialIds.FEEDBACK_PULSE, "Your pulse", "Enter your heart rate so your doctor can track your response."),
        TutorialStep(TutorialIds.FEEDBACK_SUBMIT, "Send it", "Submit your feedback to share it with your care team.")
    )

    val schedule = listOf(
        TutorialStep(TutorialIds.SCHEDULE_CALENDAR, "Your calendar", "Tap a day to see appointments and reminders."),
        TutorialStep(TutorialIds.SCHEDULE_ADD, "Add a reminder", "Create reminders so you stay on track with your sessions.")
    )

    val analytics = listOf(
        TutorialStep(TutorialIds.ANALYTICS_RANGE, "Time range", "Switch between week, month, and longer views here."),
        TutorialStep(TutorialIds.ANALYTICS_CHART, "Your trend", "This chart shows how your activity changes over time."),
        TutorialStep(TutorialIds.ANALYTICS_METRICS, "Key numbers", "Quick stats summarize your recovery at a glance.")
    )

    val patientChat = listOf(
        TutorialStep(TutorialIds.CHAT_FIELD, "Write a message", "Type a message to your care team here."),
        TutorialStep(TutorialIds.CHAT_SEND, "Send", "Tap to send your message.")
    )

    val patientProfile = listOf(
        TutorialStep(TutorialIds.PROFILE_EDIT, "Edit your profile", "Keep your personal and health details up to date."),
        TutorialStep(TutorialIds.PROFILE_PASSWORD, "Change password", "Update your password to keep your account secure."),
        TutorialStep(TutorialIds.PROFILE_LOGOUT, "Sign out", "Tap here when you're ready to log out.")
    )

    val patientHistory = listOf(
        TutorialStep(TutorialIds.HISTORY_LIST, "Your history", "Review past exercises and how well each was completed.")
    )

    val notifications = listOf(
        TutorialStep(TutorialIds.NOTIFICATION_ITEM, "Notifications", "Tap an item to open what it's about."),
        TutorialStep(TutorialIds.NOTIFICATION_MARK_READ, "Mark as read", "Clear notifications once you've seen them.")
    )

    // ───────────────────────────── Doctor ──────────────────────────────

    val doctorDashboard = listOf(
        TutorialStep(TutorialIds.DASH_PATIENTS, "Your patients", "Open the full list of patients under your care."),
        TutorialStep(TutorialIds.DASH_ADD_PATIENT, "Add a patient", "Create a new patient account and start assigning exercises."),
        TutorialStep(TutorialIds.DASH_FEEDBACK, "Feedback", "Review post-session feedback your patients submit."),
        TutorialStep(TutorialIds.DASH_PROFILE, "Your profile", "Manage your account and sign out.")
    )

    val patientList = listOf(
        TutorialStep(TutorialIds.LIST_SEARCH, "Search", "Quickly find a patient by name."),
        TutorialStep(TutorialIds.LIST_ROW, "A patient", "Tap a patient to view their profile, plan, and progress.")
    )

    val doctorPatientProfile = listOf(
        TutorialStep(TutorialIds.DPP_METRICS, "Patient metrics", "Charts summarize this patient's recovery and adherence."),
        TutorialStep(TutorialIds.DPP_SEND_FEEDBACK, "Send feedback", "Send a message or feedback directly to this patient."),
        TutorialStep(TutorialIds.DPP_ASSIGNMENTS, "Manage plan", "Assign, edit, or review this patient's exercises."),
        TutorialStep(TutorialIds.DPP_HISTORY, "History", "See everything this patient has completed.")
    )

    val patientFeedbackChat = listOf(
        TutorialStep(TutorialIds.PFC_FEEDBACKS_TAB, "Feedbacks", "Review this patient's submitted session feedback."),
        TutorialStep(TutorialIds.PFC_CHAT_TAB, "Chat", "Switch here to message the patient directly."),
        TutorialStep(TutorialIds.CHAT_SEND, "Send", "Send your message once you've typed it.")
    )

    val editAssignment = listOf(
        TutorialStep(TutorialIds.EA_SETS, "Sets & reps", "Set how many sets and reps this exercise should have."),
        TutorialStep(TutorialIds.EA_FREQUENCY, "Frequency", "Choose how many times per day the patient performs it."),
        TutorialStep(TutorialIds.EA_SAVE, "Save", "Save your changes to update the patient's plan.")
    )

    val addPatient = listOf(
        TutorialStep(TutorialIds.ADD_FORM, "Patient details", "Fill in the new patient's name, email, and other details."),
        TutorialStep(TutorialIds.ADD_SAVE, "Create account", "Save to create the patient's account.")
    )

    val addDoctor = listOf(
        TutorialStep(TutorialIds.ADD_FORM, "Doctor details", "Fill in the new doctor's name, email, and credentials."),
        TutorialStep(TutorialIds.ADD_SAVE, "Create account", "Save to create the doctor's account.")
    )

    val videoUpload = listOf(
        TutorialStep(TutorialIds.UPLOAD_PICK, "Pick a video", "Choose a video file to use for an exercise."),
        TutorialStep(TutorialIds.UPLOAD_METADATA, "Details", "Add a name and details so it's easy to find later."),
        TutorialStep(TutorialIds.UPLOAD_SUBMIT, "Upload", "Upload the video to your exercise library.")
    )

    val exerciseLibrary = listOf(
        TutorialStep(TutorialIds.LIBRARY_ROW, "Exercises", "Browse your exercise videos. Tap one to preview it."),
        TutorialStep(TutorialIds.LIBRARY_UPLOAD, "Add exercise", "Upload a new exercise video to the library.")
    )

    val feedbackDashboard = listOf(
        TutorialStep(TutorialIds.FD_LIST, "Feedback", "Every patient's session feedback appears here. Tap to open a patient.")
    )

    val doctorProfile = listOf(
        TutorialStep(TutorialIds.PROFILE_EDIT, "Your profile", "Keep your name and credentials up to date."),
        TutorialStep(TutorialIds.PROFILE_PASSWORD, "Change password", "Update your password to keep your account secure."),
        TutorialStep(TutorialIds.PROFILE_LOGOUT, "Sign out", "Tap here when you're ready to log out.")
    )

    // ────────────────────────────── Admin ──────────────────────────────

    val adminDashboard = listOf(
        TutorialStep(TutorialIds.ADMIN_SETTINGS_NAV, "Settings", "Control platform-wide access settings."),
        TutorialStep(TutorialIds.ADMIN_PATIENTS, "All users", "Browse every patient and doctor account."),
        TutorialStep(TutorialIds.ADMIN_PROFILE, "Your profile", "Manage your account and sign out."),
        TutorialStep(TutorialIds.ADMIN_LOGS, "Login logs", "Audit recent sign-in activity across the platform."),
        TutorialStep(TutorialIds.ADMIN_DOCTORS, "Doctors", "Each doctor on the platform — tap one to manage their patients.")
    )

    val adminSettings = listOf(
        TutorialStep(TutorialIds.ADMIN_SETTINGS_TOGGLE, "Access controls", "Toggle platform-wide locks and access for patients and doctors.")
    )

    val adminDoctorPatients = listOf(
        TutorialStep(TutorialIds.ADMIN_DOCTOR_PATIENT_ROW, "This doctor's patients", "Tap a patient to review and manage their assignments.")
    )

    val adminLoginLogs = listOf(
        TutorialStep(TutorialIds.ADMIN_LOGIN_LOG_ROW, "Login activity", "Each entry shows a sign-in attempt with time and details.")
    )

    val adminPatientAssignments = listOf(
        TutorialStep(TutorialIds.ADMIN_ASSIGNMENT_ROW, "Assignments", "Tap an assignment to view or edit its details.")
    )

    val adminDoctorProfile = listOf(
        TutorialStep(TutorialIds.ADP_FORM, "Doctor details", "Edit this doctor's name, contact, and credentials."),
        TutorialStep(TutorialIds.ADP_SAVE, "Save", "Save your changes to the doctor's profile."),
        TutorialStep(TutorialIds.ADP_DELETE, "Delete", "Permanently remove this doctor's account.")
    )
}
