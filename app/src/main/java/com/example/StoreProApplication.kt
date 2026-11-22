package com.example

import android.app.Application
import android.util.Log
import com.example.notifications.StoreProNotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class StoreProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized(this)
        StoreProNotificationHelper.ensureNotificationChannel(this)
    }

    companion object {
        private const val TAG = "StoreProApplication"

        fun ensureFirebaseInitialized(context: android.content.Context) {
            try {
                val appContext = context.applicationContext ?: context
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    val initialized = FirebaseApp.initializeApp(appContext)
                    if (initialized == null) {
                        val options = FirebaseOptions.fromResource(appContext)
                        if (options != null) {
                            FirebaseApp.initializeApp(appContext, options)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FirebaseApp", e)
            }
        }
    }
}
