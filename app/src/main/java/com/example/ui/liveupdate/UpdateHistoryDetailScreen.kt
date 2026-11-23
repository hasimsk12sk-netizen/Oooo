package com.example.ui.liveupdate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.liveupdate.LiveUpdateApkVerifier
import com.example.data.model.HistoryApkDownloadStatus
import com.example.data.model.HistoryDownloadState
import com.example.data.model.UpdateHistoryRecord
import com.example.data.repository.LiveUpdateRepository
import com.example.ui.admin.AdminAuthManager
import com.example.ui.theme.StoreProBackground
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DetailTimestampFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)

@Composable
fun UpdateHistoryDetailScreen(
    record: UpdateHistoryRecord,
    downloadState: HistoryDownloadState,
    liveUpdateRepository: LiveUpdateRepository,
    onDownloadApk: (UpdateHistoryRecord) -> Unit,
    onCancelDownload: (String) -> Unit,
    onLaunchInstall: (UpdateHistoryRecord) -> Result<Boolean>,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
    onStatusMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val installed = remember(context) { LiveUpdateApkVerifier.readInstalledAppDetails(context) }

    LaunchedEffect(Unit) {
        if (!AdminAuthManager.isFullyAuthorizedAdmin(context, Firebase.auth.currentUser)) {
            onUnauthorized()
        }
    }

    var isVerifyingReachability by remember { mutableStateOf(false) }
    var apkReachable by remember(record.historyId, record.apkAvailable) {
        mutableStateOf(record.apkAvailable && record.apkDownloadUrl.isNotBlank())
    }
    var downgradeWarningMessage by remember { mutableStateOf<String?>(null) }

    val hasLocalFile = remember(downloadState.localApkPath, downloadState.status) {
        val path = downloadState.localApkPath
        !path.isNullOrBlank() && File(path).exists() && File(path).length() > 0L
    }

    fun handleInstallClicked() {
        val path = downloadState.localApkPath
        if (path.isNullOrBlank() || !File(path).exists()) {
            onStatusMessage("Download the v${record.versionName} APK first.")
            return
        }
        val check = LiveUpdateApkVerifier.verifyHistoricalInstallCompatibility(
            context = context,
            downloadedFile = File(path),
            historyRecord = record,
            installed = installed
        )
        if (!check.isValid) {
            val downgradeErr = check.errors.firstOrNull { it.contains("downgrade", ignoreCase = true) }
            if (downgradeErr != null) {
                downgradeWarningMessage = downgradeErr
            } else {
                onStatusMessage(check.errors.firstOrNull() ?: "Downloaded APK compatibility verification failed.")
            }
            return
        }
        onLaunchInstall(record).onSuccess { launched ->
            if (!launched) {
                onStatusMessage("Enable \"Allow from this source\" for STORE PRO, then tap Install again.")
            }
        }.onFailure { err ->
            onStatusMessage(err.localizedMessage ?: "Unable to launch package installer.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StoreProBackground)
            .testTag("update_history_detail_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoreProCardBackground)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("history_detail_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "v${record.versionName} (Build ${record.versionCode})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Archived Release Record",
                    fontSize = 11.5.sp,
                    color = StoreProSecondaryCyan
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Release Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                border = BorderStroke(1.dp, StoreProCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryBadge(
                            text = if (record.mandatoryUpdate) "Mandatory Update" else "Optional Update",
                            color = if (record.mandatoryUpdate) Color(0xFFFBBF24) else StoreProSecondaryCyan
                        )
                        HistoryBadge(
                            text = if (apkReachable) "APK Available" else "APK Unavailable",
                            color = if (apkReachable) StoreProPrimaryGreen else Color(0xFFF87171)
                        )
                    }

                    HorizontalDivider(color = StoreProCardBorder)

                    HistoryDetailRow("Version Name", record.versionName)
                    HistoryDetailRow("Build Code", record.versionCode.toString())
                    HistoryDetailRow("Package ID", record.packageName.ifBlank { "com.aistudio.storepro.vqxknp" })
                    HistoryDetailRow("Previous Version", record.previousVersion.ifBlank { "—" })
                    HistoryDetailRow(
                        "Published Date",
                        if (record.releaseTimestamp > 0L) {
                            synchronized(DetailTimestampFormatter) { DetailTimestampFormatter.format(Date(record.releaseTimestamp)) }
                        } else {
                            listOf(record.releaseDate, record.publicationTime)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { "—" }
                        }
                    )
                    HistoryDetailRow("Admin Email", record.adminEmail.ifBlank { "—" })
                    HistoryDetailRow("APK File Name", record.apkFileName.ifBlank { "—" })
                    HistoryDetailRow("APK Size", formatApkBytes(record.apkFileSize))
                    HistoryDetailRow("Storage Path", record.apkStoragePath.ifBlank { "External URL" })
                    HistoryDetailRow("Signing Cert", record.signingCertificateFingerprint.take(24).ifBlank { "—" })
                }
            }

            // Changelog Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                border = BorderStroke(1.dp, StoreProCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Release Notes / Changelog",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoreProSecondaryCyan
                    )
                    Text(
                        text = record.releaseNotes.ifBlank { record.changelog }.ifBlank { "No changelog documented for this release." },
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 19.sp
                    )
                }
            }

            // Verification & Exact APK Re-Download Controls
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                border = BorderStroke(1.dp, StoreProCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Preserved Binary Actions",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isVerifyingReachability = true
                                val reachable = liveUpdateRepository.verifyApkUrlReachable(record.apkDownloadUrl)
                                liveUpdateRepository.updateHistoryApkAvailability(record.historyId, reachable)
                                apkReachable = reachable
                                isVerifyingReachability = false
                                onStatusMessage(
                                    if (reachable) "APK binary verified reachable in Firebase Storage."
                                    else "APK URL could not be reached right now."
                                )
                            }
                        },
                        enabled = !isVerifyingReachability && record.apkDownloadUrl.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StoreProSecondaryCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("history_verify_apk_button")
                    ) {
                        if (isVerifyingReachability) {
                            CircularProgressIndicator(color = StoreProSecondaryCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying Storage Binary...", color = StoreProSecondaryCyan)
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StoreProSecondaryCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify APK Availability", color = StoreProSecondaryCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (downloadState.status == HistoryApkDownloadStatus.DOWNLOADING) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                                color = StoreProPrimaryGreen,
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading APK (${downloadState.progressPercent}%)",
                                    fontSize = 12.sp,
                                    color = StoreProPrimaryGreen
                                )
                                TextButton(onClick = { onCancelDownload(record.historyId) }) {
                                    Text("Cancel", color = Color(0xFFFCA5A5))
                                }
                            }
                        }
                    }

                    if (!downloadState.errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF7F1D1D),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = downloadState.errorMessage, fontSize = 12.sp, color = Color(0xFFFCA5A5))
                            }
                        }
                    }

                    if (record.apkDownloadUrl.isNotBlank() &&
                        downloadState.status != HistoryApkDownloadStatus.DOWNLOADING
                    ) {
                        Button(
                            onClick = { onDownloadApk(record) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StoreProSecondaryCyan,
                                contentColor = Color(0xFF06202B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("history_download_apk_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasLocalFile) "Re-Download Exact v${record.versionName} APK" else "Download v${record.versionName} APK",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (hasLocalFile || downloadState.status == HistoryApkDownloadStatus.READY) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF064E3B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StoreProPrimaryGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verified APK downloaded on device and ready for compatibility check.",
                                    fontSize = 11.5.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Button(
                            onClick = { handleInstallClicked() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StoreProPrimaryGreen,
                                contentColor = Color(0xFF003816)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("history_install_apk_button")
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify & Install Historical APK", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    val warningText = downgradeWarningMessage
    if (warningText != null) {
        AlertDialog(
            onDismissRequest = { downgradeWarningMessage = null },
            containerColor = StoreProCardBackground,
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            title = {
                Text("Android Version Downgrade Protection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(text = warningText, fontSize = 13.sp, lineHeight = 19.sp)
            },
            confirmButton = {
                TextButton(
                    onClick = { downgradeWarningMessage = null },
                    modifier = Modifier.testTag("history_downgrade_ok_button")
                ) {
                    Text("Understood", color = StoreProSecondaryCyan, fontWeight = FontWeight.Bold)
                }
            },
            modifier = Modifier.testTag("history_downgrade_warning_dialog")
        )
    }
}

@Composable
private fun HistoryDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
