package com.example.ui.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.DisplayRefreshRateOptimizer
import kotlin.math.PI

/**
 * Curated multi-color RGB neon palettes tuned for STORE PRO's dark navy surfaces.
 * Features cyan, electric blue, emerald green, purple, magenta, and teal blends.
 */
object RgbNeonPalette {
    val palettes: List<List<Color>> = listOf(
        // 0: Emerald Green -> Cyan -> Electric Blue -> Purple -> Pink -> Emerald
        listOf(
            Color(0xFF00E676),
            Color(0xFF00E5FF),
            Color(0xFF2979FF),
            Color(0xFF8B5CF6),
            Color(0xFFEC4899),
            Color(0xFF00E676)
        ),
        // 1: Cyan -> Purple -> Pink -> Emerald -> Teal -> Cyan
        listOf(
            Color(0xFF00E5FF),
            Color(0xFF8B5CF6),
            Color(0xFFEC4899),
            Color(0xFF00E676),
            Color(0xFF14B8A6),
            Color(0xFF00E5FF)
        ),
        // 2: Electric Blue -> Teal -> Emerald -> Pink -> Purple -> Electric Blue
        listOf(
            Color(0xFF38BDF8),
            Color(0xFF14B8A6),
            Color(0xFF00E676),
            Color(0xFFF472B6),
            Color(0xFF8B5CF6),
            Color(0xFF38BDF8)
        ),
        // 3: Purple -> Pink -> Cyan -> Emerald -> Blue -> Purple
        listOf(
            Color(0xFFA855F7),
            Color(0xFFEC4899),
            Color(0xFF00E5FF),
            Color(0xFF00E676),
            Color(0xFF3B82F6),
            Color(0xFFA855F7)
        ),
        // 4: Teal -> Emerald -> Cyan -> Electric Blue -> Magenta -> Teal
        listOf(
            Color(0xFF2DD4BF),
            Color(0xFF00E676),
            Color(0xFF00E5FF),
            Color(0xFF3B82F6),
            Color(0xFFD946EF),
            Color(0xFF2DD4BF)
        ),
        // 5: Pink -> Purple -> Electric Blue -> Cyan -> Emerald -> Pink
        listOf(
            Color(0xFFEC4899),
            Color(0xFF8B5CF6),
            Color(0xFF2979FF),
            Color(0xFF00E5FF),
            Color(0xFF00E676),
            Color(0xFFEC4899)
        )
    )

    val searchFocusColors: List<Color> = listOf(
        Color(0xFF00E5FF),
        Color(0xFF00E676),
        Color(0xFF2979FF),
        Color(0xFFAA00FF),
        Color(0xFFEC4899),
        Color(0xFF00E5FF)
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
 * Shared lifecycle-aware animation phase clock provided at the app root so all RGB neon
 * card borders share a single animation ticker rather than spawning one InfiniteTransition per card.
 */
val LocalSharedNeonPhase = compositionLocalOf<State<Float>?> { null }

@Composable
fun rememberLifecycleIsResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return isResumed
}

@Composable
fun ProvideSharedNeonGlowClock(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isResumed = rememberLifecycleIsResumed()
    val reducedMotion = remember(context) { RgbNeonPalette.isReducedMotionEnabled(context) }
    val powerSave = remember(context, isResumed) { DisplayRefreshRateOptimizer.isPowerSaveMode(context) }

    val sharedPhase: State<Float> = if (isResumed && !reducedMotion && !powerSave) {
        val transition = rememberInfiniteTransition(label = "shared_rgb_neon_clock")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shared_rgb_neon_phase"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    CompositionLocalProvider(LocalSharedNeonPhase provides sharedPhase) {
        content()
    }
}

/**
 * Applies a layered RGB neon border and soft outer halo around all four sides of any card.
 * Uses a shared lifecycle-aware clock plus cached native LinearGradient Shaders and Matrix rotation
 * so scrolling 90/120Hz lists triggers zero per-frame object allocations and zero recompositions.
 */
fun Modifier.rgbNeonGlowBorder(
    seed: String,
    cornerRadius: Dp = 18.dp,
    borderWidth: Dp = 1.6.dp,
    glowIntensity: Float = 1f,
    animate: Boolean = true,
    paletteColors: List<Color>? = null,
    durationMillis: Int = 6500
): Modifier = composed {
    val context = LocalContext.current
    val sharedPhase = LocalSharedNeonPhase.current
    val isResumed = rememberLifecycleIsResumed()
    val baseColors = remember(seed, paletteColors) {
        val raw = if (!paletteColors.isNullOrEmpty()) paletteColors else RgbNeonPalette.paletteFor(seed)
        if (raw.size >= 2 && raw.first() != raw.last()) raw + raw.first() else raw
    }
    val reducedMotion = remember(context) { RgbNeonPalette.isReducedMotionEnabled(context) }
    val seedPhaseOffsetRad = remember(seed) {
        if (seed.isBlank()) 0f else ((seed.hashCode() and 0xFF) / 255f) * (2.0 * PI).toFloat()
    }

    val useSharedClock = animate && !reducedMotion && sharedPhase != null && durationMillis == 6500
    val angleState: State<Float> = when {
        useSharedClock -> sharedPhase!!
        animate && !reducedMotion && isResumed -> {
            val transition = rememberInfiniteTransition(label = "rgb_neon_glow_custom")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis.coerceAtLeast(800), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rgb_neon_phase"
            )
        }
        else -> remember { mutableFloatStateOf(0.5f) }
    }

