package com.example.data.liveupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.model.InstalledAppDetails
import com.example.data.model.LiveUpdateDashboardState
import com.example.data.model.LiveUpdateRelease
import com.example.data.model.LiveUpdateStatus
import com.example.data.model.VersionComparisonOutcome
import com.example.data.repository.LiveUpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

interface LiveUpdateInstallVerifier {
    fun readInstalled(context: Context): InstalledAppDetails
    fun verifyDownloadedApk(
        context: Context,
        downloadedFile: File,
        expectedRelease: LiveUpdateRelease,
        installed: InstalledAppDetails
    ): ApkVerificationResult
}

object DefaultLiveUpdateInstallVerifier : LiveUpdateInstallVerifier {
    override fun readInstalled(context: Context): InstalledAppDetails =
        LiveUpdateApkVerifier.readInstalledAppDetails(context)

    override fun verifyDownloadedApk(
        context: Context,
        downloadedFile: File,
        expectedRelease: LiveUpdateRelease,
        installed: InstalledAppDetails
    ): ApkVerificationResult = LiveUpdateApkVerifier.verifyForUserInstall(
        context = context,
        downloadedFile = downloadedFile,
        expectedRelease = expectedRelease,
        installed = installed
    )
}

class LiveUpdateController(
    private val context: Context,
    private val repository: LiveUpdateRepository,
    private val installVerifier: LiveUpdateInstallVerifier = DefaultLiveUpdateInstallVerifier,
    private val httpClient: OkHttpClient = defaultClient(),
    private val canInstallPackagesProvider: (Context) -> Boolean = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }
) {
    private val appContext = context.applicationContext
    private val updateDir = File(appContext.filesDir, "live_update_apks").apply { mkdirs() }

    private val _state = MutableStateFlow(
        LiveUpdateDashboardState(
            installed = installVerifier.readInstalled(appContext),
            hasInstallPermission = canInstallPackagesProvider(appContext)
        )
    )
    val state: StateFlow<LiveUpdateDashboardState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    companion object {
        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        private const val MIN_REQUIRED_BUFFER_BYTES = 10L * 1024L * 1024L
    }

    /**
     * Refreshes dynamic installed PackageManager info and install-unknown-apps capability.
     */
    fun onHostResumed() {
        val installed = installVerifier.readInstalled(appContext)
        val hasPerm = canInstallPackagesProvider(appContext)
        _state.update { current ->
            val readyFile = current.downloadedApkPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
            val outcome = LiveUpdateVersionComparator.compare(installed.versionCode, current.latestRelease)
            val nextStatus = when {
                current.status == LiveUpdateStatus.DOWNLOADING -> LiveUpdateStatus.DOWNLOADING
                current.status == LiveUpdateStatus.READY_TO_INSTALL && readyFile != null && outcome is VersionComparisonOutcome.UpdateAvailable ->
                    LiveUpdateStatus.READY_TO_INSTALL
                outcome is VersionComparisonOutcome.UpdateAvailable -> LiveUpdateStatus.UPDATE_AVAILABLE
                else -> LiveUpdateStatus.UP_TO_DATE
            }
            current.copy(
                installed = installed,
                hasInstallPermission = hasPerm,
                status = nextStatus,
                statusDetail = when (nextStatus) {
                    LiveUpdateStatus.READY_TO_INSTALL ->
                        "Verified package ready. Tap Install Update to launch the Android installer."
                    LiveUpdateStatus.UPDATE_AVAILABLE ->
                        "Version ${current.latestRelease?.versionName ?: ""} is ready to download."
                    LiveUpdateStatus.UP_TO_DATE ->
                        "Installed build (${installed.versionCode}) is up to date."
                    else -> current.statusDetail
                },
                downloadedApkPath = if (nextStatus == LiveUpdateStatus.UP_TO_DATE) null else readyFile?.absolutePath
            )
        }
    }

    suspend fun refreshUpdates(): Result<LiveUpdateDashboardState> {
        val installed = installVerifier.readInstalled(appContext)
        val hasPerm = canInstallPackagesProvider(appContext)

        // Preserve active download without interrupting progress
        if (_state.value.status == LiveUpdateStatus.DOWNLOADING) {
            return Result.success(_state.value)
        }

        _state.update {
            it.copy(
                installed = installed,
                hasInstallPermission = hasPerm,
                isChecking = true,
                status = LiveUpdateStatus.CHECKING,
                errorMessage = null
            )
        }

        val fetchResult = repository.fetchPublishedReleases()
        if (fetchResult.isFailure) {
            val msg = fetchResult.exceptionOrNull()?.localizedMessage ?: "Unable to reach update server."
            _state.update { current ->
                current.copy(
                    installed = installed,
                    isChecking = false,
                    status = LiveUpdateStatus.FAILED,
                    statusDetail = "Could not fetch release metadata.",
                    errorMessage = msg
                )
            }
            return Result.failure(fetchResult.exceptionOrNull() ?: IOException(msg))
        }

        val published = fetchResult.getOrNull().orEmpty()
        val latest = published.maxByOrNull { it.versionCode }
        applyPublishedReleases(published, latest)
        return Result.success(_state.value)
    }

    /**
     * Synchronizes UI state with a live Firestore snapshot stream of published releases.
     */
    fun applyPublishedReleases(
        publishedReleases: List<LiveUpdateRelease>,
        latestOverride: LiveUpdateRelease? = publishedReleases.maxByOrNull { it.versionCode }
    ) {
        val installed = installVerifier.readInstalled(appContext)
        val hasPerm = canInstallPackagesProvider(appContext)

        _state.update { current ->
            if (current.status == LiveUpdateStatus.DOWNLOADING) {
                return@update current.copy(
                    installed = installed,
                    latestRelease = latestOverride,
                    publishedHistory = publishedReleases.sortedByDescending { it.versionCode },
                    isChecking = false,
                    hasInstallPermission = hasPerm
                )
            }

            val existingReadyFile = current.downloadedApkPath?.let { File(it) }
                ?.takeIf {
                    it.exists() && it.length() > 0L &&
                        latestOverride != null &&
                        it.name == apkFileNameFor(latestOverride.versionCode)
                }

            val outcome = LiveUpdateVersionComparator.compare(installed.versionCode, latestOverride)
            val nextStatus = when {
                outcome is VersionComparisonOutcome.UpToDate -> LiveUpdateStatus.UP_TO_DATE
                existingReadyFile != null -> LiveUpdateStatus.READY_TO_INSTALL
                else -> LiveUpdateStatus.UPDATE_AVAILABLE
            }
            val nextDetail = when (nextStatus) {
                LiveUpdateStatus.READY_TO_INSTALL ->
                    "Verified APK downloaded and ready for Android system installation."
                LiveUpdateStatus.UPDATE_AVAILABLE ->
                    "Build ${latestOverride?.versionCode ?: ""} (${latestOverride?.versionName ?: ""}) is available."
                else -> "Installed build (${installed.versionCode}) is up to date."
            }

            current.copy(
                installed = installed,
                latestRelease = latestOverride,
                publishedHistory = publishedReleases.sortedByDescending { it.versionCode },
                isChecking = false,
                status = nextStatus,
                statusDetail = nextDetail,
                errorMessage = if (nextStatus != LiveUpdateStatus.FAILED) null else current.errorMessage,
                downloadedApkPath = existingReadyFile?.absolutePath,
                hasInstallPermission = hasPerm
            )
        }
    }

    fun startDownload(scope: CoroutineScope) {
        val currentState = _state.value
        val targetRelease = currentState.latestRelease
        val installed = installVerifier.readInstalled(appContext)

        val comparison = LiveUpdateVersionComparator.compare(installed.versionCode, targetRelease)
        if (comparison !is VersionComparisonOutcome.UpdateAvailable) {
            _state.update {
                it.copy(
                    installed = installed,
                    status = LiveUpdateStatus.UP_TO_DATE,
                    statusDetail = "You are already running the latest build.",
                    errorMessage = null
                )
            }
            return
        }

        val url = targetRelease!!.apkDownloadUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            _state.update {
                it.copy(
                    status = LiveUpdateStatus.FAILED,
                    statusDetail = "Invalid download URL.",
                    errorMessage = "The published APK URL is invalid. Contact the administrator."
                )
            }
            return
        }

        _state.update {
            it.copy(
                installed = installed,
                status = LiveUpdateStatus.DOWNLOADING,
                statusDetail = "Preparing download...",
                errorMessage = null,
                downloadPercent = 0,
                downloadedBytes = 0L,
                totalBytes = targetRelease.apkSize.coerceAtLeast(0L),
                downloadedApkPath = null
            )
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            performDownload(targetRelease, installed)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        cleanupPartialFiles()
        val installed = installVerifier.readInstalled(appContext)
        val outcome = LiveUpdateVersionComparator.compare(installed.versionCode, _state.value.latestRelease)
        _state.update {
            it.copy(
                installed = installed,
                status = if (outcome is VersionComparisonOutcome.UpdateAvailable) {
                    LiveUpdateStatus.UPDATE_AVAILABLE
                } else {
                    LiveUpdateStatus.UP_TO_DATE
                },
                statusDetail = "Download cancelled.",
                errorMessage = null,
                downloadPercent = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                downloadedApkPath = null
            )
        }
    }

    fun retryDownload(scope: CoroutineScope) {
        cleanupPartialFiles()
        startDownload(scope)
    }

    private suspend fun performDownload(
        release: LiveUpdateRelease,
        installed: InstalledAppDetails
    ) {
        val tempFile = File(updateDir, "downloading_v${release.versionCode}.apk.tmp")
        val finalFile = File(updateDir, apkFileNameFor(release.versionCode))

        _state.update {
            it.copy(
                installed = installed,
                status = LiveUpdateStatus.DOWNLOADING,
                statusDetail = "Connecting to update server...",
                errorMessage = null,
                downloadPercent = 0,
                downloadedBytes = 0L,
                totalBytes = release.apkSize.coerceAtLeast(0L),
                downloadedApkPath = null
            )
        }

        try {
            withContext(Dispatchers.IO) {
                updateDir.mkdirs()
                val availableBytes = availableFreeSpaceBytes()
                val requiredBytes = (if (release.apkSize > 0L) release.apkSize else 25L * 1024L * 1024L) +
                    MIN_REQUIRED_BUFFER_BYTES
                if (availableBytes < requiredBytes) {
                    throw IOException("Insufficient storage space on device to download update.")
                }

                if (tempFile.exists()) tempFile.delete()
                if (finalFile.exists()) finalFile.delete()

                val request = Request.Builder()
                    .url(release.apkDownloadUrl.trim())
                    .header("User-Agent", "STOREPRO-LiveUpdate/2.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Server returned HTTP ${response.code} while downloading APK.")
                    }

                    val body = response.body ?: throw IOException("Empty download response from server.")
                    val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                    if (contentType.contains("text/html")) {
                        throw IOException("Download link returned a webpage instead of an APK binary.")
                    }

                    val headerLength = body.contentLength()
                    val expectedTotal = when {
                        headerLength > 0L -> headerLength
                        release.apkSize > 0L -> release.apkSize
                        else -> 0L
                    }

                    var bytesReadTotal = 0L
                    var lastReportedPct = -1
                    var firstBytesChecked = false

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var read = input.read(buffer)
                            while (read != -1) {
                                if (!firstBytesChecked && read >= 4) {
                                    firstBytesChecked = true
                                    val prefix = String(buffer, 0, minOf(read, 16), Charsets.UTF_8).trimStart().lowercase()
                                    if (prefix.startsWith("<!doctype") || prefix.startsWith("<html")) {
                                        throw IOException("Server returned HTML instead of a valid APK package.")
                                    }
                                    // APK files are ZIP archives starting with PK (0x50 0x4B)
                                    if (buffer[0] != 0x50.toByte() || buffer[1] != 0x4B.toByte()) {
                                        throw IOException("Downloaded file is corrupted or not a valid APK archive.")
                                    }
                                }
                                output.write(buffer, 0, read)
                                bytesReadTotal += read

                                val pct = if (expectedTotal > 0L) {
                                    ((bytesReadTotal * 100L) / expectedTotal).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                if (pct != lastReportedPct || bytesReadTotal == expectedTotal) {
                                    lastReportedPct = pct
                                    _state.update { st ->
                                        st.copy(
                                            status = LiveUpdateStatus.DOWNLOADING,
                                            statusDetail = "Downloading v${release.versionName} (${release.versionCode})...",
                                            downloadPercent = pct,
                                            downloadedBytes = bytesReadTotal,
                                            totalBytes = expectedTotal
                                        )
                                    }
                                }
                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }

                    if (bytesReadTotal <= 0L) {
                        throw IOException("Downloaded APK file is empty (0 bytes).")
                    }
                    if (headerLength > 0L && bytesReadTotal != headerLength) {
                        throw IOException("Incomplete download: expected $headerLength bytes, received $bytesReadTotal bytes.")
                    }
                    if (release.apkSize > 0L && bytesReadTotal != release.apkSize) {
                        throw IOException("APK size mismatch: expected ${release.apkSize} bytes, received $bytesReadTotal bytes.")
                    }
                }

                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
            }

            _state.update {
                it.copy(
                    status = LiveUpdateStatus.DOWNLOAD_COMPLETED,
                    statusDetail = "Download completed. Verifying APK package & signature...",
                    downloadPercent = 100,
                    downloadedBytes = finalFile.length(),
                    totalBytes = finalFile.length()
                )
            }

            val verification = withContext(Dispatchers.IO) {
                installVerifier.verifyDownloadedApk(appContext, finalFile, release, installed)
            }

            if (!verification.isValid) {
                finalFile.delete()
                val failureReason = verification.errors.firstOrNull() ?: "APK verification failed."
                _state.update {
                    it.copy(
                        status = LiveUpdateStatus.FAILED,
                        statusDetail = "Verification failed.",
                        errorMessage = failureReason,
                        downloadedApkPath = null,
                        downloadPercent = 0,
                        downloadedBytes = 0L
                    )
                }
                return
            }

            val hasPerm = canInstallPackagesProvider(appContext)
            _state.update {
                it.copy(
                    status = LiveUpdateStatus.READY_TO_INSTALL,
                    statusDetail = "Verified package ready. Tap Install Update to continue.",
                    errorMessage = null,
                    downloadPercent = 100,
                    downloadedBytes = finalFile.length(),
                    totalBytes = finalFile.length(),
                    downloadedApkPath = finalFile.absolutePath,
                    hasInstallPermission = hasPerm
                )
            }
        } catch (ce: CancellationException) {
            tempFile.delete()
            throw ce
        } catch (e: Exception) {
            tempFile.delete()
            finalFile.delete()
            _state.update {
                it.copy(
                    status = LiveUpdateStatus.FAILED,
                    statusDetail = "Download interrupted or verification failed.",
                    errorMessage = e.localizedMessage ?: "Network or storage error occurred.",
                    downloadedApkPath = null,
                    downloadPercent = 0,
                    downloadedBytes = 0L
                )
            }
        }
    }

    /**
     * Builds the real Android intent for installing the verified APK or requesting
     * unknown-app-sources installation permission. Never claims "Installed" prematurely.
     */
    fun buildInstallActionIntent(): Intent? {
        val currentState = _state.value
        val apkPath = currentState.downloadedApkPath ?: return null
        val apkFile = File(apkPath)
        if (!apkFile.exists() || apkFile.length() <= 0L) return null

        if (!canInstallPackagesProvider(appContext) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${appContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val apkUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchSystemInstaller(): Result<Boolean> {
        return try {
            val intent = buildInstallActionIntent()
                ?: return Result.failure(IllegalStateException("Verified update APK is not ready on disk."))
            val isPermissionSettings = intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
            context.startActivity(intent)
            Result.success(!isPermissionSettings)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    status = LiveUpdateStatus.FAILED,
                    errorMessage = "Could not launch Android package installer: ${e.localizedMessage}"
                )
            }
            Result.failure(e)
        }
    }

    private fun availableFreeSpaceBytes(): Long {
        return runCatching {
            val usable = updateDir.usableSpace
            if (usable > 0L) return usable
            val stat = StatFs(updateDir.absolutePath)
            if (stat.blockCountLong > 0L) stat.availableBytes else Long.MAX_VALUE
        }.getOrDefault(Long.MAX_VALUE)
    }

    private fun cleanupPartialFiles() {
        runCatching {
            updateDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        }
    }

    private fun apkFileNameFor(versionCode: Long): String = "storepro_live_v$versionCode.apk"
}
