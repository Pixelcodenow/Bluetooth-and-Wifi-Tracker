package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ble.ProximityLevel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RadarCanvas(
    proximity: ProximityLevel,
    rssi: Int,
    isBeeping: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00C9FF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransitions")

    // Radar sweep rotation (0 to 360 degrees)
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweep"
    )

    // Pulsing ripple ring for beeping or active search
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulse"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulseAlpha"
    )

    // Tracker blip radial distance normalized:
    // -50 dBm -> 0.28 (very close)
    // -70 dBm -> 0.52 (close)
    // -85 dBm -> 0.78 (far)
    // Signal lost -> 0.9
    val normalizedDistance = when (proximity) {
        ProximityLevel.VERY_CLOSE -> 0.26f
        ProximityLevel.CLOSE -> 0.48f
        ProximityLevel.NEARBY -> 0.70f
        ProximityLevel.FAR_AWAY -> 0.88f
        ProximityLevel.SIGNAL_LOST -> 0.92f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = min(size.width, size.height) / 2f * 0.92f

            // 1. Concentric radar grid rings
            val ringCount = 4
            for (i in 1..ringCount) {
                val radius = maxRadius * (i.toFloat() / ringCount)
                drawCircle(
                    color = accentColor.copy(alpha = 0.15f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Crosshair grid lines
            drawLine(
                color = accentColor.copy(alpha = 0.18f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = accentColor.copy(alpha = 0.18f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // 3. Dynamic radar sweep line with gradient glow
            if (proximity != ProximityLevel.SIGNAL_LOST) {
                val rad = Math.toRadians(sweepAngle.toDouble())
                val sweepEnd = Offset(
                    center.x + (maxRadius * cos(rad)).toFloat(),
                    center.y + (maxRadius * sin(rad)).toFloat()
                )
                drawLine(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.6f), Color.Transparent),
                        center = center,
                        radius = maxRadius
                    ),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Expanding pulse wave
            val currentPulseRadius = maxRadius * pulseScale
            drawCircle(
                color = if (isBeeping) Color(0xFFEF4444).copy(alpha = pulseAlpha)
                else accentColor.copy(alpha = pulseAlpha * 0.45f),
                radius = currentPulseRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 5. Center phone locator dot
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = accentColor,
                radius = 3.dp.toPx(),
                center = center
            )

            // 6. Tracker target blip
            if (proximity != ProximityLevel.SIGNAL_LOST) {
                // Fixed angle for tracker position on radar (45 degrees / top right)
                val blipAngleRad = Math.toRadians(45.0)
                val blipRadius = maxRadius * normalizedDistance
                val blipCenter = Offset(
                    center.x + (blipRadius * cos(blipAngleRad)).toFloat(),
                    center.y - (blipRadius * sin(blipAngleRad)).toFloat() // negative for top
                )

                // Blip glow
                drawCircle(
                    color = (if (isBeeping) Color(0xFFEF4444) else accentColor).copy(alpha = 0.35f),
                    radius = 16.dp.toPx(),
                    center = blipCenter
                )
                // Blip solid center
                drawCircle(
                    color = if (isBeeping) Color(0xFFEF4444) else accentColor,
                    radius = 8.dp.toPx(),
                    center = blipCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = blipCenter
                )
            }
        }
    }
}
