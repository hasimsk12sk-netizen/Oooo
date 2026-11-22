package com.example.data.download

import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object FileSharingLinkResolver {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun extractHostLower(url: String): String {
        return try {
            url.toUri().host?.lowercase() ?: ""
        } catch (_: Exception) {
            url.substringAfter("://", "").substringBefore("/").substringBefore("?").lowercase()
        }
    }

    suspend fun resolveToDirectDownloadUrl(inputUrl: String): String = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isEmpty()) return@withContext trimmed

        try {
            // 0. Firebase Storage gs:// URI or firebasestorage.googleapis.com URL missing alt=media
            if (trimmed.startsWith("gs://", ignoreCase = true)) {
                val withoutScheme = trimmed.substringAfter("gs://")
                val bucket = withoutScheme.substringBefore("/")
                val objectPath = withoutScheme.substringAfter("/", "")
                if (bucket.isNotBlank() && objectPath.isNotBlank()) {
                    val encodedObject = android.net.Uri.encode(objectPath)
                    return@withContext "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedObject?alt=media"
                }
            }

            val host = extractHostLower(trimmed)

            if (host.contains("firebasestorage.googleapis.com") && !trimmed.contains("alt=media", ignoreCase = true)) {
                val separator = if (trimmed.contains("?")) "&" else "?"
                return@withContext "$trimmed${separator}alt=media"
            }

            // 1. Google Drive (supports large APK virus-scan confirmation bypass)
            if (host.contains("drive.google.com") || host.contains("docs.google.com")) {
                val fileId = extractGoogleDriveFileId(trimmed)
                if (!fileId.isNullOrEmpty()) {
                    return@withContext resolveGoogleDriveLargeFileUrl(fileId)
                }
            }

            // 2. Dropbox
            if (host.contains("dropbox.com")) {
                var modified = trimmed
                if (modified.contains("?dl=0")) {
                    modified = modified.replace("?dl=0", "?dl=1")
                } else if (modified.contains("&dl=0")) {
                    modified = modified.replace("&dl=0", "&dl=1")
                } else if (!modified.contains("dl=1")) {
                    modified = if (modified.contains("?")) "$modified&dl=1" else "$modified?dl=1"
                }
                return@withContext modified
            }

            // 3. GitHub
            if (host.contains("github.com")) {
                if (trimmed.contains("/blob/")) {
                    return@withContext trimmed.replace("/blob/", "/raw/")
                }
                return@withContext trimmed
            }

            // 4. MediaFire
            if (host.contains("mediafire.com")) {
                val resolvedMediafire = fetchMediaFireDirectLink(trimmed)
                if (resolvedMediafire != null) {
                    return@withContext resolvedMediafire
                }
            }

            // 5. OneDrive
            if (host.contains("1drv.ms") || host.contains("onedrive.live.com")) {
                val separator = if (trimmed.contains("?")) "&" else "?"
                if (!trimmed.contains("download=1")) {
                    return@withContext "$trimmed${separator}download=1"
                }
                return@withContext trimmed
            }

            // 6. Box.com
            if (host.contains("box.com")) {
                val separator = if (trimmed.contains("?")) "&" else "?"
                if (!trimmed.contains("download=1")) {
                    return@withContext "$trimmed${separator}download=1"
                }
                return@withContext trimmed
            }

            trimmed
        } catch (e: Exception) {
            Log.w("LinkResolver", "Error resolving link: $inputUrl", e)
            trimmed
        }
    }

    fun extractGoogleDriveFileId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("/file/d/([a-zA-Z0-9_-]+)"),
            Pattern.compile("id=([a-zA-Z0-9_-]+)"),
            Pattern.compile("/d/([a-zA-Z0-9_-]+)")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun fetchMediaFireDirectLink(pageUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null

                val regex = Regex("""href=["'](https?://download\d*\.mediafire\.com/[^"']+)["']""")
                val match = regex.find(html)
                match?.groups?.get(1)?.value ?: run {
                    val fallbackRegex = Regex("""id=["']downloadButton["'][^>]*href=["']([^"']+)["']""")
                    fallbackRegex.find(html)?.groups?.get(1)?.value
                }
            }
        } catch (e: Exception) {
            Log.e("LinkResolver", "Failed to parse MediaFire link", e)
            null
        }
    }

    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) &&
                trimmed.length > 10
    }

    private fun resolveGoogleDriveLargeFileUrl(fileId: String): String {
        val initialUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        return try {
            val request = Request.Builder()
                .url(initialUrl)
                .addHeader("Range", "bytes=0-4095")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 STORE-PRO/1.1")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                if (!contentType.startsWith("text/html")) {
                    return initialUrl
                }
                val html = response.body?.string().orEmpty()
                val actionMatch = Regex("""action=["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1)
                val uuidMatch = Regex("""name=["']uuid["'][^>]*value=["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1)
                val confirmMatch = Regex("""name=["']confirm["'][^>]*value=["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1)
                    ?: "t"
                val baseAction = actionMatch?.takeIf { it.startsWith("https://", true) }
                    ?: "https://drive.usercontent.google.com/download"
                val uuidPart = if (!uuidMatch.isNullOrBlank()) "&uuid=$uuidMatch" else ""
                "$baseAction?id=$fileId&export=download&confirm=$confirmMatch$uuidPart"
            }
        } catch (_: Exception) {
            "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }
    }

    fun identifyService(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("drive.google.com") || lower.contains("docs.google.com") -> "Google Drive"
            lower.contains("dropbox.com") -> "Dropbox"
            lower.contains("mediafire.com") -> "MediaFire"
            lower.contains("github.com") -> "GitHub Releases"
            lower.contains("mega.nz") || lower.contains("mega.io") -> "MEGA"
            lower.contains("1drv.ms") || lower.contains("onedrive.live.com") -> "OneDrive"
            lower.contains("box.com") -> "Box"
            lower.endsWith(".apk") -> "Direct APK Link"
            else -> "Cloud Hosting / Direct Link"
        }
    }
}
