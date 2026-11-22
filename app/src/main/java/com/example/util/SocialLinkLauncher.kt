package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Launches STORE PRO community social links (WhatsApp group & Telegram channel)
 * preferring the native app when installed and falling back cleanly to any available browser.
 */
object SocialLinkLauncher {

    private const val TAG = "SocialLinkLauncher"

    const val WHATSAPP_GROUP_URL =
        "https://chat.whatsapp.com/KZPB5BRZNpJ1wNIL3JawJ8?s=cl&p=a&iam=2"
    const val TELEGRAM_CHANNEL_URL = "https://t.me/hasimapk"

    internal val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    internal val TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )

    fun openWhatsAppCommunity(context: Context): Boolean {
        return launchExternalUrl(
            context = context,
            url = WHATSAPP_GROUP_URL,
            preferredPackages = WHATSAPP_PACKAGES
        )
    }

    fun openTelegramChannel(context: Context): Boolean {
        return launchExternalUrl(
            context = context,
            url = TELEGRAM_CHANNEL_URL,
            preferredPackages = TELEGRAM_PACKAGES
        )
    }

    internal fun buildLaunchCandidates(
        context: Context,
        url: String,
        preferredPackages: List<String>
    ): List<Intent> {
        val uri = Uri.parse(url)
        val pm = context.packageManager
        val needsNewTask = context !is Activity
        val candidates = mutableListOf<Intent>()

        for (pkg in preferredPackages) {
            val targeted = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = runCatching {
                pm.resolveActivity(targeted, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrNull()
            if (resolved != null) {
                candidates.add(targeted)
            }
        }

        // Standard ACTION_VIEW browser fallback
        candidates.add(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return candidates
    }

    internal fun launchExternalUrl(
        context: Context,
        url: String,
        preferredPackages: List<String>
    ): Boolean {
        val candidates = buildLaunchCandidates(context, url, preferredPackages)
        for (intent in candidates) {
            val started = runCatching {
                context.startActivity(intent)
                true
            }.onFailure { err ->
                Log.w(TAG, "Unable to open $url with pkg=${intent.`package`}: ${err.message}")
            }.getOrDefault(false)
            if (started) return true
        }
        return false
    }
}
