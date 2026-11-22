package com.example.ui.upload

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import kotlinx.coroutines.Dispatchers
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared Coil ImageLoader configured with explicit MemoryCache, DiskCache, background IO dispatchers,
 * HTTP/HTTPS redirect support, and pooled TLS connections for smooth high-speed image loading.
 */
object UniversalImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT
                )
            )
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(appContext)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "storepro_image_cache"))
                    .maxSizeBytes(64L * 1024L * 1024L)
                    .build()
            }
            .dispatcher(Dispatchers.IO)
            .allowHardware(true)
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

/**
 * UniversalImageUtils:
 * Validates and normalizes any image URL (HTTP/HTTPS, JPG, JPEG, PNG, WEBP, GIF,
 * redirecting links from Imgur, Cloudinary, Unsplash, Google Drive, Dropbox, GitHub, etc.)
 */
object UniversalImageUtils {

    private val SUPPORTED_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
    private val MULTI_URL_SPLIT_REGEX = Regex("[\n,;]")
    private val DRIVE_FILE_D_REGEX = Regex("/file/d/([a-zA-Z0-9_-]+)")
    private val DRIVE_ID_QUERY_REGEX = Regex("[?&]id=([a-zA-Z0-9_-]+)")
    private val GITHUB_BLOB_REGEX = Regex(
        "^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val IMGUR_PAGE_REGEX = Regex(
        "^https?://(?:www\\.)?imgur\\.com/([a-zA-Z0-9]+)$",
        RegexOption.IGNORE_CASE
    )

    fun isValidImageUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.length < 10 || trimmed.any { it.isWhitespace() }) return false
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false

        return try {
            val uri = trimmed.toUri()
            val host = uri.host
            !host.isNullOrBlank() && host.contains('.')
        } catch (_: Exception) {
            // Fallback for pure JVM unit tests where android.net.Uri might be stubbed
            val withoutScheme = trimmed.substringAfter("://")
            val host = withoutScheme.substringBefore("/").substringBefore("?")
            host.isNotBlank() && host.contains('.')
        }
    }

    fun detectFormatLabel(url: String): String {
        val lower = url.trim().lowercase().substringBefore("?").substringBefore("#")
        val ext = SUPPORTED_EXTENSIONS.firstOrNull { lower.endsWith(it) }
        return when {
            ext != null -> ext.removePrefix(".").uppercase()
            lower.contains("drive.google.com") -> "Google Drive Image"
            lower.contains("dropbox.com") -> "Dropbox Image"
            lower.contains("github") -> "GitHub Image"
            lower.contains("imgur.com") -> "Imgur Image"
            else -> "Universal Web Image"
        }
    }

    fun parseMultipleImageUrls(rawText: String): List<String> {
        return rawText.split(MULTI_URL_SPLIT_REGEX)
            .map { it.trim() }
            .filter { isValidImageUrl(it) }
            .distinct()
    }

    /**
     * Normalizes image URLs from common hosting platforms into direct image streams
     * while fast-pathing ordinary direct links without regex overhead.
     */
    fun normalizeImageUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""

        val isDrive = trimmed.contains("drive.google.com", ignoreCase = true)
        val isDropbox = !isDrive && trimmed.contains("dropbox.com", ignoreCase = true)
        val isGithub = !isDrive && !isDropbox && trimmed.contains("github.com", ignoreCase = true)
        val isImgur = !isDrive && !isDropbox && !isGithub && trimmed.contains("imgur.com", ignoreCase = true)

        if (!isDrive && !isDropbox && !isGithub && !isImgur) {
            return trimmed
        }

        // Google Drive sharing links -> high-res thumbnail/direct endpoint
        if (isDrive) {
            val fileId = DRIVE_FILE_D_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
                ?: DRIVE_ID_QUERY_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
            if (!fileId.isNullOrEmpty()) {
                return "https://drive.google.com/thumbnail?id=$fileId&sz=w1000"
            }
        }

        // Dropbox shared links -> raw=1 direct image
        if (isDropbox) {
            if (trimmed.contains("dl=0")) {
                return trimmed.replace("dl=0", "raw=1")
            }
            if (!trimmed.contains("raw=1") && !trimmed.contains("dl=1")) {
                val sep = if (trimmed.contains("?")) "&" else "?"
                return "${trimmed}${sep}raw=1"
            }
        }

        // GitHub blob URLs -> raw.githubusercontent.com
        if (isGithub) {
            val githubBlobMatch = GITHUB_BLOB_REGEX.find(trimmed)
            if (githubBlobMatch != null) {
                val owner = githubBlobMatch.groupValues[1]
                val repo = githubBlobMatch.groupValues[2]
                val path = githubBlobMatch.groupValues[3]
                return "https://raw.githubusercontent.com/$owner/$repo/$path"
            }
        }

        // Imgur page link -> direct i.imgur.com image
        if (isImgur) {
            val imgurPageMatch = IMGUR_PAGE_REGEX.find(trimmed)
            if (imgurPageMatch != null) {
                val id = imgurPageMatch.groupValues[1]
                return "https://i.imgur.com/$id.png"
            }
        }

        return trimmed
    }
}

/**
 * Single App Icon live preview box with automatic redirect and error state handling.
 */
@Composable
fun AppIconPreviewBox(
    iconUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val normalizedUrl = UniversalImageUtils.normalizeImageUrl(iconUrl)
    val isValid = UniversalImageUtils.isValidImageUrl(normalizedUrl)
    val formatLabel = if (isValid) UniversalImageUtils.detectFormatLabel(normalizedUrl) else null

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(StoreProCardBorder)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, StoreProCardBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isValid) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(normalizedUrl)
                            .crossfade(true)
                            .build(),
                        imageLoader = UniversalImageLoader.get(context),
                        contentDescription = "App Icon Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = StoreProPrimaryGreen,
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        error = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "Image Error",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Load Error", color = Color(0xFFEF4444), fontSize = 9.sp)
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "No Icon",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Icon Preview",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isValid) {
                        "Detected: $formatLabel • Redirects & SSL handled"
                    } else if (iconUrl.isNotBlank()) {
                        "Invalid URL — use a public http:// or https:// image link"
                    } else {
                        "Supports HTTP, HTTPS, JPG, JPEG, PNG, WEBP, GIF"
                    },
                    fontSize = 11.sp,
                    color = when {
                        isValid -> StoreProSecondaryCyan
                        iconUrl.isNotBlank() -> Color(0xFFFCA5A5)
                        else -> Color(0xFF94A3B8)
                    }
                )
            }
        }
    }
}

/**
 * Multiple Screenshots live preview gallery with thumbnail cards.
 */
@Composable
fun ScreenshotsPreviewGallery(
    rawUrls: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urls = UniversalImageUtils.parseMultipleImageUrls(rawUrls)

    if (urls.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Screenshots Live Preview (${urls.size} detected)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1)
            )
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StoreProPrimaryGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Universal Links", fontSize = 10.sp, color = StoreProPrimaryGreen)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            urls.forEachIndexed { index, rawUrl ->
                val normalized = UniversalImageUtils.normalizeImageUrl(rawUrl)
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, StoreProCardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(normalized)
                            .crossfade(true)
                            .build(),
                        imageLoader = UniversalImageLoader.get(context),
                        contentDescription = "Screenshot ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = StoreProSecondaryCyan,
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        error = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "Failed",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Failed to load", color = Color(0xFFEF4444), fontSize = 10.sp)
                            }
                        }
                    )
                }
            }
        }
    }
}
