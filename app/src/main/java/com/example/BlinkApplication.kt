package com.example

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.auth.AccountSessionStore
import com.example.data.supabase.SupabaseService
import com.example.notification.BlinkNotificationHelper
import com.example.notification.NotificationSyncWorker
import com.example.performance.HighRefreshRateController
import java.util.concurrent.TimeUnit

class BlinkApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        // Apply Blink's 120 Hz-class preference to every Activity before its UI is created.
        HighRefreshRateController.install(this)

        SupabaseService.initialize(this)
        BlinkNotificationHelper.createNotificationChannels(this)

        // Capture whichever authenticated profile/session the ViewModel persists.
        // This gives Switch Account a true recent-login list without passwords.
        val sessionPrefs = getSharedPreferences("blink_supabase_session", MODE_PRIVATE)
        val profilePrefs = getSharedPreferences("blink_user_session", MODE_PRIVATE)
        fun captureRecentAccount() {
            val access = sessionPrefs.getString("access_token", "").orEmpty()
            val refresh = sessionPrefs.getString("refresh_token", "").orEmpty()
            val username = profilePrefs.getString("username", "").orEmpty()
            val email = profilePrefs.getString("email", "").orEmpty()
            val fullName = profilePrefs.getString("full_name", "").orEmpty()
            val avatar = profilePrefs.getString("avatar_url", "").orEmpty()
            val userId = SupabaseService.accessToken()?.let { token ->
                token.split('.').getOrNull(1)?.let { part ->
                    runCatching {
                        android.util.Base64.decode(part, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                            .toString(Charsets.UTF_8)
                            .let { org.json.JSONObject(it).optString("sub") }
                    }.getOrNull()
                }
            }.orEmpty()
            if (access.isNotBlank() && refresh.isNotBlank() && username.isNotBlank() && userId.isNotBlank()) {
                AccountSessionStore.recordCurrentSession(this, userId, username, fullName, email, avatar)
            }
        }
        sessionPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "access_token" || key == "refresh_token") captureRecentAccount()
        }
        profilePrefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "username" || key == "full_name" || key == "email" || key == "avatar_url") captureRecentAccount()
        }
        captureRecentAccount()

        val work = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
            .addTag("blink_notification_sync")
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "blink_notification_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .allowHardware(true)
            .build()

    override fun onLowMemory() {
        super.onLowMemory()
        // Preserve decoded images during ordinary app backgrounding so returning to
        // the feed does not force a burst of disk decodes. Clear only on real pressure.
        coil.Coil.imageLoader(this).memoryCache?.clear()
    }
}
