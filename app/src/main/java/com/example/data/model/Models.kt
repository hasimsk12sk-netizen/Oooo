package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null
)

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

@Keep
@IgnoreExtraProperties
data class RegisteredDevice(
    val installationId: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val androidVersion: String = "",
    val sdkVersion: Int = 0,
    val deviceType: String = "",
    val accountStatus: String = ACCOUNT_STATUS_GUEST,
    val associatedUserId: String? = null,
    val status: String = STATUS_ACTIVE,
    val isAdminDevice: Boolean = false,
    val appVersionCode: Long = 1L,
    val appVersionName: String = "1.0",
    val registeredAt: Long = System.currentTimeMillis(),
    val firstUsedAt: Long = 0L,
    val lastActiveAt: Long = System.currentTimeMillis()
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

    @Exclude
    fun isCurrentlyActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return !isBlocked && (nowMs - lastActiveAt) in 0L..ACTIVE_HEARTBEAT_WINDOW_MS
    }

    @Exclude
    fun isOffline(nowMs: Long = System.currentTimeMillis()): Boolean {
        return !isCurrentlyActive(nowMs)
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
        const val ACTIVE_HEARTBEAT_WINDOW_MS = 3L * 60L * 1000L
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

