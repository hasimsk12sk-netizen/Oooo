package com.example.ui.liveupdate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HistoryFilterOption
import com.example.data.model.UpdateHistoryRecord
import com.example.ui.admin.AdminAuthManager
import com.example.ui.theme.StoreProBackground
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryDateFormatter: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
}

@Composable
fun UpdateHistoryScreen(
    records: List<UpdateHistoryRecord>,
    onSelectRecord: (UpdateHistoryRecord) -> Unit,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!AdminAuthManager.isFullyAuthorizedAdmin(context, Firebase.auth.currentUser)) {
            onUnauthorized()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HistoryFilterOption.ALL) }

    val filteredRecords = remember(records, searchQuery, selectedFilter) {
        val q = searchQuery.trim()
        val searched = if (q.isEmpty()) {
            records
        } else {
            records.filter { rec ->
                rec.versionName.contains(q, ignoreCase = true) ||
                    rec.versionCode.toString().contains(q, ignoreCase = true) ||
                    rec.releaseNotes.contains(q, ignoreCase = true) ||
                    rec.changelog.contains(q, ignoreCase = true) ||
                    rec.apkFileName.contains(q, ignoreCase = true) ||
                    rec.adminEmail.contains(q, ignoreCase = true)
            }
        }
        when (selectedFilter) {
            HistoryFilterOption.ALL,
            HistoryFilterOption.LATEST_TO_OLDEST ->
                searched.sortedWith(compareByDescending<UpdateHistoryRecord> { it.releaseTimestamp }.thenByDescending { it.versionCode })
            HistoryFilterOption.OLDEST_TO_LATEST ->
                searched.sortedWith(compareBy<UpdateHistoryRecord> { it.releaseTimestamp }.thenBy { it.versionCode })
            HistoryFilterOption.MANDATORY ->
                searched.filter { it.mandatoryUpdate }.sortedByDescending { it.releaseTimestamp }
            HistoryFilterOption.OPTIONAL ->
                searched.filter { !it.mandatoryUpdate }.sortedByDescending { it.releaseTimestamp }
            HistoryFilterOption.APK_AVAILABLE ->
                searched.filter { it.apkAvailable && it.apkDownloadUrl.isNotBlank() }.sortedByDescending { it.releaseTimestamp }
            HistoryFilterOption.APK_UNAVAILABLE ->
                searched.filter { !it.apkAvailable || it.apkDownloadUrl.isBlank() }.sortedByDescending { it.releaseTimestamp }
        }
    }

    val availableCount = remember(records) { records.count { it.apkAvailable && it.apkDownloadUrl.isNotBlank() } }
    val mandatoryCount = remember(records) { records.count { it.mandatoryUpdate } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StoreProBackground)
            .testTag("update_history_screen")
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoreProCardBackground)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("history_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.History, contentDescription = null, tint = StoreProSecondaryCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Live Update History",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Admin-Only Permanent Release Archive (${records.size})",
                    fontSize = 11.5.sp,
                    color = StoreProSecondaryCyan
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryStatPill("Total", records.size.toString(), StoreProSecondaryCyan, Modifier.weight(1f))
                HistoryStatPill("APK Ready", availableCount.toString(), StoreProPrimaryGreen, Modifier.weight(1f))
                HistoryStatPill("Mandatory", mandatoryCount.toString(), Color(0xFFFBBF24), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Search version, notes, APK filename...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = StoreProSecondaryCyan) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = StoreProCardBackground,
                    unfocusedContainerColor = StoreProCardBackground,
                    focusedBorderColor = StoreProSecondaryCyan,
                    unfocusedBorderColor = StoreProCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilterOption.entries.forEach { option ->
                    val isSelected = selectedFilter == option
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = option },
                        label = { Text(option.label, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StoreProSecondaryCyan.copy(alpha = 0.22f),
                            selectedLabelColor = StoreProSecondaryCyan,
                            containerColor = StoreProCardBackground,
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = StoreProCardBorder,
                            selectedBorderColor = StoreProSecondaryCyan
                        ),
                        modifier = Modifier.testTag("history_filter_${option.name}")
                    )
                }
            }
        }

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                    border = BorderStroke(1.dp, StoreProCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (records.isEmpty()) "No Published Releases Yet" else "No Matching History Records",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Every published STORE PRO update APK and release record is permanently preserved here for Admin review and re-download.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("update_history_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredRecords,
                    key = { it.historyId },
                    contentType = { "history_record" }
                ) { record ->
                    HistoryReleaseCard(
                        record = record,
                        onClick = { onSelectRecord(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StoreProCardBackground,
        border = BorderStroke(1.dp, StoreProCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(text = label, fontSize = 10.5.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun HistoryReleaseCard(
    record: UpdateHistoryRecord,
    onClick: () -> Unit
) {
    val hasApk = record.apkAvailable && record.apkDownloadUrl.isNotBlank()
    val publishedDate = remember(record.releaseTimestamp, record.releaseDate) {
        if (record.releaseTimestamp > 0L) {
            HistoryDateFormatter.get()?.format(Date(record.releaseTimestamp)) ?: record.releaseDate.ifBlank { "Unknown date" }
        } else {
            record.releaseDate.ifBlank { "Unknown date" }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(1.dp, StoreProCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_item_card_${record.historyId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORE PRO v${record.versionName} (Build ${record.versionCode})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View release details",
                    tint = StoreProSecondaryCyan
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryBadge(
                    text = if (record.mandatoryUpdate) "Mandatory" else "Optional",
                    color = if (record.mandatoryUpdate) Color(0xFFFBBF24) else StoreProSecondaryCyan
                )
                HistoryBadge(
                    text = if (hasApk) "APK Available" else "Unavailable",
                    color = if (hasApk) StoreProPrimaryGreen else Color(0xFFF87171)
                )
                HistoryBadge(
                    text = formatCompactMb(record.apkFileSize),
                    color = Color(0xFF94A3B8)
                )
            }

            Text(
                text = "Published $publishedDate • ${record.adminEmail.ifBlank { "Admin" }}",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val previewNotes = record.releaseNotes.ifBlank { record.changelog }
            if (previewNotes.isNotBlank()) {
                Text(
                    text = previewNotes,
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun HistoryBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}
