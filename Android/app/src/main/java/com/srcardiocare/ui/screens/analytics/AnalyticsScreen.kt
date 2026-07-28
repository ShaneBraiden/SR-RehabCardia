// AnalyticsScreen.kt — Patient progress with donut chart and stat cards
package com.srcardiocare.ui.screens.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import com.srcardiocare.R
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FeedbackRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.data.model.PostWorkoutFeedback
import com.srcardiocare.data.model.SessionStatus
import com.srcardiocare.ui.components.SkeletonBarChart
import com.srcardiocare.ui.components.SkeletonDonutChart
import com.srcardiocare.ui.components.SkeletonStatsCard
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class BarData(val day: String, val value: Float)

data class DonutSegment(
    val label: String,
    val value: Int,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    var weeklyBars by remember { mutableStateOf<List<BarData>>(listOf(
        BarData("M", 0f), BarData("T", 0f), BarData("W", 0f),
        BarData("T", 0f), BarData("F", 0f), BarData("S", 0f), BarData("S", 0f)
    )) }
    var complianceText by remember { mutableStateOf("--") }
    var streakText by remember { mutableStateOf("--") }
    var feedbacks by remember { mutableStateOf<List<PostWorkoutFeedback>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Donut chart data
    var completedWorkouts by remember { mutableIntStateOf(0) }
    var inProgressWorkouts by remember { mutableIntStateOf(0) }
    var missedWorkouts by remember { mutableIntStateOf(0) }
    var totalWorkouts by remember { mutableIntStateOf(0) }
    var selectedSegment by remember { mutableStateOf<DonutSegment?>(null) }

    val scope = rememberCoroutineScope()

    // Progress is derived from the live `assignments` + `sessionLogs` data (the
    // legacy `workouts` collection is no longer written, so it is not read here).
    suspend fun loadData() {
        try {
            val uid = FirebaseService.currentUID ?: return
            val assignments = AssignmentRepository.getAssignments(uid)
            val allSessions = assignments.flatMap { a ->
                try { SessionRepository.getAllSessionsForAssignment(uid, a.id) }
                catch (_: Exception) { emptyList() }
            }

            val completed = allSessions.count { it.status == SessionStatus.COMPLETED }
            val inProgress = allSessions.count { it.status == SessionStatus.IN_PROGRESS }
            val abandoned = allSessions.count { it.status == SessionStatus.ABANDONED }

            completedWorkouts = completed
            inProgressWorkouts = inProgress
            missedWorkouts = abandoned
            totalWorkouts = allSessions.size

            complianceText = if (allSessions.isNotEmpty()) "${completed * 100 / allSessions.size}%" else "--"
            streakText = "$completed"

            // Rolling 7-day chart: completed sessions vs. expected sessions per day.
            val today = LocalDate.now()
            val startDate = today.minusDays(6)
            val completedByDate = allSessions
                .filter { it.status == SessionStatus.COMPLETED }
                .groupingBy { it.sessionDate }
                .eachCount()

            weeklyBars = (0L..6L).map { offset ->
                val date = startDate.plusDays(offset)
                val expected = assignments.sumOf { a ->
                    val s = try { LocalDate.parse(a.startDate) } catch (_: Exception) { return@sumOf 0 }
                    val e = try { LocalDate.parse(a.endDate) } catch (_: Exception) { return@sumOf 0 }
                    if (!date.isBefore(s) && !date.isAfter(e)) a.dailyFrequency else 0
                }
                val done = completedByDate[date.toString()] ?: 0
                val ratio = if (expected > 0) (done.toFloat() / expected).coerceIn(0f, 1f) else 0f
                BarData(date.dayOfWeek.name.first().toString(), ratio)
            }

            try {
                feedbacks = FeedbackRepository.getPatientFeedbacks(uid)
            } catch (_: Exception) { }
        } catch (_: Exception) { }
        isLoading = false
    }

    // Reload whenever the screen resumes so progress reflects just-finished workouts.
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

    val segments = listOf(
        DonutSegment(stringResource(R.string.status_completed), completedWorkouts, DesignTokens.Colors.Success),
        DonutSegment(stringResource(R.string.assignments_in_progress), inProgressWorkouts, DesignTokens.Colors.Warning),
        DonutSegment(stringResource(R.string.status_missed), missedWorkouts, DesignTokens.Colors.Error)
    ).filter { it.value > 0 }
    
    TutorialHost(tourKey = TutorialKeys.ANALYTICS, steps = TutorialTours.analytics) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {},
                        modifier = Modifier.tutorialTarget(TutorialIds.ANALYTICS_RANGE)
                    ) {
                        Text(stringResource(R.string.analytics_this_week), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    }
                    TutorialHelpButton()
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.XL)
        ) {
            Text(
                stringResource(R.string.analytics_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            if (isLoading) {
                SkeletonDonutChart(diameter = 200.dp)
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
                SkeletonStatsCard(itemCount = 2)
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
                SkeletonBarChart(barCount = 7)
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
                SkeletonBarChart(barCount = 7, chartHeight = 80.dp)
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
            } else {
            // Donut Chart Card
            Card(
                shape = RoundedCornerShape(DesignTokens.Radius.XL),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(DesignTokens.Spacing.XL),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.analytics_workout_overview), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                    // Donut chart with click interaction
                    DonutChart(
                        segments = segments,
                        total = totalWorkouts,
                        selectedSegment = selectedSegment,
                        onSegmentClick = { segment ->
                            selectedSegment = if (selectedSegment == segment) null else segment
                        },
                        modifier = Modifier.size(200.dp).tutorialTarget(TutorialIds.ANALYTICS_CHART)
                    )

                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        segments.forEach { segment ->
                            LegendItem(
                                color = segment.color,
                                label = segment.label,
                                isSelected = selectedSegment == segment,
                                onClick = {
                                    selectedSegment = if (selectedSegment == segment) null else segment
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // Stat cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget(TutorialIds.ANALYTICS_METRICS),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
            ) {
                StatCard(complianceText, stringResource(R.string.analytics_compliance), DesignTokens.Colors.Primary, Modifier.weight(1f))
                StatCard(streakText, stringResource(R.string.analytics_workouts), DesignTokens.Colors.Warning, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // Weekly Bar Chart card
            Card(
                shape = RoundedCornerShape(DesignTokens.Radius.XL),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                    Text(stringResource(R.string.analytics_weekly_performance), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                    // Bar chart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklyBars.forEach { bar ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(DesignTokens.Colors.Primary)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // Bar
                                val barHeight = if (bar.value > 0f) max(bar.value * 60f, 6f) else 4f
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(barHeight.dp)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(DesignTokens.Colors.NeutralLight)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    bar.day,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // Metrics / Feedbacks Chart
            if (feedbacks.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.Radius.XL),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                        Text(stringResource(R.string.analytics_health_metrics), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                            stringResource(R.string.analytics_metrics_legend),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.Radius.XL),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.LG),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.analytics_no_feedback), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            } // end else (isLoading)

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
        }
    }
    }
}

@Composable
private fun DonutChart(
    segments: List<DonutSegment>,
    total: Int,
    selectedSegment: DonutSegment?,
    onSegmentClick: (DonutSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeWidth = 28.dp
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "donut-animation"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    // Calculate which segment was clicked based on touch position
                    // For simplicity, clicking the chart cycles through segments or deselects
                }
        ) {
            val canvasSize = size.minDimension
            val radius = (canvasSize - strokeWidth.toPx()) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            val totalValue = segments.sumOf { it.value }.toFloat().coerceAtLeast(1f)
            var startAngle = -90f // Start from top
            
            segments.forEach { segment ->
                val sweepAngle = (segment.value / totalValue) * 360f * animatedProgress
                val isSelected = selectedSegment == segment
                val currentStrokeWidth = if (isSelected) strokeWidth.toPx() + 8 else strokeWidth.toPx()
                
                drawArc(
                    color = if (isSelected) segment.color else segment.color.copy(alpha = 0.85f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - radius,
                        center.y - radius
                    ),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Round)
                )
                
                startAngle += sweepAngle
            }
            
            // Draw empty state if no segments
            if (segments.isEmpty() || total == 0) {
                drawArc(
                    color = DesignTokens.Colors.NeutralLight,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        
        // Center content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedSegment != null) {
                // Show selected segment info
                Text(
                    "${selectedSegment.value}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = selectedSegment.color
                )
                Text(
                    selectedSegment.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Show total
                Text(
                    "$total",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.analytics_total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.Radius.SM))
            .clickable(onClick = onClick)
            .background(if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(DesignTokens.Radius.XL),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.MD),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
