// Theme.kt
// Auto-generated from Google Stitch Project 14107272513072708956
// Compose Material3 theme wired to DesignTokens.
// All composables use MaterialTheme — no hardcoded values.

package com.srcardiocare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.core.view.WindowCompat
import com.srcardiocare.core.locale.LocaleManager

// ── Color Schemes ───────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = DesignTokens.Colors.Primary,
    onPrimary = DesignTokens.Colors.SurfaceLight,
    primaryContainer = DesignTokens.Colors.PrimaryLight,
    onPrimaryContainer = DesignTokens.Colors.TextMain,
    secondary = DesignTokens.Colors.PrimaryDark,
    onSecondary = DesignTokens.Colors.SurfaceLight,
    secondaryContainer = DesignTokens.Colors.PrimaryAlpha10,
    onSecondaryContainer = DesignTokens.Colors.TextMain,
    tertiary = DesignTokens.Colors.ChartSecondaryTeal,
    background = DesignTokens.Colors.BackgroundLight,
    onBackground = DesignTokens.Colors.TextMain,
    surface = DesignTokens.Colors.SurfaceLight,
    onSurface = DesignTokens.Colors.TextMain,
    surfaceVariant = DesignTokens.Colors.NeutralLight,
    onSurfaceVariant = DesignTokens.Colors.TextSub,
    outline = DesignTokens.Colors.NeutralGrey,
    outlineVariant = DesignTokens.Colors.NeutralLight,
    error = DesignTokens.Colors.Error,
    onError = DesignTokens.Colors.SurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = DesignTokens.Colors.Primary,
    onPrimary = DesignTokens.Colors.BackgroundDark,
    primaryContainer = DesignTokens.Colors.PrimaryDark,
    onPrimaryContainer = DesignTokens.Colors.PrimaryLight,
    secondary = DesignTokens.Colors.PrimaryLight,
    onSecondary = DesignTokens.Colors.BackgroundDark,
    secondaryContainer = DesignTokens.Colors.PrimaryAlpha20,
    onSecondaryContainer = DesignTokens.Colors.PrimaryLight,
    tertiary = DesignTokens.Colors.ChartSecondaryTeal,
    background = DesignTokens.Colors.BackgroundDark,
    onBackground = DesignTokens.Colors.Slate100,
    surface = DesignTokens.Colors.SurfaceDark,
    onSurface = DesignTokens.Colors.Slate100,
    surfaceVariant = DesignTokens.Colors.Slate700,
    onSurfaceVariant = DesignTokens.Colors.Slate400,
    outline = DesignTokens.Colors.Slate600,
    outlineVariant = DesignTokens.Colors.Slate700,
    error = DesignTokens.Colors.Error,
    onError = DesignTokens.Colors.BackgroundDark,
)

// ── Typography ──────────────────────────────────────────────────────────────

/**
 * Line-height floor for scripts whose glyphs overshoot Latin metrics.
 *
 * The multipliers below were tuned against Inter/Latin. Inter carries no Tamil
 * glyphs, so Tamil renders through the system fallback (Noto Sans Tamil), whose
 * above-base vowel signs sit well above the Latin ascent — in a 1.2x line box
 * they get sliced off the top. 1.45x clears the tallest combining marks; tiers
 * already roomier than that keep their tuned value.
 */
private const val TALL_SCRIPT_MIN_LINE_HEIGHT = 1.45f

/**
 * Untrimmed, centred line boxes. Compose trims the leading above the first line
 * and below the last by default, which removes exactly the room a tall script
 * needs. Applied only to the tall-script typography so the Latin vertical
 * rhythm is left untouched.
 */
private val TallScriptLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun appTypography(tallScript: Boolean): Typography {
    val type = DesignTokens.Typography

    fun style(weight: FontWeight, size: TextUnit, lineHeightMultiplier: Float) = TextStyle(
        fontFamily = type.InterFamily,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size * if (tallScript) {
            maxOf(lineHeightMultiplier, TALL_SCRIPT_MIN_LINE_HEIGHT)
        } else {
            lineHeightMultiplier
        },
        lineHeightStyle = if (tallScript) TallScriptLineHeightStyle else null
    )

    return Typography(
        displayLarge = style(type.Bold, type.Display, 1.2f),
        displayMedium = style(type.Bold, type.Hero, 1.2f),
        displaySmall = style(type.Bold, type.LargeTitle, 1.2f),
        headlineLarge = style(type.SemiBold, type.Title1, 1.3f),
        headlineMedium = style(type.SemiBold, type.Title2, 1.3f),
        headlineSmall = style(type.SemiBold, type.Title3, 1.3f),
        titleLarge = style(type.SemiBold, type.Title3, 1.3f),
        titleMedium = style(type.Medium, type.Headline, 1.4f),
        titleSmall = style(type.Medium, type.Subheadline, 1.4f),
        bodyLarge = style(type.Regular, type.Body, 1.5f),
        bodyMedium = style(type.Regular, type.Subheadline, 1.5f),
        bodySmall = style(type.Regular, type.Caption, 1.5f),
        labelLarge = style(type.SemiBold, type.Subheadline, 1.4f),
        labelMedium = style(type.Medium, type.Caption, 1.4f),
        labelSmall = style(type.Medium, type.Caption2, 1.4f),
    )
}

private val LatinTypography = appTypography(tallScript = false)
private val TallScriptTypography = appTypography(tallScript = true)

// ── Shapes ──────────────────────────────────────────────────────────────────

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(DesignTokens.Radius.SM),
    small = androidx.compose.foundation.shape.RoundedCornerShape(DesignTokens.Radius.Base),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(DesignTokens.Radius.LG),
    large = androidx.compose.foundation.shape.RoundedCornerShape(DesignTokens.Radius.XL),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(DesignTokens.Radius.XXL),
)

// ── Theme Composable ────────────────────────────────────────────────────────

@Composable
fun SRCardiocareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled: we use Stitch-defined colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Read from the resolved configuration rather than the stored preference, so
    // this follows LocaleManager's context wrapping without a SharedPreferences
    // hit on every recomposition.
    val isTallScript =
        LocalConfiguration.current.locales[0].language == LocaleManager.TAMIL

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (isTallScript) TallScriptTypography else LatinTypography,
        shapes = AppShapes,
        content = content
    )
}
