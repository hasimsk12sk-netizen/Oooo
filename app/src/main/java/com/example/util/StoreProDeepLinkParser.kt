package com.example.util

import android.content.Intent
import android.net.Uri
import com.example.data.model.ApplicationItem

sealed interface StoreProDeepLinkTarget {
    data class AppDetails(val appIdOrSlug: String) : StoreProDeepLinkTarget
    data object InstallLanding : StoreProDeepLinkTarget
}

object StoreProDeepLinkParser {

    fun parse(intent: Intent?): StoreProDeepLinkTarget? {
        if (intent == null) return null

        // Existing notification deep-link extras remain supported
        val extraAppId = intent.getStringExtra("target_app_id")?.trim().orEmpty()
        if (extraAppId.isNotEmpty()) {
            return StoreProDeepLinkTarget.AppDetails(extraAppId)
        }

        val data: Uri = intent.data ?: return null
        return parseUri(data)
    }

    fun parseUri(uri: Uri?): StoreProDeepLinkTarget? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.pathSegments.orEmpty()

        // Optional ?appId=xyz query support on landing or app routes
        val queryAppId = uri.getQueryParameter("appId")?.trim().orEmpty()
        if (queryAppId.isNotEmpty()) {
            return StoreProDeepLinkTarget.AppDetails(queryAppId)
        }

        when (scheme) {
            "storepro" -> {
                if (host == "app") {
                    val identifier = segments.firstOrNull()?.trim().orEmpty()
                    if (identifier.isNotEmpty()) {
                        return StoreProDeepLinkTarget.AppDetails(identifier)
                    }
                }
                if (host == "install" || host == "home") {
                    return StoreProDeepLinkTarget.InstallLanding
                }
            }
            "https", "http" -> {
                if (segments.size >= 2 && segments[0].equals("app", ignoreCase = true)) {
                    val identifier = segments[1].trim()
                    if (identifier.isNotEmpty()) {
                        return StoreProDeepLinkTarget.AppDetails(identifier)
                    }
                }
                if (segments.isNotEmpty() && segments[0].equals("install", ignoreCase = true)) {
                    return StoreProDeepLinkTarget.InstallLanding
                }
            }
        }
        return null
    }

    fun findMatchingApplication(
        applications: List<ApplicationItem>,
        appIdOrSlug: String
    ): ApplicationItem? {
        val clean = appIdOrSlug.trim()
        if (clean.isEmpty()) return null

        // 1. Exact Firestore document appId match
        applications.firstOrNull { it.appId == clean }?.let { return it }

        // 2. Case-insensitive appId match
        applications.firstOrNull { it.appId.equals(clean, ignoreCase = true) }?.let { return it }

        // 3. Package ID or slug match (e.g., "youtube" or "seed-vlc")
        val normalizedSlug = slugify(clean)
        return applications.firstOrNull { app ->
            app.packageId.equals(clean, ignoreCase = true) ||
                slugify(app.appName) == normalizedSlug ||
                slugify(app.appId) == normalizedSlug
        }
    }

    fun slugify(input: String): String {
        return input.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }
}
