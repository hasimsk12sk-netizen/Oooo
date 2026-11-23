package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@Immutable
@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null
)

@Immutable
@Keep
@IgnoreExtraProperties
data class ApplicationItem(
    val appId: String = "",
    val appName: String = "",
    val developerName: String = "",
    val version: String = "1.0.0",
    val description: String = "",
    val iconUrl: String = "",
    val apkUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val whatsNew: String = "",
    val privacyPolicyUrl: String = "",
    val packageId: String = "",
    val fileSizeBytes: Long = 25 * 1024 * 1024L,
    val downloads: Long = 0L,
    val averageRating: Double = 5.0,
    val ratingCount: Long = 1L,
    val status: String = "published",
    val verificationStatus: String = "verified",
    val uploadedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    @get:Exclude
    val formattedSize: String
        get() {
            val mb = fileSizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb) else "${fileSizeBytes / 1024} KB"
        }
}

@Immutable
@Keep
@IgnoreExtraProperties
data class ReviewItem(
    val reviewId: String = "",
    val appId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String? = null,
    val rating: Long = 5L,
    val comment: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
@Keep
@IgnoreExtraProperties
data class SavedAppItem(
    val appId: String = "",
    val appName: String = "",
    val developerName: String = "",
    val iconUrl: String = "",
    val version: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

@Immutable
@Keep
@IgnoreExtraProperties
data class DownloadItem(
    val downloadId: String = "",
    val appId: String = "",
    val appName: String = "",
    val version: String = "",
    val iconUrl: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0L,
    val localFilePath: String = "",
    val downloadedAt: Long = System.currentTimeMillis()
)

@Immutable
@Keep
@IgnoreExtraProperties
data class RegisteredDevice(
    val installationId: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val modelCode: String = "",
    val productName: String = "",
    val cpuArch: String = "",
    val ramInfo: String = "",
    val totalRam: String = "",
    val availableRam: String = "",
    val screenResolution: String = "",
    val screenDensity: String = "",
    val connectionType: String = "",
    val androidVersion: String = "",
    val sdkVersion: Int = 0,
    val deviceType: String = "",
    val accountStatus: String = ACCOUNT_STATUS_GUEST,
    val associatedUserId: String? = null,
    val associatedEmail: String? = null,
    val status: String = STATUS_ACTIVE,
    val isAdminDevice: Boolean = false,
    val appVersionCode: Long = 1L,
    val appVersionName: String = "1.0",
    val installedAt: Long = 0L,
    val registeredAt: Long = System.currentTimeMillis(),
    val firstUsedAt: Long = 0L,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = 0L,
    val deviceFingerprint: String = "",
    val legacyInstallationId: String? = null
) {
    @get:Exclude
    val isBlocked: Boolean
        get() = status.equals(STATUS_BLOCKED, ignoreCase = true)

    @get:Exclude
    val isLoggedInAccount: Boolean
        get() = accountStatus.equals(ACCOUNT_STATUS_LOGGED_IN, ignoreCase = true) ||
            !associatedUserId.isNullOrBlank()

    @get:Exclude
    val effectiveFirstUsedAt: Long
        get() = when {
            firstUsedAt > 0L -> firstUsedAt
            registeredAt > 0L -> registeredAt
            else -> 0L
        }

    @get:Exclude
    val effectiveInstalledAt: Long
        get() = when {
            installedAt > 0L -> installedAt
            firstUsedAt > 0L -> firstUsedAt
            registeredAt > 0L -> registeredAt
            else -> 0L
        }

    @get:Exclude
    val resolvedTotalRam: String
        get() = totalRam.trim()
            .takeIf { it.isNotBlank() && !it.equals(NOT_AVAILABLE, ignoreCase = true) }
            ?: ramInfo.trim()
                .takeIf { it.isNotBlank() && !it.equals(NOT_AVAILABLE, ignoreCase = true) }
            ?: NOT_AVAILABLE

    @get:Exclude
    val resolvedAvailableRam: String
        get() = availableRam.trim()
            .takeIf { it.isNotBlank() && !it.equals(NOT_AVAILABLE, ignoreCase = true) }
            ?: NOT_AVAILABLE

    @Exclude
    fun isCurrentlyActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return !isBlocked && lastActiveAt > 0L && (nowMs - lastActiveAt) in 0L..ACTIVE_HEARTBEAT_WINDOW_MS
    }

    @Exclude
    fun isOffline(nowMs: Long = System.currentTimeMillis()): Boolean {
        return !isCurrentlyActive(nowMs)
    }

    @Exclude
    fun isStaleOffline(
        nowMs: Long = System.currentTimeMillis(),
        staleThresholdMs: Long = DEFAULT_STALE_OFFLINE_WINDOW_MS
    ): Boolean {
        return !isAdminDevice && lastActiveAt > 0L && (nowMs - lastActiveAt) > staleThresholdMs
    }

    @Exclude
    fun usageDaysCount(nowMs: Long = System.currentTimeMillis()): Long {
        val firstTs = effectiveFirstUsedAt
        if (firstTs <= 0L) return 0L
        return ((nowMs - firstTs).coerceAtLeast(0L)) / MILLIS_PER_DAY
    }

    @Exclude
    fun formattedUsageDuration(nowMs: Long = System.currentTimeMillis()): String {
        if (effectiveFirstUsedAt <= 0L) return NOT_AVAILABLE
        val days = usageDaysCount(nowMs)
        return when (days) {
            0L -> "0 Days (Today)"
            1L -> "1 Day"
            else -> "$days Days"
        }
    }

    @Exclude
    fun isGenuineDeviceRecord(): Boolean {
        if (installationId.isBlank()) return false
        val fieldsToInspect = listOf(
            installationId,
            deviceName,
            deviceModel,
            manufacturer,
            brand
        )
        for (value in fieldsToInspect) {
            val lower = value.trim().lowercase()
            if (lower.isEmpty()) continue
            if (FAKE_AND_EMULATOR_TOKENS.any { lower.contains(it) }) {
                return false
            }
        }
        val nameLower = deviceName.trim().lowercase()
        val modelLower = deviceModel.trim().lowercase()
        if ((nameLower == "unknown" || nameLower.isEmpty()) &&
            (modelLower == "unknown" || modelLower.isEmpty())
        ) {
            return false
        }
        return true
    }

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_BLOCKED = "BLOCKED"
        const val ACCOUNT_STATUS_GUEST = "GUEST"
        const val ACCOUNT_STATUS_LOGGED_IN = "LOGGED_IN"
        const val NOT_AVAILABLE = "Not Available"
        const val ACTIVE_HEARTBEAT_WINDOW_MS = 2L * 60L * 1000L
        const val DEFAULT_STALE_OFFLINE_WINDOW_MS = 30L * 86_400_000L
        private const val MILLIS_PER_DAY = 86_400_000L

        internal val FAKE_AND_EMULATOR_TOKENS = listOf(
            "robolectric",
            "sdk_gphone",
            "gphone",
            "android sdk built for",
            "emulator",
            "goldfish",
            "ranchu",
            "vbox86",
            "genymotion",
            "generic_x86",
            "generic_arm64",
            "bluestacks",
            "ldplayer",
            "droid4x",
            "tiantian",
            "ttvm",
            "test_device",
            "fake_device",
            "mock_device",
            "dummy_device",
            "placeholder"
        )
    }
}

