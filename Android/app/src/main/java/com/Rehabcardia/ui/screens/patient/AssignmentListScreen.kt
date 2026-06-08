// AssignmentListScreen.kt — Exercise assignments with active/history sections
package com.srcardiocare.ui.screens.patient

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srcardiocare.data.model.*
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.theme.DesignTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentListScreen(
    onExerciseTap: (assignment: Assignment, sessionNumber: Int) -> Unit,
    onHistoryTap: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: AssignmentListViewModel = viewModel()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val activeExercises = ui.activeExercises
    val isLoading = ui.isLoading
    val isRefreshing = ui.isRefreshing
    val errorMessage = ui.errorMessage

    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Daily Exercises", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(today.format(dateFormatter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHistoryTap) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
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
                contentPadding = PaddingValues(bottom = DesignTokens.Spacing.XXXL)
            ) {
                // Loading state - skeleton list
                if (isLoading) {
                    items(5) { SkeletonListRow() }
                }

                // Error state
                errorMessage?.let { error ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(DesignTokens.Spacing.XL),
                            colors = CardDefaults.cardColors(
                                containerColor = DesignTokens.Colors.Error.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(DesignTokens.Spacing.MD),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DesignTokens.Colors.Error
                                )
                                Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                                Text(error, color = DesignTokens.Colors.Error)
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════════
                // ACTIVE EXERCISES SECTION
                // ═══════════════════════════════════════════════════════════════
                if (!isLoading && activeExercises.isNotEmpty()) {
                    item {
                        Text(
                            "Today's Exercises",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = DesignTokens.Spacing.XL,
                                vertical = DesignTokens.Spacing.MD
                            )
                        )
                    }

                    items(activeExercises, key = { it.assignment.id }) { item ->
                        ActiveExerciseCard(
                            item = item,
                            onClick = {
                                if (item.canStartSession) {
                                    onExerciseTap(item.assignment, item.sessionsToday + 1)
                                } else if (item.currentSession != null) {
                                    // Resume in-progress session
                                    onExerciseTap(item.assignment, item.sessionsToday + 1)
                                }
                            }
                        )
                    }
                }

                // Empty state for active
                if (!isLoading && activeExercises.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(DesignTokens.Spacing.XXL),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = DesignTokens.Colors.NeutralGrey
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                Text(
                                    "No exercises assigned",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
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

// ═══════════════════════════════════════════════════════════════════════════════
// ACTIVE EXERCISE CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActiveExerciseCard(
    item: ActiveExerciseItem,
    onClick: () -> Unit
) {
    val isBlocked = !item.canStartSession && item.currentSession == null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS)
            .clickable(enabled = !isBlocked, onClick = onClick)
            .animateContentSize(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDoneToday) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = if (!item.isDoneToday && item.canStartSession) {
            CardDefaults.cardElevation(defaultElevation = 4.dp)
        } else {
            CardDefaults.cardElevation()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            item.isDoneToday -> DesignTokens.Colors.Success.copy(alpha = 0.15f)
                            item.currentSession != null -> DesignTokens.Colors.Warning.copy(alpha = 0.15f)
                            else -> DesignTokens.Colors.Primary.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    item.isDoneToday -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Done",
                        tint = DesignTokens.Colors.Success,
                        modifier = Modifier.size(24.dp)
                    )
                    item.currentSession != null -> Icon(
                        Icons.Default.Pause,
                        contentDescription = "In Progress",
                        tint = DesignTokens.Colors.Warning,
                        modifier = Modifier.size(24.dp)
                    )
                    else -> Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = DesignTokens.Colors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

            // Exercise info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.assignment.exerciseName,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isDoneToday) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.isDoneToday) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    "${item.assignment.sets} Sets • ${item.assignment.reps} Reps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Expiry warning
                item.expiryText?.let { expiry ->
                    val expiryColor = when {
                        expiry.contains("today") -> DesignTokens.Colors.Error
                        expiry.contains("tomorrow") -> DesignTokens.Colors.Warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        expiry,
                        style = MaterialTheme.typography.labelSmall,
                        color = expiryColor
                    )
                }

                // In-progress indicator
                if (item.currentSession != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = DesignTokens.Colors.Warning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Session in progress - tap to continue",
                            style = MaterialTheme.typography.labelSmall,
                            color = DesignTokens.Colors.Warning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Progress badge
            Surface(
                shape = RoundedCornerShape(DesignTokens.Radius.Full),
                color = when {
                    item.isDoneToday -> DesignTokens.Colors.Success.copy(alpha = 0.15f)
                    item.sessionsToday > 0 -> DesignTokens.Colors.Primary.copy(alpha = 0.15f)
                    else -> DesignTokens.Colors.NeutralLight
                }
            ) {
                Text(
                    text = item.progressText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        item.isDoneToday -> DesignTokens.Colors.Success
                        item.sessionsToday > 0 -> DesignTokens.Colors.Primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HISTORY EXERCISE CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryExerciseCard(item: HistoryExerciseItem) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val endDate = LocalDate.parse(item.endDate)

    val (statusColor, statusIcon, statusText) = when (item.status) {
        AssignmentHistoryStatus.FULLY_COMPLETED -> Triple(
            DesignTokens.Colors.Success,
            Icons.Default.CheckCircle,
            "Completed"
        )
        AssignmentHistoryStatus.PARTIALLY_COMPLETED -> Triple(
            DesignTokens.Colors.Warning,
            Icons.Default.Warning,
            "Partial"
        )
        AssignmentHistoryStatus.MISSED -> Triple(
            DesignTokens.Colors.Error,
            Icons.Default.Cancel,
            "Missed"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    statusIcon,
                    contentDescription = statusText,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

            // Exercise info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.assignment.exerciseName,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${endDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Completion stats
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${item.sessionsCompleted}/${item.sessionsPossible}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    "${(item.completionRate * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

