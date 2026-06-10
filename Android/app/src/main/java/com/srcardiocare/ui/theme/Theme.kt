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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

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

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Bold,
        fontSize = DesignTokens.Typography.Display,
        lineHeight = DesignTokens.Typography.Display * 1.2
    ),
    displayMedium = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Bold,
        fontSize = DesignTokens.Typography.Hero,
        lineHeight = DesignTokens.Typography.Hero * 1.2
    ),
    displaySmall = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Bold,
        fontSize = DesignTokens.Typography.LargeTitle,
        lineHeight = DesignTokens.Typography.LargeTitle * 1.2
    ),
    headlineLarge = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.SemiBold,
        fontSize = DesignTokens.Typography.Title1,
        lineHeight = DesignTokens.Typography.Title1 * 1.3
    ),
    headlineMedium = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.SemiBold,
        fontSize = DesignTokens.Typography.Title2,
        lineHeight = DesignTokens.Typography.Title2 * 1.3
    ),
    headlineSmall = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.SemiBold,
        fontSize = DesignTokens.Typography.Title3,
        lineHeight = DesignTokens.Typography.Title3 * 1.3
    ),
    titleLarge = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.SemiBold,
        fontSize = DesignTokens.Typography.Title3,
        lineHeight = DesignTokens.Typography.Title3 * 1.3
    ),
    titleMedium = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Medium,
        fontSize = DesignTokens.Typography.Headline,
        lineHeight = DesignTokens.Typography.Headline * 1.4
    ),
    titleSmall = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Medium,
        fontSize = DesignTokens.Typography.Subheadline,
        lineHeight = DesignTokens.Typography.Subheadline * 1.4
    ),
    bodyLarge = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Regular,
        fontSize = DesignTokens.Typography.Body,
        lineHeight = DesignTokens.Typography.Body * 1.5
    ),
    bodyMedium = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Regular,
        fontSize = DesignTokens.Typography.Subheadline,
        lineHeight = DesignTokens.Typography.Subheadline * 1.5
    ),
    bodySmall = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Regular,
        fontSize = DesignTokens.Typography.Caption,
        lineHeight = DesignTokens.Typography.Caption * 1.5
    ),
    labelLarge = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.SemiBold,
        fontSize = DesignTokens.Typography.Subheadline,
        lineHeight = DesignTokens.Typography.Subheadline * 1.4
    ),
    labelMedium = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Medium,
        fontSize = DesignTokens.Typography.Caption,
        lineHeight = DesignTokens.Typography.Caption * 1.4
    ),
    labelSmall = TextStyle(
        fontFamily = DesignTokens.Typography.InterFamily,
        fontWeight = DesignTokens.Typography.Medium,
        fontSize = DesignTokens.Typography.Caption2,
        lineHeight = DesignTokens.Typography.Caption2 * 1.4
    ),
)

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
