package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.model.ApplicationItem
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProStar
import com.example.ui.upload.UniversalImageLoader
import com.example.ui.upload.UniversalImageUtils
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppCard(
    app: ApplicationItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember(context) { UniversalImageLoader.get(context) }
    val iconSizePx = remember(density) { with(density) { 76.dp.toPx().roundToInt().coerceAtLeast(144) } }
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

    Box(
        modifier = modifier
            .width(148.dp)
            .rgbNeonGlowBorder(
                seed = "${app.appId}_${app.appName}",
                cornerRadius = 18.dp,
                borderWidth = 1.6.dp,
                glowIntensity = 0.95f
            )
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag("app_card_${app.appId}")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, StoreProCardBorder, RoundedCornerShape(18.dp)),
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
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = app.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = app.developerName,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StoreProStar,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = ratingText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "${app.downloads} dl",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
fun AppListRowItem(
    app: ApplicationItem,
    rank: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember(context) { UniversalImageLoader.get(context) }
    val iconSizePx = remember(density) { with(density) { 54.dp.toPx().roundToInt().coerceAtLeast(108) } }
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
    val rowSubtitle = remember(app.developerName, app.fileSizeBytes) {
        "${app.developerName} • ${app.formattedSize}"
    }
    val rowRatingText = remember(app.averageRating) {
        String.format(Locale.US, "%.1f", app.averageRating)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .rgbNeonGlowBorder(
                seed = "${app.appId}_row_${rank ?: 0}",
                cornerRadius = 16.dp,
                borderWidth = 1.5.dp,
                glowIntensity = 0.9f
            )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag("app_row_${app.appId}")
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
            if (rank != null) {
                Text(
                    text = "$rank",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (rank <= 3) StoreProPrimaryGreen else Color(0xFF64748B),
                    modifier = Modifier.width(28.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, StoreProCardBorder, RoundedCornerShape(14.dp)),
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
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rowSubtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = StoreProStar,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = rowRatingText,
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${app.downloads} downloads",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
        }
    }
}
