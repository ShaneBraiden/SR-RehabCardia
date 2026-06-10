// AdminSettingsScreen.kt — Admin-only API & access-control settings
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

private data class AccessUser(
    val id: String,
    val name: String,
    val role: String,
    val isBlocked: Boolean,
    val blockReason: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    var sessionLocksEnabled by remember { mutableStateOf(true) }
    var blockAllPatients by remember { mutableStateOf(false) }
    var blockAllDoctors by remember { mutableStateOf(false) }
    var isGlobalSettingsLoading by remember { mutableStateOf(false) }

    var accessUsers by remember { mutableStateOf<List<AccessUser>>(emptyList()) }
    var isAccessUsersLoading by remember { mutableStateOf(false) }
    var updatingUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var accessFilter by remember { mutableStateOf("all") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        isGlobalSettingsLoading = true
        isAccessUsersLoading = true
        try {
            val settings = FirebaseService.fetchAccessControlSettings()
            sessionLocksEnabled = settings.sessionLocksEnabled
            blockAllPatients = settings.blockAllPatients
            blockAllDoctors = settings.blockAllDoctors

            val currentUid = FirebaseService.currentUID
            accessUsers = UserRepository.getAllUsers().mapNotNull { user ->
                if (user.id == currentUid) return@mapNotNull null
                if (user.role != "patient" && user.role != "doctor") return@mapNotNull null

                val name = user.fullName.ifBlank { user.email.ifBlank { "Unknown" } }

                AccessUser(
                    id = user.id,
                    name = name,
                    role = user.role,
                    isBlocked = user.isBlocked,
                    blockReason = user.blockReason ?: ""
                )
            }.sortedWith(compareBy<AccessUser> { it.role }.thenBy { it.name })
        } catch (e: Exception) {
            statusMessage = ErrorHandler.getDisplayMessage(e, "load settings")
        }
        isGlobalSettingsLoading = false
        isAccessUsersLoading = false
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.XL)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                    Text("API / Blocking Settings", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                    Text(
                        "Control app-wide API access and session locking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                    if (isGlobalSettingsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = DesignTokens.Colors.Primary
                        )
                    } else {
                        PolicyToggleRow(
                            title = "Enforce Session Locks",
                            subtitle = "Block completed or expired workout sessions.",
                            checked = sessionLocksEnabled,
                            onCheckedChange = { enabled ->
                                sessionLocksEnabled = enabled
                                scope.launch {
                                    try {
                                        FirebaseService.updateAccessControlSettings(sessionLocksEnabled = enabled)
                                        statusMessage = "Session lock policy updated."
                                        toast("Session lock policy updated")
                                    } catch (e: Exception) {
                                        statusMessage = ErrorHandler.getDisplayMessage(e, "update setting")
                                        toast("Failed to update policy")
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                        PolicyToggleRow(
                            title = "Block All Patients API",
                            subtitle = "Stops all patient accounts from app/API access.",
                            checked = blockAllPatients,
                            onCheckedChange = { blocked ->
                                blockAllPatients = blocked
                                scope.launch {
                                    try {
                                        FirebaseService.updateAccessControlSettings(blockAllPatients = blocked)
                                        statusMessage = if (blocked) "All patient API access is blocked." else "Patient API access restored."
                                        toast(if (blocked) "All patients blocked" else "All patients unblocked")
                                    } catch (e: Exception) {
                                        statusMessage = ErrorHandler.getDisplayMessage(e, "update setting")
                                        toast("Failed to update setting")
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                        PolicyToggleRow(
                            title = "Block All Doctors API",
                            subtitle = "Stops all doctor accounts from app/API access.",
                            checked = blockAllDoctors,
                            onCheckedChange = { blocked ->
                                blockAllDoctors = blocked
                                scope.launch {
                                    try {
                                        FirebaseService.updateAccessControlSettings(blockAllDoctors = blocked)
                                        statusMessage = if (blocked) "All doctor API access is blocked." else "Doctor API access restored."
                                        toast(if (blocked) "All doctors blocked" else "All doctors unblocked")
                                    } catch (e: Exception) {
                                        statusMessage = ErrorHandler.getDisplayMessage(e, "update setting")
                                        toast("Failed to update setting")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("User-Level Blocking", fontWeight = FontWeight.Bold)
                            Text(
                                "Block/unblock specific patient and doctor accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { scope.launch { load() } }) {
                            Text("Refresh")
                        }
                    }

                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)) {
                        FilterChip(selected = accessFilter == "all", onClick = { accessFilter = "all" }, label = { Text("All") })
                        FilterChip(selected = accessFilter == "doctors", onClick = { accessFilter = "doctors" }, label = { Text("Doctors") })
                        FilterChip(selected = accessFilter == "patients", onClick = { accessFilter = "patients" }, label = { Text("Patients") })
                    }

                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                    val filtered = accessUsers.filter {
                        when (accessFilter) {
                            "doctors" -> it.role == "doctor"
                            "patients" -> it.role == "patient"
                            else -> true
                        }
                    }

                    when {
                        isAccessUsersLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = DesignTokens.Colors.Primary
                            )
                        }
                        filtered.isEmpty() -> {
                            Text(
                                "No users available for this filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            filtered.forEach { user ->
                                val isUpdating = updatingUserIds.contains(user.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${user.role.replaceFirstChar { it.uppercase() }} • ${if (user.isBlocked) "Blocked" else "Active"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (user.blockReason.isNotBlank()) {
                                            Text(
                                                user.blockReason,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isUpdating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.width(22.dp).height(22.dp),
                                            strokeWidth = 2.dp,
                                            color = DesignTokens.Colors.Primary
                                        )
                                    } else {
                                        Switch(
                                            checked = user.isBlocked,
                                            onCheckedChange = { blocked ->
                                                updatingUserIds = updatingUserIds + user.id
                                                accessUsers = accessUsers.map {
                                                    if (it.id == user.id) it.copy(
                                                        isBlocked = blocked,
                                                        blockReason = if (blocked) (it.blockReason.ifBlank { "Blocked from admin settings" }) else ""
                                                    ) else it
                                                }
                                                scope.launch {
                                                    try {
                                                        FirebaseService.setUserAccessBlocked(
                                                            uid = user.id,
                                                            blocked = blocked,
                                                            reason = if (blocked) "Blocked from admin settings" else null
                                                        )
                                                        statusMessage = if (blocked) "${user.name} access blocked." else "${user.name} access restored."
                                                        toast(if (blocked) "${user.name} blocked" else "${user.name} unblocked")
                                                    } catch (e: Exception) {
                                                        statusMessage = ErrorHandler.getDisplayMessage(e, "update ${user.name}")
                                                        toast("Failed to update ${user.name}")
                                                        load()
                                                    }
                                                    updatingUserIds = updatingUserIds - user.id
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = DesignTokens.Colors.Primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            statusMessage?.let { message ->
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
        }
    }
}

@Composable
private fun PolicyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = DesignTokens.Colors.Primary)
        )
    }
}
