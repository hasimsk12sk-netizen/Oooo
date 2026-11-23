package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.data.download.DownloadProgressState
import com.example.data.model.ApplicationItem
import com.example.data.model.CatalogUiState
import com.example.data.model.LiveUpdateDashboardState
import com.example.data.model.UserProfile
import com.example.ui.components.AllAppsCatalogCard
import com.example.ui.components.AppCard
import com.example.ui.components.AppListRowItem
import com.example.ui.components.StoreProSearchBar
import com.example.ui.components.rgbNeonGlowBorder
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.example.ui.upload.UniversalImageLoader
import com.example.ui.upload.UniversalImageUtils
import java.io.File
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    applications: List<ApplicationItem>,
    userProfile: UserProfile?,
    onOpenSearch: () -> Unit,
    onSelectApp: (ApplicationItem) -> Unit,
    onOpenProfile: () -> Unit,
    @Suppress("UNUSED_PARAMETER") liveUpdateState: LiveUpdateDashboardState? = null,
    @Suppress("UNUSED_PARAMETER") onOpenLiveUpdate: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") canAccessAdminTools: Boolean = false,
    catalogState: CatalogUiState? = null,
    downloadProgress: DownloadProgressState = DownloadProgressState(),
    onDownloadApp: (ApplicationItem) -> Unit = onSelectApp,
    onInstallApk: (File) -> Unit = {},
    onOpenAllApps: () -> Unit = {},
    onRetryCatalog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember(context) { UniversalImageLoader.get(context) }
    val avatarSizePx = remember(density) { with(density) { 48.dp.toPx().roundToInt().coerceAtLeast(96) } }
    var isHomeSearchFocused by remember { mutableStateOf(false) }

    val publishedApps = remember(applications) {
        applications
            .filter { it.status.equals("published", ignoreCase = true) || it.status.isBlank() }
            .sortedByDescending { it.createdAt }
    }

    val homeSections = remember(publishedApps) {
        if (publishedApps.isEmpty()) {
            Quadruple(emptyList<ApplicationItem>(), emptyList<ApplicationItem>(), emptyList<ApplicationItem>(), emptyList<ApplicationItem>())
        } else {
            val trending = publishedApps.sortedByDescending { it.downloads }.take(6)
            val topDl = trending.take(5)
            val recent = publishedApps.take(6)
            val popular = publishedApps.sortedByDescending { it.averageRating }.take(6)
            Quadruple(trending, topDl, recent, popular)
        }
    }
    val trendingApps = homeSections.first
    val topDownloaded = homeSections.second
    val recentlyUploaded = homeSections.third
    val popularApps = homeSections.fourth

    val normalizedAvatarUrl = remember(userProfile?.avatarUrl) {
        userProfile?.avatarUrl?.takeIf { it.isNotBlank() }?.let { UniversalImageUtils.normalizeImageUrl(it) }
    }
    val avatarRequest = remember(context, normalizedAvatarUrl, avatarSizePx) {
        normalizedAvatarUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(avatarSizePx, avatarSizePx)
                .precision(Precision.INEXACT)
                .crossfade(150)
                .build()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .testTag("home_lazy_column"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // 1. Top Branding Header & Search Bar
        item(key = "home_header", contentType = "home_header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_storepro_logo_img),
                            contentDescription = "STORE PRO",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "STORE PRO",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Marketplace",
                                fontSize = 11.sp,
                                color = StoreProPrimaryGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenProfile,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131B2E))
                            .border(1.dp, StoreProCardBorder, CircleShape)
                            .testTag("home_profile_button")
                    ) {
                        if (avatarRequest != null) {
                            AsyncImage(
                                model = avatarRequest,
                                imageLoader = imageLoader,
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = StoreProSecondaryCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                StoreProSearchBar(
                    readOnlyLauncher = true,
                    isFocused = isHomeSearchFocused,
                    onFocusChanged = { isHomeSearchFocused = it },
                    onLauncherClick = onOpenSearch,
                    glowTestTag = if (isHomeSearchFocused) "home_search_glow_active" else "home_search_glow_inactive",
                    fieldTestTag = "home_search_bar",
                    glowSeed = "home_search_focus"
                )
            }
        }

        if (publishedApps.isEmpty() && catalogState !is CatalogUiState.Loading && catalogState !is CatalogUiState.Error) {
            item(key = "home_empty_card", contentType = "home_empty") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StoreProCardBorder)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = StoreProSecondaryCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Marketplace Ready",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Published applications will appear here automatically. Administrators can publish new apps using the + button below.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }

        // 2. HASIM APK 3D Animated Showcase Banner (non-interactive)
        item(key = "home_featured_banner", contentType = "featured_banner") {
            FeaturedShowcaseBanner()
        }

        // 3. Trending Applications Row
        if (trendingApps.isNotEmpty()) {
            item(key = "home_trending_section", contentType = "horizontal_app_row") {
                SectionHeader(title = "Trending Applications", icon = Icons.AutoMirrored.Filled.TrendingUp)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trendingApps, key = { "trending_${it.appId}" }, contentType = { "app_card" }) { app ->
                        AppCard(app = app, onClick = { onSelectApp(app) })
                    }
                }
            }
        }

        // 4. Popular Applications Row
        if (popularApps.isNotEmpty()) {
            item(key = "home_popular_section", contentType = "horizontal_app_row") {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "Popular Applications", icon = Icons.Default.Whatshot)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(popularApps, key = { "popular_${it.appId}" }, contentType = { "app_card" }) { app ->
                        AppCard(app = app, onClick = { onSelectApp(app) })
                    }
                }
            }
        }

        // 5. Top Downloaded Applications (Ranked List)
        if (topDownloaded.isNotEmpty()) {
            item(key = "home_top_downloaded_header", contentType = "section_header") {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "Top Downloaded", icon = Icons.Default.AutoAwesome)
            }
            itemsIndexed(
                items = topDownloaded,
                key = { idx, app -> "top_${idx}_${app.appId}" },
                contentType = { _, _ -> "app_row_item" }
            ) { index, app ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    AppListRowItem(
                        app = app,
                        rank = index + 1,
                        onClick = { onSelectApp(app) }
                    )
                }
            }
        }

        // 6. Recently Uploaded Applications Row
        if (recentlyUploaded.isNotEmpty()) {
            item(key = "home_recent_section", contentType = "horizontal_app_row") {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "Recently Uploaded", icon = Icons.Default.AutoAwesome)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyUploaded, key = { "recent_${it.appId}" }, contentType = { "app_card" }) { app ->
                        AppCard(app = app, onClick = { onSelectApp(app) })
                    }
                }
            }
        }

        // 7. ALL APPS Section (Immediately BELOW Recently Uploaded, framed in animated RGB neon border)
        item(key = "home_all_apps_header", contentType = "all_apps_section_header") {
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .rgbNeonGlowBorder(
                        seed = "home_all_apps_section",
                        cornerRadius = 20.dp,
                        borderWidth = 1.7.dp,
                        glowIntensity = 0.95f
                    )
                    .testTag("home_all_apps_section")
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                StoreProPrimaryGreen.copy(alpha = 0.22f),
                                                StoreProSecondaryCyan.copy(alpha = 0.22f)
                                            )
                                        )
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = "ALL APPS",
                                    tint = StoreProPrimaryGreen,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "ALL APPS",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.9.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = StoreProPrimaryGreen.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, StoreProPrimaryGreen.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = "${publishedApps.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = StoreProPrimaryGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Complete STORE PRO published catalog",
                                    fontSize = 11.5.sp,
                                    color = StoreProSecondaryCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .rgbNeonGlowBorder(
                                    seed = "see_all_button",
                                    cornerRadius = 12.dp,
                                    borderWidth = 1.3.dp,
                                    glowIntensity = 0.9f
                                )
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenAllApps)
                                    .testTag("home_all_apps_see_all_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "See All",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = StoreProSecondaryCyan
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "See All Applications",
                                        tint = StoreProSecondaryCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when {
            catalogState is CatalogUiState.Loading && publishedApps.isEmpty() -> {
                item(key = "home_all_apps_loading", contentType = "all_apps_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = StoreProPrimaryGreen,
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            catalogState is CatalogUiState.Error && publishedApps.isEmpty() -> {
                item(key = "home_all_apps_error", contentType = "all_apps_error") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                        border = BorderStroke(1.dp, StoreProCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = catalogState.message,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onRetryCatalog,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StoreProPrimaryGreen,
                                    contentColor = Color(0xFF003816)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            publishedApps.isNotEmpty() -> {
                items(
                    items = publishedApps,
                    key = { "home_all_${it.appId}" },
                    contentType = { "all_apps_card" }
                ) { app ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        AllAppsCatalogCard(
                            app = app,
                            downloadProgress = downloadProgress,
                            onSelectApp = { onSelectApp(app) },
                            onDownloadClick = { onDownloadApp(app) },
                            onInstallApk = onInstallApk,
                            testTagPrefix = "home_all_apps"
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = StoreProPrimaryGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
