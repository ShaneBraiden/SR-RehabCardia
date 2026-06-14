// OnboardingScreen.kt — First-login welcome flow for patients.
//
// A four-page swipeable introduction shown once after a patient's first login
// (driven by the `hasCompletedOnboarding` flag on the user document). Each page
// pairs a custom-drawn illustration with a short message about one core area
// of the app: rehab guidance, assigned exercises, progress tracking, and the
// care team connection.
package com.srcardiocare.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val body: String,
    val accent: Color,
    val illustration: @Composable () -> Unit
)

/**
 * @param onFinished invoked after the `hasCompletedOnboarding` flag is saved
 *                   (or after a save failure — the flow must never trap the user).
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Welcome to RehabCardia",
                body = "Your personal companion for cardiac rehabilitation — built around a recovery plan created just for you by your care team.",
                accent = DesignTokens.Colors.Primary,
                illustration = { HeartbeatIllustration() }
            ),
            OnboardingPage(
                title = "Exercises Made for You",
                body = "Your doctor assigns video-guided exercises matched to your recovery stage. Follow along at your own pace, one session at a time.",
                accent = DesignTokens.Colors.ChartSecondaryTeal,
                illustration = { ExerciseIllustration() }
            ),
            OnboardingPage(
                title = "Watch Your Recovery Grow",
                body = "Daily progress rings and history charts show how far you've come — every completed session counts toward your recovery.",
                accent = DesignTokens.Colors.Success,
                illustration = { ProgressIllustration() }
            ),
            OnboardingPage(
                title = "Your Care Team, One Tap Away",
                body = "Message your doctor, receive feedback on your sessions, and never miss an appointment with built-in reminders.",
                accent = DesignTokens.Colors.PrimaryDark,
                illustration = { CareTeamIllustration() }
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    val isLastPage = pagerState.currentPage == pages.lastIndex

    val finish: () -> Unit = {
        if (!isSaving) {
            isSaving = true
            scope.launch {
                try {
                    FirebaseService.updateUser(mapOf("hasCompletedOnboarding" to true))
                } catch (_: Exception) {
                    // Non-fatal: the flow re-offers next launch if the write failed.
                }
                onFinished()
            }
        }
    }

    // System back steps through pages instead of leaving the flow half-done.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    val accent by animateColorAsState(
        targetValue = pages[pagerState.currentPage].accent,
        animationSpec = tween(DesignTokens.Animation.Slow),
        label = "accent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: skip control (hidden on the last page)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.Base, vertical = DesignTokens.Spacing.SM),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(visible = !isLastPage, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = finish, enabled = !isSaving) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = DesignTokens.Typography.Medium
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = DesignTokens.Spacing.XXL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Illustration inside a soft layered disc
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(CircleShape)
                                .background(page.accent.copy(alpha = 0.08f))
                        )
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(CircleShape)
                                .background(page.accent.copy(alpha = 0.10f))
                        )
                        Surface(
                            modifier = Modifier.size(180.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = DesignTokens.Elevation.LG
                        ) {
                            Box(
                                modifier = Modifier.padding(DesignTokens.Spacing.XL),
                                contentAlignment = Alignment.Center
                            ) {
                                page.illustration()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL + DesignTokens.Spacing.Base))

                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                    Text(
                        text = page.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }

            // Page indicator — active dot stretches into a pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = DesignTokens.Spacing.LG),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 8.dp,
                        animationSpec = tween(DesignTokens.Animation.Normal),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = DesignTokens.Spacing.XXS)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                            .background(
                                if (isSelected) accent
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (isLastPage) finish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.XXL)
                    .padding(bottom = DesignTokens.Spacing.XXL)
                    .height(52.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.Button),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(DesignTokens.Spacing.XL),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = DesignTokens.Spacing.XXS
                    )
                } else {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Illustrations — drawn with Canvas so they scale crisply on any device
// ─────────────────────────────────────────────────────────────────────────────

/** Beating heart with an ECG trace running through it. */
@Composable
private fun HeartbeatIllustration() {
    val primary = DesignTokens.Colors.Primary
    val accent = DesignTokens.Colors.Error

    val transition = rememberInfiniteTransition(label = "heartbeat")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = Modifier
            .size(120.dp)
            .scale(pulse)
    ) {
        val w = size.width
        val h = size.height

        // Heart body — two cubic curves meeting at the bottom tip
        val heart = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.08f, h * 0.58f, w * 0.04f, h * 0.18f, w * 0.32f, h * 0.12f)
            cubicTo(w * 0.44f, h * 0.09f, w * 0.5f, h * 0.22f, w * 0.5f, h * 0.28f)
            cubicTo(w * 0.5f, h * 0.22f, w * 0.56f, h * 0.09f, w * 0.68f, h * 0.12f)
            cubicTo(w * 0.96f, h * 0.18f, w * 0.92f, h * 0.58f, w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(heart, color = accent.copy(alpha = 0.85f))

        // ECG trace across the middle
        val ecg = Path().apply {
            moveTo(0f, h * 0.5f)
            lineTo(w * 0.30f, h * 0.5f)
            lineTo(w * 0.38f, h * 0.34f)
            lineTo(w * 0.48f, h * 0.66f)
            lineTo(w * 0.56f, h * 0.42f)
            lineTo(w * 0.62f, h * 0.5f)
            lineTo(w, h * 0.5f)
        }
        drawPath(
            ecg,
            color = Color.White,
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Trace continues subtly outside the heart in the brand color
        drawCircle(color = primary, radius = w * 0.035f, center = Offset(w * 0.05f, h * 0.5f))
        drawCircle(color = primary, radius = w * 0.035f, center = Offset(w * 0.95f, h * 0.5f))
    }
}

