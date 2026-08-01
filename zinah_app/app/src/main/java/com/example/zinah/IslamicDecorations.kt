package com.example.zinah

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decorative composables for the Zinah design system.
 *
 * These provide Islamic-inspired visual elements:
 *  - Crescent moon (hilal)
 *  - 8-pointed star (khatim Sulaymani)
 *  - Geometric pattern fills
 *  - Radial glow / soft bokeh backgrounds
 */

/** Draws a crescent moon ( ☾ ) using two overlapping circles. */
@Composable
fun CrescentMoon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    primaryColor: Color = ZinahTheme.GoldBright,
    backgroundColor: Color = Color.Transparent
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = minOf(w, h) / 2f * 0.85f

            // Outer full circle (gold)
            drawCircle(
                color = primaryColor,
                radius = r,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = Fill
            )
            // Inner offset circle (background) to carve the crescent
            drawCircle(
                color = backgroundColor,
                radius = r * 0.85f,
                center = androidx.compose.ui.geometry.Offset(cx + r * 0.35f, cy - r * 0.1f),
                style = Fill
            )
        }
    }
}

/**
 * Draws an 8-pointed star (Khatim Sulaymani / Rub el Hizb) — a common Islamic motif.
 * Formed by two overlapping squares rotated 45° from each other.
 */
@Composable
fun EightPointStar(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    color: Color = ZinahTheme.Gold,
    strokeWidth: Dp = 2.dp
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.toPx() / 2f
            val cy = size.toPx() / 2f
            val r = size.toPx() / 2f * 0.9f

            // First square (0°)
            val square1 = Path().apply {
                val half = r * 0.7f
                moveTo(cx - half, cy - half)
                lineTo(cx + half, cy - half)
                lineTo(cx + half, cy + half)
                lineTo(cx - half, cy + half)
                close()
            }
            // Second square (45°)
            val square2 = Path().apply {
                val half = r * 0.7f
                moveTo(cx, cy - half)
                lineTo(cx + half, cy)
                lineTo(cx, cy + half)
                lineTo(cx - half, cy)
                close()
            }

            drawPath(square1, color = color, style = Stroke(width = strokeWidth.toPx()))
            drawPath(square2, color = color, style = Stroke(width = strokeWidth.toPx()))
        }
    }
}

/**
 * Soft radial glow — useful as a hero background.
 * Renders a blurred circle of color that fades to transparent.
 */
@Composable
fun RadialGlow(
    modifier: Modifier = Modifier,
    color: Color = ZinahTheme.Gold.copy(alpha = 0.3f),
    blurRadius: Dp = 80.dp
) {
    Box(
        modifier = modifier
            .blur(blurRadius)
            .background(color)
    )
}

/**
 * Draws an Islamic-style geometric border pattern (repeating 8-pointed stars)
 * along the top edge of a card. Used as a decorative accent.
 */
@Composable
fun IslamicBorderAccent(
    modifier: Modifier = Modifier,
    color: Color = ZinahTheme.Gold,
        accentHeight: Dp = 4.dp
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = accentHeight.toPx()
        // Simple dashed gold line
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, h / 2f),
            end = androidx.compose.ui.geometry.Offset(w, h / 2f),
            strokeWidth = h * 0.6f
        )
    }
}

/**
 * Draws a subtle 8-pointed star pattern as a card background fill.
 * Very low opacity — adds texture without distracting from content.
 */
@Composable
fun GeometricPatternBackground(
    modifier: Modifier = Modifier,
    color: Color = ZinahTheme.Gold.copy(alpha = 0.04f),
    starSize: Dp = 24.dp,
    spacing: Dp = 48.dp
) {
    Canvas(modifier = modifier) {
        val starPx = starSize.toPx()
        val spacingPx = spacing.toPx()
        val step = starPx + spacingPx

        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            // Offset every other row for hex-like tiling
            val rowOffset = if ((y / step).toInt() % 2 == 0) 0f else step / 2f
            while (x < size.width) {
                drawStar8(
                    centerX = x + rowOffset,
                    centerY = y,
                    radius = starPx / 2f * 0.8f,
                    color = color
                )
                x += step
            }
            y += step
        }
    }
}

/** Draws an 8-pointed star at the given position. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar8(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color
) {
    val path1 = Path().apply {
        val half = radius * 0.7f
        moveTo(centerX - half, centerY - half)
        lineTo(centerX + half, centerY - half)
        lineTo(centerX + half, centerY + half)
        lineTo(centerX - half, centerY + half)
        close()
    }
    val path2 = Path().apply {
        val half = radius * 0.7f
        moveTo(centerX, centerY - half)
        lineTo(centerX + half, centerY)
        lineTo(centerX, centerY + half)
        lineTo(centerX - half, centerY)
        close()
    }
    drawPath(path1, color = color, style = Fill)
    drawPath(path2, color = color, style = Fill)
}

/**
 * A vertical gradient brush going from deep emerald to almost-black.
 * Used as the background for full-screen premium views (AdhanActivity).
 */
val PremiumEmeraldGradient = Brush.verticalGradient(
    colors = listOf(
        ZinahTheme.EmeraldDeep,
        ZinahTheme.EmeraldDarkest,
        Color(0xFF021810)
    )
)

/**
 * A diagonal gradient brush going from emerald to a deeper emerald.
 * Used as the background for hero cards.
 */
val HeroCardGradient = Brush.linearGradient(
    colors = listOf(
        ZinahTheme.Emerald,
        ZinahTheme.EmeraldDeep
    )
)

/**
 * A warm gold gradient for accent badges.
 */
val GoldGradient = Brush.linearGradient(
    colors = listOf(
        ZinahTheme.GoldBright,
        ZinahTheme.Gold,
        ZinahTheme.GoldDeep
    )
)
