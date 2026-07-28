package com.srcardiocare.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.R
import com.srcardiocare.core.locale.LocaleManager
import com.srcardiocare.ui.theme.DesignTokens

/**
 * Persists [tag] and restarts the activity so resources are re-resolved.
 *
 * The recreate() is required: LocaleManager applies the language in
 * attachBaseContext, which only runs when the activity is built. findActivity()
 * is reused from FullscreenVideo.kt — under Compose the LocalContext is a
 * wrapper rather than the Activity, doubly so here since LocaleManager.wrap()
 * adds one of its own.
 */
private fun applyLanguage(context: Context, tag: String) {
    if (LocaleManager.getLanguage(context) == tag) return
    LocaleManager.setLanguage(context, tag)
    context.findActivity()?.recreate()
}

/**
 * Compact `English | தமிழ்` toggle for the login screen.
 *
 * The login screen needs its own switch: a Tamil-only patient who cannot read the
 * English login form would otherwise never reach the setting inside their profile.
 */
@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = LocaleManager.getLanguage(context)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.Radius.Base))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = stringResource(R.string.language),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp, end = 2.dp)
                .size(16.dp)
        )
        LanguageChip(
            label = stringResource(R.string.language_english),
            selected = current == LocaleManager.ENGLISH,
            onClick = { applyLanguage(context, LocaleManager.ENGLISH) }
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .width(1.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        LanguageChip(
            label = stringResource(R.string.language_tamil),
            selected = current == LocaleManager.TAMIL,
            onClick = { applyLanguage(context, LocaleManager.TAMIL) }
        )
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) DesignTokens.Colors.Primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * Full-width `Language / மொழி` row for the patient profile screen, styled to sit
 * alongside the existing Change Password and Sign Out rows.
 */
@Composable
fun LanguageSettingRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = LocaleManager.getLanguage(context)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.Spacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = DesignTokens.Colors.Primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = DesignTokens.Spacing.MD)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            LanguageChip(
                label = stringResource(R.string.language_english),
                selected = current == LocaleManager.ENGLISH,
                onClick = { applyLanguage(context, LocaleManager.ENGLISH) }
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(1.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            LanguageChip(
                label = stringResource(R.string.language_tamil),
                selected = current == LocaleManager.TAMIL,
                onClick = { applyLanguage(context, LocaleManager.TAMIL) }
            )
        }
    }
}
