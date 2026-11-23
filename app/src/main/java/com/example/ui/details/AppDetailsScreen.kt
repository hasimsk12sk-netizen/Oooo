package com.example.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.download.DownloadProgressState
import com.example.data.download.DownloadStatus
import com.example.data.download.FileSharingLinkResolver
import com.example.data.model.ApplicationItem
import com.example.data.model.ReviewItem
import com.example.ui.components.rgbNeonGlowBorder
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.example.ui.theme.StoreProStar
import com.example.ui.upload.UniversalImageLoader
import com.example.ui.upload.UniversalImageUtils
import com.example.util.StoreProShareHelper
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppDetailsScreen(
    app: ApplicationItem,
    reviews: List<ReviewItem>,
    isSaved: Boolean,
    currentUserId: String?,
    currentUserName: String,
    downloadProgress: DownloadProgressState,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onRequireSignIn: (String) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onOpenBrowserDownload: () -> Unit,
    onSubmitReview: (rating: Long, comment: String, existingId: String?) -> Unit,
    onDeleteReview: (reviewId: String) -> Unit,
    onShareClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember(context) { UniversalImageLoader.get(context) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var editingReview by remember { mutableStateOf<ReviewItem?>(null) }
    var previewScreenshotUrl by remember { mutableStateOf<String?>(null) }

    val normalizedIconUrl = remember(app.iconUrl) {
        UniversalImageUtils.normalizeImageUrl(app.iconUrl)
    }
    val hasValidIcon = remember(normalizedIconUrl) {
        UniversalImageUtils.isValidImageUrl(normalizedIconUrl)
    }
    val iconSizePx = remember(density) { with(density) { 88.dp.roundToPx() } }
    val heroIconRequest = remember(context, normalizedIconUrl, iconSizePx) {
        ImageRequest.Builder(context)
            .data(normalizedIconUrl)
            .size(iconSizePx, iconSizePx)
            .precision(Precision.INEXACT)
            .crossfade(150)
            .build()
    }
    val formattedRating = remember(app.averageRating) {
        if (app.averageRating > 0) String.format(Locale.US, "%.1f", app.averageRating) else "4.8"
    }
    val hostingSource = remember(app.apkUrl) {
        FileSharingLinkResolver.identifyService(app.apkUrl)
    }
    val userOwnReview = remember(reviews, currentUserId) {
        if (currentUserId != null) {
            reviews.firstOrNull { it.userId == currentUserId }
        } else {
            null
        }
    }
    val isCurrentAppDownloading = downloadProgress.currentAppId == app.appId &&
        (downloadProgress.status == DownloadStatus.CONNECTING || downloadProgress.status == DownloadStatus.DOWNLOADING)
    val isCurrentAppCompleted = downloadProgress.currentAppId == app.appId &&
        downloadProgress.status == DownloadStatus.COMPLETED &&
        downloadProgress.downloadedFile?.exists() == true

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D)),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        // App Bar
        item(key = "details_app_bar", contentType = "details_app_bar") {
            val shareInteractionSource = remember { MutableInteractionSource() }
            val isSharePressed by shareInteractionSource.collectIsPressedAsState()
            val shareScale by animateFloatAsState(
                targetValue = if (isSharePressed) 0.90f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "details_share_scale"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("details_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(shareScale)
                            .rgbNeonGlowBorder(
                                seed = "details_share_${app.appId}",
                                cornerRadius = 14.dp,
                                borderWidth = 1.4.dp,
                                glowIntensity = 0.92f
                            )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF131B2E))
                                .clickable(
                                    interactionSource = shareInteractionSource,
                                    indication = androidx.compose.material3.ripple(color = StoreProSecondaryCyan)
                                ) {
                                    if (onShareClick != null) {
                                        onShareClick()
                                    } else {
                                        StoreProShareHelper.shareApplication(context, app)
                                    }
                                }
                                .testTag("details_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share ${app.appName}",
                                tint = StoreProSecondaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (currentUserId == null) {
                                onRequireSignIn("Sign in with Google to bookmark applications in your library.")
                            } else {
                                onToggleSave()
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("details_save_button")
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isSaved) StoreProPrimaryGreen else Color.White
                        )
                    }
                }
            }
        }

        // Hero Info (Icon, Title, Studio, Verified Clean)
        item(key = "details_hero_info", contentType = "details_hero_info") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, StoreProCardBorder, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasValidIcon) {
                        AsyncImage(
                            model = heroIconRequest,
                            imageLoader = imageLoader,
                            contentDescription = app.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = StoreProPrimaryGreen,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = app.developerName,
                        fontSize = 14.sp,
                        color = StoreProSecondaryCyan,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = StoreProPrimaryGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Verified Clean • STORE PRO Protect",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Key stats bar (Rating, Downloads, Size, Version)
        item(key = "details_stats_bar", contentType = "details_stats_bar") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .rgbNeonGlowBorder(
                        seed = "details_stats_${app.appId}",
                        cornerRadius = 16.dp,
                        borderWidth = 1.5.dp,
                        glowIntensity = 0.85f
                    )
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formattedRating,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = StoreProStar,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "${app.ratingCount} reviews",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF263352)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${app.downloads}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.testTag("details_downloads_count")
                        )
                        Text(
                            text = "Downloads",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF263352)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = app.formattedSize,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Download size",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF263352)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "v${app.version}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = StoreProPrimaryGreen
                        )
                        Text(
                            text = "Version",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }

        // Action Buttons: Download in Browser / In-App Stream / Install (Open to all users)
        item(key = "details_download_actions", contentType = "details_download_actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                if (isCurrentAppDownloading) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(StoreProPrimaryGreen, StoreProSecondaryCyan))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = StoreProPrimaryGreen,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Downloading APK: ${downloadProgress.progressPercent}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = onCancelDownload,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { downloadProgress.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = StoreProPrimaryGreen,
                                trackColor = Color(0xFF1E293B)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = downloadProgress.speedFormatted,
                                    fontSize = 11.sp,
                                    color = StoreProSecondaryCyan
                                )
                                Text(
                                    text = downloadProgress.remainingTimeFormatted,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                } else if (isCurrentAppCompleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rgbNeonGlowBorder(
                                seed = "install_btn_${app.appId}",
                                cornerRadius = 16.dp,
                                borderWidth = 1.6.dp,
                                glowIntensity = 0.92f
                            )
                    ) {
                        Button(
                            onClick = { downloadProgress.downloadedFile?.let(onInstallApk) },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StoreProSecondaryCyan,
                                contentColor = Color(0xFF00363D)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("install_apk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Install APK",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rgbNeonGlowBorder(
                                seed = "download_btn_${app.appId}",
                                cornerRadius = 16.dp,
                                borderWidth = 1.6.dp,
                                glowIntensity = 0.95f
                            )
                    ) {
                        Button(
                            onClick = onOpenBrowserDownload,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StoreProPrimaryGreen,
                                contentColor = Color(0xFF003816)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("download_apk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DOWNLOAD (${app.formattedSize})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onStartDownload,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StoreProSecondaryCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StoreProSecondaryCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("download_in_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download Directly in App",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Source: $hostingSource • Opens online in Browser to download",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Screenshots Gallery
        if (app.screenshots.isNotEmpty()) {
            item(key = "details_screenshots", contentType = "details_screenshots") {
                val thumbWidthPx = remember(density) { with(density) { 180.dp.roundToPx() } }
                val thumbHeightPx = remember(density) { with(density) { 280.dp.roundToPx() } }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Screenshots",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = app.screenshots,
                        key = { it },
                        contentType = { "screenshot_thumb" }
                    ) { rawUrl ->
                        val normalized = remember(rawUrl) { UniversalImageUtils.normalizeImageUrl(rawUrl) }
                        val thumbRequest = remember(context, normalized, thumbWidthPx, thumbHeightPx) {
                            ImageRequest.Builder(context)
                                .data(normalized)
                                .size(thumbWidthPx, thumbHeightPx)
                                .precision(Precision.INEXACT)
                                .crossfade(150)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, StoreProCardBorder, RoundedCornerShape(16.dp))
                                .clickable { previewScreenshotUrl = normalized }
                        ) {
                            AsyncImage(
                                model = thumbRequest,
                                imageLoader = imageLoader,
                                contentDescription = "Screenshot",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // About this app
        item(key = "details_about", contentType = "details_about") {
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "About this app",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = app.description,
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 20.sp
                )
            }
        }

        // What's New
        if (app.whatsNew.isNotBlank()) {
            item(key = "details_whats_new", contentType = "details_whats_new") {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .rgbNeonGlowBorder(
                            seed = "details_whats_new_${app.appId}",
                            cornerRadius = 16.dp,
                            borderWidth = 1.4.dp,
                            glowIntensity = 0.80f
                        )
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "What's New in v${app.version}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StoreProPrimaryGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.whatsNew,
                                fontSize = 13.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // Reviews & Ratings Section Header
        item(key = "details_reviews_header", contentType = "details_reviews_header") {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ratings & Reviews (${reviews.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                TextButton(
                    onClick = {
                        if (currentUserId == null) {
                            onRequireSignIn("Sign in with Google to rate and review ${app.appName}.")
                        } else {
                            editingReview = userOwnReview
                            showReviewDialog = true
                        }
                    },
                    modifier = Modifier.testTag("rate_app_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        tint = StoreProSecondaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (userOwnReview != null) "Edit Review" else "Rate App",
                        color = StoreProSecondaryCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Reviews List
        if (reviews.isEmpty()) {
            item(key = "details_no_reviews", contentType = "details_no_reviews") {
                Text(
                    text = "No reviews yet. Be the first to rate this app!",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            items(
                items = reviews,
                key = { it.reviewId.ifBlank { "${it.userId}_${it.timestamp}" } },
                contentType = { "review_card" }
            ) { review ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .rgbNeonGlowBorder(
                            seed = "review_${review.reviewId.ifBlank { review.userId }}",
                            cornerRadius = 14.dp,
                            borderWidth = 1.3.dp,
                            glowIntensity = 0.72f
                        )
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = review.userName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = StoreProSecondaryCyan,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = review.userName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                        Row {
                                            repeat(5) { i ->
                                                Icon(
                                                    imageVector = if (i < review.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = null,
                                                    tint = StoreProStar,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (currentUserId != null && review.userId == currentUserId) {
                                    IconButton(
                                        onClick = { onDeleteReview(review.reviewId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Review",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (review.comment.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = review.comment,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Review Dialog
    if (showReviewDialog) {
        var userRating by remember { mutableStateOf(editingReview?.rating ?: 5L) }
        var userComment by remember { mutableStateOf(editingReview?.comment ?: "") }

        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            containerColor = Color(0xFF131B2E),
            title = {
                Text(
                    text = if (editingReview != null) "Edit Your Review" else "Rate & Review ${app.appName}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "How would you rate this app?",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (i in 1..5) {
                            IconButton(onClick = { userRating = i.toLong() }) {
                                Icon(
                                    imageVector = if (i <= userRating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "$i stars",
                                    tint = StoreProStar,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = userComment,
                        onValueChange = { userComment = it },
                        placeholder = { Text("Write your review (optional)...", color = Color(0xFF64748B), fontSize = 13.sp) },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = StoreProPrimaryGreen,
                            unfocusedBorderColor = StoreProCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("review_comment_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSubmitReview(userRating, userComment, editingReview?.reviewId)
                        showReviewDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StoreProPrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("submit_review_button")
                ) {
                    Text("Submit", color = Color(0xFF003816), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // Fullscreen Screenshot Preview Dialog
    val activePreviewUrl = previewScreenshotUrl
    if (activePreviewUrl != null) {
        val fullPreviewRequest = remember(context, activePreviewUrl) {
            ImageRequest.Builder(context)
                .data(activePreviewUrl)
                .precision(Precision.INEXACT)
                .crossfade(150)
                .build()
        }
        Dialog(onDismissRequest = { previewScreenshotUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { previewScreenshotUrl = null }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullPreviewRequest,
                    imageLoader = imageLoader,
                    contentDescription = "Full Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}
