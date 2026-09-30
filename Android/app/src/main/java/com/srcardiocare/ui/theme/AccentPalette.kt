// AccentPalette.kt — the user-selectable accent colours and the live one.
package com.srcardiocare.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.srcardiocare.R
import com.srcardiocare.core.prefs.AppPreferences.AccentColor

/**
 * One accent's worth of colour. Every palette keeps the original teal's
 * character — desaturated, mid-tone — so white text on [primary] stays legible
 * and no choice looks louder than the rest of the design.
 */
data class AccentPalette(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val chartSecondary: Color,
    val chartLight: Color,
    @param:StringRes val label: Int
)

private val Teal = AccentPalette(
    primary = Color(0xFF5A9EA6),
    primaryDark = Color(0xFF4A8A91),
    primaryLight = Color(0xFFC5DFE2),
    chartSecondary = Color(0xFF8BBDC3),
    chartLight = Color(0xFFD4E8EA),
    label = R.string.settings_accent_teal
)

private val Blue = AccentPalette(
    primary = Color(0xFF4A7FC1),
    primaryDark = Color(0xFF3B6BA8),
    primaryLight = Color(0xFFC9DAF0),
    chartSecondary = Color(0xFF8FB2DE),
    chartLight = Color(0xFFDCE7F5),
    label = R.string.settings_accent_blue
)

private val Green = AccentPalette(
    primary = Color(0xFF4E9A6B),
    primaryDark = Color(0xFF3F8259),
    primaryLight = Color(0xFFC8E3D2),
    chartSecondary = Color(0xFF8CC3A1),
    chartLight = Color(0xFFDAEEE1),
    label = R.string.settings_accent_green
)

private val Purple = AccentPalette(
    primary = Color(0xFF8267B8),
    primaryDark = Color(0xFF6D549F),
    primaryLight = Color(0xFFDCD3EE),
    chartSecondary = Color(0xFFB09FD6),
    chartLight = Color(0xFFE8E2F4),
    label = R.string.settings_accent_purple
)

private val Pink = AccentPalette(
    primary = Color(0xFFC2607F),
    primaryDark = Color(0xFFA84E6B),
    primaryLight = Color(0xFFF1D0DB),
    chartSecondary = Color(0xFFDE9CB1),
    chartLight = Color(0xFFF6E0E7),
    label = R.string.settings_accent_pink
)

private val Orange = AccentPalette(
    primary = Color(0xFFC9753C),
    primaryDark = Color(0xFFAD6230),
    primaryLight = Color(0xFFF4D9C5),
    chartSecondary = Color(0xFFE6AE85),
    chartLight = Color(0xFFF8E6D9),
    label = R.string.settings_accent_orange
)

private val Grey = AccentPalette(
    primary = Color(0xFF64748B),
    primaryDark = Color(0xFF4F5D71),
    primaryLight = Color(0xFFD5DBE3),
    chartSecondary = Color(0xFF9AA6B8),
    chartLight = Color(0xFFE4E8EE),
    label = R.string.settings_accent_grey
)

fun AccentColor.palette(): AccentPalette = when (this) {
    AccentColor.TEAL -> Teal
    AccentColor.BLUE -> Blue
    AccentColor.GREEN -> Green
    AccentColor.PURPLE -> Purple
    AccentColor.PINK -> Pink
    AccentColor.ORANGE -> Orange
    AccentColor.GREY -> Grey
}

/**
 * The accent currently on screen. Snapshot state, so any composable that reads
 * a [DesignTokens.Colors] accent token is recomposed when it changes. Written
 * only by [SRCardiocareTheme].
 */
internal object ActiveAccent {
    var palette by mutableStateOf(Teal)
}
