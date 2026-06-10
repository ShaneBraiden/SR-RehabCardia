// PatientListScreen.kt — Full patient/user list view for doctors and admins
package com.srcardiocare.ui.screens.doctor

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srcardiocare.core.push.NotificationEvent
import com.srcardiocare.core.push.Notifier
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Duration

enum class PatientFilter { ALL, NOT_ASSIGNED, ON_TRACK, ATTENTION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    onPatientTap: (String) -> Unit,
    onDoctorTap: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: PatientListViewModel = viewModel()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val allUsers = ui.allUsers
    val userRole = ui.userRole
    val isLoading = ui.isLoading
    val isRefreshing = ui.isRefreshing
    val errorMessage = ui.errorMessage

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PatientFilter.ALL) }
    var isReminding by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }

    // Auto-refresh when returning from patient profile / assignment screens
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

    val patients = remember(allUsers) { allUsers.filter { it.role == "patient" } }
    val notAssignedCount = remember(patients) { patients.count { it.status == UserStatus.INACTIVE } }
    val onTrackCount = remember(patients) { patients.count { it.status == UserStatus.ON_TRACK } }
    val attentionCount = remember(patients) { patients.count { it.status == UserStatus.NEEDS_ATTENTION } }

    val filteredUsers = remember(allUsers, searchQuery, selectedFilter) {
        val byFilter = when (selectedFilter) {
            PatientFilter.ALL -> allUsers
            PatientFilter.NOT_ASSIGNED -> patients.filter { it.status == UserStatus.INACTIVE }
            PatientFilter.ON_TRACK -> patients.filter { it.status == UserStatus.ON_TRACK }
            PatientFilter.ATTENTION -> patients.filter { it.status == UserStatus.NEEDS_ATTENTION }
        }
        if (searchQuery.isBlank()) byFilter
        else byFilter.filter { it.name.contains(searchQuery, ignoreCase = true) || it.subtitle.contains(searchQuery, ignoreCase = true) }
    }

    val screenTitle = if (userRole == "admin") "All Users" else "Patients"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                // Search
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = InputValidator.limitLength(it, InputValidator.MaxLength.TEXT_FIELD) },
                        placeholder = { Text(if (userRole == "admin") "Search all users…" else "Search patients…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.MD),
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesignTokens.Colors.Primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                // Filter chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)
                    ) {
                        PatientFilterChip(
                            label = "All",
                            count = if (userRole == "admin") allUsers.size else patients.size,
                            selected = selectedFilter == PatientFilter.ALL,
                            onClick = { selectedFilter = PatientFilter.ALL }
                        )
                        PatientFilterChip(
                            label = "Not Assigned",
                            count = notAssignedCount,
                            selected = selectedFilter == PatientFilter.NOT_ASSIGNED,
                            onClick = { selectedFilter = PatientFilter.NOT_ASSIGNED },
                            accent = DesignTokens.Colors.NeutralDark
                        )
                        PatientFilterChip(
                            label = "On Track",
                            count = onTrackCount,
                            selected = selectedFilter == PatientFilter.ON_TRACK,
                            onClick = { selectedFilter = PatientFilter.ON_TRACK },
                            accent = DesignTokens.Colors.Success
                        )
                        PatientFilterChip(
                            label = "Attention",
                            count = attentionCount,
                            selected = selectedFilter == PatientFilter.ATTENTION,
                            onClick = { selectedFilter = PatientFilter.ATTENTION },
                            accent = DesignTokens.Colors.Warning
                        )
                    }
                }

                // Remind All banner — only shown for Attention filter with patients to nudge
                if (selectedFilter == PatientFilter.ATTENTION && filteredUsers.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Warning.copy(alpha = 0.10f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.Spacing.MD),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = DesignTokens.Colors.Warning,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${filteredUsers.size} need a nudge", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Send a workout reminder to each patient in this list",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        val recipients = filteredUsers.map { it.id }
                                        scope.launch {
                                            isReminding = true
                                            recipients.forEach { pid ->
                                                Notifier.send(NotificationEvent.WorkoutReminder(patientId = pid))
                                            }
                                            isReminding = false
                                            snackbarHostState.showSnackbar(
                                                "Reminder sent to ${recipients.size} patient${if (recipients.size != 1) "s" else ""}"
                                            )
                                        }
                                    },
                                    enabled = !isReminding,
                                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Warning)
                                ) {
                                    if (isReminding) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Remind All", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Count header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val headerLabel = when (selectedFilter) {
                            PatientFilter.ALL -> if (userRole == "admin") "All Users" else "Patient Overview"
                            PatientFilter.NOT_ASSIGNED -> "Not Assigned"
                            PatientFilter.ON_TRACK -> "On Track Today"
                            PatientFilter.ATTENTION -> "Needs Attention"
                        }
                        Text(
                            headerLabel,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "${filteredUsers.size} user${if (filteredUsers.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Loading skeleton
                if (isLoading) {
                    items(5) { SkeletonUserRow() }
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

                // Empty state
                if (!isLoading && errorMessage == null && filteredUsers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.Spacing.XXXL),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                val (emptyTitle, emptySubtitle) = when {
                                    searchQuery.isNotBlank() ->
                                        "No users match \"$searchQuery\"" to "Try a different search term"
                                    selectedFilter == PatientFilter.NOT_ASSIGNED ->
                                        "No unassigned patients" to "Every patient has at least one exercise"
                                    selectedFilter == PatientFilter.ON_TRACK ->
                                        "No one is on track yet today" to "Patients appear here once they finish their daily workouts"
                                    selectedFilter == PatientFilter.ATTENTION ->
                                        "Nobody needs a nudge" to "All assigned patients have completed today"
                                    else ->
                                        "No users yet" to "Use the dashboard actions to add users"
                                }
                                Text(
                                    emptyTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                                Text(
                                    emptySubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // User rows
                if (!isLoading) {
                    items(filteredUsers) { user ->
                        UserListRow(user = user, isAdmin = userRole == "admin", onClick = {
                            if (user.role == "patient") {
                                onPatientTap(user.id)
                            } else if (user.role == "doctor" && userRole == "admin") {
                                onDoctorTap(user.id)
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonUserRow() {
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
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(shimmerBrush))
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            }
            Box(modifier = Modifier.width(60.dp).height(22.dp).clip(RoundedCornerShape(DesignTokens.Radius.Full)).background(shimmerBrush))
        }
    }
}

@Composable
private fun UserListRow(user: UserItem, isAdmin: Boolean, onClick: () -> Unit) {
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
                if (user.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(DesignTokens.Colors.Success)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(user.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

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
private fun PatientFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = DesignTokens.Colors.Primary
) {
    val containerColor = if (selected) accent else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val countBg = if (selected) Color.White.copy(alpha = 0.25f) else accent.copy(alpha = 0.15f)
    val countColor = if (selected) Color.White else accent

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.Radius.Full))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                .background(countBg)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = countColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
