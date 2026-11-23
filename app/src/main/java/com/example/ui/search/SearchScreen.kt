package com.example.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SearchQueryEntity
import com.example.data.local.StoreProDatabase
import com.example.data.model.ApplicationItem
import com.example.ui.components.AppListRowItem
import com.example.ui.components.StoreProSearchBar
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val SearchQuickKeywords = listOf("Media", "Messenger", "Player", "Stream", "Tools", "Beta", "Audio")

@Composable
fun SearchScreen(
    applications: List<ApplicationItem>,
    onSelectApp: (ApplicationItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchHistoryDao = remember(context) { StoreProDatabase.getInstance(context).searchHistoryDao() }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Room Database: Observe top 5 recent search queries reactively
    val recentQueries by searchHistoryDao.getRecentQueries().collectAsStateWithLifecycle(initialValue = emptyList())

    val popularPreview = remember(applications) { applications.take(5) }

    // Filter applications with zero temporary String.lowercase() allocations per keystroke
    val searchResults = remember(searchQuery, applications) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            applications.filter {
                it.appName.contains(q, ignoreCase = true) ||
                    it.developerName.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true) ||
                    it.whatsNew.contains(q, ignoreCase = true)
            }
        }
    }

    val quickKeywords = SearchQuickKeywords

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Compact Top Navigation Header (keeps full screen width for the search box below)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Search STORE PRO",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Unified full-width Search Bar matching Home Screen proportions identically
        StoreProSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            readOnlyLauncher = false,
            isFocused = isSearchFocused,
            onFocusChanged = { isSearchFocused = it },
            focusRequester = searchFocusRequester,
            onSearchSubmit = {
                val trimmed = searchQuery.trim()
                if (trimmed.isNotEmpty()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        searchHistoryDao.insertQuery(SearchQueryEntity(trimmed))
                    }
                }
                keyboardController?.hide()
            },
            glowTestTag = if (isSearchFocused) "search_box_rgb_active" else "search_box_rgb_inactive",
            fieldTestTag = "search_input_field",
            glowSeed = "search_input_focus"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickKeywords.forEach { keyword ->
                SuggestionChip(
                    onClick = {
                        searchQuery = keyword
                        coroutineScope.launch {
                            searchHistoryDao.insertQuery(SearchQueryEntity(keyword))
                        }
                    },
                    label = { Text(keyword, fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF131B2E),
                        labelColor = if (searchQuery.equals(keyword, ignoreCase = true)) StoreProPrimaryGreen else Color(0xFFCBD5E1)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = if (searchQuery.equals(keyword, ignoreCase = true)) StoreProPrimaryGreen else StoreProCardBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // When no search query is active, show the Room Database Search History (Last 5 queries)
        if (searchQuery.isBlank()) {
            if (recentQueries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = StoreProSecondaryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Searches (Room DB)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                searchHistoryDao.clearHistory()
                            }
                        }
                    ) {
                        Text("Clear All", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StoreProCardBorder)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        recentQueries.forEach { queryItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = queryItem
                                        coroutineScope.launch {
                                            searchHistoryDao.insertQuery(SearchQueryEntity(queryItem))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = queryItem,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 14.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            searchHistoryDao.deleteQuery(queryItem)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Remove search",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recommendations preview
            Text(
                text = "Popular on STORE PRO",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = popularPreview,
                    key = { "pop_${it.appId}" },
                    contentType = { "app_row_item" }
                ) { app ->
                    AppListRowItem(
                        app = app,
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                searchHistoryDao.insertQuery(SearchQueryEntity(app.appName))
                            }
                            onSelectApp(app)
                        }
                    )
                }
            }

        } else {
            // Search Query Active
            if (searchResults.isEmpty()) {
                // No Results Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No applications found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "No results match \"$searchQuery\". Try checking the spelling or searching with general terms.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = "${searchResults.size} results found",
                    fontSize = 13.sp,
                    color = StoreProSecondaryCyan,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = searchResults,
                        key = { "res_${it.appId}" },
                        contentType = { "app_row_item" }
                    ) { app ->
                        AppListRowItem(
                            app = app,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    searchHistoryDao.insertQuery(SearchQueryEntity(searchQuery.trim()))
                                }
                                onSelectApp(app)
                            }
                        )
                    }
                }
            }
        }
    }
}
