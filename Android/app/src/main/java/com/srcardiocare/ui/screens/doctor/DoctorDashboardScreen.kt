// DoctorDashboardScreen.kt — Doctor/Admin dashboard with pull-to-refresh and skeleton loading
package com.srcardiocare.ui.screens.doctor

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srcardiocare.R
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.SkeletonDonutChart
import com.srcardiocare.ui.components.SkeletonStatsCard
import com.srcardiocare.ui.components.StatItem
import com.srcardiocare.ui.components.StatItemStyle
import com.srcardiocare.ui.theme.DesignTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class UserStatus { ON_TRACK, NEEDS_ATTENTION, INACTIVE }

data class UserItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val role: String,
    val status: UserStatus,
    val isOnline: Boolean = false,
    val initials: String
)

private fun formatWorkoutDate(epochMs: Long?): String {
    if (epochMs == null) return "No completion yet"
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDashboardScreen(
    onPatientTap: (String) -> Unit,
    onDoctorTap: (String) -> Unit,
    onExerciseLibrary: () -> Unit,
    onSchedule: () -> Unit,
    onProfile: () -> Unit = {},
    onFeedbacks: () -> Unit = {},
    onPatientList: () -> Unit = {},
    onAddPatient: () -> Unit = {}
) {
    val viewModel: DoctorDashboardViewModel = viewModel()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val allUsers = ui.allUsers
    val workoutStats = ui.workoutStats
    val doctorName = ui.doctorName
    val userRole = ui.userRole
    val isLoading = ui.isLoading
    val isRefreshing = ui.isRefreshing
    val errorMessage = ui.errorMessage

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }

    // Auto-refresh when returning to the dashboard (e.g. after assigning a workout)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredUsers = if (searchQuery.isBlank()) allUsers else allUsers.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.subtitle.contains(searchQuery, ignoreCase = true) ||
        it.role.contains(searchQuery, ignoreCase = true)
    }

    // Counts for stats
    val patientCount = allUsers.count { it.role == "patient" }
    val doctorCount = allUsers.count { it.role == "doctor" }
    val onlineCount = allUsers.count { it.isOnline }
    val onTrackCount = allUsers.count { it.role == "patient" && it.status == UserStatus.ON_TRACK }
    val attentionCount = allUsers.count { it.role == "patient" && it.status == UserStatus.NEEDS_ATTENTION }
    val notAssignedCount = allUsers.count { it.role == "patient" && it.status == UserStatus.INACTIVE }

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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.refresh()
            },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = DesignTokens.Spacing.XL)
            ) {
                // Header
                item {
                    Column(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.MD)) {
                        Text("Welcome back,", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(doctorName.ifBlank { "Loading…" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                // Loading skeletons for stats + chart
                if (isLoading && errorMessage == null) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL)) {
                            SkeletonStatsCard(itemCount = 4)
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                    }
                    item {
                        Box(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL)) {
                            SkeletonDonutChart(diameter = 180.dp)
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                    }
                }

                // Stats summary card
                if (!isLoading && errorMessage == null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL),
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Primary)
                        ) {
                            Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                                if (userRole == "admin") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        StatItem(value = allUsers.size.toString(), label = "Total Users", style = StatItemStyle.LIGHT)
                                        StatItem(value = patientCount.toString(), label = "Patients", style = StatItemStyle.LIGHT)
                                        StatItem(value = doctorCount.toString(), label = "Doctors", style = StatItemStyle.LIGHT)
                                        StatItem(value = onlineCount.toString(), label = "Online", style = StatItemStyle.LIGHT)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        StatItem(value = patientCount.toString(), label = "Patients", style = StatItemStyle.LIGHT)
                                        StatItem(value = onTrackCount.toString(), label = "On Track", style = StatItemStyle.LIGHT)
                                        StatItem(value = attentionCount.toString(), label = "Attention", style = StatItemStyle.LIGHT)
                                        StatItem(value = notAssignedCount.toString(), label = "Not Assigned", style = StatItemStyle.LIGHT)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                    }
                }

                if (!isLoading && errorMessage == null) {
                    item {
                        WorkoutChartCard(
                            stats = workoutStats,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL)
                        )
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                    }
                }

                // Error state
                errorMessage?.let { msg ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Error.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                                Text("Failed to load data", fontWeight = FontWeight.SemiBold, color = DesignTokens.Colors.Error)
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = DesignTokens.Colors.Error)
                            }
                        }
                    }
                }

                // Dashboard Cards section
                item {
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    Text(
                        "Dashboard",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM)
                    )
                }

                // Navigation Cards Grid
                item {
                    // Row 1: Patients/Users + Exercises
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        DashboardCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.People,
                            title = if (userRole == "admin") "Users" else "Patients",
                            subtitle = if (userRole == "admin") "${allUsers.size} total users" else "${patientCount} patients",
                            badgeCount = onlineCount,
                            badgeLabel = "online",
                            onClick = onPatientList
                        )
                        DashboardCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.FitnessCenter,
                            title = "Exercises",
                            subtitle = "Exercise library",
                            onClick = onExerciseLibrary
                        )
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                    // Row 2: Feedbacks + Schedule
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        DashboardCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.PlayArrow,
                            title = "Feedbacks",
                            subtitle = "Patient responses",
                            onClick = onFeedbacks
                        )
                        DashboardCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CalendarMonth,
                            title = "Schedule",
                            subtitle = "Appointments",
                            onClick = onSchedule
                        )
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                    // Row 3: Profile + Add Patient (doctor only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        DashboardCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Person,
                            title = "Profile",
                            subtitle = "Your account",
                            onClick = onProfile
                        )
                        if (userRole == "doctor") {
                            DashboardCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PersonAdd,
                                title = "Add Patient",
                                subtitle = "Create new patient",
                                onClick = onAddPatient
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
                }
            }
        }
    }
}

