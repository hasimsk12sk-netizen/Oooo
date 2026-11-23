package com.example.ui.home

import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.RgbNeonPalette
import com.example.ui.components.rememberLifecycleIsResumed
import com.example.ui.components.rgbNeonGlowBorder
import com.example.ui.theme.StoreProSecondaryCyan
import com.example.util.DisplayRefreshRateOptimizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Immutable
data class Showcase3DApp(
    val id: String,
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color
)

private val Showcase3DTrendingApps = listOf(
    Showcase3DApp(
        id = "chatgpt",
        displayName = "ChatGPT",
        primaryColor = Color(0xFF10A37F),
        secondaryColor = Color(0xFF00E5FF)
    ),
    Showcase3DApp(
        id = "youtube",
        displayName = "YouTube",
        primaryColor = Color(0xFFFF1744),
        secondaryColor = Color(0xFFFF6D00)
    ),
    Showcase3DApp(
        id = "instagram",
        displayName = "Instagram",
        primaryColor = Color(0xFFE1306C),
        secondaryColor = Color(0xFF8B5CF6)
    ),
    Showcase3DApp(
        id = "whatsapp",
        displayName = "WhatsApp",
        primaryColor = Color(0xFF25D366),
        secondaryColor = Color(0xFF14B8A6)
    ),
    Showcase3DApp(
        id = "facebook",
        displayName = "Facebook",
        primaryColor = Color(0xFF1877F2),
        secondaryColor = Color(0xFF38BDF8)
    )
)

private val ShowcaseRgbColors = listOf(
    Color(0xFF00E5FF), // Cyan
    Color(0xFF14B8A6), // Teal
    Color(0xFF00E676), // Emerald Green
    Color(0xFF8B5CF6)  // Purple
)

private val BadgeTextGradientBrush = Brush.horizontalGradient(
    listOf(
        Color(0xFF00E5FF),
        Color(0xFF00E676),
        Color(0xFFA78BFA)
    )
)

private val BadgeBorderBrush = Brush.horizontalGradient(ShowcaseRgbColors)

private val TitleGradientBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White,
        Color(0xFFB2F5EA),
        Color(0xFF00E5FF)
    )
)

private val BackdropScrimBrush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFF040917).copy(alpha = 0.62f),
        0.38f to Color(0xFF071126).copy(alpha = 0.46f),
        0.68f to Color(0xFF050B1C).copy(alpha = 0.82f),
        1.00f to Color(0xFF030712).copy(alpha = 0.94f)
    )
)

private val TileSurfaceBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF172542).copy(alpha = 0.96f),
        Color(0xFF0A1224).copy(alpha = 0.96f)
    )
)

private val SweepColorInts = intArrayOf(
    Color(0xFF14B8A6).copy(alpha = 0.16f).toArgb(),
    Color(0xFF8B5CF6).copy(alpha = 0.08f).toArgb(),
    Color.Transparent.toArgb()
)

private val HorizonColors = listOf(
    Color(0xFF00E5FF).copy(alpha = 0.14f),
    Color(0xFF8B5CF6).copy(alpha = 0.06f),
    Color.Transparent
)

/**
 * Purely decorative, non-interactive 3D promotional showcase banner.
 * Displays the fixed STORE PRO backdrop, HASIM APK header, glowing 3D HASIM title,
 * and 5 continuously orbiting 3D app icons with lifecycle-aware gyroscope parallax.
 * Optimized to run all per-frame motion strictly in Layout & Draw phases (zero recompositions/sec).
 */
