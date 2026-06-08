// NavGraph.kt — Jetpack Compose Navigation for SR-Cardiocare
package com.srcardiocare.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.srcardiocare.core.auth.AuthManager
import com.srcardiocare.core.push.DeepLink
import com.srcardiocare.core.push.PendingRoute
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.screens.auth.LoginScreen
import com.srcardiocare.ui.screens.auth.ChangePasswordScreen
import com.srcardiocare.ui.screens.patient.PatientHomeScreen
import com.srcardiocare.ui.screens.patient.PatientProfileSelfScreen
import com.srcardiocare.ui.screens.workout.WorkoutPlayerScreen
import com.srcardiocare.ui.screens.feedback.PostWorkoutFeedbackScreen
import com.srcardiocare.ui.screens.doctor.DoctorDashboardScreen
import com.srcardiocare.ui.screens.doctor.AdminDashboardScreen
import com.srcardiocare.ui.screens.doctor.AdminLoginLogsScreen
import com.srcardiocare.ui.screens.doctor.AddPatientScreen
import com.srcardiocare.ui.screens.doctor.PatientProfileScreen
import com.srcardiocare.ui.screens.doctor.AdminDoctorPatientsScreen
import com.srcardiocare.ui.screens.doctor.AdminPatientAssignmentsScreen
import com.srcardiocare.ui.screens.doctor.EditAssignmentScreen
import com.srcardiocare.ui.screens.exercises.ExerciseLibraryScreen
import com.srcardiocare.ui.screens.video.VideoUploadScreen
import com.srcardiocare.ui.screens.schedule.ScheduleScreen
import com.srcardiocare.ui.screens.analytics.AnalyticsScreen
import com.srcardiocare.ui.screens.doctor.DoctorProfileScreen
import com.srcardiocare.ui.screens.doctor.PatientListScreen
import com.srcardiocare.ui.screens.patient.AssignmentListScreen
import com.srcardiocare.ui.screens.workout.AssignmentWorkoutScreen
import com.srcardiocare.ui.screens.notifications.NotificationsScreen
import com.srcardiocare.data.model.Assignment

/**
 * Central navigation graph. All routes use sealed class [Route].
 */
sealed class Route(val path: String) {
    object Login : Route("login")
    object PatientHome : Route("patient/home")
    object WorkoutPlayer : Route("workout/player?name={name}&videoUrl={videoUrl}&sets={sets}&reps={reps}&instructions={instructions}&planId={planId}&totalCount={totalCount}&isLastExercise={isLastExercise}") {
        fun createPath(
            name: String,
            videoUrl: String?,
            sets: Int,
            reps: Int,
            instructions: String?,
            planId: String,
            totalCount: Int,
            isLastExercise: Boolean
        ): String {
            val encodedName = Uri.encode(name)
            val encodedVideoUrl = Uri.encode(videoUrl ?: "")
            val encodedInstructions = Uri.encode(instructions ?: "")
            val encodedPlanId = Uri.encode(planId)
            return "workout/player?name=$encodedName&videoUrl=$encodedVideoUrl&sets=$sets&reps=$reps&instructions=$encodedInstructions&planId=$encodedPlanId&totalCount=$totalCount&isLastExercise=$isLastExercise"
        }
    }
    object PostWorkoutFeedback : Route("workout/feedback")
    object DoctorDashboard : Route("doctor/dashboard")
    object AdminDashboard : Route("admin/dashboard")
    object AdminSettings : Route("admin/settings")
    object FeedbackDashboard : Route("doctor/feedback")
    object AddPatient : Route("doctor/add-patient")
    object AddDoctor : Route("doctor/add-doctor")
    object PatientProfile : Route("doctor/patient/{patientId}")
    object AdminDoctorProfile : Route("admin/doctor/{doctorId}")
    object AdminLoginLogs : Route("admin/login-logs")
    object ExerciseLibrary : Route("exercises/library")
    object VideoUpload : Route("exercises/upload")
    object Schedule : Route("schedule")
    object Analytics : Route("analytics")
    object Notifications : Route("notifications")
    object DoctorProfile : Route("doctor/profile")
    object PatientProfileSelf : Route("patient/profile")
    object PatientChat : Route("patient/chat")
    object PatientList : Route("doctor/patients")
    object ExerciseList : Route("patient/exercises")
    object ChangePassword : Route("change-password")
    object PatientFeedbackChat : Route("doctor/patientChat/{patientId}") {
        fun createPath(patientId: String) = "doctor/patientChat/$patientId"
    }
    
