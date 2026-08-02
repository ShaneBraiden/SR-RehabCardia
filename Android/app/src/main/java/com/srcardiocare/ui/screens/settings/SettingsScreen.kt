// SettingsScreen.kt — Appearance, language and notification preferences.
package com.srcardiocare.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srcardiocare.core.locale.LocaleManager
import com.srcardiocare.core.prefs.AppPreferences
import com.srcardiocare.core.push.PushChannels
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.DisclaimerScreen
import com.srcardiocare.ui.components.LegalLinks
import com.srcardiocare.ui.components.findActivity
import com.srcardiocare.ui.components.openUrl
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

/**
 * One place for the settings that used to be scattered or missing entirely.
 *
 * [showLanguage] is false for clinicians: doctor and admin screens are not
 * localised, so offering Tamil there would produce a half-translated app rather
 * than a Tamil one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    showLanguage: Boolean
) {
    val context = LocalContext.current

    var themeMode by remember { mutableStateOf(AppPreferences.getThemeMode(context)) }
    var mutedChannels by remember { mutableStateOf(AppPreferences.getMutedChannels(context)) }
    var inAppSound by remember { mutableStateOf(AppPreferences.isInAppSoundEnabled(context)) }
    val currentLanguage = LocaleManager.getLanguage(context)

    val scope = rememberCoroutineScope()
    var showDisclaimer by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteState by remember { mutableStateOf<DeleteState>(DeleteState.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            SettingsSection("Appearance")
            SettingsCard {
                ThemeOptionRow(
                    label = "Follow device",
                    caption = "Match your phone's day/night setting",
                    icon = Icons.Default.PhoneAndroid,
                    selected = themeMode == AppPreferences.ThemeMode.SYSTEM,
                    onClick = {
                        themeMode = AppPreferences.ThemeMode.SYSTEM
                        AppPreferences.setThemeMode(context, themeMode)
                    }
                )
                ThemeOptionRow(
                    label = "Light",
                    caption = "Always use the light theme",
                    icon = Icons.Default.LightMode,
                    selected = themeMode == AppPreferences.ThemeMode.LIGHT,
                    onClick = {
                        themeMode = AppPreferences.ThemeMode.LIGHT
                        AppPreferences.setThemeMode(context, themeMode)
                    }
                )
                ThemeOptionRow(
                    label = "Dark",
                    caption = "Easier on the eyes at night",
                    icon = Icons.Default.DarkMode,
                    selected = themeMode == AppPreferences.ThemeMode.DARK,
                    onClick = {
                        themeMode = AppPreferences.ThemeMode.DARK
                        AppPreferences.setThemeMode(context, themeMode)
                    },
                    showDivider = false
                )
            }

            if (showLanguage) {
                SettingsSection("Language")
                SettingsCard {
                    LanguageOptionRow(
                        label = "English",
                        selected = currentLanguage == LocaleManager.ENGLISH,
                        onClick = { applyLanguage(context, LocaleManager.ENGLISH) }
                    )
                    LanguageOptionRow(
                        label = "தமிழ்",
                        selected = currentLanguage == LocaleManager.TAMIL,
                        onClick = { applyLanguage(context, LocaleManager.TAMIL) },
                        showDivider = false
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                Text(
                    "Changing the language restarts the app screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection("Notifications")
            SettingsCard {
                NOTIFICATION_ROWS.forEach { row ->
                    ToggleRow(
                        label = row.label,
                        caption = row.caption,
                        icon = row.icon,
                        checked = row.channelId !in mutedChannels,
                        onCheckedChange = { enabled ->
                            AppPreferences.setChannelMuted(context, row.channelId, !enabled)
                            mutedChannels = AppPreferences.getMutedChannels(context)
                        }
                    )
                }
                ToggleRow(
                    label = "In-app sounds",
                    caption = "Play a sound for confirmations inside the app",
                    icon = Icons.Default.VolumeUp,
                    checked = inAppSound,
                    onCheckedChange = {
                        inAppSound = it
                        AppPreferences.setInAppSoundEnabled(context, it)
                    },
                    showDivider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                )

                // Tone and vibration are properties of the system channel on
                // Android 8+ and cannot be set from inside the app — a channel's
                // sound is fixed once created. Rather than show a picker that
                // silently does nothing, hand the user straight to the screen
                // that does work.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NavigateRow(
                        label = "Notification tone & vibration",
                        caption = "Opens Android's notification settings",
                        icon = Icons.Default.MusicNote,
                        onClick = { openChannelSettings(context) }
                    )
                }
            }

            SettingsSection("Legal & safety")
            SettingsCard {
                NavigateRow(
                    label = "Medical disclaimer & safety information",
                    caption = "What this app is for, and when to stop and get help",
                    icon = Icons.Default.HealthAndSafety,
                    onClick = { showDisclaimer = true }
                )
                NavigateRow(
                    label = "Privacy policy",
                    caption = "What we collect and who it is shared with",
                    icon = Icons.Default.Policy,
                    onClick = { openUrl(context, LegalLinks.PRIVACY_POLICY) }
                )
                NavigateRow(
                    label = "Terms of service",
                    caption = "The terms you agreed to when your account was created",
                    icon = Icons.Default.Description,
                    onClick = { openUrl(context, LegalLinks.TERMS) }
                )
            }

            SettingsSection("Account")
            SettingsCard {
                NavigateRow(
                    label = "Delete my account",
                    caption = "Request permanent removal of your account and app data",
                    icon = Icons.Default.DeleteForever,
                    onClick = { showDeleteConfirm = true }
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
        }
    }

    // Re-reading the disclaimer is a read-only view: acceptance was recorded
    // once and re-confirming it here would overwrite the original timestamp
    // with a meaningless one.
    if (showDisclaimer) {
        DisclaimerScreen(onClose = { showDisclaimer = false })
    }

    if (showDeleteConfirm) {
        DeleteAccountDialog(
            state = deleteState,
            onConfirm = { reason ->
                deleteState = DeleteState.Submitting
                scope.launch {
                    deleteState = try {
                        UserRepository.requestOwnAccountDeletion(reason)
                        DeleteState.Submitted
                    } catch (e: Exception) {
                        DeleteState.Failed(e.message ?: "Could not submit the request.")
                    }
                }
            },
            onDismiss = {
                // A submitted request has already blocked the session server
                // side, so there is nothing left to return to.
                if (deleteState is DeleteState.Submitted) {
                    FirebaseService.logout()
                    context.findActivity()?.recreate()
                } else {
                    showDeleteConfirm = false
                    deleteState = DeleteState.Idle
                }
            }
        )
    }
}

private sealed interface DeleteState {
    object Idle : DeleteState
    object Submitting : DeleteState
    object Submitted : DeleteState
    data class Failed(val message: String) : DeleteState
}

/**
 * Confirmation for account deletion.
 *
 * Typing the word is not theatre here: the row sits two taps from the theme
 * picker on a screen patients open to mute notifications, and an accidental tap
 * would end their access to a rehabilitation plan mid-programme.
 */
