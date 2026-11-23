package com.example.ui.liveupdate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveUpdateDashboardState
import com.example.data.model.LiveUpdateStatus
import com.example.ui.components.RgbNeonPalette
import com.example.ui.components.rememberLifecycleIsResumed
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan

@Composable
fun LiveUpdateButton(
    state: LiveUpdateDashboardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isResumed = rememberLifecycleIsResumed()
    val reducedMotion = remember(context) { RgbNeonPalette.isReducedMotionEnabled(context) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "live_update_press_scale"
    )

    val hasActionableUpdate = state.status == LiveUpdateStatus.UPDATE_AVAILABLE ||
        state.status == LiveUpdateStatus.DOWNLOADING ||
        state.status == LiveUpdateStatus.READY_TO_INSTALL ||
        state.status == LiveUpdateStatus.DOWNLOAD_COMPLETED

    val pulseAlphaState = if (hasActionableUpdate && isResumed && !reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "live_update_pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "live_update_dot_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0.85f) }
    }

    val accentColor by animateColorAsState(
        targetValue = when (state.status) {
            LiveUpdateStatus.UPDATE_AVAILABLE,
            LiveUpdateStatus.DOWNLOADING,
            LiveUpdateStatus.DOWNLOAD_COMPLETED,
            LiveUpdateStatus.READY_TO_INSTALL -> StoreProPrimaryGreen
            LiveUpdateStatus.FAILED -> Color(0xFFFF6B6B)
            else -> StoreProSecondaryCyan
        },
        label = "live_update_accent"
    )

    val subtitleText = when (state.status) {
        LiveUpdateStatus.UPDATE_AVAILABLE ->
            "New v${state.latestRelease?.versionName ?: ""} available • Tap to view"
        LiveUpdateStatus.DOWNLOADING ->
            "Downloading update (${state.downloadPercent}%)"
        LiveUpdateStatus.DOWNLOAD_COMPLETED ->
            "Download completed • Verifying package"
        LiveUpdateStatus.READY_TO_INSTALL ->
            "Ready to install v${state.latestRelease?.versionName ?: ""}"
        LiveUpdateStatus.CHECKING ->
            "Checking live release status..."
        LiveUpdateStatus.FAILED ->
            "Update check/download attention needed"
        LiveUpdateStatus.UP_TO_DATE ->
            "Installed v${state.installed.versionName} (Build ${state.installed.versionCode}) • Up to date"
    }

    val badgeLabel = when (state.status) {
        LiveUpdateStatus.UPDATE_AVAILABLE -> "NEW v${state.latestRelease?.versionName ?: ""}"
        LiveUpdateStatus.DOWNLOADING -> "${state.downloadPercent}%"
        LiveUpdateStatus.READY_TO_INSTALL -> "INSTALL"
        LiveUpdateStatus.FAILED -> "RETRY"
        else -> "LIVE"
    }

    val leadingIcon = when (state.status) {
        LiveUpdateStatus.READY_TO_INSTALL -> Icons.Default.InstallMobile
        LiveUpdateStatus.UP_TO_DATE -> Icons.Default.CloudDone
        LiveUpdateStatus.FAILED -> Icons.Default.ErrorOutline
        else -> Icons.Default.SystemUpdate
    }

    val shape = remember { RoundedCornerShape(18.dp) }
    val borderBrush = remember(hasActionableUpdate) {
        Brush.horizontalGradient(
            colors = listOf(
                StoreProSecondaryCyan.copy(alpha = if (hasActionableUpdate) 0.9f else 0.55f),
                StoreProPrimaryGreen.copy(alpha = if (hasActionableUpdate) 0.95f else 0.55f)
            )
        )
    }
    val surfaceBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF0F172A),
                Color(0xFF132238),
                Color(0xFF0E1D2F)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .scale(pressScale)
            .clip(shape)
            .background(surfaceBrush)
            .border(width = 1.5.dp, brush = borderBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = accentColor),
                onClick = onClick
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("live_update_entry_button")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    StoreProSecondaryCyan.copy(alpha = 0.22f),
                                    StoreProPrimaryGreen.copy(alpha = 0.22f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            accentColor.copy(alpha = 0.45f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = "STORE PRO LIVE UPDATE",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    if (hasActionableUpdate) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(8.dp)
                                .graphicsLayer {
                                    alpha = pulseAlphaState.value
                                }
                                .clip(CircleShape)
                                .background(StoreProPrimaryGreen)
                                .testTag("live_update_availability_dot")
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STORE PRO LIVE UPDATE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleText,
                        fontSize = 11.5.sp,
                        color = if (hasActionableUpdate) accentColor else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.border(
                        1.dp,
                        accentColor.copy(alpha = 0.45f),
                        RoundedCornerShape(50)
                    )
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open Live Update Center",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
