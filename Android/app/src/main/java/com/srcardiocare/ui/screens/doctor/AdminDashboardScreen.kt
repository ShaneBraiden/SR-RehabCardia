// AdminDashboardScreen.kt — Admin dashboard with doctor cards and system overview
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srcardiocare.ui.components.SkeletonDonutChart
import com.srcardiocare.ui.components.SkeletonStatsCard
import com.srcardiocare.ui.components.StatItem
import com.srcardiocare.ui.components.StatItemStyle
import com.srcardiocare.ui.theme.DesignTokens
import java.time.Duration
import java.time.Instant

data class DoctorItem(
    val id: String,
    val name: String,
    val specialty: String,
    val patientCount: Int,
    val isOnline: Boolean,
    val initials: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onDoctorTap: (String) -> Unit,
    onAddDoctor: () -> Unit,
    onAddPatient: () -> Unit,
    onUserList: () -> Unit,
    onChatMonitor: () -> Unit,
    onLoginLogs: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit = {}
) {
    val viewModel: AdminDashboardViewModel = viewModel()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val doctors = ui.doctors
    val totalPatients = ui.totalPatients
    val totalUsers = ui.totalUsers
    val onlineCount = ui.onlineCount
    val blockedCount = ui.blockedCount
    val onTrackCount = ui.onTrackCount
    val attentionCount = ui.attentionCount
    val notAssignedCount = ui.notAssignedCount
    val adminName = ui.adminName
    val isLoading = ui.isLoading
    val isRefreshing = ui.isRefreshing
    val errorMessage = ui.errorMessage

    LaunchedEffect(Unit) { viewModel.load() }

    // Auto-refresh when navigating back to the dashboard
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin Dashboard", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("System overview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                // Welcome header
                item {
                    Column(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.MD)) {
                        Text("Welcome back,", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(adminName.ifBlank { "Loading…" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }

                // Loading skeletons for stats + status + chart
                if (isLoading && errorMessage == null) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL)) {
                            SkeletonStatsCard(itemCount = 4)
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                    }
                    item {
                        Box(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL)) {
                            SkeletonStatsCard(itemCount = 3)
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                    }
                    item {
                        Box(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL)) {
                            SkeletonDonutChart(diameter = 180.dp)
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                    }
                }

                // Stats card
                if (!isLoading && errorMessage == null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL),
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Primary)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.Spacing.XL),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                StatItem(value = doctors.size.toString(), label = "Doctors", style = StatItemStyle.LIGHT)
                                StatItem(value = totalPatients.toString(), label = "Patients", style = StatItemStyle.LIGHT)
                                StatItem(value = onlineCount.toString(), label = "Online", style = StatItemStyle.LIGHT)
                                StatItem(value = blockedCount.toString(), label = "Blocked", style = StatItemStyle.LIGHT)
                            }
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                    }

                    // Patient status card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL),
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                                Text(
                                    "Patient Status — Today",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    AdminStatusChip(
                                        label = "On Track",
                                        value = onTrackCount,
                                        accent = DesignTokens.Colors.Success
                                    )
                                    AdminStatusChip(
                                        label = "Attention",
                                        value = attentionCount,
                                        accent = DesignTokens.Colors.Warning
                                    )
                                    AdminStatusChip(
                                        label = "Not Assigned",
                                        value = notAssignedCount,
                                        accent = DesignTokens.Colors.NeutralDark
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                    }

                    // User Distribution Donut Chart
                    item {
                        AdminDonutChart(
                            doctorCount = doctors.size,
                            patientCount = totalPatients,
                            adminCount = 1, // Current admin
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL)
                        )
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                    }
                }

                // Quick actions
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.People,
                            title = "All Users",
                            subtitle = "$totalUsers users",
                            onClick = onUserList
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Person,
                            title = "Profile",
                            subtitle = "Your account",
                            onClick = onProfile
                        )
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                }

                // Chat + logs monitoring actions
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Forum,
                            title = "Chat Monitor",
                            subtitle = "View all patient chats",
                            onClick = onChatMonitor
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.History,
                            title = "Login Logs",
                            subtitle = "Audit sign-ins",
                            onClick = onLoginLogs
                        )
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                }

                // Add-user cards (requested in-dashboard cards)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Add,
                            title = "Add Doctor",
                            subtitle = "Create doctor account",
                            onClick = onAddDoctor
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Add,
                            title = "Add Patient",
                            subtitle = "Create patient account",
                            onClick = onAddPatient
                        )
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                }

                // Doctors section header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Doctors", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${doctors.size} total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("Failed to load", fontWeight = FontWeight.SemiBold, color = DesignTokens.Colors.Error)
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = DesignTokens.Colors.Error)
                            }
                        }
                    }
                }

                // Loading skeleton
                if (isLoading) {
                    items(3) { DoctorCardSkeleton() }
                }

                // Empty state
                if (!isLoading && errorMessage == null && doctors.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.Spacing.XL),
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.Spacing.XXXL),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.LocalHospital,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                Text("No doctors yet", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                                Text("Use the Add Doctor card to add the first one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Doctor cards
                if (!isLoading && errorMessage == null) {
                    items(doctors) { doctor ->
                        DoctorCard(doctor = doctor, onClick = { onDoctorTap(doctor.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStatusChip(
    label: String,
    value: Int,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                .background(accent.copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.LG)) {
            Icon(icon, contentDescription = null, tint = DesignTokens.Colors.Primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DoctorCard(doctor: DoctorItem, onClick: () -> Unit) {
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
            // Avatar with online indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DesignTokens.Colors.Success.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        doctor.initials,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = DesignTokens.Colors.Success
                    )
                }
                if (doctor.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
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

            // Doctor info
            Column(modifier = Modifier.weight(1f)) {
                Text(doctor.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(doctor.specialty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = DesignTokens.Colors.Primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${doctor.patientCount} patients",
                        style = MaterialTheme.typography.labelSmall,
                        color = DesignTokens.Colors.Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

        }
    }
}

@Composable
private fun DoctorCardSkeleton() {
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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun AdminDonutChart(
    doctorCount: Int,
    patientCount: Int,
    adminCount: Int,
    modifier: Modifier = Modifier
) {
    // Chart data
    data class ChartSegment(val label: String, val count: Int, val color: Color)
    
    val segments = listOf(
        ChartSegment("Doctors", doctorCount, DesignTokens.Colors.Primary),
        ChartSegment("Patients", patientCount, DesignTokens.Colors.Success),
        ChartSegment("Admins", adminCount, DesignTokens.Colors.Warning)
    ).filter { it.count > 0 }
    val emptyRingColor = MaterialTheme.colorScheme.outlineVariant
    
    val total = segments.sumOf { it.count }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "User Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
            
            // Donut chart
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clickable { 
                        selectedIndex = if (selectedIndex >= segments.lastIndex) -1 else selectedIndex + 1 
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 32.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    val topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius)
                    val arcSize = Size(radius * 2, radius * 2)
                    
                    if (total == 0) {
                        // Empty state
                        drawArc(
                            color = emptyRingColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    } else {
                        var currentAngle = -90f
                        segments.forEachIndexed { index, segment ->
                            val sweepAngle = (segment.count.toFloat() / total) * 360f
                            val color = if (selectedIndex == index) segment.color else segment.color.copy(alpha = 0.7f)
                            val width = if (selectedIndex == index) strokeWidth * 1.15f else strokeWidth
                            
                            drawArc(
                                color = color,
                                startAngle = currentAngle,
                                sweepAngle = sweepAngle - 2f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = width, cap = StrokeCap.Round)
                            )
                            currentAngle += sweepAngle
                        }
                    }
                }
                
                // Center label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedIndex in segments.indices) {
                        val selected = segments[selectedIndex]
                        Text(
                            selected.count.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = selected.color
                        )
                        Text(
                            selected.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            total.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Total Users",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                segments.forEachIndexed { index, segment ->
                    Row(
                        modifier = Modifier
                            .clickable { selectedIndex = if (selectedIndex == index) -1 else index },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(segment.color)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            segment.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
