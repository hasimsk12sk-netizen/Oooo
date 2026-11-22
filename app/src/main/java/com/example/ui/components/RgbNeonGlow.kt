package com.example.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Curated multi-color RGB neon palettes tuned for STORE PRO's dark navy surfaces.
 * Features cyan, electric blue, emerald green, purple, magenta, and teal blends.
 */
object RgbNeonPalette {
    val palettes: List<List<Color>> = listOf(
        // 0: Emerald -> Cyan -> Electric Blue (Featured Spotlight signature)
        listOf(
            Color(0xFF00E676),
            Color(0xFF00E5FF),
            Color(0xFF2979FF),
            Color(0xFF00E676)
        ),
        // 1: Cyan -> Purple -> Magenta -> Teal
        listOf(
            Color(0xFF00E5FF),
            Color(0xFF8B5CF6),
            Color(0xFFEC4899),
            Color(0xFF14B8A6)
        ),
        // 2: Electric Blue -> Teal -> Emerald -> Cyan
        listOf(
            Color(0xFF38BDF8),
            Color(0xFF14B8A6),
            Color(0xFF00E676),
            Color(0xFF3B82F6)
        ),
        // 3: Purple -> Magenta -> Electric Blue -> Cyan
        listOf(
            Color(0xFFA855F7),
            Color(0xFFEC4899),
            Color(0xFF3B82F6),
            Color(0xFF00E5FF)
        ),
        // 4: Teal -> Emerald -> Cyan -> Violet
        listOf(
            Color(0xFF2DD4BF),
            Color(0xFF00E676),
            Color(0xFF00E5FF),
            Color(0xFF8B5CF6)
        ),
        // 5: Magenta -> Electric Blue -> Cyan -> Emerald
        listOf(
            Color(0xFFF43F5E),
            Color(0xFFD946EF),
            Color(0xFF2979FF),
            Color(0xFF00E676)
        )
    )

    fun paletteFor(seed: String): List<Color> {
        if (seed.isBlank()) return palettes[0]
        val index = (seed.hashCode() and Int.MAX_VALUE) % palettes.size
        return palettes[index]
    }

    fun isReducedMotionEnabled(context: Context): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        return scale <= 0f
    }
}

/**
 * Applies a layered RGB neon border and soft outer halo around all four sides of any card.
 * Deferred draw-phase state reads and cached geometry/color stops prevent per-frame recomposition
 * and GC allocations while keeping 60fps scrolling smooth across lists.
 */
fun Modifier.rgbNeonGlowBorder(
    seed: String,
    cornerRadius: Dp = 18.dp,
    borderWidth: Dp = 1.6.dp,
    glowIntensity: Float = 1f,
    animate: Boolean = true,
    paletteColors: List<Color>? = null
): Modifier = composed {
    val context = LocalContext.current
    val baseColors = remember(seed, paletteColors) {
        if (!paletteColors.isNullOrEmpty()) paletteColors else RgbNeonPalette.paletteFor(seed)
    }
    val reducedMotion = remember(context) { RgbNeonPalette.isReducedMotionEnabled(context) }

    val angleState: State<Float> = if (animate && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "rgb_neon_glow")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rgb_neon_phase"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    val clampedIntensity = glowIntensity.coerceIn(0.4f, 1.5f)
    val cachedColors = remember(baseColors, clampedIntensity) {
        Triple(
            baseColors.map { it.copy(alpha = (0.11f * clampedIntensity).coerceAtMost(0.24f)) },
            baseColors.map { it.copy(alpha = (0.24f * clampedIntensity).coerceAtMost(0.42f)) },
            baseColors.map { it.copy(alpha = (0.86f * clampedIntensity).coerceAtMost(1f)) }
        )
    }

    this.drawWithCache {
        if (size.width <= 0f || size.height <= 0f) {
            return@drawWithCache onDrawBehind { }
        }

        val radiusPx = cornerRadius.toPx()
        val borderPx = borderWidth.toPx()
        val outerExpand = 2.6.dp.toPx()
        val midExpand = 1.1.dp.toPx()

        val outerTopLeft = Offset(-outerExpand, -outerExpand)
        val outerSize = Size(size.width + outerExpand * 2f, size.height + outerExpand * 2f)
        val outerCorner = CornerRadius(radiusPx + outerExpand, radiusPx + outerExpand)
        val outerStroke = Stroke(width = 5.5.dp.toPx())

        val midTopLeft = Offset(-midExpand, -midExpand)
        val midSize = Size(size.width + midExpand * 2f, size.height + midExpand * 2f)
        val midCorner = CornerRadius(radiusPx + midExpand, radiusPx + midExpand)
        val midStroke = Stroke(width = 2.8.dp.toPx())

        val borderCorner = CornerRadius(radiusPx, radiusPx)
        val borderStroke = Stroke(width = borderPx)

        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxW = size.width
        val maxH = size.height
        val outerHaloColors = cachedColors.first
        val midHaloColors = cachedColors.second
        val borderColors = cachedColors.third

        onDrawBehind {
            val angleRad = angleState.value
            val dx = (cos(angleRad) * cx).coerceIn(-maxW, maxW)
            val dy = (sin(angleRad) * cy).coerceIn(-maxH, maxH)
            val start = Offset(cx - dx, cy - dy)
            val end = Offset(cx + dx, cy + dy)

            // 1. Soft outer neon halo
            drawRoundRect(
                brush = Brush.linearGradient(colors = outerHaloColors, start = start, end = end),
                topLeft = outerTopLeft,
                size = outerSize,
                cornerRadius = outerCorner,
                style = outerStroke
            )

            // 2. Medium neon bloom
            drawRoundRect(
                brush = Brush.linearGradient(colors = midHaloColors, start = start, end = end),
                topLeft = midTopLeft,
                size = midSize,
                cornerRadius = midCorner,
                style = midStroke
            )

            // 3. Crisp 4-sided RGB neon border
            drawRoundRect(
                brush = Brush.linearGradient(colors = borderColors, start = start, end = end),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = borderCorner,
                style = borderStroke
            )
        }
    }
}
