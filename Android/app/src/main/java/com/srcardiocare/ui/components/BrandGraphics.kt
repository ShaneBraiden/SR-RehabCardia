package com.srcardiocare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.ui.theme.DesignTokens

private val HeartRed = Color(0xFFEF4C43)
private val PulseBlue = Color(0xFF0E7CC3)
private val BrandTextBlue = Color(0xFF0A3F7A)

@Composable
fun BrandHeartLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val heart = Path().apply {
            moveTo(w * 0.50f, h * 0.90f)
            cubicTo(w * 0.22f, h * 0.74f, w * 0.06f, h * 0.50f, w * 0.20f, h * 0.28f)
            cubicTo(w * 0.32f, h * 0.10f, w * 0.46f, h * 0.15f, w * 0.50f, h * 0.29f)
            cubicTo(w * 0.54f, h * 0.15f, w * 0.68f, h * 0.10f, w * 0.80f, h * 0.28f)
            cubicTo(w * 0.94f, h * 0.50f, w * 0.78f, h * 0.74f, w * 0.50f, h * 0.90f)
            close()
        }
        drawPath(
            path = heart,
            color = HeartRed,
            style = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val pulse = Path().apply {
            moveTo(w * 0.10f, h * 0.54f)
            lineTo(w * 0.34f, h * 0.54f)
            lineTo(w * 0.40f, h * 0.50f)
            lineTo(w * 0.47f, h * 0.36f)
            lineTo(w * 0.56f, h * 0.66f)
            lineTo(w * 0.64f, h * 0.46f)
            lineTo(w * 0.72f, h * 0.54f)
            lineTo(w * 0.90f, h * 0.54f)
        }
        drawPath(
            path = pulse,
            color = PulseBlue,
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun BrandDashboardBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDFEFF), Color(0xFFF1F8FF)),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            )
            .padding(horizontal = DesignTokens.Spacing.LG, vertical = DesignTokens.Spacing.MD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandHeartLogo(modifier = Modifier.size(58.dp))
        Column {
            Text(
                text = "SrCardioCare",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandTextBlue
            )
            Text(
                text = "A digital health platform for cardiac rehabilitation",
                style = MaterialTheme.typography.bodySmall,
                color = BrandTextBlue.copy(alpha = 0.85f)
            )
        }
    }
}
