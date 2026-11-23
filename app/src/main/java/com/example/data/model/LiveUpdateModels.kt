package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
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

@Immutable
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
    val changelog: String = "",
    val releaseDate: String = "",
    val mandatoryUpdate: Boolean = false,
    val published: Boolean = false,
    val releaseStatus: String = LiveReleaseStatus.DRAFT.name,
    val storagePath: String = "",
    val apkFileName: String = "",
    val apkSha256: String = "",
    val signingSha256: String = "",
    val adminEmail: String = "",
    val historyId: String = "",
    val publishedBy: String = "",
    val updatedAt: Long = 0L
)

@Immutable
@Keep
@IgnoreExtraProperties
data class UpdateHistoryRecord(
    val historyId: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val buildNumber: Long = 0L,
    val previousVersion: String = "",
    val releaseDate: String = "",
    val publicationTime: String = "",
    val releaseTimestamp: Long = 0L,
    val releaseNotes: String = "",
    val changelog: String = "",
    val mandatoryUpdate: Boolean = false,
    val publicationStatus: String = LiveReleaseStatus.PUBLISHED.name,
    val apkFileName: String = "",
    val apkFileSize: Long = 0L,
    val apkDownloadUrl: String = "",
    val apkStoragePath: String = "",
    val packageName: String = "",
    val signingCertificateFingerprint: String = "",
    val apkAvailable: Boolean = true,
    val adminUid: String = "",
    val adminEmail: String = "",
    val publishedBy: String = ""
)

enum class HistoryFilterOption(val label: String) {
    ALL("All"),
    LATEST_TO_OLDEST("Latest First"),
    OLDEST_TO_LATEST("Oldest First"),
    MANDATORY("Mandatory"),
    OPTIONAL("Optional"),
    APK_AVAILABLE("APK Available"),
    APK_UNAVAILABLE("Unavailable")
}

enum class HistoryApkDownloadStatus {
    IDLE,
    DOWNLOADING,
    READY,
    FAILED
}

@Immutable
data class HistoryDownloadState(
    val historyId: String = "",
    val status: HistoryApkDownloadStatus = HistoryApkDownloadStatus.IDLE,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val localApkPath: String? = null,
    val errorMessage: String? = null
)

@Immutable
data class InstalledAppDetails(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val androidVersion: String,
    val apiLevel: Int,
    val signingSha256Set: Set<String> = emptySet()
)

@Immutable
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

@Immutable
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
