package com.example.data.liveupdate

import com.example.data.model.LiveReleaseStatus
import com.example.data.model.LiveUpdateRelease
import com.example.data.model.LiveUpdateStatus
import com.example.data.model.VersionComparisonOutcome

/**
 * Pure VersionCode comparison engine for STORE PRO Live Update.
 * Primary comparisons strictly use versionCode (Long) and never versionName.
 */
object LiveUpdateVersionComparator {

    fun compare(
        installedVersionCode: Long,
        latestPublishedRelease: LiveUpdateRelease?
    ): VersionComparisonOutcome {
        if (latestPublishedRelease == null) {
            return VersionComparisonOutcome.UpToDate(LiveUpdateStatus.UP_TO_DATE.label)
        }
        val isActuallyPublished = latestPublishedRelease.published &&
            latestPublishedRelease.releaseStatus == LiveReleaseStatus.PUBLISHED.name &&
            latestPublishedRelease.apkDownloadUrl.isNotBlank() &&
            latestPublishedRelease.versionCode > 0L

        if (!isActuallyPublished) {
            return VersionComparisonOutcome.UpToDate(LiveUpdateStatus.UP_TO_DATE.label)
        }

        return when {
            installedVersionCode < latestPublishedRelease.versionCode -> {
                VersionComparisonOutcome.UpdateAvailable(
                    release = latestPublishedRelease,
                    message = LiveUpdateStatus.UPDATE_AVAILABLE.label
                )
            }
            // installedVersionCode == latestPublishedRelease.versionCode OR
            // installedVersionCode > latestPublishedRelease.versionCode (never offer older APK)
            else -> {
                VersionComparisonOutcome.UpToDate(LiveUpdateStatus.UP_TO_DATE.label)
            }
        }
    }
}
