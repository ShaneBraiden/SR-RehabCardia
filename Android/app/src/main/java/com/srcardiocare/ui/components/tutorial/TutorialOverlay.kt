// TutorialOverlay.kt — Non-blocking beacon + tip card drawn over a screen.
//
// Drawn inside the TutorialHost's root Box. It deliberately has NO full-size
// click/scrim modifier: only the beacon ring and the tip-card buttons consume
// touches, so the underlying screen stays fully usable while a tour runs (a true
// nudge, not a modal). The screen is never dimmed.
package com.srcardiocare.ui.components.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srcardiocare.ui.theme.DesignTokens

private val BeaconSize = 28.dp
private val TipCardMaxWidth = 320.dp
private val EdgePadding = DesignTokens.Spacing.Base
private val TipGap = DesignTokens.Spacing.SM

/**
 * Renders the beacon + tip card for [controller]'s current step. Caller must
 * place this as the LAST child of the host Box (so it sits on top) and only
 * when [TutorialController.isActive] is true.
 *
 * @param onFinished invoked when the tour ends (Done / Skip / back press), so
 *                   the host can persist that the tour was seen.
 */
@Composable
fun TutorialOverlay(
    controller: TutorialController,
    onFinished: () -> Unit
) {
    val rect = controller.currentTargetRect
    val step = controller.currentStep

    // Back press while a tour is active closes it (and marks it seen) rather
    // than leaving the screen.
    BackHandler(enabled = controller.isActive) {
        controller.stop()
        onFinished()
    }

    if (rect == null || step == null) {
        // Target for this step isn't positioned (e.g. scrolled off / not yet
        // laid out): skip to keep the tour moving instead of showing a beacon
        // in the wrong place.
        return
    }

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val screenHeightDp = config.screenHeightDp.dp

    // Convert the target's root-space px bounds into dp for layout offsets.
    val targetLeftDp: Dp
    val targetTopDp: Dp
    val targetWidthDp: Dp
    val targetHeightDp: Dp
    with(density) {
        targetLeftDp = rect.left.toDp()
        targetTopDp = rect.top.toDp()
        targetWidthDp = rect.width.toDp()
        targetHeightDp = rect.height.toDp()
    }
    val targetCenterX = targetLeftDp + targetWidthDp / 2
    val targetCenterY = targetTopDp + targetHeightDp / 2

    // ── Beacon position: centered on the target, animated between steps ──
    val beaconX by animateDpAsState(
        targetValue = (targetCenterX - BeaconSize / 2)
            .coerceIn(0.dp, (screenWidthDp - BeaconSize).coerceAtLeast(0.dp)),
        animationSpec = tween(DesignTokens.Animation.Normal),
        label = "beaconX"
    )
    val beaconY by animateDpAsState(
        targetValue = (targetCenterY - BeaconSize / 2)
            .coerceIn(0.dp, (screenHeightDp - BeaconSize).coerceAtLeast(0.dp)),
        animationSpec = tween(DesignTokens.Animation.Normal),
        label = "beaconY"
    )

    // ── Tip card placement: below the target if there's room, else above ──
    val estimatedCardHeight = 132.dp
    val spaceBelow = screenHeightDp - (targetTopDp + targetHeightDp)
    val placeBelow = spaceBelow > (estimatedCardHeight + TipGap + EdgePadding)

    val cardY by animateDpAsState(
        targetValue = if (placeBelow) {
            (targetTopDp + targetHeightDp + TipGap)
                .coerceAtMost((screenHeightDp - estimatedCardHeight - EdgePadding).coerceAtLeast(EdgePadding))
        } else {
            (targetTopDp - estimatedCardHeight - TipGap).coerceAtLeast(EdgePadding)
        },
        animationSpec = tween(DesignTokens.Animation.Normal),
        label = "cardY"
    )

    // Horizontally clamp the card so it stays fully on screen, biased toward
    // the target's horizontal center.
    val cardWidth = if (screenWidthDp - EdgePadding * 2 < TipCardMaxWidth) {
        screenWidthDp - EdgePadding * 2
    } else {
        TipCardMaxWidth
    }
    val cardX by animateDpAsState(
        targetValue = (targetCenterX - cardWidth / 2)
            .coerceIn(EdgePadding, (screenWidthDp - cardWidth - EdgePadding).coerceAtLeast(EdgePadding)),
        animationSpec = tween(DesignTokens.Animation.Normal),
        label = "cardX"
    )

    // NOTE: this Box has no background/clickable — it does not intercept touches.
    Box(modifier = Modifier.fillMaxWidth()) {
        Beacon(
            modifier = Modifier
                .offset(x = beaconX, y = beaconY)
                .size(BeaconSize),
            onClick = { controller.next() }
        )

        TipCard(
            modifier = Modifier
                .offset(x = cardX, y = cardY)
                .widthIn(max = cardWidth),
            title = step.title,
            text = step.text,
            index = controller.currentIndex,
            total = controller.stepCount,
            onBack = { controller.back() },
            onSkip = {
                controller.stop()
                onFinished()
            },
            onNext = {
                val wasLast = controller.currentIndex >= controller.stepCount - 1
                controller.next()
                if (wasLast) onFinished()
            }
        )
    }
}

/** Pulsing brand-colored ring; reuses the OnboardingScreen breathing pattern. */
@Composable
private fun Beacon(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "beacon")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconScale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Expanding halo
        Box(
            modifier = Modifier
                .size(BeaconSize)
                .scale(scale)
                .clip(CircleShape)
                .background(DesignTokens.Colors.Primary.copy(alpha = ringAlpha))
        )
        // Solid core
        Box(
            modifier = Modifier
                .size(BeaconSize * 0.42f)
                .clip(CircleShape)
                .background(DesignTokens.Colors.Primary)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

@Composable
private fun TipCard(
    modifier: Modifier = Modifier,
    title: String,
    text: String,
    index: Int,
    total: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val isFirst = index == 0
    val isLast = index >= total - 1

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = DesignTokens.Elevation.XL,
        tonalElevation = DesignTokens.Elevation.SM
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.Base)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXS))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${index + 1} / $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.XXS)
                ) {
                    if (isFirst) {
                        TextButton(onClick = onSkip) { Text("Skip") }
                    } else {
                        TextButton(onClick = onBack) { Text("Back") }
                    }
                    Button(
                        onClick = onNext,
                        shape = RoundedCornerShape(DesignTokens.Radius.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesignTokens.Colors.Primary
                        )
                    ) {
                        Text(
                            text = if (isLast) "Done" else "Next",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
