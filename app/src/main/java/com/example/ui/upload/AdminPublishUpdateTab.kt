package com.example.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.repository.LiveUpdateRepository
import com.example.ui.admin.AdminAuthManager
import com.example.ui.liveupdate.formatApkBytes
import com.example.ui.liveupdate.todayIsoDate
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.io.File

private val AdminPurpleAccent = Color(0xFF9B5DE5)

@Composable
fun AdminPublishUpdateTab(
    allReleases: List<LiveUpdateRelease>,
    liveUpdateRepository: LiveUpdateRepository,
    onOpenHistory: () -> Unit,
    onStatusMessage: (String) -> Unit,
    onUnauthorized: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val installed = remember(context) { LiveUpdateApkVerifier.readInstalledAppDetails(context) }

    val publishedReleases = remember(allReleases) {
        allReleases.filter { it.published && it.releaseStatus == LiveReleaseStatus.PUBLISHED.name }
            .sortedByDescending { it.versionCode }
    }
    val currentPublished = publishedReleases.firstOrNull()
    val highestPublishedVersionCode = maxOf(
        installed.versionCode,
        currentPublished?.versionCode ?: 0L
    )
    val previousVersionLabel = remember(installed, currentPublished) {
        if (currentPublished != null && currentPublished.versionCode >= installed.versionCode) {
            "v${currentPublished.versionName} (${currentPublished.versionCode})"
        } else {
            "v${installed.versionName} (${installed.versionCode})"
        }
    }

    var versionCodeInput by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("") }
    var apkDownloadUrl by remember { mutableStateOf("") }
    var apkFileName by remember { mutableStateOf("") }
    var apkSizeBytes by remember { mutableLongStateOf(0L) }
    var releaseNotes by remember { mutableStateOf("") }
    var mandatoryUpdate by remember { mutableStateOf(false) }
    var publishImmediately by remember { mutableStateOf(true) }
    var storagePath by remember { mutableStateOf("") }
    var apkSha256 by remember { mutableStateOf("") }
    var signingSha256 by remember { mutableStateOf("") }

    var inspectedApk by remember { mutableStateOf<InspectedApkMetadata?>(null) }
    var verificationResult by remember { mutableStateOf<ApkVerificationResult?>(null) }
    var isInspecting by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

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
            successMessage = null
            val res = LiveUpdateApkVerifier.inspectPickedUri(context, uri)
            isInspecting = false
            res.onSuccess { meta ->
                inspectedApk = meta
                apkFileName = meta.fileName
                apkSizeBytes = meta.fileSizeBytes
                apkSha256 = meta.apkSha256
                signingSha256 = meta.signingSha256Set.firstOrNull().orEmpty()
                if (meta.versionName.isNotBlank()) {
                    versionName = meta.versionName
                }
                if (meta.versionCode > 0L) {
                    versionCodeInput = meta.versionCode.toString()
                }
                apkDownloadUrl = ""
                storagePath = ""
                recomputeVerification(meta, versionName, versionCodeInput)
            }.onFailure { err ->
                formError = err.localizedMessage ?: "Unable to read selected APK file."
            }
        }
    }

    fun resetForm() {
        versionCodeInput = ""
        versionName = ""
        apkDownloadUrl = ""
        apkFileName = ""
        apkSizeBytes = 0L
        releaseNotes = ""
        mandatoryUpdate = false
        publishImmediately = true
        storagePath = ""
        apkSha256 = ""
        signingSha256 = ""
        inspectedApk = null
        verificationResult = null
        uploadProgress = 0
        formError = null
    }

    fun uploadSelectedApkNow() {
        val currentInspected = inspectedApk
        if (currentInspected == null) {
            formError = "Select a signed APK file first."
            return
        }
        val cleanName = versionName.trim().ifBlank { currentInspected.versionName }
        val codeLong = versionCodeInput.trim().toLongOrNull() ?: currentInspected.versionCode
        if (cleanName.isBlank() || codeLong <= 0L) {
            formError = "Valid Version Name and Version Code are required before upload."
            return
        }

        coroutineScope.launch {
            if (!AdminAuthManager.isFullyAuthorizedAdmin(context, Firebase.auth.currentUser)) {
                onUnauthorized()
                return@launch
            }
            isUploading = true
            uploadProgress = 0
            formError = null
            val uploadRes = liveUpdateRepository.uploadApkFile(
                localFile = File(currentInspected.localTempPath),
                versionName = cleanName,
                versionCode = codeLong,
                originalFileName = currentInspected.fileName,
                published = publishImmediately,
                onProgress = { pct -> uploadProgress = pct }
            )
            isUploading = false
            uploadRes.onSuccess { uploaded ->
                apkDownloadUrl = uploaded.downloadUrl
                storagePath = uploaded.storagePath
                apkSizeBytes = uploaded.fileSizeBytes
                successMessage = "APK stored permanently at ${uploaded.storagePath}"
            }.onFailure { err ->
                formError = err.localizedMessage ?: "APK upload to Firebase Storage failed."
            }
        }
    }

    fun submitUpdate() {
        formError = null
        successMessage = null

        val activeUser = Firebase.auth.currentUser
        if (!AdminAuthManager.isFullyAuthorizedAdmin(context, activeUser)) {
            onUnauthorized()
            return
        }

        val codeLong = versionCodeInput.trim().toLongOrNull()
        val cleanName = versionName.trim()
        val cleanNotes = releaseNotes.trim()

        if (codeLong == null || codeLong <= 0L) {
            formError = "Enter a valid positive integer Version Code."
            return
        }
        if (cleanName.isBlank()) {
            formError = "Enter Version Name."
            return
        }
        if (publishImmediately && cleanNotes.isBlank()) {
            formError = "Release Notes / Changelog are required before publishing an update."
            return
        }

        val currentInspected = inspectedApk
        if (publishImmediately && currentInspected == null && apkDownloadUrl.isBlank()) {
            formError = "Pick and verify the new STORE PRO APK binary or enter its verified HTTPS APK URL."
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
            if (publishImmediately && !verify.isValid) {
                formError = verify.errors.firstOrNull() ?: "APK verification failed."
                return
            }
            signingSha256 = verify.matchedSigningSha256
        }

        coroutineScope.launch {
            isSubmitting = true
            var finalUrl = apkDownloadUrl.trim()
            var finalStoragePath = storagePath
            var finalSize = apkSizeBytes

            if (currentInspected != null && finalUrl.isBlank()) {
                isUploading = true
                uploadProgress = 0
                val uploadRes = liveUpdateRepository.uploadApkFile(
                    localFile = File(currentInspected.localTempPath),
                    versionName = cleanName,
                    versionCode = codeLong,
                    originalFileName = currentInspected.fileName,
                    published = publishImmediately,
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

            if (publishImmediately && !finalUrl.startsWith("https://", ignoreCase = true)) {
                isSubmitting = false
                formError = "A valid HTTPS APK download URL is required to publish."
                return@launch
            }

            val status = if (publishImmediately) LiveReleaseStatus.PUBLISHED else LiveReleaseStatus.DRAFT
            val adminEmail = activeUser?.email?.takeIf { it.isNotBlank() }
                ?: AdminAuthManager.getAdminUsername(context)

            val releaseObj = LiveUpdateRelease(
                releaseId = LiveUpdateRepository.releaseIdForVersion(codeLong),
                versionName = cleanName,
                versionCode = codeLong,
                packageName = inspectedApk?.packageName?.ifBlank { installed.packageName } ?: installed.packageName,
                apkDownloadUrl = finalUrl,
                apkSize = finalSize,
                releaseNotes = cleanNotes,
                releaseDate = todayIsoDate(),
                published = publishImmediately,
                releaseStatus = status.name,
                mandatoryUpdate = mandatoryUpdate,
                changelog = cleanNotes,
                storagePath = finalStoragePath,
                apkSha256 = apkSha256,
                signingSha256 = signingSha256,
                publishedBy = activeUser?.uid.orEmpty(),
                adminEmail = adminEmail,
                apkFileName = apkFileName.ifBlank { "STORE_PRO_v${cleanName}_b${codeLong}.apk" }
            )

            val saveRes = liveUpdateRepository.saveOrPublishRelease(
                release = releaseObj,
                previousVersionLabel = previousVersionLabel
            )
            isSubmitting = false
            saveRes.onSuccess {
                val msg = if (publishImmediately) {
                    "STORE PRO v$cleanName (Build $codeLong) archived to History & saved!"
                } else {
                    "Release draft v$cleanName (Build $codeLong) saved."
                }
                successMessage = msg
                onStatusMessage(msg)
                resetForm()
            }.onFailure { err ->
                formError = err.localizedMessage ?: "Failed to save update release."
            }
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = StoreProCardBackground,
        unfocusedContainerColor = StoreProCardBackground,
        focusedBorderColor = StoreProSecondaryCyan,
        unfocusedBorderColor = StoreProCardBorder,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = StoreProPrimaryGreen
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_publish_update_tab"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Installed Build + HISTORY Button Row
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
            border = BorderStroke(1.dp, AdminPurpleAccent.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Installed Build",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoreProSecondaryCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "STORE PRO v${installed.versionName} (Build ${installed.versionCode})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.testTag("admin_current_installed_version_text")
                    )
                    if (currentPublished != null) {
                        Text(
                            text = "Latest Recorded: v${currentPublished.versionName} (Build ${currentPublished.versionCode})",
                            fontSize = 11.sp,
                            color = StoreProPrimaryGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedButton(
                    onClick = onOpenHistory,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.2.dp, StoreProSecondaryCyan),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StoreProSecondaryCyan.copy(alpha = 0.12f),
                        contentColor = StoreProSecondaryCyan
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("admin_open_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Open Update History",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HISTORY", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Column {
            Text(
                text = "Publish STORE PRO Update",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Upload signed STORE PRO APK permanently to Storage & record release metadata in History.",
                fontSize = 12.5.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Pick APK Binary Button
        OutlinedButton(
            onClick = { apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
            enabled = !isInspecting && !isUploading && !isSubmitting,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.2.dp, StoreProSecondaryCyan),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = StoreProCardBackground,
                contentColor = StoreProSecondaryCyan
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("admin_live_pick_apk_button")
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = inspectedApk?.fileName ?: "Pick APK Binary & Auto-Detect Version",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isInspecting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = StoreProSecondaryCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Inspecting APK package, version & signing certificate...",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            }
        }

        // Verification Card
        inspectedApk?.let { meta ->
            val isOk = verificationResult?.isValid == true
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, if (isOk) StoreProPrimaryGreen else Color(0xFFF59E0B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_live_verification_card")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOk) Icons.Default.VerifiedUser else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (isOk) StoreProPrimaryGreen else Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOk) "APK Verified for Release" else "APK Inspection Notice",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOk) StoreProPrimaryGreen else Color(0xFFFBBF24)
                            )
                        }
                        if (apkDownloadUrl.isBlank() && !isUploading) {
                            OutlinedButton(
                                onClick = { uploadSelectedApkNow() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, StoreProPrimaryGreen),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = StoreProPrimaryGreen, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload APK", color = StoreProPrimaryGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "Package: ${meta.packageName.ifBlank { "Invalid" }} • v${meta.versionName} (Build ${meta.versionCode})",
                        fontSize = 11.5.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        text = "Size: ${formatApkBytes(meta.fileSizeBytes)} • SHA-256: ${meta.apkSha256.take(16)}…",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
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
                    text = "Uploading APK permanently to STORE_PRO/RELEASES: $uploadProgress%",
                    fontSize = 11.5.sp,
                    color = StoreProPrimaryGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Side-by-side Version Code & Version Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Version Code *",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                OutlinedTextField(
                    value = versionCodeInput,
                    onValueChange = {
                        versionCodeInput = it.filter { ch -> ch.isDigit() }
                        recomputeVerification(inspectedApk, versionName, versionCodeInput)
                    },
                    placeholder = { Text("e.g. 4", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_live_version_code_input")
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Version Name *",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                OutlinedTextField(
                    value = versionName,
                    onValueChange = {
                        versionName = it
                        recomputeVerification(inspectedApk, it, versionCodeInput)
                    },
                    placeholder = { Text("e.g. 1.3", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_live_version_name_input")
                )
            }
        }

        // APK Download URL
        Column {
            Text(
                text = "APK Download URL *",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.padding(bottom = 5.dp)
            )
            OutlinedTextField(
                value = apkDownloadUrl,
                onValueChange = { apkDownloadUrl = it },
                placeholder = { Text("Auto-filled after APK upload or paste HTTPS URL", color = Color(0xFF64748B), fontSize = 12.5.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = textFieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_live_apk_url_input")
            )
            Text(
                text = "APK File Size: ${formatApkBytes(apkSizeBytes)}",
                fontSize = 11.5.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("admin_live_apk_size_text")
            )
        }

        // Release Notes / Changelog
        Column {
            Text(
                text = "Release Notes / Changelog *",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.padding(bottom = 5.dp)
            )
            OutlinedTextField(
                value = releaseNotes,
                onValueChange = { releaseNotes = it },
                placeholder = { Text("What changed in this STORE PRO version...", color = Color(0xFF64748B), fontSize = 13.sp) },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                colors = textFieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_live_release_notes_input")
            )
        }

        // Mandatory Update & Publish Immediately Toggles
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
            border = BorderStroke(1.dp, StoreProCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mandatory Update",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tag release as critical/mandatory in Release History",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = mandatoryUpdate,
                        onCheckedChange = { mandatoryUpdate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AdminPurpleAccent
                        ),
                        modifier = Modifier.testTag("admin_live_mandatory_switch")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Publish Immediately",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Store release permanently in Admin Version History",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = publishImmediately,
                        onCheckedChange = { publishImmediately = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF003816),
                            checkedTrackColor = StoreProPrimaryGreen
                        ),
                        modifier = Modifier.testTag("admin_live_publish_immediately_switch")
                    )
                }
            }
        }

        if (!formError.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF7F1D1D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formError!!,
                        fontSize = 12.5.sp,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.testTag("admin_live_form_error")
                    )
                }
            }
        }

        if (!successMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF064E3B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StoreProPrimaryGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = successMessage!!,
                        fontSize = 12.5.sp,
                        color = Color.White
                    )
                }
            }
        }

        Button(
            onClick = { submitUpdate() },
            enabled = !isSubmitting && !isInspecting && !isUploading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (publishImmediately) StoreProPrimaryGreen else StoreProSecondaryCyan,
                contentColor = Color(0xFF003816)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("admin_live_submit_button")
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color(0xFF003816),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Archiving & Publishing...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = if (publishImmediately) Icons.Default.Publish else Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (publishImmediately) "Publish STORE PRO Update" else "Save Release Draft",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
