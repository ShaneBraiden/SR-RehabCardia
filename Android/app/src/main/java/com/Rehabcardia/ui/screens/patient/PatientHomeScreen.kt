// PatientHomeScreen.kt — Patient dashboard with cards for navigation
package com.srcardiocare.ui.screens.patient

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.R
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.ShimmerBox
import com.srcardiocare.ui.components.TourOverlay
import com.srcardiocare.ui.components.TourStep
import com.srcardiocare.ui.components.rememberTourState
import com.srcardiocare.ui.components.tourTarget
import com.srcardiocare.ui.theme.DesignTokens
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

private const val MAX_WORKOUTS_PER_DAY = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onExerciseTap: () -> Unit,
    onScheduleTap: () -> Unit,
    onAnalyticsTap: () -> Unit,
    onNotificationsTap: () -> Unit,
    onChatTap: () -> Unit = {},
    onProfile: () -> Unit = {}
) {
    var userName by remember { mutableStateOf("") }
    var completedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var expiryText by remember { mutableStateOf<String?>(null) }
    var shouldShowTour by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val tourSteps = remember {
        listOf(
            TourStep(
                key = "progress",
                title = "Your Daily Progress",
                body = "This ring shows how many of today's exercises you've completed. It refreshes each day."
            ),
            TourStep(
                key = "exercises",
                title = "Exercises",
                body = "Start your assigned workouts here. Your doctor sets these based on your recovery plan."
            ),
            TourStep(
                key = "schedule",
                title = "Schedule",
                body = "View upcoming appointments and session reminders for your care plan."
            ),
            TourStep(
                key = "progressCard",
                title = "Progress Analytics",
                body = "Track your recovery trends and see stats over time."
            ),
            TourStep(
                key = "messages",
                title = "Messages",
                body = "Chat directly with your care team. A red dot means a new reply is waiting."
            ),
            TourStep(
                key = "notifications",
                title = "Notifications",
                body = "All reminders, feedback, and alerts land here."
            ),
            TourStep(
                key = "profile",
                title = "Profile",
                body = "Manage your account, change your password, or sign out from here."
            )
        )
    }
    val tour = rememberTourState(steps = tourSteps, active = shouldShowTour)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    try {
                        val uid = FirebaseService.currentUID ?: return@launch
                        val user = UserRepository.getUser(uid)
                        userName = user.firstName

                        // First-login tour trigger: fires only if the user has never
                        // completed (or dismissed) the tour before.
                        if (!shouldShowTour && !user.hasCompletedOnboarding) {
                            shouldShowTour = true
                        }

                        FirebaseService.updateLastSeen()

                        // Use assignment-based progress tracking
                        val today = LocalDate.now()
                        val assignments = AssignmentRepository.getAssignments(uid)
                        val todaySessions = try { SessionRepository.getTodaysSessions(uid) } catch (_: Exception) { emptyList() }

                        // Count active assignments for today
                        var activeCount = 0
                        var doneCount = 0

                        for (assignment in assignments) {
                            val startDate = try { LocalDate.parse(assignment.startDate) } catch (_: Exception) { continue }
                            val endDate = try { LocalDate.parse(assignment.endDate) } catch (_: Exception) { continue }

                            // Only count assignments active today
                            if (today.isBefore(startDate) || today.isAfter(endDate)) continue

                            activeCount++

                            val dailyFreq = assignment.dailyFrequency
                            val assignmentSessions = todaySessions.filter {
                                it.assignmentId == assignment.id
                            }
                            val completedSessions = assignmentSessions.count {
                                it.status == com.srcardiocare.data.model.SessionStatus.COMPLETED
                            }
                            if (completedSessions >= dailyFreq) {
                                doneCount++
                            }
                        }

                        totalCount = activeCount
                        completedCount = doneCount

                        // Check expiry from assignments
                        val nearestExpiry = assignments.mapNotNull { assignment ->
                            try { LocalDate.parse(assignment.endDate) } catch (_: Exception) { null }
                        }.filter { !today.isAfter(it) }.minOrNull()

                        expiryText = if (nearestExpiry != null) {
                            val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, nearestExpiry).toInt()
                            when {
                                daysRemaining < 0 -> "Plan Expired"
                                daysRemaining == 0 -> "Expires Today"
                                else -> "Expires in $daysRemaining days"
                            }
                        } else null
                    } catch (_: Exception) { }
                    isLoading = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hour = LocalDateTime.now().hour
    val greeting = when {
        hour in 5..11 -> "Good Morning"
        hour in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.sr_logo),
                                contentDescription = "SrCardioCare logo",
                                modifier = Modifier.size(38.dp),
                                contentScale = ContentScale.Fit
                            )
                            Column {
                                Text(
                                    "SrCardioCare",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "A digital health platform for Cardiac rehabilitation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = DesignTokens.Spacing.XL)
            ) {
            // Welcome header
            item {
                Column(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.MD)) {
                    Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(userName.ifBlank { "Loading…" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Progress card (or skeleton while loading)
            item {
                if (isLoading) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        shape = RoundedCornerShape(DesignTokens.Radius.XL),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(DesignTokens.Spacing.XL),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.XL)
                        ) {
                            ShimmerBox(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                ShimmerBox(
                                    modifier = Modifier.fillMaxWidth(0.6f).height(16.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                ShimmerBox(
                                    modifier = Modifier.fillMaxWidth(0.4f).height(12.dp)
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL)
                            .tourTarget(tour, "progress"),
                        shape = RoundedCornerShape(DesignTokens.Radius.XL),
                        colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Primary)
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
                                    modifier = Modifier.size(80.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                    strokeWidth = 8.dp,
                                    strokeCap = StrokeCap.Round,
                                )
                                Text("$progressPercent%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Column {
                                Text("Today's Progress", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$completedCount of $totalCount exercises", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
            }

            // Dashboard Cards section
            item {
                Text(
                    "Dashboard",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM)
                )
            }

            // Navigation Cards Grid - Row 1
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                ) {
                    DashboardCard(
                        modifier = Modifier.weight(1f).tourTarget(tour, "exercises"),
                        icon = Icons.Default.FitnessCenter,
                        title = "Exercises",
                        subtitle = "$totalCount assigned",
                        badgeCount = if (totalCount - completedCount > 0) totalCount - completedCount else null,
                        onClick = onExerciseTap
                    )
                    DashboardCard(
                        modifier = Modifier.weight(1f).tourTarget(tour, "schedule"),
                        icon = Icons.Default.CalendarMonth,
                        title = "Schedule",
                        subtitle = "View appointments",
                        onClick = onScheduleTap
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            }

            // Navigation Cards Grid - Row 2
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                ) {
                    DashboardCard(
                        modifier = Modifier.weight(1f).tourTarget(tour, "progressCard"),
                        icon = Icons.Default.Insights,
                        title = "Progress",
                        subtitle = "Track your stats",
                        onClick = onAnalyticsTap
                    )
                    DashboardCard(
                        modifier = Modifier.weight(1f).tourTarget(tour, "messages"),
                        icon = Icons.Default.ChatBubble,
                        title = "Messages",
                        subtitle = "Chat with doctor",
                        onClick = onChatTap
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            }

            // Navigation Cards Grid - Row 3
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                ) {
                    DashboardCard(
                        modifier = Modifier.weight(1f).tourTarget(tour, "notifications"),
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = "View alerts",
                        onClick = onNotificationsTap
                    )
                    DashboardCard(
                        modifier = Modifier.weight(1f).tourTarget(tour, "profile"),
                        icon = Icons.Default.Person,
                        title = "Profile",
                        subtitle = "Your account",
                        onClick = onProfile
                    )
                }
            }
            }
        }

        val finishTour: () -> Unit = {
            shouldShowTour = false
            scope.launch {
                try {
                    FirebaseService.updateUser(mapOf("hasCompletedOnboarding" to true))
                } catch (_: Exception) { }
            }
        }
        TourOverlay(
            state = tour,
            onComplete = finishTour,
            onSkip = finishTour
        )
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badgeCount: Int? = null,
    showDot: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.LG),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DesignTokens.Colors.Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = DesignTokens.Colors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (badgeCount != null) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = DesignTokens.Colors.Error
                    ) {
                        Text(badgeCount.toString())
                    }
                } else if (showDot) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(DesignTokens.Colors.Error)
                    )
                }
            }
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