/** Dumbbell with a gentle rocking motion. */
@Composable
private fun ExerciseIllustration() {
    val primary = DesignTokens.Colors.Primary
    val dark = DesignTokens.Colors.PrimaryDark

    val transition = rememberInfiniteTransition(label = "exercise")
    val tilt by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        rotate(degrees = tilt) {
            val barY = h * 0.5f
            val barHeight = h * 0.09f
            // Bar
            drawRoundRect(
                color = dark,
                topLeft = Offset(w * 0.18f, barY - barHeight / 2),
                size = androidx.compose.ui.geometry.Size(w * 0.64f, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight)
            )
            // Inner plates
            plate(primary, w * 0.16f, barY, w * 0.085f, h * 0.42f)
            plate(primary, w * 0.755f, barY, w * 0.085f, h * 0.42f)
            // Outer plates
            plate(dark, w * 0.06f, barY, w * 0.075f, h * 0.30f)
            plate(dark, w * 0.865f, barY, w * 0.075f, h * 0.30f)
        }
    }
}

private fun DrawScope.plate(color: Color, x: Float, centerY: Float, width: Float, height: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, centerY - height / 2),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.45f)
    )
}

/** Rising bar chart wrapped by a progress arc. */
@Composable
private fun ProgressIllustration() {
    val primary = DesignTokens.Colors.Primary
    val success = DesignTokens.Colors.Success
    val light = DesignTokens.Colors.ChartLightTeal

    val transition = rememberInfiniteTransition(label = "progress")
    val sweep by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.06f

        // Progress arc around the chart
        drawArc(
            color = light,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = success,
            startAngle = -90f,
            sweepAngle = 360f * sweep,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Ascending bars inside
        val barWidth = w * 0.10f
        val baseline = h * 0.70f
        val heights = listOf(0.14f, 0.22f, 0.32f, 0.42f)
        heights.forEachIndexed { i, hRatio ->
            val x = w * 0.26f + i * (barWidth * 1.32f)
            drawRoundRect(
                color = if (i == heights.lastIndex) success else primary,
                topLeft = Offset(x, baseline - h * hRatio),
                size = androidx.compose.ui.geometry.Size(barWidth, h * hRatio),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.4f)
            )
        }
    }
}

/** Two chat bubbles with a typing indicator. */
@Composable
private fun CareTeamIllustration() {
    val primary = DesignTokens.Colors.Primary
    val grey = DesignTokens.Colors.BubbleGrey

    val transition = rememberInfiniteTransition(label = "chat")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dots"
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        val w = size.width
        val h = size.height

        // Doctor bubble (top-left, brand color)
        val docBubble = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = w * 0.04f, top = h * 0.10f,
                    right = w * 0.66f, bottom = h * 0.42f,
                    radiusX = w * 0.10f, radiusY = w * 0.10f
                )
            )
            // Tail
            moveTo(w * 0.12f, h * 0.40f)
            lineTo(w * 0.06f, h * 0.52f)
            lineTo(w * 0.24f, h * 0.42f)
            close()
        }
        drawPath(docBubble, color = primary)

        // Message lines inside the doctor bubble
        listOf(0.19f, 0.27f).forEachIndexed { i, y ->
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(w * 0.12f, h * y),
                size = androidx.compose.ui.geometry.Size(w * (if (i == 0) 0.42f else 0.30f), h * 0.045f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.03f)
            )
        }

        // Patient bubble (bottom-right, grey) with typing dots
        val patBubble = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = w * 0.34f, top = h * 0.56f,
                    right = w * 0.96f, bottom = h * 0.86f,
                    radiusX = w * 0.10f, radiusY = w * 0.10f
                )
            )
            moveTo(w * 0.88f, h * 0.84f)
            lineTo(w * 0.94f, h * 0.96f)
            lineTo(w * 0.76f, h * 0.86f)
            close()
        }
        drawPath(patBubble, color = grey)

        val dotY = h * 0.71f
        listOf(0.50f, 0.62f, 0.74f).forEach { x ->
            drawCircle(
                color = primary.copy(alpha = dotAlpha),
                radius = w * 0.035f,
                center = Offset(w * x, dotY)
            )
        }
    }
}
