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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.srcardiocare.data.firebase.FeedbackRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.model.PostWorkoutFeedback
import com.srcardiocare.ui.components.SkeletonBarChart
import com.srcardiocare.ui.components.SkeletonDonutChart
import com.srcardiocare.ui.components.SkeletonStatsCard
import com.srcardiocare.ui.theme.DesignTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    LaunchedEffect(Unit) {
        try {
            val uid = FirebaseService.currentUID ?: return@LaunchedEffect
            val workouts = FirebaseService.fetchWorkouts(uid)
            totalWorkouts = workouts.size
            completedWorkouts = workouts.count { it.second["completedAt"] != null }
            
            // Calculate in-progress (started but not completed)
            inProgressWorkouts = workouts.count { 
                it.second["startedAt"] != null && it.second["completedAt"] == null 
            }
            
            // Missed = total - completed - in progress
            missedWorkouts = max(0, totalWorkouts - completedWorkouts - inProgressWorkouts)
            
            if (totalWorkouts > 0) {
                complianceText = "${(completedWorkouts * 100 / totalWorkouts)}%"
            }
            streakText = "$completedWorkouts"

            // Build a rolling 7-day chart ending today from workout completion ratio.
            val today = LocalDate.now()
            val startDate = today.minusDays(6)
            val dayLabels = (0L..6L).map { offset ->
                startDate.plusDays(offset)
            }
            val dayBuckets = MutableList(7) { mutableListOf<Float>() }

            workouts.forEach { (_, data) ->
                val instant = parseToInstant(data["startedAt"]) ?: parseToInstant(data["completedAt"]) ?: return@forEach
                val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                if (localDate.isBefore(startDate) || localDate.isAfter(today)) return@forEach

                val index = java.time.temporal.ChronoUnit.DAYS.between(startDate, localDate).toInt()
                if (index !in 0..6) return@forEach

                val totalExercises = (data["totalExercises"] as? Number)?.toFloat() ?: 0f
                val completedExercises = (data["exercisesCompleted"] as? Number)?.toFloat() ?: 0f
                val completionRatio = when {
                    totalExercises > 0f -> (completedExercises / totalExercises).coerceIn(0f, 1f)
                    data["completedAt"] != null -> 1f
                    else -> 0f
                }

                dayBuckets[index].add(completionRatio)
            }

            weeklyBars = dayLabels.mapIndexed { i, date ->
                val avg = if (dayBuckets[i].isNotEmpty()) {
                    dayBuckets[i].average().toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                BarData(date.dayOfWeek.name.first().toString(), avg)
            }
            
            try {
                feedbacks = FeedbackRepository.getPatientFeedbacks(uid)
            } catch (e: Exception) { }

        } catch (_: Exception) { }
        isLoading = false
    }
    
    val segments = listOf(
        DonutSegment("Completed", completedWorkouts, DesignTokens.Colors.Success),
        DonutSegment("In Progress", inProgressWorkouts, DesignTokens.Colors.Warning),
        DonutSegment("Missed", missedWorkouts, DesignTokens.Colors.Error)
    ).filter { it.value > 0 }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Progress", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {}) {
                        Text("This Week ▾", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    }
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
                "Track your recovery journey",
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
                    Text("Workout Overview", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                    // Donut chart with click interaction
                    DonutChart(
                        segments = segments,
                        total = totalWorkouts,
                        selectedSegment = selectedSegment,
                        onSegmentClick = { segment ->
                            selectedSegment = if (selectedSegment == segment) null else segment
                        },
                        modifier = Modifier.size(200.dp)
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
            ) {
                StatCard(complianceText, "Compliance", DesignTokens.Colors.Primary, Modifier.weight(1f))
                StatCard(streakText, "Workouts", DesignTokens.Colors.Warning, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            // Weekly Bar Chart card
            Card(
                shape = RoundedCornerShape(DesignTokens.Radius.XL),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                    Text("Weekly Performance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                                val resp = f.respiratoryDifficulty.toFloat()
                                val stress = f.stress
                                val strain = f.strain
                                
                                val barColor = if (stress || strain) DesignTokens.Colors.Warning else DesignTokens.Colors.Success
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
                            "Respiratory Difficulty (1-10) with Stress/Strain (Yellow)",
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
                        Text("No feedback data available yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            } // end else (isLoading)

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
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
                    "Total",
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

private fun parseToInstant(raw: Any?): Instant? {
    return when (raw) {
        is Timestamp -> raw.toDate().toInstant()
        is String -> try {
            Instant.parse(raw)
        } catch (_: Exception) {
            null
        }
        else -> null
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
