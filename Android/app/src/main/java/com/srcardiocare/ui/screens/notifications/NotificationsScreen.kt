package com.srcardiocare.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.NotificationRepository
import com.srcardiocare.data.model.AppNotification
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenRoute: (route: String, params: Map<String, String>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    var items by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val uid = FirebaseService.currentUID ?: return@LaunchedEffect
        NotificationRepository.observeNotificationsTyped(uid).collect {
            items = it
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val unread = items.count { !it.isRead }
                    if (unread > 0) {
                        IconButton(onClick = {
                            scope.launch {
                                val uid = FirebaseService.currentUID ?: return@launch
                                runCatching { FirebaseService.markAllNotificationsRead(uid) }
                                    .onSuccess { toast("All notifications marked read") }
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
            ) {
                items(5) { SkeletonListRow() }
            }
            return@Scaffold
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "You're all caught up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(DesignTokens.Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)
        ) {
            items(items, key = { it.id }) { notification ->
                NotificationRow(
                    notification = notification,
                    onTap = {
                        scope.launch { runCatching { FirebaseService.markNotificationRead(notification.id) } }
                        if (notification.route.isNotBlank()) onOpenRoute(notification.route, notification.params)
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    onTap: () -> Unit
) {
    val isRead = notification.isRead
    val type = notification.type
    val (icon, tint) = iconFor(type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(
            containerColor = if (isRead) MaterialTheme.colorScheme.surface
            else DesignTokens.Colors.Primary.copy(alpha = 0.06f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.LG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title.ifBlank { "Notification" },
                    fontWeight = if (isRead) FontWeight.Medium else FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val body = notification.body
                if (body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val timeLabel = formatTime(notification.createdAtMs)
                if (timeLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            if (!isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DesignTokens.Colors.Primary)
                )
            }
        }
    }
}

private fun iconFor(type: String): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (type.lowercase()) {
    "exercise", "prescription" -> Icons.Default.FitnessCenter to DesignTokens.Colors.Primary
    "message" -> Icons.Default.ChatBubble to DesignTokens.Colors.Primary
    "appointment", "appointment_update", "appointment_request" -> Icons.Default.CalendarMonth to DesignTokens.Colors.Primary
    "doctor_assigned" -> Icons.Default.Person to DesignTokens.Colors.Primary
    else -> Icons.Default.Notifications to DesignTokens.Colors.Primary
}

private fun formatTime(raw: Any?): String {
    val millis = when (raw) {
        is com.google.firebase.Timestamp -> raw.toDate().time
        is Long -> raw
        else -> return ""
    }
    val now = System.currentTimeMillis()
    val diff = now - millis
    val sec = diff / 1000
    val min = sec / 60
    val hr = min / 60
    val day = hr / 24
    return when {
        sec < 60 -> "Just now"
        min < 60 -> "${min}m ago"
        hr < 24 -> "${hr}h ago"
        day < 7 -> "${day}d ago"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(millis))
    }
}
