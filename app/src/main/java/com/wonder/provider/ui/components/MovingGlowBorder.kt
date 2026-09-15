package com.wonder.provider.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a highlight that travels along the rounded-rect perimeter behind [content].
 */
@Composable
fun MovingGlowBorderBox(
    active: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    colors: List<Color>,
    borderWidth: Dp = 2.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        if (active) {
            val transition = rememberInfiniteTransition(label = "movingGlow")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing)
                ),
                label = "glowProgress"
            )

            val primary = colors.firstOrNull() ?: Color(0xFF2DD4BF)
            val secondary = colors.getOrElse(1) { primary }

            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = borderWidth.toPx()
                val inset = stroke * 0.75f
                val radius = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f - inset)

                val roundRect = RoundRect(
                    left = inset,
                    top = inset,
                    right = size.width - inset,
                    bottom = size.height - inset,
                    cornerRadius = CornerRadius(radius, radius)
                )
                val outline = Path().apply { addRoundRect(roundRect) }

                drawPath(
                    path = outline,
                    color = primary.copy(alpha = 0.22f),
                    style = Stroke(width = stroke)
                )

                val measure = PathMeasure()
                measure.setPath(outline, false)
                val perimeter = measure.length
                if (perimeter <= 1f) return@Canvas

                val highlightLen = perimeter * 0.26f
                val start = progress * perimeter
                val end = start + highlightLen
                val highlight = Path()

                if (end <= perimeter) {
                    measure.getSegment(start, end, highlight, true)
                } else {
                    measure.getSegment(start, perimeter, highlight, true)
                    measure.getSegment(0f, end - perimeter, highlight, true)
                }

                drawPath(
                    path = highlight,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.95f),
                            secondary,
                            primary.copy(alpha = 0.15f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    style = Stroke(width = stroke * 1.4f, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}
