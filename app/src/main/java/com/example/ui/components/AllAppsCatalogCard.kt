package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.download.DownloadProgressState
import com.example.data.download.DownloadStatus
import com.example.data.model.ApplicationItem
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.example.ui.theme.StoreProStar
import com.example.ui.upload.UniversalImageLoader
import com.example.ui.upload.UniversalImageUtils
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-catalog application card used in Home's "ALL APPS" section and on AllAppsScreen.
 * Displays icon, app name, developer, version, file size, rating, download count, and an
 * inline interactive Download / Install button wrapped in an animated RGB neon border.
 */
@Composable
fun AllAppsCatalogCard(
    app: ApplicationItem,
    downloadProgress: DownloadProgressState = DownloadProgressState(),
    onSelectApp: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallApk: (File) -> Unit = {},
    modifier: Modifier = Modifier,
    testTagPrefix: String = "all_apps"
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember(context) { UniversalImageLoader.get(context) }
    val iconSizePx = remember(density) { with(density) { 58.dp.toPx().roundToInt().coerceAtLeast(116) } }
    val normalizedIconUrl = remember(app.iconUrl) { UniversalImageUtils.normalizeImageUrl(app.iconUrl) }
    val hasValidIcon = remember(normalizedIconUrl) { UniversalImageUtils.isValidImageUrl(normalizedIconUrl) }
    val iconRequest = remember(context, normalizedIconUrl, hasValidIcon, iconSizePx) {
        if (hasValidIcon) {
            ImageRequest.Builder(context)
                .data(normalizedIconUrl)
                .size(iconSizePx, iconSizePx)
                .precision(Precision.INEXACT)
                .crossfade(150)
                .build()
        } else {
            null
        }
    }

    val ratingText = remember(app.averageRating) {
        if (app.averageRating > 0) String.format(Locale.US, "%.1f", app.averageRating) else "4.8"
    }
    val metaLine = remember(app.version, app.fileSizeBytes, app.downloads) {
        "v${app.version} • ${app.formattedSize} • ${app.downloads} dl"
    }

    val isDownloading = downloadProgress.currentAppId == app.appId &&
        (downloadProgress.status == DownloadStatus.CONNECTING || downloadProgress.status == DownloadStatus.DOWNLOADING)
    val isReadyToInstall = downloadProgress.currentAppId == app.appId &&
        downloadProgress.status == DownloadStatus.COMPLETED &&
        downloadProgress.downloadedFile?.exists() == true

    Box(
        modifier = modifier
            .fillMaxWidth()
            .rgbNeonGlowBorder(
                seed = "all_apps_${app.appId}_${app.appName}",
                cornerRadius = 18.dp,
                borderWidth = 1.5.dp,
                glowIntensity = 0.88f
            )
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectApp)
                .testTag("${testTagPrefix}_card_${app.appId}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, StoreProCardBorder, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconRequest != null) {
                        AsyncImage(
                            model = iconRequest,
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
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.developerName.ifBlank { "STORE PRO Publisher" },
                        fontSize = 12.sp,
                        color = StoreProSecondaryCyan,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StoreProStar,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = ratingText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = metaLine,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (isReadyToInstall) {
                            downloadProgress.downloadedFile?.let(onInstallApk)
                        } else if (!isDownloading) {
                            onDownloadClick()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReadyToInstall) StoreProSecondaryCyan else StoreProPrimaryGreen,
                        contentColor = if (isReadyToInstall) Color(0xFF00363D) else Color(0xFF003816)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("${testTagPrefix}_download_${app.appId}")
                ) {
                    when {
                        isDownloading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF003816),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${downloadProgress.progressPercent}%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        isReadyToInstall -> {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = "Install ${app.appName}",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Install",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download ${app.appName}",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Download",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}
