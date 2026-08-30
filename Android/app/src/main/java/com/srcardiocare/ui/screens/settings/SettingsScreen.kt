// SettingsScreen.kt — Appearance, language and notification preferences.
package com.srcardiocare.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srcardiocare.R
import com.srcardiocare.core.auth.signOutAndRestart
import com.srcardiocare.core.locale.LocaleManager
import com.srcardiocare.core.prefs.AppPreferences
import com.srcardiocare.core.push.PushChannels
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

    // Resolved here, not at the catch site: stringResource is @Composable and
    // the failure is handled inside a coroutine.
    val deleteErrorFallback = stringResource(R.string.settings_delete_error_generic)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
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
            SettingsSection(R.string.settings_section_appearance)
            SettingsCard {
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_system),
                    caption = stringResource(R.string.settings_theme_system_caption),
                    icon = Icons.Default.PhoneAndroid,
                    selected = themeMode == AppPreferences.ThemeMode.SYSTEM,
                    onClick = {
                        themeMode = AppPreferences.ThemeMode.SYSTEM
                        AppPreferences.setThemeMode(context, themeMode)
                    }
                )
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_light),
                    caption = stringResource(R.string.settings_theme_light_caption),
                    icon = Icons.Default.LightMode,
                    selected = themeMode == AppPreferences.ThemeMode.LIGHT,
                    onClick = {
                        themeMode = AppPreferences.ThemeMode.LIGHT
                        AppPreferences.setThemeMode(context, themeMode)
                    }
                )
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_dark),
                    caption = stringResource(R.string.settings_theme_dark_caption),
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
                SettingsSection(R.string.language)
                SettingsCard {
                    LanguageOptionRow(
                        label = stringResource(R.string.language_english),
                        selected = currentLanguage == LocaleManager.ENGLISH,
                        onClick = { applyLanguage(context, LocaleManager.ENGLISH) }
                    )
                    LanguageOptionRow(
                        label = stringResource(R.string.language_tamil),
                        selected = currentLanguage == LocaleManager.TAMIL,
                        onClick = { applyLanguage(context, LocaleManager.TAMIL) },
                        showDivider = false
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                Text(
                    stringResource(R.string.settings_language_restart_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection(R.string.settings_section_notifications)
            SettingsCard {
                NOTIFICATION_ROWS.forEach { row ->
                    ToggleRow(
                        label = stringResource(row.label),
                        caption = stringResource(row.caption),
                        icon = row.icon,
                        checked = row.channelId !in mutedChannels,
                        onCheckedChange = { enabled ->
                            AppPreferences.setChannelMuted(context, row.channelId, !enabled)
                            mutedChannels = AppPreferences.getMutedChannels(context)
                        }
                    )
                }
                ToggleRow(
                    label = stringResource(R.string.settings_notif_sound),
                    caption = stringResource(R.string.settings_notif_sound_caption),
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
                        label = stringResource(R.string.settings_notif_tone),
                        caption = stringResource(R.string.settings_notif_tone_caption),
                        icon = Icons.Default.MusicNote,
                        onClick = { openChannelSettings(context) }
                    )
                }
            }

            SettingsSection(R.string.settings_section_legal)
            SettingsCard {
                NavigateRow(
                    label = stringResource(R.string.settings_legal_disclaimer),
                    caption = stringResource(R.string.settings_legal_disclaimer_caption),
                    icon = Icons.Default.HealthAndSafety,
                    onClick = { showDisclaimer = true }
                )
                NavigateRow(
                    label = stringResource(R.string.settings_legal_privacy),
                    caption = stringResource(R.string.settings_legal_privacy_caption),
                    icon = Icons.Default.Policy,
                    onClick = { openUrl(context, LegalLinks.PRIVACY_POLICY) }
                )
                NavigateRow(
                    label = stringResource(R.string.settings_legal_terms),
                    caption = stringResource(R.string.settings_legal_terms_caption),
                    icon = Icons.Default.Description,
                    onClick = { openUrl(context, LegalLinks.TERMS) }
                )
            }

            SettingsSection(R.string.settings_section_account)
            SettingsCard {
                NavigateRow(
                    label = stringResource(R.string.settings_delete_row),
                    caption = stringResource(R.string.settings_delete_row_caption),
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
                        DeleteState.Failed(e.message ?: deleteErrorFallback)
                    }
                }
            },
            onDismiss = {
                // A submitted request has already blocked the session server
                // side, so there is nothing left to return to.
                if (deleteState is DeleteState.Submitted) {
                    signOutAndRestart(context)
                } else {
                    showDeleteConfirm = false
                    deleteState = DeleteState.Idle
                }
            }
        )
    }
}

/**
 * Typed verbatim by the user and compared literally, so it is not translated —
 * a Tamil word here would be matched against a Latin-script keyboard entry and
 * the confirm button would never enable. The label around it *is* translated.
 */
private const val DELETE_CONFIRM_WORD = "DELETE"

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
    val confirmed = typed.trim().equals(DELETE_CONFIRM_WORD, ignoreCase = true)

    if (state is DeleteState.Submitted) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(R.string.settings_delete_done_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.settings_delete_done_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.action_sign_out),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (state !is DeleteState.Submitting) onDismiss() },
        title = {
            Text(
                stringResource(R.string.settings_delete_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.settings_delete_body))
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text(stringResource(R.string.settings_delete_reason_label)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = {
                        Text(
                            stringResource(
                                R.string.settings_delete_type_label,
                                DELETE_CONFIRM_WORD
                            )
                        )
                    },
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
                    stringResource(
                        if (state is DeleteState.Submitting) R.string.settings_delete_submitting
                        else R.string.settings_delete_confirm
                    ),
                    color = DesignTokens.Colors.Error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = state !is DeleteState.Submitting
            ) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(DesignTokens.Radius.LG)
    )
}

/**
 * Resource ids rather than strings: this table is a top-level `val`, built once
 * at class-init time when there is no Context and no locale. Holding rendered
 * text here would freeze whatever language was current at process start.
 */
private data class NotificationRow(
    val channelId: String,
    @StringRes val label: Int,
    @StringRes val caption: Int,
    val icon: ImageVector
)

private val NOTIFICATION_ROWS = listOf(
    NotificationRow(
        PushChannels.GENERAL,
        R.string.settings_notif_general,
        R.string.settings_notif_general_caption,
        Icons.Default.Notifications
    ),
    NotificationRow(
        PushChannels.CHAT,
        R.string.settings_notif_chat,
        R.string.settings_notif_chat_caption,
        Icons.Default.ChatBubble
    ),
    NotificationRow(
        PushChannels.APPOINTMENTS,
        R.string.settings_notif_appointments,
        R.string.settings_notif_appointments_caption,
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
private fun SettingsSection(@StringRes title: Int) {
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
    Text(
        // Tamil has no case, so uppercase() is a no-op there and the heading
        // simply renders as written — the spacing below carries the hierarchy.
        stringResource(title).uppercase(),
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
    caption = stringResource(
        if (selected) R.string.settings_language_selected
        else R.string.settings_language_switch
    ),
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
