package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.MainActivity
import com.example.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StoreProNotificationHelper {

    const val CHANNEL_ID = "storepro_new_apps_channel"
    private const val CHANNEL_NAME = "New App Releases"
    private const val CHANNEL_DESCRIPTION = "Alerts when new applications are published on STORE PRO."

    const val EXTRA_TARGET_APP_ID = "target_app_id"
    const val KEY_FCM_APP_ID = "appId"
    const val KEY_FCM_EVENT_ID = "eventId"

    private const val DEDUP_PREFS = "storepro_notif_dedup"
    private const val KEY_SEEN_EVENT_IDS = "seen_notification_events"
    private const val MAX_SEEN_IDS = 100

    private val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(true)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun extractTargetAppId(intent: Intent?): String? {
        if (intent == null) return null
        val direct = intent.getStringExtra(EXTRA_TARGET_APP_ID)?.trim()
        if (!direct.isNullOrBlank()) return direct
        val fcmData = intent.getStringExtra(KEY_FCM_APP_ID)?.trim()
        if (!fcmData.isNullOrBlank()) return fcmData
        return null
    }

    fun buildOpenAppDetailsIntent(context: Context, appId: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_APP_DETAILS_$appId"
            putExtra(EXTRA_TARGET_APP_ID, appId)
            putExtra(KEY_FCM_APP_ID, appId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    @Synchronized
    fun markAndCheckShouldNotify(context: Context, dedupKey: String): Boolean {
        if (dedupKey.isBlank()) return true
        val prefs = context.applicationContext.getSharedPreferences(DEDUP_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_SEEN_EVENT_IDS, emptySet())?.toMutableList() ?: mutableListOf()
        if (existing.contains(dedupKey)) {
            return false
        }
        existing.add(dedupKey)
        while (existing.size > MAX_SEEN_IDS) {
            existing.removeAt(0)
        }
        prefs.edit { putStringSet(KEY_SEEN_EVENT_IDS, existing.toSet()) }
        return true
    }

    fun showNewAppNotification(
        context: Context,
        appId: String,
        appName: String,
        iconUrl: String? = null,
        eventId: String? = null,
        customTitle: String? = null,
        customBody: String? = null
    ): Boolean {
        if (appId.isBlank()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        if (PushNotificationCoordinator.isUserNotificationsOptedOut(context)) {
            return false
        }

        val dedupKey = eventId?.takeIf { it.isNotBlank() } ?: "app_$appId"
        if (!markAndCheckShouldNotify(context, dedupKey)) {
            return false
        }

        ensureNotificationChannel(context)

        val cleanName = appName.trim().ifBlank { "New Application" }
        val title = customTitle?.trim()?.ifBlank { null } ?: "New App Available on STORE PRO!"
        val message = customBody?.trim()?.ifBlank { null }
            ?: "$cleanName has just been uploaded. Explore it now on STORE PRO."

        val openIntent = buildOpenAppDetailsIntent(context, appId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            appId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = loadNotificationBitmap(iconUrl)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_storepro)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        return try {
            NotificationManagerCompat.from(context).notify(appId.hashCode(), builder.build())
            true
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification permission denied while posting alert", se)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification", e)
            false
        }
    }

    private fun loadNotificationBitmap(rawUrl: String?): Bitmap? {
        val url = rawUrl?.trim() ?: return null
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
            return null
        }
        return runCatching {
            val req = Request.Builder().url(url).build()
            imageClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private const val TAG = "StoreProNotifHelper"
}
