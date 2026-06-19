// PatientProfileScreen.kt — Patient detail view for doctors.
//
// Reads the patient's *assignments* (the same source of truth the patient app
// uses), so the assigned-exercise list and its status stay in sync. Assigning a
// new exercise opens the dedicated AssignExerciseScreen; tapping an assigned row
// opens EditAssignmentScreen (edit / remove). The legacy `plans` collection is
// still dual-written by the data layer for back-compat but is no longer read here.
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FeedbackRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.data.model.Assignment
import com.srcardiocare.data.model.PostWorkoutFeedback
import com.srcardiocare.ui.components.InitialsAvatar
import com.srcardiocare.ui.components.SkeletonBarChart
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.components.SkeletonProfileHeader
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ExpiryDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileScreen(
    patientId: String,
    onBack: () -> Unit,
    onAssignExercise: () -> Unit,
    onEditAssignment: (assignmentId: String) -> Unit,
    onOpenChat: () -> Unit,
    onHistoryTap: () -> Unit,
    onManageAssignments: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Server rules only permit admins to delete user accounts — hide the
    // action for doctors so they don't hit a guaranteed permission error.
    val isAdmin = remember {
        com.srcardiocare.core.auth.AuthManager(context).userRole == "ADMIN"
    }
    var patientName by remember { mutableStateOf("") }
    var patientCondition by remember { mutableStateOf("") }
    var patientInitials by remember { mutableStateOf("") }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var feedbacks by remember { mutableStateOf<List<PostWorkoutFeedback>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Send-feedback dialog state
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var isSendingFeedback by remember { mutableStateOf(false) }

    // Admin doctor assignment state
    var currentUserRole by remember { mutableStateOf("") }
    var allDoctors by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id to name
    var currentAssignedDoctorId by remember { mutableStateOf("") }
    var showDoctorPicker by remember { mutableStateOf(false) }
    var isAssigningDoctor by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    // Reload patient profile + assignments (source of truth the patient reads).
    suspend fun loadPatientData() {
        try {
            val patient = UserRepository.getUser(patientId)
            patientName = patient.fullName
            patientInitials = "${patient.firstName.firstOrNull() ?: ""}${patient.lastName.firstOrNull() ?: ""}".uppercase()
            patientCondition = patient.injuries.firstOrNull() ?: ""

            assignments = try { AssignmentRepository.getAssignments(patientId) } catch (_: Exception) { emptyList() }
            feedbacks = try { FeedbackRepository.getPatientFeedbacks(patientId) } catch (_: Exception) { emptyList() }
        } catch (_: Exception) { }
    }

    LaunchedEffect(patientId) {
        try {
            val uid = FirebaseService.currentUID
            if (uid != null) {
                currentUserRole = UserRepository.getUser(uid).role
            }
        } catch (_: Exception) { }

        loadPatientData()

        // Fetch assigned doctor and all doctors (for admin picker)
        try {
            currentAssignedDoctorId = UserRepository.getUser(patientId).assignedDoctorId ?: ""
            if (currentUserRole == "admin") {
                allDoctors = UserRepository.getAllDoctors().map { doctor ->
                    doctor.id to "Dr. ${doctor.lastName}".let { if (doctor.lastName.isBlank()) doctor.firstName else it }
                }
            }
        } catch (_: Exception) { }

        isLoading = false
    }

    // Refresh assignments when returning from the assign / edit screens.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { loadPatientData() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSendingFeedback) showFeedbackDialog = false },
            title = { Text("Send Feedback to Patient", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = feedbackMessage,
                    onValueChange = { feedbackMessage = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isSendingFeedback = true
                        scope.launch {
                            try {
                                FirebaseService.sendFeedback(patientId, feedbackMessage.trim())
                                toast("Feedback sent")
                                showFeedbackDialog = false
                                feedbackMessage = ""
                            } catch (e: Exception) {
                                toast("Failed to send feedback")
                            }
                            isSendingFeedback = false
                        }
                    },
                    enabled = !isSendingFeedback && feedbackMessage.isNotBlank()
                ) {
                    if (isSendingFeedback) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DesignTokens.Colors.Primary, strokeWidth = 2.dp)
                    } else {
                        Text("Send")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }, enabled = !isSendingFeedback) {
                    Text("Cancel")
                }
            }
        )
    }

    TutorialHost(tourKey = TutorialKeys.DOCTOR_PATIENT_PROFILE, steps = TutorialTours.doctorPatientProfile) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onHistoryTap,
                        modifier = Modifier.tutorialTarget(TutorialIds.DPP_HISTORY)
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Patient History")
                    }
                    TutorialHelpButton()
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SkeletonProfileHeader()
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                Box(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL)) {
                    SkeletonBarChart(barCount = 7, chartHeight = 80.dp)
                }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
                repeat(3) { SkeletonListRow() }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
            }
        } else {
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // Avatar
            InitialsAvatar(
                initials = patientInitials,
                size = 80.dp
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            Text(patientName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (patientCondition.isNotBlank()) {
                Text(patientCondition, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // Metrics / Feedbacks Chart
            if (feedbacks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .tutorialTarget(TutorialIds.DPP_METRICS),
                    shape = RoundedCornerShape(DesignTokens.Radius.LG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                        Text("Recent Health Metrics", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                        // Recent up to 7 feedbacks
                        val recent = feedbacks.take(7).reversed()
                        Row(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            recent.forEach { f ->
                                val resp = f.respiration.toFloat()

                                val barColor = when {
                                    f.hadPain && f.painIntensity >= 7 -> DesignTokens.Colors.Error
                                    f.hadPain -> DesignTokens.Colors.Warning
                                    else -> DesignTokens.Colors.Success
                                }
                                val barHeight = (resp / 10f * 60f).coerceAtLeast(4f)

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(barHeight.dp)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                        Text(
                            "Respiration (0-10); orange = pain reported, red = severe pain",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .tutorialTarget(TutorialIds.DPP_METRICS),
                    shape = RoundedCornerShape(DesignTokens.Radius.LG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.LG),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No feedback data available yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // ── Actions ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.XL),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)
            ) {
                // Primary: assign a new exercise (opens the dedicated screen)
                Button(
                    onClick = onAssignExercise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .tutorialTarget(TutorialIds.DPP_ASSIGNMENTS),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary)
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                    Text("Assign Exercise", fontWeight = FontWeight.SemiBold)
                }

                // Secondary: send a quick note, or open the full chat
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)) {
                    OutlinedButton(
                        onClick = { showFeedbackDialog = true },
                        modifier = Modifier.weight(1f).height(48.dp).tutorialTarget(TutorialIds.DPP_SEND_FEEDBACK),
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesignTokens.Colors.Primary)
                    ) {
                        Text("Send Feedback", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = onOpenChat,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesignTokens.Colors.Primary)
                    ) {
                        Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Message", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Admin Doctor Assignment ───────────────────────────────────
            if (currentUserRole == "admin" && allDoctors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                Text(
                    "Assigned Doctor",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

                val currentDoctorName = allDoctors.firstOrNull { it.first == currentAssignedDoctorId }?.second ?: "Not Assigned"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .clickable { showDoctorPicker = true },
                    shape = RoundedCornerShape(DesignTokens.Radius.LG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DesignTokens.Spacing.MD),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current: $currentDoctorName", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Tap to change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isAssigningDoctor) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DesignTokens.Colors.Primary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DesignTokens.Colors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (showDoctorPicker) {
                    AlertDialog(
                        onDismissRequest = { showDoctorPicker = false },
                        title = { Text("Assign Doctor", fontWeight = FontWeight.Bold) },
                        text = {
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(allDoctors) { (docId, docName) ->
                                    val isSelected = docId == currentAssignedDoctorId
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                isAssigningDoctor = true
                                                showDoctorPicker = false
                                                scope.launch {
                                                    try {
                                                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                            .collection("users").document(patientId)
                                                            .update("assignedDoctorId", docId)
                                                            .await()
                                                        com.srcardiocare.core.push.Notifier.send(
                                                            com.srcardiocare.core.push.NotificationEvent.DoctorAssigned(
                                                                patientId = patientId,
                                                                doctorName = docName
                                                            )
                                                        )
                                                        currentAssignedDoctorId = docId
                                                        toast("Doctor changed to $docName")
                                                    } catch (e: Exception) {
                                                        toast("Failed to change doctor")
                                                    }
                                                    isAssigningDoctor = false
                                                }
                                            },
                                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) DesignTokens.Colors.Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(DesignTokens.Spacing.MD),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(docName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                            if (isSelected) {
                                                Text("✓", color = DesignTokens.Colors.Primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDoctorPicker = false }) { Text("Close") }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // ── Assigned Exercises ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DesignTokens.Spacing.XL, end = DesignTokens.Spacing.SM),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Assigned Exercises", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${assignments.size} active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onManageAssignments) {
                        Text("View all", color = DesignTokens.Colors.Primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (assignments.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                    shape = RoundedCornerShape(DesignTokens.Radius.LG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DesignTokens.Spacing.XXL),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No exercises assigned yet", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Tap 'Assign Exercise' above to get started", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            assignments.forEach { assignment ->
                AssignedExerciseRow(
                    assignment = assignment,
                    onClick = { onEditAssignment(assignment.id) }
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

            // ── Delete Patient Section ────────────────────────────────────
            var showDeleteDialog by remember { mutableStateOf(false) }
            var isDeleting by remember { mutableStateOf(false) }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                    title = { Text("Delete Patient", color = DesignTokens.Colors.Error) },
                    text = { Text("Are you sure you want to delete this patient? This will remove all their data (plans, workouts, etc.) and cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                isDeleting = true
                                scope.launch {
                                    try {
                                        FirebaseService.deletePatient(patientId)
                                        toast("Patient deleted")
                                        showDeleteDialog = false
                                        onBack() // Navigate back on success
                                    } catch (e: Exception) {
                                        toast("Failed to delete patient")
                                        isDeleting = false
                                        showDeleteDialog = false
                                    }
                                }
                            },
                            enabled = !isDeleting
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DesignTokens.Colors.Error, strokeWidth = 2.dp)
                            } else {
                                Text("Delete", color = DesignTokens.Colors.Error, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (isAdmin) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .height(48.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.Button),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DesignTokens.Colors.Error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Colors.Error)
                ) {
                    Text("Delete Patient", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
        }
        }
    }
    }
}

@Composable
private fun AssignedExerciseRow(assignment: Assignment, onClick: () -> Unit) {
    val today = LocalDate.now()
    val endDate = runCatching { LocalDate.parse(assignment.endDate) }.getOrNull()
    val isExpired = endDate != null && today.isAfter(endDate)

    val statusColor = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else DesignTokens.Colors.Primary
    val statusLabel = if (isExpired) "Expired" else "Active"

    val detail = buildString {
        append("${assignment.sets} Sets • ${assignment.reps} Reps")
        append(" • ${assignment.dailyFrequency}×/day")
    }
    val scheduleText = endDate?.let {
        if (isExpired) "Ended ${it.format(ExpiryDateFormat)}" else "Ends ${it.format(ExpiryDateFormat)}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(DesignTokens.Radius.Base))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Text(assignment.exerciseName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (scheduleText != null) {
                    Text(scheduleText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
            Surface(
                shape = RoundedCornerShape(DesignTokens.Radius.Full),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.XS))
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
