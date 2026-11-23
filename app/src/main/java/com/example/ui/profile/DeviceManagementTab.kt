package com.example.ui.profile

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RegisteredDevice
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DateOnlyFormatterLocal = ThreadLocal.withInitial { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
private val DateTimeFormatterLocal = ThreadLocal.withInitial { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

internal fun prepareGenuineDevices(devices: List<RegisteredDevice>): List<RegisteredDevice> {
    if (devices.isEmpty()) return emptyList()
    return devices
        .filter { it.isGenuineDeviceRecord() }
        .distinctBy { it.installationId }
        .sortedByDescending { it.lastActiveAt }
}

internal fun filterGenuineDevices(
    genuineDevices: List<RegisteredDevice>,
    searchQuery: String
): List<RegisteredDevice> {
    val trimmedQuery = searchQuery.trim()
    if (trimmedQuery.isEmpty() || genuineDevices.isEmpty()) return genuineDevices
    return genuineDevices.filter { device ->
        val accountLabel = if (device.isLoggedInAccount) "Logged In Gmail" else "Guest"
        device.deviceName.contains(trimmedQuery, ignoreCase = true) ||
            device.manufacturer.contains(trimmedQuery, ignoreCase = true) ||
            device.brand.contains(trimmedQuery, ignoreCase = true) ||
            device.deviceModel.contains(trimmedQuery, ignoreCase = true) ||
            device.modelCode.contains(trimmedQuery, ignoreCase = true) ||
            device.associatedEmail.orEmpty().contains(trimmedQuery, ignoreCase = true) ||
            device.associatedUserId.orEmpty().contains(trimmedQuery, ignoreCase = true) ||
            accountLabel.contains(trimmedQuery, ignoreCase = true) ||
            device.installationId.contains(trimmedQuery, ignoreCase = true)
    }
}

/**
 * Renders the Admin-only real-time Device Management dashboard inside My Library's LazyColumn.
 * Synchronized via live Firestore listeners plus a dedicated manual Refresh action, deduplicated
 * per installationId, displays accurate counts (Total = Active + Offline), supports search across
 * hardware/model/email/account, full hardware & network details, stale cleanup, Block/Unblock, and Delete.
 */
fun LazyListScope.deviceManagementTabItems(
    genuineDevices: List<RegisteredDevice>,
    filteredDevices: List<RegisteredDevice>,
    nowMs: Long = System.currentTimeMillis(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    expandedDeviceIds: Set<String> = emptySet(),
    onToggleExpandDevice: (String) -> Unit = {},
    onToggleBlock: (RegisteredDevice) -> Unit,
    onRequestDeleteDevice: (RegisteredDevice) -> Unit,
    isRefreshing: Boolean = false,
    lastSyncAtMs: Long = 0L,
    onRefreshDevices: () -> Unit = {},
    onCleanupStaleDevices: () -> Unit = {}
) {
    val totalDevices = genuineDevices.size
    val activeDevices = genuineDevices.count { it.isCurrentlyActive(nowMs) }
    val offlineDevices = totalDevices - activeDevices
    val blockedDevices = genuineDevices.count { it.isBlocked }
    val staleCount = genuineDevices.count { it.isStaleOffline(nowMs) }

    item(key = "device_dashboard_summary", contentType = "device_summary") {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
            border = BorderStroke(1.dp, StoreProCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("device_dashboard_summary")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Registered Devices",
                            tint = StoreProSecondaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Live Device Dashboard",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (lastSyncAtMs > 0L) {
                                    "Synced ${formatRelativeTime(lastSyncAtMs, nowMs)}"
                                } else {
                                    "Real-time Firestore sync active"
                                },
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = StoreProPrimaryGreen.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, StoreProPrimaryGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("device_live_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StoreProPrimaryGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StoreProPrimaryGreen
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onRefreshDevices,
                            enabled = !isRefreshing,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StoreProSecondaryCyan.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StoreProSecondaryCyan),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("device_refresh_button")
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    color = StoreProSecondaryCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refreshing", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Devices",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("REFRESH", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (staleCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCleanupStaleDevices,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("device_cleanup_stale_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Clean stale devices",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove $staleCount Stale Offline Record(s) (>30d inactive)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeviceMetricTile(
                        label = "Total Devices",
                        value = totalDevices.toString(),
                        accent = StoreProSecondaryCyan,
                        testTag = "device_total_count",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceMetricTile(
                        label = "Active Now",
                        value = activeDevices.toString(),
                        accent = StoreProPrimaryGreen,
                        testTag = "device_active_count",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeviceMetricTile(
                        label = "Offline",
                        value = offlineDevices.toString(),
                        accent = Color(0xFF94A3B8),
                        testTag = "device_offline_count",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceMetricTile(
                        label = "Blocked",
                        value = blockedDevices.toString(),
                        accent = Color(0xFFF87171),
                        testTag = "device_blocked_count",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Search by name, brand, model, or account",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search devices",
                            tint = StoreProSecondaryCyan
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("device_search_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear device search",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProSecondaryCyan,
                        unfocusedBorderColor = StoreProCardBorder,
                        focusedContainerColor = Color(0xFF0A0F1D),
                        unfocusedContainerColor = Color(0xFF0A0F1D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_search_input")
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (genuineDevices.isEmpty()) {
        item(key = "device_empty_state", contentType = "device_empty") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No genuine devices detected yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Physical user devices will appear here automatically in real time as STORE PRO is used.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    } else if (filteredDevices.isEmpty()) {
        item(key = "device_search_empty", contentType = "device_empty") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("device_search_empty")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No matching devices",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try searching by another device name, manufacturer, brand, model, or account.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    } else {
        items(
            items = filteredDevices,
            key = { it.installationId },
            contentType = { "device_card" }
        ) { device ->
            RegisteredDeviceCard(
                device = device,
                nowMs = nowMs,
                isExpanded = expandedDeviceIds.contains(device.installationId),
                onToggleExpand = { onToggleExpandDevice(device.installationId) },
                onToggleBlock = { onToggleBlock(device) },
                onDelete = { onRequestDeleteDevice(device) }
            )
        }
    }
}

@Composable
fun DeviceDeleteConfirmationDialog(
    device: RegisteredDevice,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val resolvedName = device.deviceName.ifBlank { RegisteredDevice.NOT_AVAILABLE }
    val resolvedModel = device.deviceModel.ifBlank { RegisteredDevice.NOT_AVAILABLE }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StoreProCardBackground,
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        title = {
            Text(
                text = "Delete Device Record",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Text(
                text = "Permanently remove \"$resolvedName\" ($resolvedModel) from the STORE PRO device registry? This action cannot be undone.",
                fontSize = 13.sp
            )
        },
        confirmButton = {
            OutlinedButton(
                onClick = onConfirmDelete,
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("device_delete_confirm_button")
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("device_delete_cancel_button")
            ) {
                Text("Cancel", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
            }
        },
        modifier = Modifier.testTag("device_delete_confirm_dialog")
    )
}

@Composable
private fun DeviceMetricTile(
    label: String,
    value: String,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0A0F1D),
        border = BorderStroke(1.dp, StoreProCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                modifier = Modifier.testTag(testTag)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RegisteredDeviceCard(
    device: RegisteredDevice,
    nowMs: Long,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleBlock: () -> Unit,
    onDelete: () -> Unit
) {
    val isBlocked = device.isBlocked
    val isActive = device.isCurrentlyActive(nowMs)
    val isStale = !isBlocked && !isActive && device.isStaleOffline(nowMs)
    val statusColor = when {
        isBlocked -> Color(0xFFF87171)
        isActive -> StoreProPrimaryGreen
        isStale -> Color(0xFFFBBF24)
        else -> Color(0xFF94A3B8)
    }
    val statusLabel = when {
        isBlocked -> "Blocked"
        isActive -> "Online"
        isStale -> "Offline (Stale)"
        else -> "Offline"
    }
    val accountLabel = if (device.isLoggedInAccount) "Gmail" else "Guest"
    val accountColor = if (device.isLoggedInAccount) StoreProSecondaryCyan else Color(0xFFFBBF24)

    val resolvedName = device.deviceName.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
    val resolvedModel = device.deviceModel.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
    val resolvedManufacturer = device.manufacturer.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
    val resolvedBrand = device.brand.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
    val resolvedDeviceType = device.deviceType.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
    val resolvedAndroidVersion = device.androidVersion.trim().takeIf {
        it.isNotBlank() && !it.equals(RegisteredDevice.NOT_AVAILABLE, ignoreCase = true)
    }?.let { "Android $it" } ?: RegisteredDevice.NOT_AVAILABLE
    val resolvedSdkVersion = if (device.sdkVersion > 0) "API ${device.sdkVersion}" else RegisteredDevice.NOT_AVAILABLE
    val resolvedEmail = device.associatedEmail?.trim()?.ifBlank { null } ?: RegisteredDevice.NOT_AVAILABLE
    val firstUsedText = remember(device.effectiveFirstUsedAt) {
        formatDateOnly(device.effectiveFirstUsedAt)
    }
    val installedDateText = remember(device.effectiveInstalledAt) {
        formatTimestamp(device.effectiveInstalledAt)
    }
    val usageDurationText = device.formattedUsageDuration(nowMs)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(
            1.dp,
            if (isBlocked) Color(0xFFEF4444).copy(alpha = 0.5f) else StoreProCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("device_card_${device.installationId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = "Device",
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolvedName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = resolvedModel,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(resolvedManufacturer)
                            append(" • Last seen ")
                            append(formatRelativeTime(device.lastActiveAt, nowMs))
                            if (device.isLoggedInAccount && resolvedEmail != RegisteredDevice.NOT_AVAILABLE) {
                                append(" • ")
                                append(resolvedEmail)
                            }
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accountColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = accountLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accountColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("device_details_toggle_${device.installationId}")
                ) {
                    Text(
                        text = if (isExpanded) "Hide Details" else "Details",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StoreProSecondaryCyan
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Hide Device Details" else "Show Device Details",
                        tint = StoreProSecondaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onToggleBlock,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isBlocked) StoreProPrimaryGreen else Color(0xFFEF4444)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isBlocked) StoreProPrimaryGreen else Color(0xFFFCA5A5)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("device_block_toggle_${device.installationId}")
                ) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = if (isBlocked) "Unblock Device" else "Block Device",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBlocked) "Unblock" else "Block",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("device_delete_${device.installationId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Device",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                val resolvedModelCode = device.modelCode.trim().ifBlank { resolvedModel }
                val resolvedProduct = device.productName.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
                val resolvedCpu = device.cpuArch.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
                val resolvedTotalRam = device.resolvedTotalRam
                val resolvedAvailableRam = device.resolvedAvailableRam
                val resolvedResolution = device.screenResolution.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
                val resolvedDensity = device.screenDensity.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }
                val resolvedConnection = device.connectionType.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0F1D))
                        .padding(12.dp)
                        .testTag("device_details_panel_${device.installationId}"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = StoreProCardBorder)
                    DeviceDetailRow("Device Name", resolvedName)
                    DeviceDetailRow("Manufacturer", resolvedManufacturer)
                    DeviceDetailRow("Brand", resolvedBrand)
                    DeviceDetailRow("Device Model", resolvedModel)
                    DeviceDetailRow("Model Number", resolvedModelCode)
                    DeviceDetailRow("Product", resolvedProduct)
                    DeviceDetailRow("Device Type", resolvedDeviceType)
                    DeviceDetailRow("Android Version", resolvedAndroidVersion)
                    DeviceDetailRow("Android SDK Version", resolvedSdkVersion)
                    DeviceDetailRow("CPU Architecture", resolvedCpu)
                    DeviceDetailRow("Total RAM", resolvedTotalRam)
                    DeviceDetailRow("Available RAM", resolvedAvailableRam)
                    DeviceDetailRow("Screen Resolution", resolvedResolution)
                    DeviceDetailRow("Screen Density", resolvedDensity)
                    DeviceDetailRow("Network Connection", resolvedConnection)
                    DeviceDetailRow("App Version", "v${device.appVersionName} (${device.appVersionCode})")
                    DeviceDetailRow("Installation Date", installedDateText)
                    DeviceDetailRow("First Used", firstUsedText)
                    DeviceDetailRow("Using STORE PRO", usageDurationText)
                    DeviceDetailRow(
                        "Last Active",
                        "${formatTimestamp(device.lastActiveAt)} (${formatRelativeTime(device.lastActiveAt, nowMs)})"
                    )
                    DeviceDetailRow(
                        "Last Synced",
                        if (device.lastSyncAt > 0L) formatTimestamp(device.lastSyncAt) else RegisteredDevice.NOT_AVAILABLE
                    )
                    DeviceDetailRow(
                        "Online / Offline Status",
                        when {
                            isBlocked -> "Blocked"
                            isActive -> "Online (Active)"
                            isStale -> "Offline (Stale >30d)"
                            else -> "Offline"
                        }
                    )
                    DeviceDetailRow(
                        "Login Type",
                        if (device.isLoggedInAccount) {
                            val uidSuffix = device.associatedUserId?.take(8)?.let { " (UID: $it…)" }.orEmpty()
                            "Gmail (Logged In)$uidSuffix"
                        } else {
                            "Guest"
                        }
                    )
                    DeviceDetailRow("Associated Gmail", resolvedEmail)
                    DeviceDetailRow("Installation ID", device.installationId)
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun formatDateOnly(epochMs: Long): String {
    if (epochMs <= 0L) return RegisteredDevice.NOT_AVAILABLE
    return DateOnlyFormatterLocal.get()?.format(Date(epochMs)) ?: RegisteredDevice.NOT_AVAILABLE
}

private fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return RegisteredDevice.NOT_AVAILABLE
    return DateTimeFormatterLocal.get()?.format(Date(epochMs)) ?: RegisteredDevice.NOT_AVAILABLE
}

private fun formatRelativeTime(epochMs: Long, nowMs: Long): String {
    if (epochMs <= 0L) return "unknown"
    val diffMs = (nowMs - epochMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ago"
        else -> "${minutes / (24L * 60L)}d ago"
    }
}
