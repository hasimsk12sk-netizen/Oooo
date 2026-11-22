package com.example.ui.liveupdate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveUpdateDashboardState
import com.example.data.model.LiveUpdateStatus
import com.example.ui.theme.StoreProBackground
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan

@Composable
fun LiveUpdateDashboardScreen(
    state: LiveUpdateDashboardState,
    canAccessAdminTools: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenAdminManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StoreProBackground)
            .testTag("live_update_dashboard_screen")
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoreProCardBackground)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("live_update_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "STORE PRO LIVE UPDATE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "Live Release & Verification Center",
                        fontSize = 11.5.sp,
                        color = StoreProSecondaryCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !state.isChecking && state.status != LiveUpdateStatus.DOWNLOADING,
                modifier = Modifier.size(48.dp).testTag("live_update_refresh_button")
            ) {
                if (state.isChecking) {
                    CircularProgressIndicator(
                        color = StoreProSecondaryCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Check Updates",
                        tint = StoreProSecondaryCyan
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LiveStatusHeroCard(
                    state = state,
                    onStartDownload = onStartDownload,
                    onCancelDownload = onCancelDownload,
                    onRetryDownload = onRetryDownload,
                    onInstallUpdate = onInstallUpdate
                )

                // Responsive 2-column summary on tablets/landscape, stacked on phones
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 600.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InstalledVersionCard(
                                installed = state.installed,
                                modifier = Modifier.weight(1f)
                            )
                            LatestAvailableCard(
                                latestRelease = state.latestRelease,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InstalledVersionCard(installed = state.installed)
                            LatestAvailableCard(latestRelease = state.latestRelease)
                        }
                    }
                }

                VersionComparisonTableCard(state = state)

                ReleaseNotesAndHistorySection(
                    latestRelease = state.latestRelease,
                    history = state.publishedHistory,
                    installedVersionCode = state.installed.versionCode
                )

                // Strictly Admin-Only Management Launcher
                if (canAccessAdminTools) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF102033)),
                        border = BorderStroke(1.dp, StoreProPrimaryGreen.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Release Studio",
                                    tint = StoreProPrimaryGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Administrator Live Update Studio",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Upload signed STORE PRO APKs, verify package signatures, manage drafts, and publish updates.",
                                fontSize = 12.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenAdminManager,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StoreProPrimaryGreen,
                                    contentColor = Color(0xFF003816)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_admin_live_update_button")
                            ) {
                                Text(
                                    text = "Open Admin Release Management",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun LiveStatusHeroCard(
    state: LiveUpdateDashboardState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    val accent = when (state.status) {
        LiveUpdateStatus.UPDATE_AVAILABLE,
        LiveUpdateStatus.DOWNLOADING,
        LiveUpdateStatus.DOWNLOAD_COMPLETED,
        LiveUpdateStatus.READY_TO_INSTALL -> StoreProPrimaryGreen
        LiveUpdateStatus.FAILED -> Color(0xFFFF6B6B)
        else -> StoreProSecondaryCyan
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("live_update_status_card")
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF132238),
                            StoreProCardBackground
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val heroIcon = when (state.status) {
                    LiveUpdateStatus.UP_TO_DATE,
                    LiveUpdateStatus.DOWNLOAD_COMPLETED -> Icons.Default.CheckCircle
                    LiveUpdateStatus.READY_TO_INSTALL -> Icons.Default.InstallMobile
                    LiveUpdateStatus.FAILED -> Icons.Default.ErrorOutline
                    else -> Icons.Default.SystemUpdate
                }
                Icon(
                    imageVector = heroIcon,
                    contentDescription = state.status.label,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.status.label,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.testTag("live_update_status_text")
                    )
                    if (!state.statusDetail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.statusDetail,
                            fontSize = 12.5.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            if (state.status == LiveUpdateStatus.DOWNLOADING || state.status == LiveUpdateStatus.DOWNLOAD_COMPLETED) {
                Spacer(modifier = Modifier.height(14.dp))
                val progressFraction = (state.downloadPercent / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = StoreProPrimaryGreen,
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .testTag("live_update_progress_bar")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val bytesSummary = if (state.totalBytes > 0L) {
                        "${formatCompactMb(state.downloadedBytes)} / ${formatCompactMb(state.totalBytes)}"
                    } else {
                        "${formatCompactMb(state.downloadedBytes)} downloaded"
                    }
                    Text(
                        text = bytesSummary,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.testTag("live_update_bytes_text")
                    )
                    Text(
                        text = "${state.downloadPercent}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StoreProPrimaryGreen,
                        modifier = Modifier.testTag("live_update_percent_text")
                    )
                }
                if (state.status == LiveUpdateStatus.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onCancelDownload,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("live_update_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Download",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Download", color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.status == LiveUpdateStatus.UPDATE_AVAILABLE) {
                Spacer(modifier = Modifier.height(14.dp))
                val sizeTag = formatCompactMb(state.latestRelease?.apkSize ?: 0L)
                Button(
                    onClick = onStartDownload,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StoreProPrimaryGreen,
                        contentColor = Color(0xFF003816)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("live_update_download_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Update",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if ((state.latestRelease?.apkSize ?: 0L) > 0L) {
                            "Download Update ($sizeTag)"
                        } else {
                            "Download Update"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }

            if (state.status == LiveUpdateStatus.READY_TO_INSTALL) {
                Spacer(modifier = Modifier.height(14.dp))
                val buttonText = if (state.hasInstallPermission) {
                    "Install Update"
                } else {
                    "Allow App Installs & Continue"
                }
                Button(
                    onClick = onInstallUpdate,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StoreProSecondaryCyan,
                        contentColor = Color(0xFF00363D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("live_update_install_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.InstallMobile,
                        contentDescription = buttonText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            if (state.status == LiveUpdateStatus.FAILED) {
                if (!state.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.errorMessage,
                        fontSize = 12.5.sp,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.testTag("live_update_error_text")
                    )
                }
                if (state.latestRelease != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRetryDownload,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StoreProSecondaryCyan,
                            contentColor = Color(0xFF00363D)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("live_update_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