@Composable
private fun DeleteAccountDialog(
    state: DeleteState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val confirmed = typed.trim().equals("DELETE", ignoreCase = true)

    if (state is DeleteState.Submitted) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Request received", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your account has been deactivated and your clinic has been " +
                        "notified. Your profile and app data will be deleted, and " +
                        "we will confirm by email within 30 days.\n\n" +
                        "Some clinical records may be kept where your provider is " +
                        "required by law to retain them."
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Sign out", fontWeight = FontWeight.SemiBold)
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (state !is DeleteState.Submitting) onDismiss() },
        title = { Text("Delete your account?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "This ends your access to RehabCardia. Your profile, exercise " +
                        "assignments, session history, feedback and messages will be " +
                        "deleted. Records your clinic is legally required to keep may " +
                        "be retained — see the privacy policy."
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Reason (optional)") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type DELETE to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state is DeleteState.Failed) {
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignTokens.Colors.Error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = confirmed && state !is DeleteState.Submitting
            ) {
                Text(
                    if (state is DeleteState.Submitting) "Submitting…" else "Delete my account",
                    color = DesignTokens.Colors.Error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = state !is DeleteState.Submitting
            ) { Text("Cancel") }
        },
        shape = RoundedCornerShape(DesignTokens.Radius.LG)
    )
}

private data class NotificationRow(
    val channelId: String,
    val label: String,
    val caption: String,
    val icon: ImageVector
)

private val NOTIFICATION_ROWS = listOf(
    NotificationRow(
        PushChannels.GENERAL,
        "Exercises & updates",
        "New prescriptions, reminders and feedback",
        Icons.Default.Notifications
    ),
    NotificationRow(
        PushChannels.CHAT,
        "Messages",
        "Direct messages with your clinician",
        Icons.Default.ChatBubble
    ),
    NotificationRow(
        PushChannels.APPOINTMENTS,
        "Appointments",
        "Bookings, changes and confirmations",
        Icons.Default.CalendarMonth
    )
)

private fun applyLanguage(context: Context, tag: String) {
    if (LocaleManager.getLanguage(context) == tag) return
    LocaleManager.setLanguage(context, tag)
    context.findActivity()?.recreate()
}

/** Deep-links to the app's notification settings, where tone lives. */
private fun openChannelSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

// ── Building blocks ─────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String) {
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp
    )
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider(show: Boolean) {
    if (!show) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ThemeOptionRow(
    label: String,
    caption: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(DesignTokens.Spacing.MD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) DesignTokens.Colors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.SM))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                    .background(DesignTokens.Colors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    RowDivider(showDivider)
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true
) = ThemeOptionRow(
    label = label,
    caption = if (selected) "Currently selected" else "Tap to switch",
    icon = Icons.Default.Language,
    selected = selected,
    onClick = onClick,
    showDivider = showDivider
)

@Composable
private fun ToggleRow(
    label: String,
    caption: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(DesignTokens.Spacing.MD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.SM))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Colors.Primary)
        )
    }
    RowDivider(showDivider)
}

@Composable
private fun NavigateRow(
    label: String,
    caption: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(DesignTokens.Spacing.MD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.SM))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
