// AdminPatientAssignmentsScreen.kt — Shows patient's assignments grouped by date (Admin view)
package com.srcardiocare.ui.screens.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.data.model.AssignmentHistoryStatus
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class DateGroupedAssignment(
    val date: LocalDate,
    val dateLabel: String,
    val assignments: List<PatientAssignmentItem>
)

data class PatientAssignmentItem(
    val id: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val startDate: String,
    val endDate: String,
    val dailyFrequency: Int,
    val completedToday: Int,
    val totalCompleted: Int,
    val totalPossible: Int,
    val status: AssignmentStatus,
    val isExpired: Boolean
)

enum class AssignmentStatus {
    ACTIVE, DONE_TODAY, COMPLETED, MISSED, PARTIAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPatientAssignmentsScreen(
    patientId: String,
    onEditAssignment: (assignmentId: String) -> Unit = {},
    onBack: () -> Unit
) {
    var patientName by remember { mutableStateOf("Loading...") }
    var groupedAssignments by remember { mutableStateOf<List<DateGroupedAssignment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAllWorkouts by remember { mutableStateOf(false) }
    var expandedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }

    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE")

    suspend fun loadData() {
        try {
            // Fetch patient info
            val patient = UserRepository.getUser(patientId)
            val firstName = patient.firstName
            val lastName = patient.lastName
            patientName = "$firstName $lastName".trim().ifBlank { "Patient" }

            // Fetch all assignments for patient
            val assignments = AssignmentRepository.getAssignments(patientId)

            // Group by end date
            val dateMap = mutableMapOf<LocalDate, MutableList<PatientAssignmentItem>>()

            for (assignment in assignments) {
                val exerciseName = assignment.exerciseName
                val sets = assignment.sets
                val reps = assignment.reps
                val startDateStr = assignment.startDate
                val endDateStr = assignment.endDate
                val dailyFrequency = assignment.dailyFrequency

                val startDate = try { LocalDate.parse(startDateStr) } catch (_: Exception) { continue }
                val endDate = try { LocalDate.parse(endDateStr) } catch (_: Exception) { continue }

                // Fetch sessions for this assignment
                val allSessions = try {
                    SessionRepository.getAllSessionsForAssignment(assignment.id)
                } catch (_: Exception) { emptyList() }

                val completedSessions = allSessions.count { it.status == com.srcardiocare.data.model.SessionStatus.COMPLETED }
                val todaySessions = allSessions.filter {
                    it.sessionDate == today.toString()
                }
                val completedToday = todaySessions.count { it.status == com.srcardiocare.data.model.SessionStatus.COMPLETED }

                val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
                val totalPossible = totalDays * dailyFrequency
                val isExpired = today.isAfter(endDate)
                val isDoneToday = completedToday >= dailyFrequency

                val status = when {
                    isExpired && completedSessions >= totalPossible -> AssignmentStatus.COMPLETED
                    isExpired && completedSessions > 0 -> AssignmentStatus.PARTIAL
                    isExpired -> AssignmentStatus.MISSED
                    isDoneToday -> AssignmentStatus.DONE_TODAY
                    else -> AssignmentStatus.ACTIVE
                }

                val item = PatientAssignmentItem(
                    id = assignment.id,
                    exerciseName = exerciseName,
                    sets = sets,
                    reps = reps,
                    startDate = startDateStr,
                    endDate = endDateStr,
                    dailyFrequency = dailyFrequency,
                    completedToday = completedToday,
                    totalCompleted = completedSessions,
                    totalPossible = totalPossible,
                    status = status,
                    isExpired = isExpired
                )

                // Group by end date
                val groupDate = endDate
                dateMap.getOrPut(groupDate) { mutableListOf() }.add(item)
            }

            // Convert to sorted list
            groupedAssignments = dateMap.map { (date, items) ->
                val label = when {
                    date == today -> "Today"
                    date == today.plusDays(1) -> "Tomorrow"
                    date == today.minusDays(1) -> "Yesterday"
                    date.isBefore(today) -> "Expired - ${date.format(dateFormatter)}"
                    else -> "${date.format(dayFormatter)} - ${date.format(dateFormatter)}"
                }
                DateGroupedAssignment(
                    date = date,
                    dateLabel = label,
                    assignments = items.sortedBy { it.exerciseName }
                )
            }.sortedBy { it.date }

            // Auto-expand today's date
            expandedDates = setOf(today)

            errorMessage = null
        } catch (e: Exception) {
            errorMessage = ErrorHandler.getDisplayMessage(e, "load assignments")
        }
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(patientId) { loadData() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { loadData() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(patientName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Exercise Assignments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { loadData() }
            },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            when {
                isLoading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
                    ) {
                        items(6) { SkeletonListRow() }
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.XL), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Error.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(DesignTokens.Spacing.XL),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = DesignTokens.Colors.Error, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                Text("Error", fontWeight = FontWeight.Bold, color = DesignTokens.Colors.Error)
                                Text(errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                groupedAssignments.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.XL), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(DesignTokens.Spacing.XXXL),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                Text("No assignments", fontWeight = FontWeight.SemiBold)
                                Text("This patient has no exercise assignments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                else -> {
                    val displayedGroups = if (showAllWorkouts) {
                        groupedAssignments
                    } else {
                        // Show only active/today and future, plus recent expired
                        groupedAssignments.filter { !it.date.isBefore(today.minusDays(7)) }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
                    ) {
                        // Stats summary
                        item {
                            val totalAssignments = groupedAssignments.sumOf { it.assignments.size }
                            val completedCount = groupedAssignments.sumOf { group ->
                                group.assignments.count { it.status == AssignmentStatus.COMPLETED || it.status == AssignmentStatus.DONE_TODAY }
                            }
                            val activeCount = groupedAssignments.sumOf { group ->
                                group.assignments.count { it.status == AssignmentStatus.ACTIVE }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                                shape = RoundedCornerShape(DesignTokens.Radius.Card),
                                colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Primary)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.LG),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(totalAssignments.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                        Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(activeCount.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                        Text("Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(completedCount.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                        Text("Completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                        }

                        // Show all toggle
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM)
                                    .clickable { showAllWorkouts = !showAllWorkouts },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (showAllWorkouts) "Showing all workouts" else "Showing recent workouts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = { showAllWorkouts = !showAllWorkouts }) {
                                    Text(if (showAllWorkouts) "Show Less" else "Show All")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        if (showAllWorkouts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Date groups
                        displayedGroups.forEach { group ->
                            item(key = "header_${group.date}") {
                                DateGroupHeader(
                                    group = group,
                                    isExpanded = group.date in expandedDates,
                                    today = today,
                                    onClick = {
                                        expandedDates = if (group.date in expandedDates) {
                                            expandedDates - group.date
                                        } else {
                                            expandedDates + group.date
                                        }
                                    }
                                )
                            }

                            // Assignments under this date
                            if (group.date in expandedDates) {
                                items(group.assignments, key = { it.id }) { assignment ->
                                    AssignmentCard(
                                        assignment = assignment,
                                        onEdit = { onEditAssignment(assignment.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateGroupHeader(
    group: DateGroupedAssignment,
    isExpanded: Boolean,
    today: LocalDate,
    onClick: () -> Unit
) {
    val isExpired = group.date.isBefore(today)
    val isToday = group.date == today

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.Base),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isToday -> DesignTokens.Colors.Primary.copy(alpha = 0.1f)
                isExpired -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.MD, vertical = DesignTokens.Spacing.SM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isToday -> DesignTokens.Colors.Primary
                            isExpired -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else -> DesignTokens.Colors.Success
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        isExpired -> Icons.Default.History
                        isToday -> Icons.Default.Today
                        else -> Icons.Default.Event
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.dateLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${group.assignments.size} exercise${if (group.assignments.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssignmentCard(assignment: PatientAssignmentItem, onEdit: () -> Unit = {}) {
    val statusColor = when (assignment.status) {
        AssignmentStatus.COMPLETED, AssignmentStatus.DONE_TODAY -> DesignTokens.Colors.Success
        AssignmentStatus.ACTIVE -> DesignTokens.Colors.Primary
        AssignmentStatus.PARTIAL -> DesignTokens.Colors.Warning
        AssignmentStatus.MISSED -> DesignTokens.Colors.Error
    }

    val statusIcon = when (assignment.status) {
        AssignmentStatus.COMPLETED -> Icons.Default.CheckCircle
        AssignmentStatus.DONE_TODAY -> Icons.Default.CheckCircle
        AssignmentStatus.ACTIVE -> Icons.Default.PlayCircle
        AssignmentStatus.PARTIAL -> Icons.Default.HourglassTop
        AssignmentStatus.MISSED -> Icons.Default.Cancel
    }

    val statusLabel = when (assignment.status) {
        AssignmentStatus.COMPLETED -> "Completed"
        AssignmentStatus.DONE_TODAY -> "Done Today"
        AssignmentStatus.ACTIVE -> "Active"
        AssignmentStatus.PARTIAL -> "Partial"
        AssignmentStatus.MISSED -> "Missed"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DesignTokens.Spacing.XXXL, end = DesignTokens.Spacing.XL, top = 2.dp, bottom = 2.dp)
            .clickable(enabled = !assignment.isExpired) { onEdit() },
        shape = RoundedCornerShape(DesignTokens.Radius.Base),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exercise icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(DesignTokens.Radius.SM))
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    assignment.exerciseName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (assignment.status == AssignmentStatus.DONE_TODAY || assignment.status == AssignmentStatus.COMPLETED) {
                        TextDecoration.LineThrough
                    } else null,
                    color = if (assignment.isExpired && assignment.status != AssignmentStatus.COMPLETED) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${assignment.sets} sets × ${assignment.reps} reps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!assignment.isExpired) {
                    Text(
                        "Today: ${assignment.completedToday}/${assignment.dailyFrequency}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DesignTokens.Colors.Primary
                    )
                } else {
                    Text(
                        "Total: ${assignment.totalCompleted}/${assignment.totalPossible}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status badge
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
