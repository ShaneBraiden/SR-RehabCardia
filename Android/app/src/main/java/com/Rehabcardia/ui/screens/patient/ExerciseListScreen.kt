// ExerciseListScreen.kt — Daily exercises list grouped by prescription dates
package com.srcardiocare.ui.screens.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ExStatus { COMPLETED, ACTIVE, PENDING, EXPIRED, DAILY_LIMIT }

private data class ExItem(
    val name: String,
    val detail: String,
    val status: ExStatus,
    val sets: Int,
    val reps: Int,
    val videoUrl: String?,
    val instructions: String?,
    val statusLabel: String? = null,
    val assignedDate: String? = null,
    val expiryDate: String? = null // Individual workout expiry
)

private data class ExerciseGroup(
    val dateLabel: String,
    val exercises: List<ExItem>
)

private const val MAX_WORKOUTS_PER_DAY = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(
    onExerciseTap: (name: String, videoUrl: String?, sets: Int, reps: Int, instructions: String?, planId: String, totalCount: Int, isLastExercise: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var exerciseGroups by remember { mutableStateOf<List<ExerciseGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var completedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var fullyCompletedToday by remember { mutableIntStateOf(0) }
    var dailyLimitReached by remember { mutableStateOf(false) }
    var activePlanId by remember { mutableStateOf<String?>(null) }
    var expiryText by remember { mutableStateOf<String?>(null) }
    var sessionLocksEnabled by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    suspend fun loadData() {
        try {
            val uid = FirebaseService.currentUID ?: return
            sessionLocksEnabled = FirebaseService.fetchSessionLocksEnabled()

            val todayCompletions = try {
                FirebaseService.fetchWorkoutCompletionsToday(uid)
            } catch (_: Exception) { 0 }
            dailyLimitReached = todayCompletions >= MAX_WORKOUTS_PER_DAY

            val plans = FirebaseService.fetchPlans(uid)
            val activePlan = plans.firstOrNull { (it.second["isActive"] as? Boolean) == true }
                ?: plans.firstOrNull()

            if (activePlan != null) {
                activePlanId = activePlan.first
                val planData = activePlan.second

                // Check plan expiry
                val expiryDateRaw = planData["expiryDate"]
                val expiryDays = (planData["expiryDays"] as? Number)?.toInt()
                val planCreatedAt = planData["createdAt"]
                val startDateRaw = planData["startDate"]
                val today = LocalDate.now()

                var computedExpiryDate: LocalDate? = null
                var startDate: LocalDate? = null

                // Parse start date
                startDate = when (startDateRaw) {
                    is String -> try { LocalDate.parse(startDateRaw) } catch (_: Exception) { null }
                    is com.google.firebase.Timestamp -> startDateRaw.toDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    else -> null
                }

                val isExpired = when {
                    expiryDateRaw is String -> {
                        try {
                            computedExpiryDate = LocalDate.parse(expiryDateRaw)
                            today.isAfter(computedExpiryDate)
                        } catch (_: Exception) { false }
                    }
                    expiryDateRaw is com.google.firebase.Timestamp -> {
                        computedExpiryDate = expiryDateRaw.toDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        today.isAfter(computedExpiryDate)
                    }
                    expiryDays != null && planCreatedAt is com.google.firebase.Timestamp -> {
                        val createdDate = planCreatedAt.toDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        computedExpiryDate = createdDate.plusDays(expiryDays.toLong())
                        today.isAfter(computedExpiryDate)
                    }
                    else -> false
                }

                expiryText = if (computedExpiryDate != null) {
                    val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, computedExpiryDate).toInt()
                    if (daysRemaining < 0) {
                        "Plan Expired"
                    } else if (daysRemaining == 0) {
                        "Expires Today"
                    } else {
                        "Expires in $daysRemaining days (${computedExpiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))})"
                    }
                } else null

                val planExercises = planData["exercises"] as? List<*> ?: emptyList<Any>()
                totalCount = planExercises.size

                // Workout history tracking
                val workouts = try { FirebaseService.fetchWorkouts(uid) } catch (_: Exception) { emptyList() }

                val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                val todayWorkouts = workouts.filter {
                    (it.second["startedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L >= todayStart
                }

                fullyCompletedToday = todayWorkouts.count {
                    (it.second["exercisesCompleted"] as? Number)?.toInt() == totalCount
                }

                dailyLimitReached = fullyCompletedToday >= MAX_WORKOUTS_PER_DAY

                val latestWorkout = todayWorkouts.firstOrNull()
                completedCount = if (latestWorkout != null) {
                    (latestWorkout.second["exercisesCompleted"] as? Number)?.toInt() ?: 0
                } else {
                    0
                }

                // Parse exercises with assigned dates
                val exercisesByDate = mutableMapOf<String, MutableList<ExItem>>()
                val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

                planExercises.mapIndexedNotNull { index, ex ->
                    val exMap = ex as? Map<*, *> ?: return@mapIndexedNotNull null
                    val name = (exMap["name"] as? String)
                        ?: (exMap["title"] as? String)
                        ?: "Exercise ${index + 1}"
                    val sets = (exMap["customSets"] as? Number)?.toInt()
                        ?: (exMap["sets"] as? Number)?.toInt()
                        ?: 0
                    val reps = (exMap["customReps"] as? Number)?.toInt()
                        ?: (exMap["reps"] as? Number)?.toInt()
                        ?: 0
                    val videoUrl = exMap["videoUrl"] as? String
                    val instructionsValue = exMap["instructions"]
                    val instructions = when (instructionsValue) {
                        is String -> instructionsValue
                        is List<*> -> instructionsValue.joinToString("\n") { it?.toString().orEmpty() }
                        else -> null
                    }

                    // Get assigned date for exercise
                    val assignedDateRaw = exMap["assignedDate"]
                    val assignedDate: LocalDate? = when (assignedDateRaw) {
                        is String -> try { LocalDate.parse(assignedDateRaw) } catch (_: Exception) { null }
                        is com.google.firebase.Timestamp -> assignedDateRaw.toDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        else -> startDate
                    }
                    val dateKey = assignedDate?.format(dateFormatter) ?: "Assigned Exercises"

                    // Check individual exercise expiry date
                    val expiryDateRaw = exMap["expiryDate"]
                    val exerciseExpiry: LocalDate? = when (expiryDateRaw) {
                        is String -> try { LocalDate.parse(expiryDateRaw) } catch (_: Exception) { null }
                        is com.google.firebase.Timestamp -> expiryDateRaw.toDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        else -> null
                    }
                    val isExerciseExpired = exerciseExpiry != null && today.isAfter(exerciseExpiry)
                    
                    // Format expiry text for display
                    val exerciseExpiryText = if (exerciseExpiry != null) {
                        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, exerciseExpiry).toInt()
                        when {
                            daysLeft < 0 -> "Expired"
                            daysLeft == 0 -> "Expires today"
                            daysLeft == 1 -> "Expires tomorrow"
                            daysLeft <= 7 -> "Expires in $daysLeft days"
                            else -> "Expires ${exerciseExpiry.format(dateFormatter)}"
                        }
                    } else null

                    val status = when {
                        isExerciseExpired -> ExStatus.EXPIRED // Individual exercise expired
                        isExpired -> ExStatus.EXPIRED // Plan expired
                        dailyLimitReached && index >= completedCount -> ExStatus.DAILY_LIMIT
                        index < completedCount -> ExStatus.COMPLETED
                        index == completedCount -> ExStatus.ACTIVE
                        else -> ExStatus.PENDING
                    }

                    val statusLabel = when {
                        isExerciseExpired -> "This workout has expired"
                        status == ExStatus.EXPIRED -> "Plan expired"
                        else -> null
                    }

                    ExItem(
                        name = name,
                        detail = "$sets Sets • $reps Reps",
                        status = status,
                        sets = sets,
                        reps = reps,
                        videoUrl = videoUrl,
                        instructions = instructions,
                        statusLabel = statusLabel,
                        assignedDate = dateKey,
                        expiryDate = exerciseExpiryText
                    )
                }.forEach { item ->
                    val dateKey = item.assignedDate ?: "Assigned Exercises"
                    exercisesByDate.getOrPut(dateKey) { mutableListOf() }.add(item)
                }

                // Convert to groups, sorted by date
                exerciseGroups = exercisesByDate.map { (date, exercises) ->
                    ExerciseGroup(dateLabel = date, exercises = exercises)
                }.sortedByDescending { group ->
                    try {
                        LocalDate.parse(group.dateLabel, dateFormatter)
                    } catch (_: Exception) {
                        LocalDate.MIN
                    }
                }
            }
        } catch (_: Exception) { }
        isLoading = false
        isRefreshing = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { loadData() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Daily Exercises", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        expiryText?.let { text ->
                            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { loadData() }
            },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = DesignTokens.Spacing.XL)
            ) {
                // Progress card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.MD),
                        shape = RoundedCornerShape(DesignTokens.Radius.XL),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(DesignTokens.Spacing.XL),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.XL)
                        ) {
                            val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                            val progressPercent = (progress * 100).toInt()
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(70.dp),
                                    color = DesignTokens.Colors.Primary,
                                    trackColor = DesignTokens.Colors.NeutralLight,
                                    strokeWidth = 7.dp,
                                    strokeCap = StrokeCap.Round,
                                )
                                Text("$progressPercent%", fontWeight = FontWeight.Bold, color = DesignTokens.Colors.Primary)
                            }
                            Column {
                                Text("Today's Progress", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("$completedCount of $totalCount exercises", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Daily limit warning removed by user request

                // Loading state - skeleton list
                if (isLoading) {
                    items(5) { SkeletonListRow() }
                }

                // Exercise groups by date
                exerciseGroups.forEach { group ->
                    item {
                        Text(
                            text = group.dateLabel,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM)
                        )
                    }

                    itemsIndexed(group.exercises) { index, exercise ->
                        val globalIndex = exerciseGroups
                            .takeWhile { it != group }
                            .sumOf { it.exercises.size } + index

                        ExerciseRow(
                            item = exercise,
                            sessionLocksEnabled = sessionLocksEnabled,
                            onClick = {
                                if (exercise.status == ExStatus.COMPLETED || (sessionLocksEnabled && exercise.status == ExStatus.EXPIRED)) {
                                    // Can't open
                                } else {
                                    onExerciseTap(
                                        exercise.name,
                                        exercise.videoUrl,
                                        exercise.sets,
                                        exercise.reps,
                                        exercise.instructions,
                                        activePlanId ?: "",
                                        totalCount,
                                        globalIndex == totalCount - 1
                                    )
                                }
                            }
                        )
                    }
                }

                // Empty state
                if (!isLoading && exerciseGroups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(DesignTokens.Spacing.XL),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No exercises assigned",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                                Text(
                                    "Your doctor will assign exercises for you",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(item: ExItem, sessionLocksEnabled: Boolean, onClick: () -> Unit) {
    val isBlocked = (item.status == ExStatus.COMPLETED) || (sessionLocksEnabled && item.status == ExStatus.EXPIRED)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS)
            .clickable(enabled = !isBlocked, onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                ExStatus.EXPIRED -> MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                ExStatus.DAILY_LIMIT -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ExStatus.ACTIVE -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            }
        ),
        elevation = if (item.status == ExStatus.ACTIVE) CardDefaults.cardElevation(defaultElevation = 4.dp) else CardDefaults.cardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (item.status) {
                            ExStatus.COMPLETED -> DesignTokens.Colors.Success.copy(alpha = 0.15f)
                            ExStatus.ACTIVE -> DesignTokens.Colors.Primary.copy(alpha = 0.15f)
                            ExStatus.EXPIRED -> DesignTokens.Colors.Error.copy(alpha = 0.15f)
                            ExStatus.DAILY_LIMIT -> DesignTokens.Colors.Warning.copy(alpha = 0.15f)
                            ExStatus.PENDING -> DesignTokens.Colors.NeutralLight
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (item.status) {
                    ExStatus.COMPLETED -> Text("✓", color = DesignTokens.Colors.Success, fontWeight = FontWeight.Bold)
                    ExStatus.ACTIVE -> Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DesignTokens.Colors.Primary, modifier = Modifier.size(20.dp))
                    ExStatus.EXPIRED -> Icon(Icons.Default.Lock, contentDescription = null, tint = DesignTokens.Colors.Error, modifier = Modifier.size(18.dp))
                    ExStatus.DAILY_LIMIT -> Icon(Icons.Default.Lock, contentDescription = null, tint = DesignTokens.Colors.Warning, modifier = Modifier.size(18.dp))
                    ExStatus.PENDING -> Text("·", color = DesignTokens.Colors.NeutralDark)
                }
            }
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Medium,
                    color = if (isBlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.status == ExStatus.COMPLETED || item.status == ExStatus.DAILY_LIMIT) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                // Show expiry date if available
                item.expiryDate?.let { expiry ->
                    val expiryColor = when {
                        expiry.contains("Expired") -> DesignTokens.Colors.Error
                        expiry.contains("today") || expiry.contains("tomorrow") -> DesignTokens.Colors.Warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        expiry,
                        style = MaterialTheme.typography.labelSmall,
                        color = expiryColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                item.statusLabel?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.status) {
                            ExStatus.EXPIRED -> DesignTokens.Colors.Error
                            ExStatus.DAILY_LIMIT -> DesignTokens.Colors.Warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