@Composable
fun FeaturedShowcaseBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isResumed = rememberLifecycleIsResumed()
    val reducedMotion = remember(context) { RgbNeonPalette.isReducedMotionEnabled(context) }
    val powerSave = remember(context, isResumed) { DisplayRefreshRateOptimizer.isPowerSaveMode(context) }
    val animateShowcase = isResumed && !reducedMotion && !powerSave

    val parallaxState = rememberShowcaseParallax(enabled = !reducedMotion && isResumed)

    val orbitPhaseState: State<Float> = if (animateShowcase) {
        val infiniteTransition = rememberInfiniteTransition(label = "hasim_3d_showcase")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 15000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbit_phase"
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    val ambientShimmerState: State<Float> = if (animateShowcase) {
        val shimmerTransition = rememberInfiniteTransition(label = "hasim_shimmer")
        shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer_value"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .rgbNeonGlowBorder(
                seed = "hasim_apk_featured_showcase",
                cornerRadius = 24.dp,
                borderWidth = 2.dp,
                glowIntensity = 1.15f,
                paletteColors = ShowcaseRgbColors
            )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF050A18)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .testTag("featured_banner")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(312.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clipToBounds()
            ) {
                // 1. Fixed STORE PRO Logo Background (never stretched, distorted, or clipped awkwardly)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF040917))
                ) {
                    // Cached volumetric ambient glow behind the STORE PRO emblem
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val w = size.width
                                val h = size.height
                                val c1 = Offset(w * 0.25f, h * 0.35f)
                                val r1 = (w * 0.55f).coerceAtLeast(1f)
                                val b1 = Brush.radialGradient(
                                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.22f), Color.Transparent),
                                    center = c1,
                                    radius = r1
                                )
                                val c2 = Offset(w * 0.52f, h * 0.32f)
                                val r2 = (w * 0.52f).coerceAtLeast(1f)
                                val b2 = Brush.radialGradient(
                                    colors = listOf(Color(0xFF00E676).copy(alpha = 0.20f), Color.Transparent),
                                    center = c2,
                                    radius = r2
                                )
                                val c3 = Offset(w * 0.82f, h * 0.42f)
                                val r3 = (w * 0.50f).coerceAtLeast(1f)
                                val b3 = Brush.radialGradient(
                                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.24f), Color.Transparent),
                                    center = c3,
                                    radius = r3
                                )
                                onDrawBehind {
                                    drawCircle(brush = b1, radius = r1, center = c1)
                                    drawCircle(brush = b2, radius = r2, center = c2)
                                    drawCircle(brush = b3, radius = r3, center = c3)
                                }
                            }
                    )

                    // Existing STORE PRO image fixed & centered behind dark overlay
                    Image(
                        painter = painterResource(id = R.drawable.ic_storepro_logo_img),
                        contentDescription = "STORE PRO Showcase Background",
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 6.dp, bottom = 28.dp)
                            .graphicsLayer {
                                alpha = 0.72f
                            }
                    )

                    // Professional dark gradient scrim + subtle specular 3D depth lighting
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackdropScrimBrush)
                    )

                    // Dynamic soft RGB light sweep + horizon stage ellipse inside card boundaries (cached shaders)
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val sweepRadius = (size.width * 0.52f).coerceAtLeast(1f)
                                val sweepY = size.height * 0.55f
                                val sweepShader = android.graphics.RadialGradient(
                                    0f, 0f, sweepRadius,
                                    SweepColorInts, null, Shader.TileMode.CLAMP
                                )
                                val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    shader = sweepShader
                                }
                                val sweepMatrix = Matrix()

                                val horizonCenter = Offset(size.width / 2f, size.height * 0.86f)
                                val horizonRadius = (size.width * 0.45f).coerceAtLeast(1f)
                                val horizonBrush = Brush.radialGradient(
                                    colors = HorizonColors,
                                    center = horizonCenter,
                                    radius = horizonRadius
                                )
                                val horizonTopLeft = Offset(size.width * 0.08f, size.height * 0.72f)
                                val horizonSize = Size(size.width * 0.84f, size.height * 0.24f)

                                onDrawBehind {
                                    val sweepX = size.width * (0.2f + 0.6f * ambientShimmerState.value) +
                                        parallaxState.value.first * 24f
                                    sweepMatrix.setTranslate(sweepX, sweepY)
                                    sweepShader.setLocalMatrix(sweepMatrix)
                                    sweepPaint.shader = sweepShader
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawCircle(sweepX, sweepY, sweepRadius, sweepPaint)
                                    }
                                    drawOval(
                                        brush = horizonBrush,
                                        topLeft = horizonTopLeft,
                                        size = horizonSize
                                    )
                                }
                            }
                    )
                }

                // 2. Foreground Content Layer
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Branding Header Row ("HASIM APK")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF0B162C).copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, BadgeBorderBrush)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = StoreProSecondaryCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "HASIM APK",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.6.sp,
                                    style = TextStyle(brush = BadgeTextGradientBrush),
                                    modifier = Modifier.testTag("showcase_heading")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Large 3D Glowing "HASIM" Typography (reads parallax strictly inside graphicsLayer)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.graphicsLayer {
                            val (tiltX, tiltY) = parallaxState.value
                            cameraDistance = 12f * density.density
                            translationX = tiltX * 6.dp.toPx()
                            translationY = tiltY * 3.dp.toPx()
                            rotationY = tiltX * 4f
                            rotationX = -tiltY * 3f
                        }
                    ) {
                        // Back extrusion shadow layer
                        Text(
                            text = "HASIM",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            color = Color(0xFF020617).copy(alpha = 0.85f),
                            modifier = Modifier.offset(x = 2.dp, y = 3.dp)
                        )
                        // Atmospheric cyan/green halo layer
                        Text(
                            text = "HASIM",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            color = Color(0xFF00E5FF).copy(alpha = 0.28f),
                            modifier = Modifier.offset(x = (-1).dp, y = (-1).dp)
                        )
                        // Luminous metallic gradient main text
                        Text(
                            text = "HASIM",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            style = TextStyle(brush = TitleGradientBrush),
                            modifier = Modifier.testTag("showcase_brand_title")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Animated 3D Orbital Trending Apps Carousel (strictly clipped & zero-recomposition motion)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                            .testTag("showcase_3d_carousel")
                    ) {
                        val containerW = constraints.maxWidth.toFloat()
                        val containerH = constraints.maxHeight.toFloat()
                        val metrics = remember(containerW, containerH, density) {
                            val tileWidthPx = with(density) { 74.dp.toPx() }
                            val tileHeightPx = with(density) { 86.dp.toPx() }
                            OrbitMetrics(
                                rx = ((containerW - tileWidthPx) * 0.44f).coerceAtLeast(24f),
                                ry = with(density) { 11.dp.toPx() },
                                floatAmpPx = with(density) { 4.5.dp.toPx() },
                                parallaxXPx = with(density) { 8.dp.toPx() },
                                parallaxYPx = with(density) { 5.dp.toPx() },
                                centerX = (containerW - tileWidthPx) / 2f,
                                centerY = ((containerH - tileHeightPx) * 0.48f).coerceAtLeast(0f)
                            )
                        }

                        val count = Showcase3DTrendingApps.size
                        Showcase3DTrendingApps.forEachIndexed { index, app ->
                            Showcase3DOrbitTile(
                                app = app,
                                index = index,
                                totalCount = count,
                                orbitPhaseState = orbitPhaseState,
                                parallaxState = parallaxState,
                                metrics = metrics,
                                reducedMotion = reducedMotion
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class OrbitMetrics(
    val rx: Float,
    val ry: Float,
    val floatAmpPx: Float,
    val parallaxXPx: Float,
    val parallaxYPx: Float,
    val centerX: Float,
    val centerY: Float
)

@Composable
private fun Showcase3DOrbitTile(
    app: Showcase3DApp,
    index: Int,
    totalCount: Int,
    orbitPhaseState: State<Float>,
    parallaxState: State<Pair<Float, Float>>,
    metrics: OrbitMetrics,
    reducedMotion: Boolean
) {
    val density = LocalDensity.current
    val baseAngle = remember(index, totalCount) {
        (index.toFloat() / totalCount.toFloat()) * (2f * PI.toFloat())
    }
    val rimBrush = remember(app.id) {
        Brush.linearGradient(
            colors = listOf(
                app.primaryColor,
                app.secondaryColor,
                Color.White.copy(alpha = 0.45f)
            )
        )
    }
    val shadowBrush = remember(app.id) {
        Brush.radialGradient(
            colors = listOf(
                app.primaryColor.copy(alpha = 0.55f),
                Color.Transparent
            )
        )
    }
    val labelBorder = remember(app.id) {
        BorderStroke(0.6.dp, app.secondaryColor.copy(alpha = 0.4f))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val orbitPhase = orbitPhaseState.value
                val (tiltX, tiltY) = parallaxState.value
                val angle = orbitPhase + baseAngle
                val sinVal = sin(angle)
                val cosVal = cos(angle)
                val depthNorm = (cosVal + 1f) * 0.5f
                val floatOffset = if (reducedMotion) {
                    0f
                } else {
                    sin(orbitPhase * 2f + index * 1.25f) * metrics.floatAmpPx
                }
                val xPos = metrics.centerX + sinVal * metrics.rx + tiltX * metrics.parallaxXPx * (0.4f + depthNorm)
                val yPos = metrics.centerY + (1f - depthNorm) * metrics.ry * 1.4f - depthNorm * metrics.ry * 0.4f +
                    floatOffset + tiltY * metrics.parallaxYPx * (0.4f + depthNorm)

                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(
                        x = xPos.roundToInt(),
                        y = yPos.roundToInt(),
                        zIndex = depthNorm * 10f
                    )
                }
            }
            .width(74.dp)
            .graphicsLayer {
                val orbitPhase = orbitPhaseState.value
                val (tiltX, _) = parallaxState.value
                val angle = orbitPhase + baseAngle
                val sinVal = sin(angle)
                val cosVal = cos(angle)
                val depthNorm = (cosVal + 1f) * 0.5f
                val scale = 0.68f + 0.36f * depthNorm
                scaleX = scale
                scaleY = scale
                rotationY = -sinVal * 26f + tiltX * 6f
                cameraDistance = 12f * density.density
                alpha = 0.48f + 0.52f * depthNorm
            }
            .testTag("showcase_3d_item_${app.id}")
    ) {
        // 3D Floating Frosted Tile with Glow Rim & Reflection
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TileSurfaceBrush)
                .border(
                    width = 1.4.dp,
                    brush = rimBrush,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val sheenCenter = Offset(size.width * 0.3f, size.height * 0.25f)
                        val sheenRadius = (size.width * 0.7f).coerceAtLeast(1f)
                        val sheenBrush = Brush.radialGradient(
                            colors = listOf(
                                app.primaryColor.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            center = sheenCenter,
                            radius = sheenRadius
                        )
                        onDrawBehind {
                            drawCircle(brush = sheenBrush, radius = sheenRadius, center = sheenCenter)
                        }
                    }
            )

            ShowcaseAppGlyph(
                app = app,
                modifier = Modifier.size(34.dp)
            )
        }

        // Ground shadow / reflection beneath the 3D icon
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(34.dp)
                .height(4.dp)
                .background(shadowBrush, shape = CircleShape)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF060C1A).copy(alpha = 0.85f),
            border = labelBorder
        ) {
            Text(
                text = app.displayName,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ShowcaseAppGlyph(
    app: Showcase3DApp,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            when (app.id) {
                "facebook" -> {
                    val bgBrush = Brush.linearGradient(listOf(Color(0xFF1877F2), Color(0xFF0EA5E9)))
                    val fStem = Path().apply {
                        moveTo(w * 0.56f, h * 0.80f)
                        lineTo(w * 0.56f, h * 0.36f)
                        quadraticTo(w * 0.56f, h * 0.24f, w * 0.68f, h * 0.25f)
                    }
                    val stemStroke = Stroke(width = w * 0.13f, cap = StrokeCap.Round)
                    val bgRadius = w * 0.44f
                    val bgCenter = Offset(w * 0.50f, h * 0.50f)
                    val crossStart = Offset(w * 0.42f, h * 0.50f)
                    val crossEnd = Offset(w * 0.68f, h * 0.50f)
                    val crossWidth = w * 0.12f
                    onDrawBehind {
                        drawCircle(brush = bgBrush, radius = bgRadius, center = bgCenter)
                        drawPath(path = fStem, color = Color.White, style = stemStroke)
                        drawLine(
                            color = Color.White,
                            start = crossStart,
                            end = crossEnd,
                            strokeWidth = crossWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                "chatgpt" -> {
                    val r = size.minDimension * 0.42f
                    val cx = w / 2f
                    val cy = h / 2f
                    val ringBrush = Brush.linearGradient(listOf(app.primaryColor, app.secondaryColor))
                    val ringStroke = Stroke(width = size.minDimension * 0.11f)
                    val lineStrokeWidth = size.minDimension * 0.09f
                    val segments = Array(6) { i ->
                        val a = (i * 60f) * (PI / 180f).toFloat()
                        val x1 = cx + cos(a) * (r * 0.35f)
                        val y1 = cy + sin(a) * (r * 0.35f)
                        val x2 = cx + cos(a + 0.65f) * (r * 0.88f)
                        val y2 = cy + sin(a + 0.65f) * (r * 0.88f)
                        Offset(x1, y1) to Offset(x2, y2)
                    }
                    val center = Offset(cx, cy)
                    onDrawBehind {
                        drawCircle(brush = ringBrush, radius = r, center = center, style = ringStroke)
                        for (pair in segments) {
                            drawLine(
                                color = Color.White,
                                start = pair.first,
                                end = pair.second,
                                strokeWidth = lineStrokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                "youtube" -> {
                    val boxBrush = Brush.linearGradient(listOf(Color(0xFFFF1744), Color(0xFFD50000)))
                    val topLeft = Offset(w * 0.06f, h * 0.18f)
                    val rectSize = Size(w * 0.88f, h * 0.64f)
                    val cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
                    val playPath = Path().apply {
                        moveTo(w * 0.41f, h * 0.35f)
                        lineTo(w * 0.68f, h * 0.50f)
                        lineTo(w * 0.41f, h * 0.65f)
                        close()
                    }
                    onDrawBehind {
                        drawRoundRect(
                            brush = boxBrush,
                            topLeft = topLeft,
                            size = rectSize,
                            cornerRadius = cornerRadius
                        )
                        drawPath(path = playPath, color = Color.White)
                    }
                }

                "instagram" -> {
                    val frameBrush = Brush.linearGradient(
                        listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF))
                    )
                    val topLeft = Offset(w * 0.10f, h * 0.10f)
                    val rectSize = Size(w * 0.80f, h * 0.80f)
                    val cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
                    val frameStroke = Stroke(width = w * 0.11f)
                    val lensCenter = Offset(w * 0.50f, h * 0.50f)
                    val lensRadius = w * 0.20f
                    val lensStroke = Stroke(width = w * 0.10f)
                    val dotCenter = Offset(w * 0.72f, h * 0.28f)
                    val dotRadius = w * 0.055f
                    onDrawBehind {
                        drawRoundRect(
                            brush = frameBrush,
                            topLeft = topLeft,
                            size = rectSize,
                            cornerRadius = cornerRadius,
                            style = frameStroke
                        )
                        drawCircle(
                            color = Color.White,
                            radius = lensRadius,
                            center = lensCenter,
                            style = lensStroke
                        )
                        drawCircle(
                            color = Color.White,
                            radius = dotRadius,
                            center = dotCenter
                        )
                    }
                }

                else -> { // WhatsApp
                    val bubbleBrush = Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E)))
                    val center = Offset(w * 0.50f, h * 0.48f)
                    val radius = w * 0.42f
                    val tailPath = Path().apply {
                        moveTo(w * 0.24f, h * 0.74f)
                        lineTo(w * 0.14f, h * 0.90f)
                        lineTo(w * 0.36f, h * 0.84f)
                        close()
                    }
                    val handsetPath = Path().apply {
                        moveTo(w * 0.36f, h * 0.34f)
                        quadraticTo(w * 0.36f, h * 0.62f, w * 0.65f, h * 0.63f)
                    }
                    val handsetStroke = Stroke(
                        width = w * 0.12f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    onDrawBehind {
                        drawCircle(brush = bubbleBrush, radius = radius, center = center)
                        drawPath(path = tailPath, color = Color(0xFF128C7E))
                        drawPath(path = handsetPath, color = Color.White, style = handsetStroke)
                    }
                }
            }
        }
    )
}

/**
 * Registers lightweight hardware orientation parallax only while the screen is resumed,
 * falling back cleanly to (0f, 0f) when unavailable or when reduced motion is enabled.
 */
@Composable
private fun rememberShowcaseParallax(enabled: Boolean): State<Pair<Float, Float>> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tiltState = remember { mutableStateOf(0f to 0f) }

    DisposableEffect(context, lifecycleOwner, enabled) {
        if (!enabled) {
            tiltState.value = 0f to 0f
            return@DisposableEffect onDispose { }
        }

        val sensorManager = runCatching {
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }.getOrNull()
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        var baselineX: Float? = null
        var baselineY: Float? = null
        var smoothX = 0f
        var smoothY = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rawX: Float
                val rawY: Float
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    rawX = (event.values.getOrNull(1) ?: 0f) * 0.35f
                    rawY = (event.values.getOrNull(0) ?: 0f) * 0.35f
                } else {
                    val v0 = event.values.getOrNull(0) ?: 0f
                    val v1 = event.values.getOrNull(1) ?: 0f
                    if (baselineX == null) baselineX = v1
                    if (baselineY == null) baselineY = v0
                    rawX = ((v1 - (baselineX ?: v1)) * 3.5f).coerceIn(-1f, 1f)
                    rawY = ((v0 - (baselineY ?: v0)) * 3.5f).coerceIn(-1f, 1f)
                }
                val nextX = (smoothX * 0.84f + rawX * 0.16f).coerceIn(-1f, 1f)
                val nextY = (smoothY * 0.84f + rawY * 0.16f).coerceIn(-1f, 1f)
                // Skip unnecessary state updates for imperceptible sub-pixel sensor jitter
                if (kotlin.math.abs(nextX - smoothX) > 0.004f || kotlin.math.abs(nextY - smoothY) > 0.004f) {
                    smoothX = nextX
                    smoothY = nextY
                    tiltState.value = nextX to nextY
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun registerSensor() {
            runCatching {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        fun unregisterSensor() {
            runCatching { sensorManager.unregisterListener(listener) }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerSensor()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerSensor()
                Lifecycle.Event.ON_PAUSE -> unregisterSensor()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterSensor()
        }
    }

    return tiltState
}
