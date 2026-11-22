package com.example.data.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri

object BrowserDownloadManager {

    private const val TAG = "BrowserDownloadManager"
    private const val CHROME_PACKAGE = "com.android.chrome"

    fun openDownloadUrl(
        context: Context,
        rawUrl: String,
        appName: String = "",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val trimmed = rawUrl.trim()
        val parsedUri = try {
            trimmed.toUri()
        } catch (_: Exception) {
            null
        }
        val scheme = parsedUri?.scheme?.lowercase()
        val host = parsedUri?.host
        if (trimmed.isEmpty() || (scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
            val errorMsg = "Invalid download link. URL must be a valid http:// or https:// address."
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            onError?.invoke(errorMsg)
            return
        }

        try {
            var launched = false

            // Strategy 1: Google Chrome
            try {
                val chromeIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                    setPackage(CHROME_PACKAGE)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chromeIntent)
                launched = true
            } catch (e: Exception) {
                Log.d(TAG, "Chrome not directly launchable: ${e.message}")
            }

            // Strategy 2: Default browser
            if (!launched) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    launched = true
                } catch (e: Exception) {
                    Log.d(TAG, "Default browser failed: ${e.message}")
                }
            }

            // Strategy 3: Generic VIEW intent
            if (!launched) {
                val genericIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
                launched = true
            }

            if (launched) {
                val serviceName = FileSharingLinkResolver.identifyService(trimmed)
                val toastMsg = if (appName.isNotBlank()) {
                    "Redirecting to $serviceName in browser to download $appName..."
                } else {
                    "Opening download page on $serviceName in browser..."
                }
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            } else {
                val err = "No suitable web browser found on this device to open the download link."
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                onError?.invoke(err)
            }
        } catch (e: ActivityNotFoundException) {
            val errorMsg = "No web browser is installed to open this download link."
            Log.e(TAG, errorMsg, e)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            onError?.invoke(errorMsg)
        } catch (e: Exception) {
            val errorMsg = "Unable to open download URL: ${e.localizedMessage ?: "Unknown error"}"
            Log.e(TAG, errorMsg, e)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            onError?.invoke(errorMsg)
        }
    }
}
