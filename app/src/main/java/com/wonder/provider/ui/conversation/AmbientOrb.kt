package com.wonder.provider.ui.conversation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.wonder.provider.ui.theme.WonderColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wonder itself. The orb is the only constant on screen — it breathes when idle, spins up
 * while thinking, and swells with the provider's voice while listening.
 */
@Composable
fun AmbientOrb(
    modifier: Modifier = Modifier,
    energy: Float = 0f,
    thinking: Boolean = false,
    speaking: Boolean = false
) {
    val aurora = WonderColors.current.aurora
    val transition = rememberInfiniteTransition(label = "orb")

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (thinking) 5_000 else 22_000, easing = LinearEasing)
        ),
        label = "spin"
    )

    val breath by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (speaking) 900 else 3_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val swell by animateFloatAsState(
        targetValue = 1f + energy * 0.4f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "swell"
    )

    val intensity by animateFloatAsState(
        targetValue = if (thinking || speaking || energy > 0.05f) 1f else 0.72f,
        animationSpec = tween(600),
        label = "intensity"
    )

    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val middle = size.center
        val scale = breath * swell

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    aurora.first().copy(alpha = 0.30f * intensity),
                    Color.Transparent
                ),
                center = middle,
                radius = radius * scale
            ),
            radius = radius * scale,
            center = middle
        )

        aurora.forEachIndexed { index, color ->
            val angle = Math.toRadians((spin + index * 120f).toDouble())
            val drift = radius * 0.26f * scale
            val lobeCenter = Offset(
                x = middle.x + (cos(angle) * drift).toFloat(),
                y = middle.y + (sin(angle) * drift).toFloat()
            )
            val lobeRadius = radius * 0.7f * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.62f * intensity),
                        color.copy(alpha = 0f)
                    ),
                    center = lobeCenter,
                    radius = lobeRadius
                ),
                radius = lobeRadius,
                center = lobeCenter
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.45f * intensity), Color.Transparent),
                center = middle,
                radius = radius * 0.42f * scale
            ),
            radius = radius * 0.42f * scale,
            center = middle
        )
    }
}

/**
 * A soft aurora wash behind the whole conversation. It drifts slowly enough to read as
 * atmosphere rather than motion.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier, alive: Boolean = false) {
    val wash = WonderColors.current.ambientWash
    val aurora = WonderColors.current.aurora
    val transition = rememberInfiniteTransition(label = "backdrop")

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (alive) 24_000 else 60_000, easing = LinearEasing)
        ),
        label = "drift"
    )

    Canvas(modifier) {
        val radius = size.maxDimension * 0.75f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(wash.first(), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.height * 0.12f),
                radius = radius
            ),
            radius = radius,
            center = Offset(size.width * 0.5f, -size.height * 0.12f)
        )

        val angle = Math.toRadians(drift.toDouble())
        val roamer = Offset(
            x = size.width * (0.5f + 0.4f * cos(angle).toFloat()),
            y = size.height * (0.22f + 0.12f * sin(angle).toFloat())
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(aurora[1].copy(alpha = 0.14f), Color.Transparent),
                center = roamer,
                radius = radius * 0.6f
            ),
            radius = radius * 0.6f,
            center = roamer
        )
    }
}
