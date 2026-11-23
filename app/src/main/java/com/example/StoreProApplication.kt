package com.example

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.local.StoreProDatabase
import com.example.notifications.StoreProNotificationHelper
import com.example.ui.upload.UniversalImageLoader
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StoreProApplication : Application(), ImageLoaderFactory {

    private val appWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized(this)
        StoreProNotificationHelper.ensureNotificationChannel(this)
        // Warm up Coil disk/memory cache & Room database off the main thread for snappy first-screen transitions
        appWarmupScope.launch {
            runCatching { UniversalImageLoader.get(this@StoreProApplication) }
            runCatching { StoreProDatabase.getInstance(this@StoreProApplication).searchHistoryDao() }
        }
    }

    override fun newImageLoader(): ImageLoader = UniversalImageLoader.get(this)

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
