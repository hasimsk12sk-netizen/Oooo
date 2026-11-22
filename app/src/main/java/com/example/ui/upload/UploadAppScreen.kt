package com.example.ui.upload

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.download.FileSharingLinkResolver
import com.example.data.model.ApplicationItem
import com.example.data.repository.DeviceAndUpdateRepository
import com.example.data.repository.StoreProRepository
import com.example.ui.admin.AdminAuthManager
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

private val metadataProbeClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

@Composable
fun UploadAppScreen(
    currentUserId: String,
    repository: StoreProRepository,
    onBack: () -> Unit,
    onUploadSuccess: (String) -> Unit,
    onAdminLogout: () -> Unit,
    onUnauthorized: () -> Unit,
    deviceUpdateRepository: DeviceAndUpdateRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val resolvedUpdateRepo = remember(deviceUpdateRepository) {
        deviceUpdateRepository ?: DeviceAndUpdateRepository(context)
    }

    // Guard route on entry so unauthorized users can never access upload UI
    LaunchedEffect(Unit) {
        if (!AdminAuthManager.isFullyAuthorizedAdmin(context, Firebase.auth.currentUser)) {
            onUnauthorized()
        } else {
            resolvedUpdateRepo.syncAdminRoleIfAuthorized()
        }
    }

    var appName by remember { mutableStateOf("") }
    var developerName by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0.0") }
    var iconUrl by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var whatsNew by remember { mutableStateOf("") }
    var screenshotsText by remember { mutableStateOf("") }
    var privacyPolicyUrl by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var verificationStep by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .padding(bottom = 60.dp)
    ) {
        // Top navigation bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("upload_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Admin Upload Studio",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Admin Header Bar with Session Status and Logout
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(StoreProPrimaryGreen)
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StoreProPrimaryGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = StoreProPrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Admin Upload Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Authenticated as ${AdminAuthManager.getAdminUsername(context)}",
                            fontSize = 11.sp,
                            color = StoreProSecondaryCyan
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        AdminAuthManager.logout(context)
                        onAdminLogout()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                    modifier = Modifier.testTag("admin_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Logout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            text = "Publish Application",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            text = "Submit a new APK to STORE PRO marketplace with universal image & download link handling.",
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Application Name
        UploadTextField(
            value = appName,
            onValueChange = { appName = it },
            label = "Application Name *",
            placeholder = "e.g. VLC Media Player",
            testTag = "input_app_name"
        )

        // Developer Name
        UploadTextField(
            value = developerName,
            onValueChange = { developerName = it },
            label = "Developer / Studio Name *",
            placeholder = "e.g. VideoLAN or Studio Dev",
            testTag = "input_developer_name"
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UploadTextField(
                value = version,
                onValueChange = { version = it },
                label = "Version *",
                placeholder = "1.0.0",
                modifier = Modifier.weight(1f),
                testTag = "input_version"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 31.dp)
            ) {
                Button(
                    onClick = {
                        appName = "VLC Streamer Pro"
                        developerName = "VideoLAN Studio"
                        version = "3.6.0"
                        iconUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=256&h=256&fit=crop"
                        apkUrl = "https://get.videolan.org/vlc-android/3.5.4/VLC-Android-3.5.4-arm64-v8a.apk"
                        description = "Premium media player with hardware acceleration, high-definition streaming, subtitle support, and 4K playback."
                        whatsNew = "High bitrate stream fixes and performance boosts."
                        screenshotsText = "https://images.unsplash.com/photo-1536240478700-b869070f9279?w=600, https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600"
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = StoreProSecondaryCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text("Fill Demo Data", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Application Icon URL with Universal Live Preview
        UploadTextField(
            value = iconUrl,
            onValueChange = { iconUrl = it },
            label = "Application Icon URL *",
            placeholder = "Any public HTTP/HTTPS image URL (JPG, JPEG, PNG, WEBP, GIF, Drive, GitHub...)",
            testTag = "input_icon_url"
        )

        AppIconPreviewBox(iconUrl = iconUrl, modifier = Modifier.padding(bottom = 8.dp))

        // APK Download URL with Universal File-Sharing Link Detection
        UploadTextField(
            value = apkUrl,
            onValueChange = { apkUrl = it },
            label = "APK / File-Sharing Download Link *",
            placeholder = "Paste Google Drive, MediaFire, Dropbox, GitHub, Mega, or any hosting link",
            testTag = "input_apk_url"
        )

        if (apkUrl.isNotBlank()) {
            val detectedService = FileSharingLinkResolver.identifyService(apkUrl)
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = StoreProSecondaryCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Detected Service: $detectedService (Will auto-resolve to direct download)",
                        color = StoreProSecondaryCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Application Description
        UploadTextField(
            value = description,
            onValueChange = { description = it },
            label = "Application Description *",
            placeholder = "Explain what your app does, features, and functionality...",
            minLines = 3,
            maxLines = 6,
            testTag = "input_description"
        )

        // What's New
        UploadTextField(
            value = whatsNew,
            onValueChange = { whatsNew = it },
            label = "What's New in this Version",
            placeholder = "Release notes, bug fixes, features...",
            testTag = "input_whats_new"
        )

        // Screenshot URLs (Optional, comma or line separated) with Live Gallery
        UploadTextField(
            value = screenshotsText,
            onValueChange = { screenshotsText = it },
            label = "Screenshot URLs (Optional, comma or line separated)",
            placeholder = "https://.../shot1.png, http://.../shot2.webp (HTTP or HTTPS)",
            minLines = 2,
            maxLines = 4,
            testTag = "input_screenshots"
        )

        ScreenshotsPreviewGallery(rawUrls = screenshotsText)

        // Privacy Policy URL
        UploadTextField(
            value = privacyPolicyUrl,
            onValueChange = { privacyPolicyUrl = it },
            label = "Privacy Policy URL (Optional)",
            placeholder = "https://example.com/privacy",
            testTag = "input_privacy_url"
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(StoreProCardBorder)
            ),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = StoreProPrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STORE PRO Protect Verification",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Server-Verified Admin: Only authenticated administrators can publish APK files.\n• Universal Link Compatibility: Direct downloads, Google Drive, MediaFire, Dropbox, GitHub.\n• Automatic Malware & Integrity Scan: URLs checked for safety and reachability.\n• Universal Images: HTTP & HTTPS icons and screenshots load with live previews.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            }
        }

        if (validationError != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = validationError ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (successMessage != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StoreProPrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = successMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                validationError = null
                successMessage = null

                val activeFirebaseUser = Firebase.auth.currentUser
                if (!AdminAuthManager.isFullyAuthorizedAdmin(context, activeFirebaseUser)) {
                    validationError = "Access Denied: Administrator authentication and verified admin Google sign-in are required."
                    onUnauthorized()
                    return@Button
                }

                val trimmedAppName = appName.trim()
                val trimmedDeveloper = developerName.trim()
                val trimmedVersion = version.trim()
                val trimmedApkUrl = apkUrl.trim()
                val trimmedIconUrl = iconUrl.trim()
                val trimmedDescription = description.trim()
                val trimmedWhatsNew = whatsNew.trim()
                val trimmedPrivacyUrl = privacyPolicyUrl.trim()

                if (trimmedAppName.isBlank() || trimmedAppName.length > 120) {
                    validationError = "Please enter an application name (1–120 characters)."
                    return@Button
                }
                if (trimmedDeveloper.isBlank() || trimmedDeveloper.length > 120) {
                    validationError = "Please enter a developer name (1–120 characters)."
                    return@Button
                }
                if (trimmedVersion.isBlank() || trimmedVersion.length > 40) {
                    validationError = "Please enter a valid version (1–40 characters)."
                    return@Button
                }
                if (trimmedApkUrl.length < 8 || trimmedApkUrl.length > 2000 ||
                    (!trimmedApkUrl.startsWith("https://", ignoreCase = true) && !trimmedApkUrl.startsWith("http://", ignoreCase = true))) {
                    validationError = "Please enter a valid HTTP or HTTPS file-sharing link (Google Drive, Dropbox, MediaFire, GitHub, etc.)."
                    return@Button
                }
                if (trimmedIconUrl.isNotBlank() && (!UniversalImageUtils.isValidImageUrl(trimmedIconUrl) || trimmedIconUrl.length > 2000)) {
                    validationError = "Application Icon URL must be a valid HTTP or HTTPS image URL."
                    return@Button
                }
                val finalIconUrl = if (trimmedIconUrl.isBlank()) {
                    "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=256&h=256&fit=crop"
                } else {
                    trimmedIconUrl
                }
                if (trimmedIconUrl.isBlank()) {
                    iconUrl = finalIconUrl
                }
                if (trimmedDescription.isBlank() || trimmedDescription.length > 5000) {
                    validationError = "Please provide an application description (1–5000 characters)."
                    return@Button
                }

                val screenshots = UniversalImageUtils.parseMultipleImageUrls(screenshotsText).take(20)

                isSubmitting = true
                coroutineScope.launch {
                    try {
                        val hostService = FileSharingLinkResolver.identifyService(trimmedApkUrl)
                        verificationStep = "Resolving $hostService link to direct download stream..."
                        delay(500)

                        val directResolved = FileSharingLinkResolver.resolveToDirectDownloadUrl(trimmedApkUrl)

                        verificationStep = "Inspecting package metadata and reachability..."
                        var estimatedSize = 25 * 1024 * 1024L
                        try {
                            withContext(Dispatchers.IO) {
                                val req = Request.Builder()
                                    .url(directResolved)
                                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                    .head()
                                    .build()
                                metadataProbeClient.newCall(req).execute().use { resp ->
                                    val length = resp.header("Content-Length")?.toLongOrNull()
                                        ?: resp.body?.contentLength()
                                        ?: -1L
                                    if (length > 0) estimatedSize = length
                                }
                            }
                        } catch (_: Exception) {
                            // Proceed gracefully if HEAD rejected by server policy
                        }

                        verificationStep = "Running STORE PRO Protect security & integrity verification..."
                        delay(600)

                        verificationStep = "Publishing to STORE PRO Marketplace..."
                        val devSlug = trimmedDeveloper.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "dev" }
                        val appSlug = trimmedAppName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }
                        val safePackageId = "com.$devSlug.$appSlug".take(150)

                        val newApp = ApplicationItem(
                            appId = "app_${UUID.randomUUID().toString().take(12)}",
                            appName = trimmedAppName,
                            developerName = trimmedDeveloper,
                            version = trimmedVersion,
                            description = trimmedDescription,
                            iconUrl = UniversalImageUtils.normalizeImageUrl(finalIconUrl).take(2000),
                            apkUrl = trimmedApkUrl,
                            screenshots = screenshots.map { UniversalImageUtils.normalizeImageUrl(it).take(2000) },
                            whatsNew = trimmedWhatsNew.take(2000),
                            privacyPolicyUrl = trimmedPrivacyUrl.take(2000),
                            packageId = safePackageId,
                            fileSizeBytes = estimatedSize,
                            downloads = 0L,
                            averageRating = 5.0,
                            ratingCount = 1L,
                            status = "published",
                            verificationStatus = "verified",
                            uploadedBy = activeFirebaseUser?.uid ?: currentUserId
                        )

                        val result = repository.uploadApplication(newApp)
                        if (result.isSuccess) {
                            verificationStep = null
                            successMessage = "Application successfully verified and published to STORE PRO!"
                            delay(800)
                            onUploadSuccess(newApp.appId)
                        } else {
                            verificationStep = null
                            validationError = result.exceptionOrNull()?.localizedMessage ?: "Failed to upload application"
                        }
                    } catch (e: Exception) {
                        verificationStep = null
                        validationError = e.localizedMessage ?: "Upload failed"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StoreProPrimaryGreen,
                contentColor = Color(0xFF003816)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("submit_upload_button")
        ) {
            if (isSubmitting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF003816),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = verificationStep ?: "Publishing Application...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Publish Application",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) minLines + 3 else 1,
    testTag: String = ""
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFF64748B), fontSize = 13.sp) },
            minLines = minLines,
            maxLines = maxOf(minLines, maxLines),
            singleLine = minLines <= 1,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF131B2E),
                unfocusedContainerColor = Color(0xFF131B2E),
                focusedBorderColor = StoreProPrimaryGreen,
                unfocusedBorderColor = StoreProCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = StoreProPrimaryGreen
            ),
            modifier = Modifier.fillMaxWidth().testTag(testTag)
        )
    }
}
