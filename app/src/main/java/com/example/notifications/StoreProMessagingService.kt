package com.example.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles incoming FCM push notifications (foreground & data messages) and automatic
 * FCM token refresh synchronization with Firestore.
 */
class StoreProMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        PushNotificationCoordinator.syncRefreshedToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        handleIncomingDataPayload(
            context = applicationContext,
            data = remoteMessage.data,
            fallbackMessageId = remoteMessage.messageId,
            notificationTitle = remoteMessage.notification?.title,
            notificationBody = remoteMessage.notification?.body
        )
    }

    companion object {
        private const val TAG = "StoreProMessagingSvc"

        internal fun handleIncomingDataPayload(
            context: Context,
            data: Map<String, String>,
            fallbackMessageId: String? = null,
            notificationTitle: String? = null,
            notificationBody: String? = null
        ): Boolean {
            val appId = data[StoreProNotificationHelper.KEY_FCM_APP_ID]?.trim().orEmpty()
            if (appId.isBlank()) {
                Log.d(TAG, "Ignoring FCM message without appId payload")
                return false
            }

            val appName = data["appName"]?.trim().orEmpty()
            val iconUrl = data["iconUrl"]?.trim()
            val eventId = data[StoreProNotificationHelper.KEY_FCM_EVENT_ID]?.trim() ?: fallbackMessageId
            val title = data["title"] ?: notificationTitle
            val body = data["body"] ?: notificationBody

            return StoreProNotificationHelper.showNewAppNotification(
                context = context,
                appId = appId,
                appName = appName,
                iconUrl = iconUrl,
                eventId = eventId,
                customTitle = title,
                customBody = body
            )
        }
    }
}