// ── Donut Chart Colors ─────────────────────────────────────────────────────────
private val donutChartColors = listOf(
    Color(0xFF4CAF50),  // Green
    Color(0xFF2196F3),  // Blue
    Color(0xFFFF9800),  // Orange
    Color(0xFF9C27B0),  // Purple
    Color(0xFFE91E63),  // Pink
    Color(0xFF00BCD4),  // Cyan
    Color(0xFFFFEB3B),  // Yellow
    Color(0xFF795548),  // Brown
    Color(0xFF607D8B),  // Blue Grey
    Color(0xFFFF5722)   // Deep Orange
)

// ── Workout Donut Chart Card ───────────────────────────────────────────────────
@Composable
private fun WorkoutChartCard(
    stats: List<PatientWorkoutStat>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.LG),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Workout Completion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Patient workout sessions overview",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))

            if (stats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No workout data yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val totalSessions = stats.sumOf { it.completedSessions }.toFloat()
                val sweepAngles = if (totalSessions > 0) {
                    stats.map { (it.completedSessions / totalSessions) * 360f }
                } else {
                    stats.map { 0f }
                }

                // Donut Chart with click interaction
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                // Cycle through segments on click
                                selectedIndex = if (selectedIndex >= stats.size - 1) -1 else selectedIndex + 1
                            }
                    ) {
                        var startAngle = -90f
                        val strokeWidth = 40.dp.toPx()  // Donut thickness
                        val padding = strokeWidth / 2 + 8.dp.toPx()
                        val radius = (size.minDimension - padding * 2) / 2
                        val center = Offset(size.width / 2, size.height / 2)

                        sweepAngles.forEachIndexed { index, sweepAngle ->
                            if (sweepAngle > 0f) {
                                val isSelected = index == selectedIndex
                                val currentStrokeWidth = if (isSelected) strokeWidth + 10 else strokeWidth
                                
                                drawArc(
                                    color = if (isSelected) 
                                        donutChartColors[index % donutChartColors.size]
                                    else 
                                        donutChartColors[index % donutChartColors.size].copy(alpha = 0.85f),
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }

                    // Center label - show selected patient or total
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selectedIndex >= 0 && selectedIndex < stats.size) {
                            val selected = stats[selectedIndex]
                            Text(
                                "${selected.completedSessions}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = donutChartColors[selectedIndex % donutChartColors.size]
                            )
                            Text(
                                selected.patientName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                "${totalSessions.toInt()}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Total Sessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                
                Text(
                    "Tap chart to view details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))

                // Legend with clickable items
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stats.forEachIndexed { index, stat ->
                        val isSelected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) 
                                        donutChartColors[index % donutChartColors.size].copy(alpha = 0.1f) 
                                    else 
                                        Color.Transparent
                                )
                                .clickable { 
                                    selectedIndex = if (selectedIndex == index) -1 else index 
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(donutChartColors[index % donutChartColors.size])
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stat.patientName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) 
                                        donutChartColors[index % donutChartColors.size]
                                    else 
                                        MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Text(
                                "${stat.completedSessions} sessions",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected)
                                    donutChartColors[index % donutChartColors.size]
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar skeleton
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                // Name skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Subtitle skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
            // Badge skeleton
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                    .background(shimmerBrush)
            )
        }
    }
}

// ── User row ────────────────────────────────────────────────────────────────
@Composable
private fun UserRow(user: UserItem, isAdmin: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with role color
            val avatarColor = when (user.role) {
                "admin"  -> DesignTokens.Colors.Warning.copy(alpha = 0.2f)
                "doctor" -> DesignTokens.Colors.Success.copy(alpha = 0.2f)
                else     -> DesignTokens.Colors.PrimaryLight
            }
            val textColor = when (user.role) {
                "admin"  -> DesignTokens.Colors.Warning
                "doctor" -> DesignTokens.Colors.Success
                else     -> DesignTokens.Colors.PrimaryDark
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(user.initials, fontWeight = FontWeight.Bold, color = textColor)
                }
                // Online indicator removed from dashboard - shown in patient list page instead
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(user.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Role badge (for admin view) or status badge
            if (isAdmin) {
                val (badgeText, badgeColor) = when (user.role) {
                    "admin"  -> "Admin" to DesignTokens.Colors.Warning
                    "doctor" -> "Doctor" to DesignTokens.Colors.Success
                    else     -> "Patient" to DesignTokens.Colors.Primary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.SemiBold)
                }
            } else {
                val (statusText, statusColor) = when (user.status) {
                    UserStatus.ON_TRACK -> "On Track" to DesignTokens.Colors.Success
                    UserStatus.NEEDS_ATTENTION -> "Attention" to DesignTokens.Colors.Warning
                    UserStatus.INACTIVE -> "Inactive" to DesignTokens.Colors.NeutralDark
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badgeCount: Int? = null,
    badgeLabel: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(DesignTokens.Colors.Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = DesignTokens.Colors.Primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (badgeCount != null && badgeCount > 0 && badgeLabel != null) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                        .background(DesignTokens.Colors.Success.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$badgeCount $badgeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = DesignTokens.Colors.Success,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
