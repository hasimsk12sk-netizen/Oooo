package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.data.model.ApplicationItem

/**
 * Builds native Android share intents and genuine configured App Link URLs
 * for individual STORE PRO applications and for the STORE PRO platform itself.
 */
object StoreProShareHelper {

    fun configuredWebDomain(context: Context): String {
        val raw = runCatching { context.getString(R.string.storepro_web_domain).trim() }
            .getOrDefault("gen-lang-client-0338531403.web.app")
        return raw.removePrefix("https://").removePrefix("http://").trimEnd('/')
            .ifBlank { "gen-lang-client-0338531403.web.app" }
    }

    fun configuredDirectDownloadUrl(context: Context): String {
        val raw = runCatching { context.getString(R.string.storepro_download_url).trim() }
            .getOrDefault("")
        return if (raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true)) {
            raw
        } else {
            ""
        }
    }

    fun hasExplicitStoreProDownloadUrl(context: Context): Boolean {
        return configuredDirectDownloadUrl(context).isNotEmpty()
    }

    fun buildApplicationDetailsLink(context: Context, appId: String): String {
        val cleanId = appId.trim().ifBlank { "catalog" }
        return "https://${configuredWebDomain(context)}/app/$cleanId"
    }

    fun resolveStoreProShareUrl(context: Context): String {
        val direct = configuredDirectDownloadUrl(context)
        return if (direct.isNotEmpty()) {
            direct
        } else {
            "https://${configuredWebDomain(context)}/install"
        }
    }

    fun buildApplicationShareMessage(context: Context, app: ApplicationItem): String {
        val appLink = buildApplicationDetailsLink(context, app.appId)
        val devName = app.developerName.trim().ifBlank { "STORE PRO Developer" }
        val versionName = app.version.trim().ifBlank { "1.0" }
        return buildString {
            appendLine("Application Name: ${app.appName.trim()}")
            appendLine("Developer: $devName")
            appendLine("Version: $versionName")
            appendLine()
            appendLine("Application Details Link:")
            append(appLink)
        }.trim()
    }

    fun buildStoreProShareMessage(context: Context): String {
        val shareUrl = resolveStoreProShareUrl(context)
        return buildString {
            appendLine("STORE PRO")
            appendLine()
            appendLine("Discover premium applications in one place.")
            appendLine()
            appendLine("Download STORE PRO:")
            append(shareUrl)
        }.trim()
    }

    fun createApplicationShareChooserIntent(context: Context, app: ApplicationItem): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${app.appName} — STORE PRO")
            putExtra(Intent.EXTRA_TEXT, buildApplicationShareMessage(context, app))
        }
        return Intent.createChooser(sendIntent, "Share ${app.appName}").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun createStoreProShareChooserIntent(context: Context): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Download STORE PRO")
            putExtra(Intent.EXTRA_TEXT, buildStoreProShareMessage(context))
        }
        return Intent.createChooser(sendIntent, "Share STORE PRO").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun shareApplication(context: Context, app: ApplicationItem): Boolean {
        return runCatching {
            context.startActivity(createApplicationShareChooserIntent(context, app))
            true
        }.getOrDefault(false)
    }

    fun shareStoreProApp(context: Context): Boolean {
        return runCatching {
            context.startActivity(createStoreProShareChooserIntent(context))
            true
        }.getOrDefault(false)
    }
}
