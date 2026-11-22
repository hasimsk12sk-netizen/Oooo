package com.example.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.liveupdate.LiveUpdateApkVerifier
import com.example.data.model.ApplicationItem
import com.example.data.model.LiveUpdateDashboardState
import com.example.data.model.UserProfile
import com.example.ui.components.AppCard
import com.example.ui.components.AppListRowItem
import com.example.ui.liveupdate.LiveUpdateButton
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.example.ui.upload.UniversalImageLoader
import com.example.ui.upload.UniversalImageUtils

@Composable
fun HomeScreen(
    applications: List<ApplicationItem>,
    userProfile: UserProfile?,
    onOpenSearch: () -> Unit,
    onSelectApp: (ApplicationItem) -> Unit,
    onOpenProfile: () -> Unit,
    liveUpdateState: LiveUpdateDashboardState? = null,
    onOpenLiveUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedLiveUpdateState = liveUpdateState ?: remember(context) {
        LiveUpdateDashboardState(installed = LiveUpdateApkVerifier.readInstalledAppDetails(context))
    }
    val trendingApps = remember(applications) { applications.sortedByDescending { it.downloads }.take(6) }
    val topDownloaded = remember(trendingApps) { trendingApps.take(5) }
    val recentlyUploaded = remember(applications) { applications.sortedByDescending { it.createdAt }.take(6) }
    val popularApps = remember(applications) { applications.sortedByDescending { it.averageRating }.take(6) }
    val normalizedAvatarUrl = remember(userProfile?.avatarUrl) {
        userProfile?.avatarUrl?.takeIf { it.isNotBlank() }?.let { UniversalImageUtils.normalizeImageUrl(it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .testTag("home_lazy_column"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // 1. Top Branding Header & Search Bar
        item {
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
                        if (normalizedAvatarUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(normalizedAvatarUrl)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = UniversalImageLoader.get(context),
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

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(StoreProCardBorder, Color(0xFF1E293B)))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSearch() }
                        .testTag("home_search_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = StoreProSecondaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Search apps, developers, keywords...",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LiveUpdateButton(
                        state = resolvedLiveUpdateState,
                        onClick = onOpenLiveUpdate
                    )
                }
            }
        }

        if (applications.isEmpty()) {
            item {
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
        item {
            FeaturedShowcaseBanner()
        }

        // 3. Trending Applications Row
        if (trendingApps.isNotEmpty()) {
            item {
                SectionHeader(title = "Trending Applications", icon = Icons.AutoMirrored.Filled.TrendingUp)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trendingApps, key = { "trending_${it.appId}" }) { app ->
                        AppCard(app = app, onClick = { onSelectApp(app) })
                    }
                }
            }
        }

        // 4. Popular Applications Row
        if (popularApps.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "Popular Applications", icon = Icons.Default.Whatshot)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(popularApps, key = { "popular_${it.appId}" }) { app ->
                        AppCard(app = app, onClick = { onSelectApp(app) })
                    }
                }
            }
        }

        // 5. Top Downloaded Applications (Ranked List)
        if (topDownloaded.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "Top Downloaded", icon = Icons.Default.AutoAwesome)
            }
            itemsIndexed(topDownloaded, key = { idx, app -> "top_${idx}_${app.appId}" }) { index, app ->
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
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "Recently Uploaded", icon = Icons.Default.AutoAwesome)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyUploaded, key = { "recent_${it.appId}" }) { app ->
                        AppCard(app = app, onClick = { onSelectApp(app) })
                    }
                }
            }
        }
    }
}

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
