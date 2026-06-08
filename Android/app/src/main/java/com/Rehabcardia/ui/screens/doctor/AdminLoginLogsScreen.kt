package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.theme.DesignTokens
import java.text.SimpleDateFormat
import java.util.Locale

private data class LoginLogItem(
    val id: String,
    val email: String,
    val role: String,
    val status: String,
    val message: String?,
    val platform: String,
    val createdAt: Timestamp?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginLogsScreen(
    onBack: () -> Unit
) {
    var logs by remember { mutableStateOf<List<LoginLogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        try {
            val fetched = FirebaseService.fetchLoginLogs(200)
            logs = fetched.map { (id, data) ->
                LoginLogItem(
                    id = id,
                    email = data["email"] as? String ?: "Unknown",
                    role = (data["role"] as? String)?.uppercase().orEmpty().ifBlank { "UNKNOWN" },
                    status = (data["status"] as? String)?.lowercase().orEmpty(),
                    message = data["message"] as? String,
                    platform = (data["platform"] as? String ?: "android").uppercase(),
                    createdAt = data["createdAt"] as? Timestamp
                )
            }
            errorMessage = null
        } catch (e: Exception) {
            logs = emptyList()
            errorMessage = ErrorHandler.getDisplayMessage(e, "load login logs")
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            isLoading -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
                ) {
                    items(8) { SkeletonListRow() }
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(errorMessage!!, color = DesignTokens.Colors.Error)
                }
            }

            logs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No login activity found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        horizontal = DesignTokens.Spacing.XL,
                        vertical = DesignTokens.Spacing.MD
                    ),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)
                ) {
                    items(logs) { item ->
                        LoginLogCard(item = item)
                    }
                    item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL)) }
                }
            }
        }
    }
}

@Composable
private fun LoginLogCard(item: LoginLogItem) {
    val statusLabel = when (item.status) {
        "success" -> "SUCCESS"
        "failed" -> "FAILED"
        "blocked" -> "BLOCKED"
        else -> item.status.uppercase().ifBlank { "UNKNOWN" }
    }
    val statusColor = when (item.status) {
        "success" -> DesignTokens.Colors.Success
        "blocked" -> DesignTokens.Colors.Warning
        "failed" -> DesignTokens.Colors.Error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault()) }
    val dateText = item.createdAt?.toDate()?.let { sdf.format(it) } ?: "Unknown time"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.LG)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.email,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatusChip(label = statusLabel, color = statusColor)
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
            Text(
                text = "Role: ${item.role}   Platform: ${item.platform}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!item.message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
