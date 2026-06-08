// SkeletonComponents.kt — Reusable shimmer skeleton placeholders for loading states
package com.srcardiocare.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srcardiocare.ui.theme.DesignTokens

@Composable
fun rememberShimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    val brush = rememberShimmerBrush()
    Box(modifier = modifier.clip(shape).background(brush))
}

@Composable
fun SkeletonListRow(
    modifier: Modifier = Modifier,
    showTrailing: Boolean = true
) {
    val brush = rememberShimmerBrush()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(brush))
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            if (showTrailing) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 120.dp
) {
    val brush = rememberShimmerBrush()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height - 60.dp)
                    .clip(RoundedCornerShape(DesignTokens.Radius.LG))
                    .background(brush)
            )
        }
    }
}

@Composable
fun SkeletonDonutChart(
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    showLegend: Boolean = true
) {
    val brush = rememberShimmerBrush()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.XL),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
            // Donut ring placeholder
            Box(
                modifier = Modifier.size(diameter),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(diameter)
                        .clip(CircleShape)
                        .background(brush)
                )
                // Hollow center
                Box(
                    modifier = Modifier
                        .size(diameter * 0.6f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
            if (showLegend) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(3) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(brush)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(brush)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonBarChart(
    modifier: Modifier = Modifier,
    barCount: Int = 7,
    chartHeight: Dp = 100.dp
) {
    val brush = rememberShimmerBrush()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.XL),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val heights = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.45f)
                repeat(barCount) { i ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        val barFraction = heights[i % heights.size]
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .fillMaxHeight(barFraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(brush)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonStatsCard(modifier: Modifier = Modifier, itemCount: Int = 4) {
    val brush = rememberShimmerBrush()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.XL),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            repeat(itemCount) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonProfileHeader(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DesignTokens.Spacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(brush)
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Loading placeholder for profile screens: avatar/name header followed by five
 * field-row shimmers. Emit inside the screen's scrolling Column.
 */
@Composable
fun ProfileFormSkeleton(rows: Int = 5) {
    SkeletonProfileHeader()
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
    repeat(rows) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(DesignTokens.Radius.Base)
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
    }
}
