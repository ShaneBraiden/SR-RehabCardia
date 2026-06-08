// CommonComponents.kt — Shared UI components to reduce code duplication
package com.srcardiocare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srcardiocare.ui.theme.DesignTokens

/**
 * Global "Powered by SRET-AIDA" attribution badge displayed on every screen.
 * Compact pill at the bottom of the viewport — visible but unobtrusive.
 */
@Composable
fun PoweredByBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(bottom = 4.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Text(
            text = "Powered by SRET-AIDA",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/**
 * Style variants for StatItem component.
 */
enum class StatItemStyle {
    /** White text on primary background (used in dashboard stats card) */
    LIGHT,
    /** Primary colored text on surface background (used in profile stats) */
    PRIMARY
}

/**
 * Reusable stat display component showing a value and label.
 *
 * @param value The main value to display (e.g., "12", "89%")
 * @param label The description label below the value
 * @param style Determines the color scheme (LIGHT for white text, PRIMARY for colored text)
 */
@Composable
fun StatItem(
    value: String,
    label: String,
    style: StatItemStyle = StatItemStyle.PRIMARY
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (style) {
            StatItemStyle.LIGHT -> {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            StatItemStyle.PRIMARY -> {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesignTokens.Colors.Primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Reusable profile information row displaying a label and value.
 *
 * @param label The field label (e.g., "Email", "First Name")
 * @param value The field value
 * @param showDivider Whether to show a divider below the row
 */
@Composable
fun ProfileInfoRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.padding(vertical = DesignTokens.Spacing.XS)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
    if (showDivider) {
        HorizontalDivider(color = DesignTokens.Colors.NeutralLight, thickness = 0.5.dp)
    }
}

/**
 * Reusable logout confirmation dialog.
 *
 * @param show Whether to show the dialog
 * @param onDismiss Called when the dialog is dismissed
 * @param onConfirm Called when logout is confirmed
 */
@Composable
fun LogoutConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Sign Out", color = DesignTokens.Colors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Circular avatar displaying initials.
 *
 * @param initials The initials to display (e.g., "JD" for John Doe)
 * @param size The diameter of the avatar
 * @param backgroundColor The background color of the avatar circle
 * @param textColor The color of the initials text
 * @param textStyle Optional text style; defaults based on size
 */
@Composable
fun InitialsAvatar(
    initials: String,
    size: Dp = 80.dp,
    backgroundColor: Color = DesignTokens.Colors.PrimaryLight,
    textColor: Color = DesignTokens.Colors.PrimaryDark
) {
    val textStyle = when {
        size >= 96.dp -> MaterialTheme.typography.headlineLarge
        size >= 80.dp -> MaterialTheme.typography.headlineMedium
        size >= 48.dp -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.bodyLarge
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.ifBlank { "?" },
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Returns standard OutlinedTextField colors with primary focus color.
 * Use this to ensure consistent text field styling across all forms.
 */
@Composable
fun primaryTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DesignTokens.Colors.Primary,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface
)
