// DesignTokens.kt
// Auto-generated from Google Stitch Project 14107272513072708956
// DO NOT hardcode any design values anywhere else in the codebase.
// All values flow through MaterialTheme — no hardcoded values in composables.
//
// Source screens: 29 screens fetched via Stitch MCP on 2026-03-01
// Theme: Light mode primary, Inter/Lexend fonts, ROUND_EIGHT, saturation 2

package com.srcardiocare.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Single source of truth for all design values extracted from Stitch.
 * Every composable must reference these tokens — zero hardcoded hex, font sizes, or spacing.
 */
object DesignTokens {

    // ── Colors ──────────────────────────────────────────────────────────────

    object Colors {
        // Primary Brand - Greyish Teal palette (desaturated)
        val Primary = Color(0xFF5A9EA6)       // Desaturated teal (greyish green)
        val PrimaryDark = Color(0xFF4A8A91)   // Darker greyish teal
        val PrimaryLight = Color(0xFFC5DFE2)  // Light greyish teal

        // Backgrounds - with grey undertones
        val BackgroundLight = Color(0xFFE8EDEF)  // Light grey-teal — distinct from white cards
        val BackgroundDark = Color(0xFF1A2426)   // Dark grey-teal

        // Surfaces
        val SurfaceLight = Color(0xFFFFFFFF)     // White cards — pop against grey background
        val SurfaceDark = Color(0xFF252F31)      // Dark surface with teal undertone

        // Text
        val TextMain = Color(0xFF2D3738)         // Softer dark for text
        val TextSub = Color(0xFF6B7D80)          // Muted grey-teal
        val TextSecondary = Color(0xFF7A9295)    // Secondary text

        // Neutrals - greyer
        val NeutralLight = Color(0xFFE3E7E8)
        val NeutralGrey = Color(0xFFD8DFE0)
        val NeutralDark = Color(0xFF8A9A9C)

        // Special - chart colors updated
        val BubbleGrey = Color(0xFFECF0F1)
        val ChartTeal = Color(0xFF5A9EA6)        // Match primary
        val ChartLightTeal = Color(0xFFD4E8EA)   // Lighter variant
        val ChartSecondaryTeal = Color(0xFF8BBDC3)
        val ChartGrey = Color(0xFFC4CDD0)

        // Semantic - slightly desaturated
        val Error = Color(0xFFD95959)
        val Success = Color(0xFF4DA889)
        val Warning = Color(0xFFD9A14A)

        // Slate scale (from Tailwind — used in Stitch screens)
        val Slate100 = Color(0xFFF1F5F9)
        val Slate200 = Color(0xFFE2E8F0)
        val Slate300 = Color(0xFFCBD5E1)
        val Slate400 = Color(0xFF94A3B8)
        val Slate500 = Color(0xFF64748B)
        val Slate600 = Color(0xFF475569)
        val Slate700 = Color(0xFF334155)
        val Slate800 = Color(0xFF1E293B)
        val Slate900 = Color(0xFF0F172A)

        // Primary with alpha variants
        val PrimaryAlpha10 = Color(0x1A5A9EA6)
        val PrimaryAlpha20 = Color(0x335A9EA6)
        val PrimaryAlpha30 = Color(0x4D5A9EA6)
    }

    // ── Typography ──────────────────────────────────────────────────────────

    object Typography {
        // Font Families
        val InterFamily = FontFamily.Default // Will be replaced with actual Inter font resource
        val LexendFamily = FontFamily.Default // Will be replaced with actual Lexend font resource

        // Font Sizes
        val Caption2 = 10.sp
        val Caption = 12.sp
        val Footnote = 13.sp
        val Subheadline = 14.sp
        val Body = 16.sp
        val Callout = 15.sp
        val Headline = 17.sp
        val Title3 = 20.sp
        val Title2 = 22.sp
        val Title1 = 24.sp
        val LargeTitle = 28.sp
        val Hero = 32.sp
        val Display = 36.sp

        // Font Weights
        val Light = FontWeight.Light         // 300
        val Regular = FontWeight.Normal      // 400
        val Medium = FontWeight.Medium       // 500
        val SemiBold = FontWeight.SemiBold   // 600
        val Bold = FontWeight.Bold           // 700
    }

    // ── Spacing ─────────────────────────────────────────────────────────────

    object Spacing {
        val XXXS = 2.dp
        val XXS = 4.dp
        val XS = 6.dp
        val SM = 8.dp
        val MD = 12.dp
        val Base = 16.dp
        val LG = 20.dp
        val XL = 24.dp
        val XXL = 32.dp
        val XXXL = 48.dp
        val XXXXL = 64.dp

        // Screen edge padding
        val ScreenHorizontal = 16.dp
        val ScreenVertical = 16.dp
        val CardPadding = 16.dp
        val SectionSpacing = 24.dp
        val ListItemSpacing = 12.dp
    }

    // ── Radius ──────────────────────────────────────────────────────────────

    object Radius {
        val None = 0.dp
        val SM = 4.dp       // 0.25rem
        val Base = 8.dp     // 0.5rem — DEFAULT (ROUND_EIGHT theme)
        val LG = 12.dp      // 0.75rem
        val XL = 16.dp      // 1rem
        val XXL = 24.dp     // 1.5rem
        val XXXL = 32.dp    // 2rem
        val Full = 9999.dp  // Pill / circle

        // Semantic aliases
        val Button = XL
        val Card = XL
        val Input = Base
        val Chip = Full
        val Avatar = Full
        val BottomSheet = XXL
    }

    // ── Elevation / Shadow ──────────────────────────────────────────────────

    object Elevation {
        val None = 0.dp
        val SM = 1.dp
        val MD = 2.dp
        val LG = 4.dp
        val XL = 8.dp
        val XXL = 16.dp
    }

    // ── Animation ───────────────────────────────────────────────────────────

    object Animation {
        const val Fast = 150
        const val Normal = 300
        const val Slow = 500
    }
}
