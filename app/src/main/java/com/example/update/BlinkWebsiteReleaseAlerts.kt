package com.example.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.blinkng.shared.update.BlinkReleaseAlertPolicy
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Website APK announcements are public FCM topic messages. They contain only
 * version metadata, never account identifiers or installer URLs. Even after a
 * notification tap, the app fetches/validates the official release again.
 */
object BlinkWebsiteReleaseAlerts {
    const val ACTION_OPEN_UPDATE = "com.example.blink.OPEN_WEBSITE_UPDATE"
    private const val TOPIC = "blink_android_website_releases"
    private const val CHANNEL = "blink_app_release_alerts"
    private const val PREFS = "blink_website_release_alerts"
    private const val LAST_NOTIFIED = "last_notified_version_code"
    private const val NOTIFICATION_ID = 47620

    private val _refreshSignal = MutableStateFlow(0L)
    val refreshSignal = _refreshSignal.asStateFlow()
    private val _openPromptSignal = MutableStateFlow(0L)
    val openPromptSignal = _openPromptSignal.asStateFlow()

    fun requestRefresh(openPrompt: Boolean = false) {
        _refreshSignal.update { it + 1 }
        if (openPrompt) _openPromptSignal.update { it + 1 }
    }

    /** Only the website-distributed app subscribes. FCM automatically retries transient failures. */
    fun configureSubscription(context: Context) {
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            val task = if (WebsiteApkDownloadCoordinator.isPlayStoreInstall(context)) {
                messaging.unsubscribeFromTopic(TOPIC)
            } else {
                messaging.subscribeToTopic(TOPIC)
            }
            task.addOnFailureListener { error ->
                Log.w("BlinkReleaseAlert", "Release topic subscription pending retry", error)
            }
        }.onFailure { Log.w("BlinkReleaseAlert", "Unable to configure release notifications", it) }
    }

    /** A data-only FCM message is processed even when the application is not visible. */
    fun handleReleasePush(context: Context, data: Map<String, String>): Boolean {
        if (data["type"] != "blink_release_update") return false
        if (WebsiteApkDownloadCoordinator.isPlayStoreInstall(context)) return true
        val candidate = data["version_code"]?.toIntOrNull() ?: return true
        requestRefresh() // An active user gets an immediate official metadata re-check.
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!BlinkReleaseAlertPolicy.shouldNotify(
                BuildConfig.VERSION_CODE, candidate,
                preferences.getInt(LAST_NOTIFIED, 0),
            )) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return true

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "BLINK app updates", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        val open = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_UPDATE
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, NOTIFICATION_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_blink)
            .setContentTitle("BLINK update available")
            .setContentText("A new verified BLINK version is ready. Tap to update.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notice)
            preferences.edit().putInt(LAST_NOTIFIED, candidate).apply()
        }.onFailure { Log.w("BlinkReleaseAlert", "Could not display update notification", it) }
        return true
    }
}
