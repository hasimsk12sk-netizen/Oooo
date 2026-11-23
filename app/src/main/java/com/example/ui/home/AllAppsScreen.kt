package com.example.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.download.DownloadProgressState
import com.example.data.model.AppCategory
import com.example.data.model.AppSortOption
import com.example.data.model.ApplicationItem
import com.example.data.model.CatalogUiState
import com.example.data.model.resolveCategory
import com.example.ui.components.AllAppsCatalogCard
import com.example.ui.components.StoreProSearchBar
import com.example.ui.components.rgbNeonGlowBorder
import com.example.ui.theme.StoreProBackground
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import java.io.File

/**
 * Full STORE PRO Catalog screen displaying every published application from Firestore
 * with real-time search, sorting (Latest, Popularity, Most Downloaded), category filters,
 * loading indicator, error retry UI, and smooth LazyColumn scrolling.
 */
@Composable
fun AllAppsScreen(
    catalogState: CatalogUiState,
    downloadProgress: DownloadProgressState = DownloadProgressState(),
    onSelectApp: (ApplicationItem) -> Unit,
    onDownloadApp: (ApplicationItem) -> Unit,
    onInstallApk: (File) -> Unit = {},
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(AppSortOption.LATEST) }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }

    val publishedApps = remember(catalogState) {
        when (catalogState) {
            is CatalogUiState.Success -> catalogState.applications
            is CatalogUiState.Error -> catalogState.cachedApplications
            CatalogUiState.Loading -> emptyList()
        }
    }

    val filteredSortedApps = remember(publishedApps, searchQuery, selectedSort, selectedCategory) {
        val q = searchQuery.trim()
        val categoryFiltered = publishedApps.asSequence()
            .filter { app ->
                selectedCategory == AppCategory.ALL || app.resolveCategory() == selectedCategory
            }
            .filter { app ->
                q.isEmpty() ||
                    app.appName.contains(q, ignoreCase = true) ||
                    app.developerName.contains(q, ignoreCase = true) ||
                    app.description.contains(q, ignoreCase = true) ||
                    app.packageId.contains(q, ignoreCase = true) ||
                    app.whatsNew.contains(q, ignoreCase = true)
            }
            .toList()

        when (selectedSort) {
            AppSortOption.LATEST -> categoryFiltered.sortedByDescending { it.createdAt }
            AppSortOption.POPULARITY -> categoryFiltered.sortedWith(
                compareByDescending<ApplicationItem> { it.averageRating }
                    .thenByDescending { it.ratingCount }
                    .thenByDescending { it.downloads }
            )
            AppSortOption.DOWNLOADS -> categoryFiltered.sortedByDescending { it.downloads }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StoreProBackground)
            .testTag("all_apps_screen")
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoreProCardBackground)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("all_apps_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = StoreProPrimaryGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ALL APPS",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${filteredSortedApps.size} of ${publishedApps.size} published apps",
                        fontSize = 11.sp,
                        color = StoreProSecondaryCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .testTag("all_apps_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh All Apps",
                    tint = StoreProSecondaryCyan
                )
            }
        }

        // Search + Sort + Category Filter Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            StoreProSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                isFocused = isSearchFocused,
                onFocusChanged = { isSearchFocused = it },
                placeholderText = "Search all applications...",
                glowTestTag = if (isSearchFocused) "all_apps_search_glow_active" else "all_apps_search_glow_inactive",
                fieldTestTag = "all_apps_search_input",
                glowSeed = "all_apps_search"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sort Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort options",
                    tint = StoreProPrimaryGreen,
                    modifier = Modifier.size(16.dp)
                )
                AppSortOption.entries.forEach { sortOption ->
                    val selected = selectedSort == sortOption
                    FilterChip(
                        selected = selected,
                        onClick = { selectedSort = sortOption },
                        label = {
                            Text(
                                text = sortOption.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StoreProPrimaryGreen.copy(alpha = 0.20f),
                            selectedLabelColor = StoreProPrimaryGreen,
                            containerColor = StoreProCardBackground,
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = StoreProCardBorder,
                            selectedBorderColor = StoreProPrimaryGreen
                        ),
                        modifier = Modifier.testTag("all_apps_sort_${sortOption.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppCategory.entries.forEach { category ->
                    val selected = selectedCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StoreProSecondaryCyan.copy(alpha = 0.20f),
                            selectedLabelColor = StoreProSecondaryCyan,
                            containerColor = StoreProCardBackground,
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = StoreProCardBorder,
                            selectedBorderColor = StoreProSecondaryCyan
                        ),
                        modifier = Modifier.testTag("all_apps_category_${category.name}")
                    )
                }
            }
        }

        // Body Content (Loading / Error / Empty / Catalog List)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                catalogState is CatalogUiState.Loading && publishedApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("all_apps_loading_indicator"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = StoreProPrimaryGreen,
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading published apps from STORE PRO...",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                catalogState is CatalogUiState.Error && publishedApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 460.dp)
                                .rgbNeonGlowBorder(
                                    seed = "all_apps_error_card",
                                    cornerRadius = 18.dp,
                                    borderWidth = 1.5.dp,
                                    glowIntensity = 0.85f
                                )
                        ) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("all_apps_error_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Could Not Load Applications",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = catalogState.message,
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = onRetry,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StoreProPrimaryGreen,
                                            contentColor = Color(0xFF003816)
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("all_apps_retry_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                filteredSortedApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = StoreProCardBackground,
                            border = BorderStroke(1.dp, StoreProCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 460.dp)
                                .testTag("all_apps_empty_state")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = StoreProSecondaryCyan,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (publishedApps.isEmpty()) {
                                        "No Published Apps Yet"
                                    } else {
                                        "No Matching Applications"
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (publishedApps.isEmpty()) {
                                        "Newly uploaded and published applications will automatically appear here."
                                    } else {
                                        "Try adjusting your search keywords or selected category filter."
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isNotBlank() || selectedCategory != AppCategory.ALL) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            searchQuery = ""
                                            selectedCategory = AppCategory.ALL
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, StoreProSecondaryCyan),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StoreProSecondaryCyan),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("all_apps_clear_filters_button")
                                    ) {
                                        Text("Reset Filters", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("all_apps_lazy_column"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = filteredSortedApps,
                            key = { it.appId },
                            contentType = { "all_apps_full_card" }
                        ) { app ->
                            AllAppsCatalogCard(
                                app = app,
                                downloadProgress = downloadProgress,
                                onSelectApp = { onSelectApp(app) },
                                onDownloadClick = { onDownloadApp(app) },
                                onInstallApk = onInstallApk,
                                testTagPrefix = "all_apps_screen"
                            )
                        }
                        item(key = "all_apps_bottom_spacer", contentType = "spacer") {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}