    val clampedIntensity = glowIntensity.coerceIn(0.4f, 1.4f)
    val cachedColorInts = remember(baseColors, clampedIntensity) {
        Triple(
            baseColors.map { it.copy(alpha = (0.10f * clampedIntensity).coerceAtMost(0.22f)).toArgb() }.toIntArray(),
            baseColors.map { it.copy(alpha = (0.22f * clampedIntensity).coerceAtMost(0.38f)).toArgb() }.toIntArray(),
            baseColors.map { it.copy(alpha = (0.85f * clampedIntensity).coerceAtMost(0.96f)).toArgb() }.toIntArray()
        )
    }

    this.drawWithCache {
        if (size.width <= 0f || size.height <= 0f) {
            return@drawWithCache onDrawBehind { }
        }

        val radiusPx = cornerRadius.toPx()
        val borderPx = borderWidth.toPx()
        val outerExpand = 2.4.dp.toPx()
        val midExpand = 1.0.dp.toPx()

        val outerRadius = radiusPx + outerExpand
        val midRadius = radiusPx + midExpand

        val outerShader = android.graphics.SweepGradient(0f, 0f, cachedColorInts.first, null)
        val midShader = android.graphics.SweepGradient(0f, 0f, cachedColorInts.second, null)
        val borderShader = android.graphics.SweepGradient(0f, 0f, cachedColorInts.third, null)

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.0.dp.toPx()
            shader = outerShader
        }
        val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5.dp.toPx()
            shader = midShader
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderPx
            shader = borderShader
        }

        val shaderMatrix = Matrix()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxW = size.width
        val maxH = size.height
        val radToDeg = (180.0 / PI).toFloat()

        onDrawBehind {
            val angleDeg = (angleState.value + seedPhaseOffsetRad) * radToDeg
            shaderMatrix.setRotate(angleDeg)
            shaderMatrix.postTranslate(cx, cy)

            outerShader.setLocalMatrix(shaderMatrix)
            midShader.setLocalMatrix(shaderMatrix)
            borderShader.setLocalMatrix(shaderMatrix)
            outerPaint.shader = outerShader
            midPaint.shader = midShader
            borderPaint.shader = borderShader

            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                // 1. Soft outer neon halo
                nc.drawRoundRect(
                    -outerExpand, -outerExpand,
                    maxW + outerExpand, maxH + outerExpand,
                    outerRadius, outerRadius,
                    outerPaint
                )
                // 2. Medium neon bloom
                nc.drawRoundRect(
                    -midExpand, -midExpand,
                    maxW + midExpand, maxH + midExpand,
                    midRadius, midRadius,
                    midPaint
                )
                // 3. Crisp 4-sided RGB neon border
                nc.drawRoundRect(
                    0f, 0f,
                    maxW, maxH,
                    radiusPx, radiusPx,
                    borderPaint
                )
            }
        }
    }
}