    // Assignment-based exercise system
    object AssignmentList : Route("patient/assignments")
    object AssignmentWorkout : Route("patient/assignment-workout/{assignmentId}/{sessionNumber}") {
        fun createPath(assignmentId: String, sessionNumber: Int) = 
            "patient/assignment-workout/$assignmentId/$sessionNumber"
    }
    object PatientHistory : Route("patient/{patientId}/history") {
        fun createPath(patientId: String) = "patient/$patientId/history"
    }
    
    // Admin routes for viewing doctor's patients and patient's assignments
    object AdminDoctorPatients : Route("admin/doctor/{doctorId}/patients") {
        fun createPath(doctorId: String) = "admin/doctor/$doctorId/patients"
    }
    object AdminPatientAssignments : Route("admin/patient/{patientId}/assignments") {
        fun createPath(patientId: String) = "admin/patient/$patientId/assignments"
    }
    object EditAssignment : Route("doctor/assignment/{assignmentId}/edit") {
        fun createPath(assignmentId: String) = "doctor/assignment/$assignmentId/edit"
    }
}

@Composable
fun SRCardiocareNavGraph(
    navController: NavHostController,
    startDestination: String = Route.Login.path
) {
    CurrentUserDocGuard(navController = navController)
    PushDeepLinkHandler(navController = navController)

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Route.Login.path) {
            LoginScreen(
                onLoginSuccess = { role ->
                    val dest = when (role) {
                        "ADMIN" -> Route.AdminDashboard.path
                        "DOCTOR" -> Route.DoctorDashboard.path
                        else -> Route.PatientHome.path
                    }
                    navController.navigate(dest) { popUpTo(Route.Login.path) { inclusive = true } }
                },
                onChangePassword = { navController.navigate(Route.ChangePassword.path) }
            )
        }

        composable(Route.PatientHome.path) {
            PatientHomeScreen(
                onExerciseTap = { navController.navigate(Route.AssignmentList.path) },
                onScheduleTap = { navController.navigate(Route.Schedule.path) },
                onAnalyticsTap = { navController.navigate(Route.Analytics.path) },
                onNotificationsTap = { navController.navigate(Route.Notifications.path) },
                onChatTap = { navController.navigate(Route.PatientChat.path) },
                onProfile = { navController.navigate(Route.PatientProfileSelf.path) }
            )
        }

        composable(Route.ExerciseList.path) {
            com.srcardiocare.ui.screens.patient.ExerciseListScreen(
                onExerciseTap = { name, videoUrl, sets, reps, instructions, planId, totalCount, isLastExercise ->
                    navController.navigate(
                        Route.WorkoutPlayer.createPath(
                            name, videoUrl, sets, reps, instructions, planId, totalCount, isLastExercise
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PatientChat.path) {
            com.srcardiocare.ui.screens.patient.PatientChatScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.WorkoutPlayer.path,
            arguments = listOf(
                navArgument("name") { type = NavType.StringType; defaultValue = "Workout" },
                navArgument("videoUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("sets") { type = NavType.IntType; defaultValue = 3 },
                navArgument("reps") { type = NavType.IntType; defaultValue = 10 },
                navArgument("instructions") { type = NavType.StringType; defaultValue = "" },
                navArgument("planId") { type = NavType.StringType; defaultValue = "" },
                navArgument("totalCount") { type = NavType.IntType; defaultValue = 1 },
                navArgument("isLastExercise") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            WorkoutPlayerScreen(
                exerciseName = args?.getString("name") ?: "Workout",
                videoUrl = args?.getString("videoUrl").orEmpty().ifBlank { null },
                sets = args?.getInt("sets") ?: 3,
                reps = args?.getInt("reps") ?: 10,
                instructions = args?.getString("instructions").orEmpty().ifBlank { null },
                planId = args?.getString("planId") ?: "",
                totalCount = args?.getInt("totalCount") ?: 1,
                isLastExercise = args?.getBoolean("isLastExercise") ?: false,
                onFinish = {
                    if (args?.getBoolean("isLastExercise") == true) {
                        navController.navigate(Route.PostWorkoutFeedback.path) {
                            popUpTo(Route.PatientHome.path)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PostWorkoutFeedback.path) {
            PostWorkoutFeedbackScreen(
                onSubmit = { navController.navigate(Route.PatientHome.path) { popUpTo(Route.PatientHome.path) { inclusive = true } } }
            )
        }

        composable(Route.DoctorDashboard.path) {
            DoctorDashboardScreen(
                onPatientTap = { id -> navController.navigate("doctor/patient/$id") },
                onDoctorTap = { id -> navController.navigate("admin/doctor/$id") },
                onExerciseLibrary = { navController.navigate(Route.ExerciseLibrary.path) },
                onSchedule = { navController.navigate(Route.Schedule.path) },
                onProfile = { navController.navigate(Route.DoctorProfile.path) },
                onFeedbacks = { navController.navigate(Route.FeedbackDashboard.path) },
                onPatientList = { navController.navigate(Route.PatientList.path) },
                onAddPatient = { navController.navigate(Route.AddPatient.path) }
            )
        }

        composable(Route.AdminDashboard.path) {
            AdminDashboardScreen(
                onDoctorTap = { id -> navController.navigate(Route.AdminDoctorPatients.createPath(id)) },
                onAddDoctor = { navController.navigate(Route.AddDoctor.path) },
                onAddPatient = { navController.navigate(Route.AddPatient.path) },
                onUserList = { navController.navigate(Route.PatientList.path) },
                onChatMonitor = { navController.navigate(Route.FeedbackDashboard.path) },
                onLoginLogs = { navController.navigate(Route.AdminLoginLogs.path) },
                onSettings = {
                    navController.navigate(Route.AdminSettings.path) {
                        launchSingleTop = true
                    }
                },
                onProfile = {
                    navController.navigate(Route.DoctorProfile.path) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Route.PatientList.path) {
            PatientListScreen(
                onPatientTap = { id -> navController.navigate("doctor/patient/$id") },
                onDoctorTap = { id -> navController.navigate("admin/doctor/$id") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.FeedbackDashboard.path) {
            com.srcardiocare.ui.screens.doctor.FeedbackDashboardScreen(
                onPatientTap = { id -> navController.navigate(Route.PatientFeedbackChat.createPath(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.AdminLoginLogs.path) {
            AdminLoginLogsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.PatientFeedbackChat.path,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            com.srcardiocare.ui.screens.doctor.PatientFeedbackChatScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.AddPatient.path) {
            AddPatientScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.AddDoctor.path) {
            com.srcardiocare.ui.screens.doctor.AddDoctorScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PatientHistory.path) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            com.srcardiocare.ui.screens.patient.PatientHistoryScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PatientProfile.path) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            PatientProfileScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() },
                onVideoUpload = { navController.navigate(Route.VideoUpload.path) },
                onHistoryTap = { navController.navigate(Route.PatientHistory.createPath(patientId)) },
                onManageAssignments = {
                    navController.navigate(Route.AdminPatientAssignments.createPath(patientId))
                }
            )
        }

        composable(Route.AdminDoctorProfile.path) { backStackEntry ->
            val doctorId = backStackEntry.arguments?.getString("doctorId") ?: ""
            com.srcardiocare.ui.screens.doctor.AdminDoctorProfileScreen(
                doctorId = doctorId,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(Route.ExerciseLibrary.path) {
            ExerciseLibraryScreen(
                onBack = { navController.popBackStack() },
                onUpload = { navController.navigate(Route.VideoUpload.path) }
            )
        }

        composable(Route.VideoUpload.path) {
            VideoUploadScreen(
                onBack = { navController.popBackStack() },
                onUploaded = { navController.popBackStack() }
            )
        }

        composable(Route.Schedule.path) {
            ScheduleScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.Analytics.path) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.Notifications.path) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenRoute = { route, params ->
                    DeepLink.navigate(navController, route, params)
                }
            )
        }

        composable(Route.DoctorProfile.path) {
            DoctorProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Route.Login.path) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onChangePassword = { navController.navigate(Route.ChangePassword.path) }
            )
        }

        composable(Route.AdminSettings.path) {
            com.srcardiocare.ui.screens.doctor.AdminSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PatientProfileSelf.path) {
            PatientProfileSelfScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Route.Login.path) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onChangePassword = { navController.navigate(Route.ChangePassword.path) }
            )
        }

        composable(Route.ChangePassword.path) {
            ChangePasswordScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        // ═══════════════════════════════════════════════════════════════════════
        // ASSIGNMENT-BASED EXERCISE SYSTEM
        // ═══════════════════════════════════════════════════════════════════════
        
        composable(Route.AssignmentList.path) {
            AssignmentListScreen(
                onExerciseTap = { assignment, sessionNumber ->
                    navController.navigate(Route.AssignmentWorkout.createPath(assignment.id, sessionNumber))
                },
                onHistoryTap = {
                    val uid = com.srcardiocare.data.firebase.FirebaseService.currentUID
                    if (uid != null) {
                        navController.navigate(Route.PatientHistory.createPath(uid))
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Route.AssignmentWorkout.path,
            arguments = listOf(
                navArgument("assignmentId") { type = NavType.StringType },
                navArgument("sessionNumber") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val assignmentId = backStackEntry.arguments?.getString("assignmentId") ?: ""
            val sessionNumber = backStackEntry.arguments?.getInt("sessionNumber") ?: 1
            
            // Fetch assignment data
            var assignment by remember { mutableStateOf<Assignment?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            
            androidx.compose.runtime.LaunchedEffect(assignmentId) {
                try {
                    val patientId = FirebaseService.currentUID ?: return@LaunchedEffect
                    val assignments = FirebaseService.fetchAssignments(patientId)
                    val found = assignments.find { it.first == assignmentId }
                    if (found != null) {
                        assignment = parseAssignmentFromMap(found.first, found.second)
                    }
                } catch (_: Exception) {}
                isLoading = false
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (assignment != null) {
                AssignmentWorkoutScreen(
                    assignment = assignment!!,
                    sessionNumber = sessionNumber,
                    onComplete = {
                        navController.navigate(Route.PostWorkoutFeedback.path) {
                            popUpTo(Route.AssignmentList.path)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            } else {
                // Assignment not found
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Exercise not found")
                }
            }
        }
        
        // Admin routes for viewing doctor's patients and patient's assignments
        composable(
            route = Route.AdminDoctorPatients.path,
            arguments = listOf(navArgument("doctorId") { type = NavType.StringType })
        ) { backStackEntry ->
            val doctorId = backStackEntry.arguments?.getString("doctorId") ?: ""
            AdminDoctorPatientsScreen(
                doctorId = doctorId,
                onPatientTap = { patientId -> 
                    navController.navigate(Route.AdminPatientAssignments.createPath(patientId)) 
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Route.AdminPatientAssignments.path,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            AdminPatientAssignmentsScreen(
                patientId = patientId,
                onEditAssignment = { assignmentId ->
                    navController.navigate(Route.EditAssignment.createPath(assignmentId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.EditAssignment.path,
            arguments = listOf(navArgument("assignmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assignmentId = backStackEntry.arguments?.getString("assignmentId") ?: ""
            EditAssignmentScreen(
                assignmentId = assignmentId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }
    }
}

// Helper function to parse Assignment from Firebase map
private fun parseAssignmentFromMap(id: String, data: Map<String, Any?>): Assignment {
    return Assignment(
        id = id,
        patientId = data["patientId"] as? String ?: "",
        doctorId = data["doctorId"] as? String ?: "",
        exerciseId = data["exerciseId"] as? String ?: "",
        exerciseName = data["exerciseName"] as? String ?: "Exercise",
        exerciseVideoUrl = data["exerciseVideoUrl"] as? String,
        exerciseThumbnailUrl = data["exerciseThumbnailUrl"] as? String,
        exerciseCategory = data["exerciseCategory"] as? String,
        exerciseDifficulty = data["exerciseDifficulty"] as? String,
        startDate = data["startDate"] as? String ?: java.time.LocalDate.now().toString(),
        endDate = data["endDate"] as? String ?: java.time.LocalDate.now().plusDays(7).toString(),
        dailyFrequency = ((data["dailyFrequency"] as? Number)?.toInt() ?: 3).coerceIn(1, 3),
        sets = (data["sets"] as? Number)?.toInt() ?: 3,
        reps = (data["reps"] as? Number)?.toInt() ?: 10,
        restSeconds = (data["restSeconds"] as? Number)?.toInt() ?: 45,
        instructions = data["instructions"] as? String,
        completionThreshold = (data["completionThreshold"] as? Number)?.toFloat() ?: 0.8f,
        isActive = data["isActive"] as? Boolean ?: true,
        createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.toString()
    )
}

/**
 * Drains [PendingRoute] when a push tap queued a destination.
 *
 * Collects the state-flow so every re-queue after NavHost is composed (warm
 * foreground taps arriving via onNewIntent) also routes, not just the first one.
 */
@Composable
private fun PushDeepLinkHandler(navController: NavHostController) {
    val pending by PendingRoute.pending.collectAsState()
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        // Drain first so a NavController exception can't cause a re-routing loop.
        PendingRoute.consume()
        if (FirebaseService.currentUID == null) return@LaunchedEffect
        runCatching { DeepLink.navigate(navController, target.route, target.params) }
    }
}

@Composable
private fun CurrentUserDocGuard(navController: NavHostController) {
    val context = LocalContext.current
    var redirected by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val uid = FirebaseService.currentUID
        if (uid == null) return@DisposableEffect onDispose { }

        val registration = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (redirected || error != null) return@addSnapshotListener

                if (snapshot != null && !snapshot.exists()) {
                    redirected = true
                    AuthManager(context).clearAll()
                    navController.navigate(Route.Login.path) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val isBlocked = snapshot.getBoolean("isBlocked") == true
                    if (isBlocked) {
                        redirected = true
                        AuthManager(context).clearAll()
                        navController.navigate(Route.Login.path) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }

        onDispose { registration.remove() }
    }
}
