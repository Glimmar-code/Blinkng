package com.example

import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.auth.AccountSessionStore
import com.example.call.IncomingCallNotification
import com.example.call.BlinkTelecomManager
import com.example.data.supabase.SupabaseService
import com.example.notification.BlinkFirebaseMessagingService
import com.example.notification.BlinkNotificationHelper
import com.example.notification.NotificationSyncWorker
import com.example.items.BlinkItemNotificationManager
import com.example.items.BlinkItemsSyncWorker
import com.example.performance.HighRefreshRateController
import com.example.util.safeString
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.TimeUnit

class BlinkApplication : Application(), ImageLoaderFactory {
    // Non-essential services must not prevent the main BLINK feed from starting.
    // Do not catch fatal JVM errors (including OutOfMemoryError).
    private fun startOptionalService(name: String, start: () -> Unit) {
        try {
            start()
        } catch (error: Exception) {
            android.util.Log.e("BlinkApplication", "Optional service failed: $name", error)
            try {
                FirebaseCrashlytics.getInstance().recordException(error)
            } catch (_: Exception) {
                // Crash reporting must never become a second startup failure.
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Make on-demand game splits accessible, including in the isolated Godot process.
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Native Godot uses a separate :blink_life process. Never run BLINK
        // social app initialization, Firebase, calls or workers in that process.
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.runningAppProcesses?.firstOrNull {
                it.pid == android.os.Process.myPid()
            }?.processName
        }
        if (processName?.endsWith(":blink_life") == true) return
        if (processName?.endsWith(":blink_life_web") == true) {
            // A WebView hosted in another process must never share Chromium data-dir
            // with BLINK's normal web/auth screens (Android 9+ requirement).
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.webkit.WebView.setDataDirectorySuffix("blink_life_web")
            }
            return // Game crash/renderer must not initialize BLINK social workers.
        }

        // Apply Blink's 120 Hz-class preference to every Activity before its UI is created.
        startOptionalService("refresh rate") { HighRefreshRateController.install(this) }

        // Keep every production crash report traceable to the exact source revision that
        // produced the installed APK. This value is generated from Git at build time.
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("build_commit_sha", BuildConfig.BUILD_COMMIT_SHA)
                setCustomKey("build_version_name", BuildConfig.VERSION_NAME)
                setCustomKey("build_version_code", BuildConfig.VERSION_CODE)
            }
        }

        SupabaseService.initialize(this)

        // Google Mobile Ads is initialized from MainActivity only after UMP says ad requests
        // are allowed. Keeping it out of Application prevents ads from starting before consent.

        startOptionalService("notification channels") { BlinkNotificationHelper.createNotificationChannels(this) }
        startOptionalService("item channels") { BlinkItemNotificationManager.createChannels(this) }
        // Create voice/video/missed-call channels on cold start as well, so users can tune
        // call sounds and lock-screen behavior before their first incoming Blink call.
        startOptionalService("call notification channels") { IncomingCallNotification.createChannels(this) }

        // Register BLINK as a self-managed VoIP application so Android can coordinate
        // voice/video calls with Bluetooth, wearables and cellular/system calls.
        startOptionalService("telecom registration") { BlinkTelecomManager.registerApplication(this) }

        // Fetch/log the current token on every cold start. If a Supabase session already
        // exists it is also registered immediately; otherwise MainActivity re-syncs after login.
        startOptionalService("push token registration") { BlinkFirebaseMessagingService.syncCurrentToken(this) }

        // Capture whichever authenticated profile/session the ViewModel persists.
        // This gives Switch Account a true recent-login list without passwords.
        val sessionPrefs = getSharedPreferences("blink_supabase_session", MODE_PRIVATE)
        val profilePrefs = getSharedPreferences("blink_user_session", MODE_PRIVATE)
        fun captureRecentAccount() {
            val access = sessionPrefs.safeString("access_token", "").orEmpty()
            val refresh = sessionPrefs.safeString("refresh_token", "").orEmpty()
            val username = profilePrefs.safeString("username", "").orEmpty()
            val email = profilePrefs.safeString("email", "").orEmpty()
            val fullName = profilePrefs.safeString("full_name", "").orEmpty()
            val avatar = profilePrefs.safeString("avatar_url", "").orEmpty()
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
        startOptionalService("recent account restoration") {
            sessionPrefs.registerOnSharedPreferenceChangeListener { _, key ->
                if (key == "access_token" || key == "refresh_token") {
                    startOptionalService("recent account refresh") { captureRecentAccount() }
                }
            }
            profilePrefs.registerOnSharedPreferenceChangeListener { _, key ->
                if (key == "username" || key == "full_name" || key == "email" || key == "avatar_url") {
                    startOptionalService("recent profile refresh") { captureRecentAccount() }
                }
            }
            captureRecentAccount()
        }

        startOptionalService("notification sync worker") {
            val work = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
                .addTag("blink_notification_sync")
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "blink_notification_sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
        }

        startOptionalService("item sync worker") {
            val itemWork = PeriodicWorkRequestBuilder<BlinkItemsSyncWorker>(15, TimeUnit.MINUTES)
                .addTag("blink_items_sync")
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "blink_items_sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                itemWork
            )
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .strongReferencesEnabled(false)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("blink_media/images").apply { mkdirs() })
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .allowHardware(true)
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN is an ordinary background transition; preserve its warm cache.
        // Release decoded bitmaps only under genuine memory pressure.
        val underPressure =
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (underPressure) clearImageMemorySafely()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        clearImageMemorySafely()
    }

    private fun clearImageMemorySafely() {
        try {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        } catch (error: Exception) {
            android.util.Log.w("BlinkApplication", "Unable to clear image cache", error)
        }
    }
}
