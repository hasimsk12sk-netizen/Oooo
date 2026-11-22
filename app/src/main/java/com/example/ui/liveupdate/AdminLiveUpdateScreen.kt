package com.example.ui.liveupdate

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.liveupdate.ApkVerificationResult
import com.example.data.liveupdate.LiveUpdateApkVerifier
import com.example.data.model.InspectedApkMetadata
import com.example.data.model.LiveReleaseStatus
import com.example.data.model.LiveUpdateRelease
import com.example.data.repository.DeviceAndUpdateRepository
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

@Composable
fun AdminLiveUpdateScreen(
    allReleases: List<LiveUpdateRelease>,
    liveUpdateRepository: LiveUpdateRepository,
    deviceUpdateRepository: DeviceAndUpdateRepository,
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
        } else {
            deviceUpdateRepository.syncAdminRoleIfAuthorized()
        }
    }

    val publishedReleases = remember(allReleases) {
        allReleases.filter { it.published && it.releaseStatus == LiveReleaseStatus.PUBLISHED.name }
            .sortedByDescending { it.versionCode }
    }
    val currentPublished = publishedReleases.firstOrNull()
    val highestPublishedVersionCode = maxOf(
        installed.versionCode,
        currentPublished?.versionCode ?: 0L
    )

    var editingReleaseId by remember { mutableStateOf<String?>(null) }
    var versionName by remember { mutableStateOf("") }
    var versionCodeInput by remember { mutableStateOf("") }
    var apkDownloadUrl by remember { mutableStateOf("") }
    var apkSizeBytes by remember { mutableStateOf(0L) }
    var releaseNotes by remember { mutableStateOf("") }
    var releaseDate by remember { mutableStateOf(todayIsoDate()) }
    var selectedStatus by remember { mutableStateOf(LiveReleaseStatus.PUBLISHED) }
    var storagePath by remember { mutableStateOf("") }
    var apkSha256 by remember { mutableStateOf("") }
    var signingSha256 by remember { mutableStateOf("") }

    var inspectedApk by remember { mutableStateOf<InspectedApkMetadata?>(null) }
    var verificationResult by remember { mutableStateOf<ApkVerificationResult?>(null) }
    var verifiedDraftVersionName by remember { mutableStateOf<String?>(null) }
    var verifiedDraftVersionCode by remember { mutableStateOf<Long?>(null) }
    var isInspecting by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    fun recomputeVerification(
        inspected: InspectedApkMetadata?,
        verName: String,
        verCodeStr: String
    ) {
        if (inspected == null) {
            verificationResult = null
            return
        }
        val parsedCode = verCodeStr.trim().toLongOrNull() ?: 0L
        verificationResult = LiveUpdateApkVerifier.verifyForAdminPublish(
            inspected = inspected,
            installed = installed,
            enteredVersionName = verName,
            enteredVersionCode = parsedCode,
            highestPublishedVersionCode = highestPublishedVersionCode
        )
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isInspecting = true
            formError = null
            val res = LiveUpdateApkVerifier.inspectPickedUri(context, uri)
            isInspecting = false
            res.onSuccess { meta ->
                inspectedApk = meta
                apkSizeBytes = meta.fileSizeBytes
                apkSha256 = meta.apkSha256
                signingSha256 = meta.signingSha256Set.firstOrNull().orEmpty()
                if (versionName.isBlank() && meta.versionName.isNotBlank()) {
                    versionName = meta.versionName
                }
                if (versionCodeInput.isBlank() && meta.versionCode > 0L) {
                    versionCodeInput = meta.versionCode.toString()
                }
                apkDownloadUrl = ""
                storagePath = ""
                verifiedDraftVersionName = null
                verifiedDraftVersionCode = null
                recomputeVerification(meta, versionName, versionCodeInput)
            }.onFailure { err ->
                formError = err.localizedMessage ?: "Unable to read selected APK file."
            }
        }
    }

    fun resetForm() {
        editingReleaseId = null
        versionName = ""
        versionCodeInput = ""
        apkDownloadUrl = ""
        apkSizeBytes = 0L
        releaseNotes = ""
        releaseDate = todayIsoDate()
        selectedStatus = LiveReleaseStatus.PUBLISHED
        storagePath = ""
        apkSha256 = ""
        signingSha256 = ""
        inspectedApk = null
        verificationResult = null
        verifiedDraftVersionName = null
        verifiedDraftVersionCode = null
        uploadProgress = 0
        formError = null
    }

    fun loadReleaseIntoForm(release: LiveUpdateRelease) {
        editingReleaseId = release.releaseId
        versionName = release.versionName
        versionCodeInput = release.versionCode.toString()
        apkDownloadUrl = release.apkDownloadUrl
        apkSizeBytes = release.apkSize
        releaseNotes = release.releaseNotes
        releaseDate = release.releaseDate.ifBlank { todayIsoDate() }
        selectedStatus = if (release.releaseStatus == LiveReleaseStatus.DRAFT.name) {
            LiveReleaseStatus.DRAFT
        } else {
            LiveReleaseStatus.PUBLISHED
        }
        storagePath = release.storagePath
        apkSha256 = release.apkSha256
        signingSha256 = release.signingSha256
        inspectedApk = null
        verificationResult = null
        verifiedDraftVersionName = release.versionName.takeIf { release.apkSha256.isNotBlank() }
        verifiedDraftVersionCode = release.versionCode.takeIf { release.apkSha256.isNotBlank() }
        formError = null
    }

    fun submitRelease() {
        val codeLong = versionCodeInput.trim().toLongOrNull()
        val cleanName = versionName.trim()
        val cleanDate = releaseDate.trim()
        val cleanNotes = releaseNotes.trim()

        if (cleanName.isBlank()) {
            formError = "Enter Version Name."
            return
        }
        if (codeLong == null || codeLong <= 0L) {
            formError = "Enter a valid positive integer Version Code."
            return
        }
        if (cleanDate.length < 4) {
            formError = "Enter a valid Release Date (e.g. 2026-11-21)."
            return
        }

        val currentInspected = inspectedApk
        if (selectedStatus == LiveReleaseStatus.PUBLISHED) {
            if (cleanNotes.isBlank()) {
                formError = "Release Notes are required before publishing an update."
                return
            }
            if (currentInspected == null && (apkDownloadUrl.isBlank() || apkSizeBytes <= 0L || apkSha256.isBlank())) {
                formError = "Select and verify the new APK file before publishing."
                return
            }
            if (currentInspected != null) {
                val verify = LiveUpdateApkVerifier.verifyForAdminPublish(
                    inspected = currentInspected,
                    installed = installed,
                    enteredVersionName = cleanName,
                    enteredVersionCode = codeLong,
                    highestPublishedVersionCode = highestPublishedVersionCode
                )
                verificationResult = verify
                if (!verify.isValid) {
                    formError = verify.errors.firstOrNull() ?: "APK verification failed."
                    return
                }
                signingSha256 = verify.matchedSigningSha256
            } else {
                val matchesVerifiedDraft = verifiedDraftVersionName == cleanName &&
                    verifiedDraftVersionCode == codeLong &&
                    signingSha256.isNotBlank() &&
                    (installed.signingSha256Set.isEmpty() ||
                        installed.signingSha256Set.any { it.equals(signingSha256, ignoreCase = true) })
                if (!matchesVerifiedDraft) {
                    formError = "Re-select and verify the APK file because version metadata changed or signature is unverified."
                    return
                }
                if (codeLong <= highestPublishedVersionCode) {
                    formError = "Published Version Code ($codeLong) must be newer than current build ($highestPublishedVersionCode)."
                    return
                }
            }
        }

        coroutineScope.launch {
            isSubmitting = true
            formError = null
            var finalUrl = apkDownloadUrl.trim()
            var finalStoragePath = storagePath
            var finalSize = apkSizeBytes

            if (currentInspected != null && finalUrl.isBlank()) {
                isUploading = true
                uploadProgress = 0
                val uploadRes = liveUpdateRepository.uploadApkFile(
                    localFile = File(currentInspected.localTempPath),
                    versionCode = codeLong,
                    published = selectedStatus == LiveReleaseStatus.PUBLISHED,
                    onProgress = { pct -> uploadProgress = pct }
                )
                isUploading = false
                if (uploadRes.isFailure) {
                    isSubmitting = false
                    formError = uploadRes.exceptionOrNull()?.localizedMessage ?: "APK upload failed."
                    return@launch
                }
                val uploaded = uploadRes.getOrThrow()
                finalUrl = uploaded.downloadUrl
                finalStoragePath = uploaded.storagePath
                finalSize = uploaded.fileSizeBytes
                apkDownloadUrl = finalUrl
                storagePath = finalStoragePath
                apkSizeBytes = finalSize
            }

            if (selectedStatus == LiveReleaseStatus.PUBLISHED && !finalUrl.startsWith("https://")) {
                isSubmitting = false
                formError = "A valid HTTPS APK download URL is required to publish."
                return@launch
            }

            val releaseId = editingReleaseId ?: LiveUpdateRepository.releaseIdForVersion(codeLong)
            val releaseObj = LiveUpdateRelease(
                releaseId = releaseId,
                versionName = cleanName,
                versionCode = codeLong,
                packageName = installed.packageName,
                apkDownloadUrl = finalUrl,
                apkSize = finalSize,
                releaseNotes = cleanNotes,
                releaseDate = cleanDate,
                published = selectedStatus == LiveReleaseStatus.PUBLISHED,
                releaseStatus = selectedStatus.name,
                storagePath = finalStoragePath,
                apkSha256 = apkSha256,
                signingSha256 = signingSha256,
                publishedBy = Firebase.auth.currentUser?.uid.orEmpty()
            )

            val saveRes = liveUpdateRepository.saveOrPublishRelease(releaseObj)
            isSubmitting = false
            saveRes.onSuccess {
                onStatusMessage(
                    if (selectedStatus == LiveReleaseStatus.PUBLISHED) {
                        "Live Update v$cleanName ($codeLong) published!"
                    } else {
                        "Draft v$cleanName ($codeLong) saved."
                    }
                )
                resetForm()
            }.onFailure { err ->
                formError = err.localizedMessage ?: "Failed to save release."
            }
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = StoreProSecondaryCyan,
        unfocusedBorderColor = StoreProCardBorder,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = StoreProSecondaryCyan,
        unfocusedLabelColor = Color(0xFF94A3B8)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StoreProBackground)
            .testTag("admin_live_update_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoreProCardBackground)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("admin_live_update_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = StoreProPrimaryGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Admin Live Release Studio",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Signed APK Verification & Publishing",
                    fontSize = 11.5.sp,
                    color = StoreProSecondaryCyan
                )
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
                // Currently Published Summary Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                    border = BorderStroke(1.dp, StoreProPrimaryGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Currently Published Live Version",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StoreProPrimaryGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (currentPublished != null) {
                                "v${currentPublished.versionName} (Build ${currentPublished.versionCode}) • ${currentPublished.releaseDate}"
                            } else {
                                "None published yet (Installed Build ${installed.versionCode})"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Release Creation & Verification Form (All 10 required fields)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                    border = BorderStroke(1.dp, StoreProCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editingReleaseId != null) "Edit Release / Draft" else "Create New Live Release",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (editingReleaseId != null) {
                                OutlinedButton(onClick = { resetForm() }) {
                                    Text("New", color = StoreProSecondaryCyan)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = versionName,
                                onValueChange = {
                                    versionName = it
                                    recomputeVerification(inspectedApk, it, versionCodeInput)
                                },
                                label = { Text("Version Name") },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_live_version_name_input")
                            )
                            OutlinedTextField(
                                value = versionCodeInput,
                                onValueChange = {
                                    versionCodeInput = it.filter { ch -> ch.isDigit() }
                                    recomputeVerification(inspectedApk, versionName, versionCodeInput)
                                },
                                label = { Text("Version Code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_live_version_code_input")
                            )
                        }

                        OutlinedButton(
                            onClick = { apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                            enabled = !isInspecting && !isUploading && !isSubmitting,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StoreProSecondaryCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_live_pick_apk_button")
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = StoreProSecondaryCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = inspectedApk?.fileName ?: "Select STORE PRO APK File",
                                color = StoreProSecondaryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isInspecting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = StoreProSecondaryCyan, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inspecting APK signature & manifest...", color = Color(0xFFCBD5E1), fontSize = 12.5.sp)
                            }
                        }

                        // Verification Report
                        inspectedApk?.let { meta ->
                            val isOk = verificationResult?.isValid == true
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(
                                    1.dp,
                                    if (isOk) StoreProPrimaryGreen else Color(0xFFFF6B6B)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_live_verification_card")
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isOk) Icons.Default.VerifiedUser else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isOk) StoreProPrimaryGreen else Color(0xFFFF6B6B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isOk) "APK Signature & Version Verified" else "APK Verification Blocked",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOk) StoreProPrimaryGreen else Color(0xFFFCA5A5)
                                        )
                                    }
                                    Text(
                                        text = "Archive Package: ${meta.packageName.ifBlank { "Invalid" }} • v${meta.versionName} (${meta.versionCode})",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    verificationResult?.errors?.forEach { err ->
                                        Text(text = "• $err", fontSize = 11.5.sp, color = Color(0xFFFCA5A5))
                                    }
                                }
                            }
                        }

                        if (isUploading || uploadProgress > 0) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { (uploadProgress / 100f).coerceIn(0f, 1f) },
                                    color = StoreProPrimaryGreen,
                                    trackColor = Color(0xFF1E293B),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .testTag("admin_live_upload_progress")
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "APK Upload Progress: $uploadProgress%",
                                    fontSize = 12.sp,
                                    color = StoreProPrimaryGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedTextField(
                            value = apkDownloadUrl,
                            onValueChange = { apkDownloadUrl = it },
                            label = { Text("APK Download URL") },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_live_apk_url_input")
                        )

                        Text(
                            text = "APK File Size: ${formatApkBytes(apkSizeBytes)}",
                            fontSize = 12.5.sp,
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("admin_live_apk_size_text")
                        )

                        OutlinedTextField(
                            value = releaseNotes,
                            onValueChange = { releaseNotes = it },
                            label = { Text("Release Notes") },
                            minLines = 3,
                            maxLines = 6,
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_live_release_notes_input")
                        )

                        OutlinedTextField(
                            value = releaseDate,
                            onValueChange = { releaseDate = it },
                            label = { Text("Release Date (YYYY-MM-DD)") },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_live_release_date_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = selectedStatus == LiveReleaseStatus.PUBLISHED,
                                onClick = { selectedStatus = LiveReleaseStatus.PUBLISHED },
                                label = { Text("Published") },
                                leadingIcon = {
                                    if (selectedStatus == LiveReleaseStatus.PUBLISHED) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StoreProPrimaryGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = StoreProPrimaryGreen
                                ),
                                modifier = Modifier.testTag("admin_live_status_published")
                            )
                            FilterChip(
                                selected = selectedStatus == LiveReleaseStatus.DRAFT,
                                onClick = { selectedStatus = LiveReleaseStatus.DRAFT },
                                label = { Text("Draft") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StoreProSecondaryCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = StoreProSecondaryCyan
                                ),
                                modifier = Modifier.testTag("admin_live_status_draft")
                            )
                        }

                        if (!formError.isNullOrBlank()) {
                            Text(
                                text = formError!!,
                                fontSize = 12.5.sp,
                                color = Color(0xFFFCA5A5),
                                modifier = Modifier.testTag("admin_live_form_error")
                            )
                        }

                        Button(
                            onClick = { submitRelease() },
                            enabled = !isSubmitting && !isInspecting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedStatus == LiveReleaseStatus.PUBLISHED) StoreProPrimaryGreen else StoreProSecondaryCyan,
                                contentColor = Color(0xFF003816)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("admin_live_submit_button")
                        ) {
                            Icon(
                                imageVector = if (selectedStatus == LiveReleaseStatus.PUBLISHED) Icons.Default.Publish else Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isSubmitting -> "Uploading & Saving..."
                                    selectedStatus == LiveReleaseStatus.PUBLISHED -> "Verify & Publish Update"
                                    else -> "Save Release as Draft"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Release & Draft History Manager
                Text(
                    text = "All Releases & Drafts (${allReleases.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                allReleases.forEach { rel ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                        border = BorderStroke(1.dp, StoreProCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "v${rel.versionName} (Build ${rel.versionCode})",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                SmallTagChip(
                                    text = rel.releaseStatus,
                                    color = when (rel.releaseStatus) {
                                        LiveReleaseStatus.PUBLISHED.name -> StoreProPrimaryGreen
                                        LiveReleaseStatus.DRAFT.name -> StoreProSecondaryCyan
                                        else -> Color(0xFF94A3B8)
                                    }
                                )
                            }
                            Text(
                                text = "${rel.releaseDate} • ${formatCompactMb(rel.apkSize)}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            if (rel.releaseNotes.isNotBlank()) {
                                Text(text = rel.releaseNotes, fontSize = 12.5.sp, color = Color(0xFFCBD5E1))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (rel.releaseStatus == LiveReleaseStatus.DRAFT.name) {
                                    OutlinedButton(onClick = { loadReleaseIntoForm(rel) }) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit Draft")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val res = liveUpdateRepository.deleteDraftRelease(rel)
                                                onStatusMessage(
                                                    if (res.isSuccess) "Draft deleted." else "Failed to delete draft."
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", color = Color(0xFFFCA5A5))
                                    }
                                } else if (rel.releaseStatus == LiveReleaseStatus.PUBLISHED.name) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val res = liveUpdateRepository.archiveRelease(rel)
                                                onStatusMessage(
                                                    if (res.isSuccess) "Release archived." else "Failed to archive."
                                                )
                                            }
                                        }
                                    ) {
                                        Text("Archive Release", color = Color(0xFFCBD5E1))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
