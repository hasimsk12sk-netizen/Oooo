package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

enum class LiveReleaseStatus {
    PUBLISHED,
    DRAFT,
    ARCHIVED
}

enum class LiveUpdateStatus(val label: String) {
    CHECKING("Checking for updates..."),
    UP_TO_DATE("You are using the latest version."),
    UPDATE_AVAILABLE("New update available."),
    DOWNLOADING("Update is downloading."),
    DOWNLOAD_COMPLETED("Download completed."),
    READY_TO_INSTALL("Ready to install."),
    FAILED("Update failed.")
}

@Keep
@IgnoreExtraProperties
data class LiveUpdateRelease(
    val releaseId: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val packageName: String = "",
    val apkDownloadUrl: String = "",
    val apkSize: Long = 0L,
    val releaseNotes: String = "",
    val releaseDate: String = "",
    val published: Boolean = false,
    val releaseStatus: String = LiveReleaseStatus.DRAFT.name,
    val storagePath: String = "",
    val apkSha256: String = "",
    val signingSha256: String = "",
    val publishedBy: String = "",
    val updatedAt: Long = 0L
)

data class InstalledAppDetails(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val androidVersion: String,
    val apiLevel: Int,
    val signingSha256Set: Set<String> = emptySet()
)

data class InspectedApkMetadata(
    val fileName: String,
    val localTempPath: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val fileSizeBytes: Long,
    val apkSha256: String,
    val signingSha256Set: Set<String>,
    val isValidArchive: Boolean,
    val errorMessage: String? = null
)

sealed class VersionComparisonOutcome {
    data class UpToDate(val message: String) : VersionComparisonOutcome()
    data class UpdateAvailable(
        val release: LiveUpdateRelease,
        val message: String
    ) : VersionComparisonOutcome()
}

data class LiveUpdateDashboardState(
    val installed: InstalledAppDetails,
    val latestRelease: LiveUpdateRelease? = null,
    val publishedHistory: List<LiveUpdateRelease> = emptyList(),
    val status: LiveUpdateStatus = LiveUpdateStatus.UP_TO_DATE,
    val statusDetail: String? = null,
    val errorMessage: String? = null,
    val isChecking: Boolean = false,
    val downloadPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val hasInstallPermission: Boolean = true
)