enum class AppCategory(val label: String) {
    ALL("All"),
    MEDIA("Media & Video"),
    SOCIAL("Social & Chat"),
    TOOLS("Tools & Utility"),
    ENTERTAINMENT("Entertainment"),
    PRODUCTIVITY("Productivity")
}

enum class AppSortOption(val label: String) {
    LATEST("Latest"),
    POPULARITY("Popularity"),
    DOWNLOADS("Most Downloaded")
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val applications: List<ApplicationItem>) : CatalogUiState
    data class Error(
        val message: String,
        val cachedApplications: List<ApplicationItem> = emptyList()
    ) : CatalogUiState
}

fun ApplicationItem.resolveCategory(): AppCategory {
    val haystack = "${appName} ${packageId} ${description} ${whatsNew}".lowercase()
    return when {
        listOf("video", "player", "audio", "music", "vlc", "kodi", "stream", "editor", "photo", "camera", "media")
            .any { haystack.contains(it) } -> AppCategory.MEDIA
        listOf("chat", "messenger", "signal", "whatsapp", "telegram", "social", "call", "instagram", "facebook")
            .any { haystack.contains(it) } -> AppCategory.SOCIAL
        listOf("tool", "utility", "launcher", "cleaner", "manager", "vpn", "browser", "scanner", "file", "apk")
            .any { haystack.contains(it) } -> AppCategory.TOOLS
        listOf("game", "play", "tv", "movie", "cinema", "show", "anime", "fun", "entertainment")
            .any { haystack.contains(it) } -> AppCategory.ENTERTAINMENT
        else -> AppCategory.PRODUCTIVITY
    }
}


