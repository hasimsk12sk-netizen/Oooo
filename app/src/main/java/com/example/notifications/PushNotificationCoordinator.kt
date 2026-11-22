package com.example.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.StoreProApplication
import com.example.data.device.DeviceIdentityProvider
import com.example.data.repository.DeviceAndUpdateRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Coordinates Android 13+ notification permission state, one-time prompt tracking,
 * FCM token registration, and Firestore token synchronization.
 */
object PushNotificationCoordinator {

    private const val TAG = "PushNotifCoordinator"
    private const val PREFS_NAME = "storepro_notification_prefs"
    private const val KEY_PROMPTED_ONCE = "has_prompted_notifications"
    private const val KEY_USER_OPT_OUT = "user_notifications_opted_out"
    private const val KEY_LAST_FCM_TOKEN = "last_fcm_token"

    const val TOPIC_NEW_APPS = "storepro_new_apps"

    fun hasOsNotificationPermission(context: Context): Boolean {
        val osEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED && osEnabled
        } else {
            osEnabled
        }
    }

    fun shouldShowInitialPermissionPrompt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (hasOsNotificationPermission(context)) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_PROMPTED_ONCE, false)
    }

    fun markPermissionPromptHandled(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_PROMPTED_ONCE, true) }
    }

    fun isUserNotificationsOptedOut(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USER_OPT_OUT, false)
    }

    fun setUserNotificationsOptedOut(context: Context, optedOut: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_USER_OPT_OUT, optedOut) }
    }

    fun saveCachedToken(context: Context, token: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LAST_FCM_TOKEN, token) }
    }

    fun getCachedToken(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_FCM_TOKEN, null)
    }

    fun openAppNotificationSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings", e)
            false
        }
    }

    fun syncCurrentFcmToken(
        context: Context,
        repository: DeviceAndUpdateRepository,
        scope: CoroutineScope
    ) {
        val appContext = context.applicationContext
        StoreProApplication.ensureFirebaseInitialized(appContext)
        scope.launch(Dispatchers.IO) {
            try {
                val allowed = hasOsNotificationPermission(appContext) && !isUserNotificationsOptedOut(appContext)
                val token = FirebaseMessaging.getInstance().token.await()
                if (!token.isNullOrBlank()) {
                    saveCachedToken(appContext, token)
                    val installationId = DeviceIdentityProvider.getOrCreateInstallationId(appContext)
                    val versionCode = DeviceIdentityProvider.getInstalledVersionCode(appContext)
                    repository.upsertFcmToken(
                        installationId = installationId,
                        token = token,
                        notificationsAllowed = allowed,
                        appVersionCode = versionCode
                    )
                    if (allowed) {
                        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_NEW_APPS).await() }
                    } else {
                        runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_NEW_APPS).await() }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token sync deferred: ${e.message}")
            }
        }
    }

    fun syncRefreshedToken(context: Context, newToken: String) {
        val appContext = context.applicationContext
        StoreProApplication.ensureFirebaseInitialized(appContext)
        saveCachedToken(appContext, newToken)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allowed = hasOsNotificationPermission(appContext) && !isUserNotificationsOptedOut(appContext)
                val installationId = DeviceIdentityProvider.getOrCreateInstallationId(appContext)
                val versionCode = DeviceIdentityProvider.getInstalledVersionCode(appContext)
                val repo = DeviceAndUpdateRepository(appContext)
                repo.upsertFcmToken(
                    installationId = installationId,
                    token = newToken,
                    notificationsAllowed = allowed,
                    appVersionCode = versionCode
                )
                if (allowed) {
                    runCatching { FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_NEW_APPS).await() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync refreshed FCM token: ${e.message}")
            }
        }
    }
}
