package com.example.ui.liveupdate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InstalledAppDetails
import com.example.data.model.LiveUpdateDashboardState
import com.example.data.model.LiveUpdateRelease
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan

@Composable
internal fun InstalledVersionCard(
    installed: InstalledAppDetails,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(1.dp, StoreProSecondaryCyan.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_update_installed_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Installed App",
                    tint = StoreProSecondaryCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Current Installed Version",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "v${installed.versionName} (Build ${installed.versionCode})",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = StoreProSecondaryCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow("App Name", installed.appName)
            MetricRow("Status", "Currently Installed", StoreProPrimaryGreen)
            MetricRow("Package", installed.packageName)
            MetricRow("Android OS", "Android ${installed.androidVersion} (API ${installed.apiLevel})")
        }
    }
}

@Composable
internal fun LatestAvailableCard(
    latestRelease: LiveUpdateRelease?,
    modifier: Modifier = Modifier
) {
    val isAvailable = latestRelease != null && latestRelease.published
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(
            1.dp,
            if (isAvailable) StoreProPrimaryGreen.copy(alpha = 0.45f) else StoreProCardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_update_latest_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Latest Available Version",
                    tint = StoreProPrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Latest Available Version",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isAvailable) {
                    "v${latestRelease!!.versionName} (Build ${latestRelease.versionCode})"
                } else {
                    "Not Published"
                },
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isAvailable) StoreProPrimaryGreen else Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow("Release Date", latestRelease?.releaseDate?.ifBlank { "—" } ?: "—")
            MetricRow(
                label = "Release Status",
                value = if (isAvailable) "Available" else "Not Published",
                valueColor = if (isAvailable) StoreProPrimaryGreen else Color(0xFF94A3B8)
            )
            MetricRow("APK Size", formatApkBytes(latestRelease?.apkSize ?: 0L))
            MetricRow(
                label = "Notes",
                value = latestRelease?.releaseNotes?.take(90)?.ifBlank { "—" } ?: "No published release yet"
            )
        }
    }
}

@Composable
internal fun VersionComparisonTableCard(state: LiveUpdateDashboardState) {
    val latest = state.latestRelease
    val rows = listOf(
        "App Name" to state.installed.appName,
        "Installed Version" to state.installed.versionName,
        "Installed Build" to state.installed.versionCode.toString(),
        "Latest Version" to (latest?.versionName?.ifBlank { "Not Published" } ?: "Not Published"),
        "Latest Build" to (latest?.versionCode?.takeIf { it > 0L }?.toString() ?: "—"),
        "Update Status" to state.status.label,
        "Release Date" to (latest?.releaseDate?.ifBlank { "—" } ?: "—"),
        "APK Size" to formatApkBytes(latest?.apkSize ?: 0L),
        "Package Name" to state.installed.packageName
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(1.dp, StoreProCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_update_comparison_table")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = "Version Comparison Table",
                    tint = StoreProSecondaryCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Version Information Comparison",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) Color(0xFF0F172A) else Color(0xFF131D33),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.weight(0.45f)
                    )
                    Text(
                        text = value,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            label == "Update Status" && state.status == com.example.data.model.LiveUpdateStatus.UPDATE_AVAILABLE -> StoreProPrimaryGreen
                            label == "Update Status" && state.status == com.example.data.model.LiveUpdateStatus.FAILED -> Color(0xFFFF6B6B)
                            else -> Color.White
                        },
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.55f)
                    )
                }
                if (index < rows.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
internal fun ReleaseNotesAndHistorySection(
    latestRelease: LiveUpdateRelease?,
    history: List<LiveUpdateRelease>,
    installedVersionCode: Long
) {
    if (latestRelease != null && latestRelease.releaseNotes.isNotBlank()) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
            border = BorderStroke(1.dp, StoreProCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Release Notes",
                        tint = StoreProPrimaryGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Release Notes • v${latestRelease.versionName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = latestRelease.releaseNotes,
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 20.sp
                )
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(1.dp, StoreProCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_update_release_history")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Release History",
                    tint = StoreProSecondaryCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Published Release History",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (history.isEmpty()) {
                Text(
                    text = "No published live releases found yet.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            } else {
                history.forEachIndexed { idx, rel ->
                    val isInstalled = rel.versionCode == installedVersionCode
                    val isLatest = idx == 0
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isLatest) StoreProPrimaryGreen.copy(alpha = 0.35f) else StoreProCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${rel.versionName} (Build ${rel.versionCode})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isInstalled) {
                                    SmallTagChip("INSTALLED", StoreProSecondaryCyan)
                                }
                                if (isLatest) {
                                    SmallTagChip("LATEST", StoreProPrimaryGreen)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${rel.releaseDate.ifBlank { "Published" }} • ${formatCompactMb(rel.apkSize)}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                        if (rel.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = StoreProCardBorder)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = rel.releaseNotes,
                                fontSize = 12.5.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 18.sp
                            )
                        }
                    }
                    if (idx < history.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFFE2E8F0)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f)
        )
    }
}

@Composable
internal fun SmallTagChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}
