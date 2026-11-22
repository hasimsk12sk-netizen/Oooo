package com.example.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.ApplicationItem
import com.example.data.model.DownloadItem
import com.example.data.repository.StoreProRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadProgressState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val currentAppId: String? = null,
    val currentAppName: String? = null,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val remainingTimeSec: Long = 0L,
    val errorMessage: String? = null,
    val downloadedFile: File? = null
) {
    val speedFormatted: String
        get() {
            if (speedBytesPerSec <= 0) return "-- KB/s"
            val mb = speedBytesPerSec / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB/s".format(mb) else "${speedBytesPerSec / 1024} KB/s"
        }

    val remainingTimeFormatted: String
        get() {
            if (remainingTimeSec <= 0) return "Calculating..."
            return if (remainingTimeSec < 60) "${remainingTimeSec}s left" else "${remainingTimeSec / 60}m ${remainingTimeSec % 60}s left"
        }
}

class DownloadController(
    private val context: Context,
    private val repository: StoreProRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _progressState = MutableStateFlow(DownloadProgressState())
    val progressState = _progressState.asStateFlow()

    private val downloadLock = Any()
    private val lastBrowserTriggerAtMs = mutableMapOf<String, Long>()
    private var downloadJob: Job? = null

    companion object {
        internal const val BROWSER_DOWNLOAD_COOLDOWN_MS = 2500L
    }

    internal fun tryAcquireBrowserDownloadSlot(
        appId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (appId.isBlank()) return false
        synchronized(downloadLock) {
            val previous = lastBrowserTriggerAtMs[appId]
            if (previous != null && (nowMs - previous) < BROWSER_DOWNLOAD_COOLDOWN_MS) {
                return false
            }
            lastBrowserTriggerAtMs[appId] = nowMs
            return true
        }
    }

    internal fun releaseBrowserDownloadSlot(appId: String) {
        synchronized(downloadLock) {
            lastBrowserTriggerAtMs.remove(appId)
        }
    }

    fun startBrowserDownload(
        app: ApplicationItem,
        coroutineScope: CoroutineScope,
        currentUserId: String?,
        onError: (String) -> Unit
    ) {
        if (!tryAcquireBrowserDownloadSlot(app.appId)) {
            return
        }

        val actionId = UUID.randomUUID().toString()
        BrowserDownloadManager.openDownloadUrl(
            context = context,
            rawUrl = app.apkUrl,
            appName = app.appName,
            onSuccess = {
                coroutineScope.launch {
                    withContext(NonCancellable) {
                        repository.incrementDownloads(app.appId, actionId = actionId)
                        if (currentUserId != null) {
                            repository.recordDownload(
                                userId = currentUserId,
                                item = DownloadItem(
                                    appId = app.appId,
                                    appName = app.appName,
                                    version = app.version,
                                    iconUrl = app.iconUrl,
                                    downloadUrl = app.apkUrl,
                                    fileSize = app.fileSizeBytes.coerceAtLeast(0L),
                                    localFilePath = "Browser Download (${app.apkUrl})".take(2500)
                                )
                            )
                        }
                    }
                }
            },
            onError = { error ->
                releaseBrowserDownloadSlot(app.appId)
                onError(error)
            }
        )
    }

    fun startDownload(app: ApplicationItem, coroutineScope: CoroutineScope, currentUserId: String?) {
        val actionId = UUID.randomUUID().toString()
        synchronized(downloadLock) {
            val current = _progressState.value
            val isInProgressForSameApp = current.currentAppId == app.appId &&
                (current.status == DownloadStatus.CONNECTING || current.status == DownloadStatus.DOWNLOADING)
            if (isInProgressForSameApp) {
                return
            }
            downloadJob?.cancel()
            _progressState.value = DownloadProgressState(
                status = DownloadStatus.CONNECTING,
                currentAppId = app.appId,
                currentAppName = app.appName
            )
        }

        downloadJob = coroutineScope.launch(Dispatchers.IO) {
            var targetFile: File? = null
            try {
                val sanitizedName = app.appName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "app" }
                val fileName = "${sanitizedName}_${app.version}.apk"
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val outputFile = File(downloadsDir, fileName)
                targetFile = outputFile

                val directDownloadUrl = FileSharingLinkResolver.resolveToDirectDownloadUrl(app.apkUrl)

                val request = Request.Builder()
                    .url(directDownloadUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 STORE-PRO/1.0")
                    .build()

                var totalRead = 0L
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download server returned HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty response body")
                    val contentLength = body.contentLength().let { if (it > 0) it else app.fileSizeBytes.coerceAtLeast(1024 * 1024) }

                    _progressState.value = _progressState.value.copy(
                        status = DownloadStatus.DOWNLOADING,
                        totalBytes = contentLength
                    )

                    FileOutputStream(outputFile).use { fileOutputStream ->
                        body.byteStream().use { inputStream ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var lastTime = System.currentTimeMillis()
                            var bytesSinceLastTime = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) {
                                    throw CancellationException("Download cancelled by user")
                                }
                                fileOutputStream.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                bytesSinceLastTime += bytesRead

                                val now = System.currentTimeMillis()
                                val timeDiff = now - lastTime
                                if (timeDiff >= 400) {
                                    val speed = (bytesSinceLastTime * 1000L) / timeDiff.coerceAtLeast(1)
                                    val remainingBytes = (contentLength - totalRead).coerceAtLeast(0)
                                    val remTime = if (speed > 0) remainingBytes / speed else 0L
                                    val percent = if (contentLength > 0) ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 99) else 50

                                    _progressState.value = _progressState.value.copy(
                                        progressPercent = percent,
                                        downloadedBytes = totalRead,
                                        speedBytesPerSec = speed,
                                        remainingTimeSec = remTime
                                    )
                                    lastTime = now
                                    bytesSinceLastTime = 0L
                                }
                            }
                            fileOutputStream.flush()
                        }
                    }
                }

                if (totalRead <= 0L || !outputFile.exists() || outputFile.length() <= 0L) {
                    throw IllegalStateException("Downloaded APK file is empty.")
                }

                _progressState.value = _progressState.value.copy(
                    status = DownloadStatus.COMPLETED,
                    progressPercent = 100,
                    downloadedBytes = totalRead,
                    downloadedFile = outputFile
                )

                withContext(NonCancellable) {
                    repository.incrementDownloads(app.appId, actionId = actionId)
                    repository.recordDownload(
                        userId = currentUserId,
                        item = DownloadItem(
                            appId = app.appId,
                            appName = app.appName,
                            version = app.version,
                            iconUrl = app.iconUrl,
                            downloadUrl = app.apkUrl,
                            fileSize = outputFile.length(),
                            localFilePath = outputFile.absolutePath.take(2500)
                        )
                    )
                }

            } catch (e: CancellationException) {
                runCatching { targetFile?.delete() }
                _progressState.value = DownloadProgressState(status = DownloadStatus.CANCELLED)
            } catch (e: Exception) {
                runCatching { targetFile?.delete() }
                Log.e("DownloadController", "Failed to download APK", e)
                _progressState.value = _progressState.value.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Failed to download APK"
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _progressState.value = DownloadProgressState(status = DownloadStatus.CANCELLED)
    }

    fun installApk(file: File) {
        try {
            if (!file.exists() || file.length() == 0L) {
                Log.e("DownloadController", "Target APK file is empty or does not exist")
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DownloadController", "Failed to launch package installer", e)
        }
    }
}
