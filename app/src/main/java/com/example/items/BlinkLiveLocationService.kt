package com.example.items

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notification.BlinkNotificationHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * User-visible foreground service for an explicitly started live-location session.
 *
 * It never starts itself, never requests background-location permission, and stops when
 * the selected sharing window expires or the user taps Stop sharing.
 */
class BlinkLiveLocationService : Service() {
    companion object {
        const val ACTION_START = "com.example.items.START_LIVE_LOCATION"
        const val ACTION_STOP = "com.example.items.STOP_LIVE_LOCATION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_EXPIRES_AT = "expires_at"

        private const val NOTIFICATION_ID = 63_001
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 15f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val repository by lazy { BlinkLiveLocationRepository(this) }

    private var activeSessionId: String = ""
    private var expiresAtMillis: Long = 0L
    private var expiryJob: Job? = null
    private var callback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val session = intent.getStringExtra(EXTRA_SESSION_ID)
                    .orEmpty()
                    .ifBlank { activeSessionId }
                stopSharing(session)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val session = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
                val expiry = intent.getStringExtra(EXTRA_EXPIRES_AT).orEmpty()
                if (session.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                activeSessionId = session
                expiresAtMillis = repository.expiresAtMillis(expiry)
                if (expiresAtMillis <= System.currentTimeMillis()) {
                    stopSharing(session)
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, buildNotification(session, expiry))
                startExpiryTimer(session)
                startLocationUpdates()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(sessionId: String, expiresAt: String) =
        NotificationCompat.Builder(this, BlinkItemNotificationManager.CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_stat_blink)
            .setContentTitle("BLINK Live Location")
            .setContentText("You're sharing your live location with selected people.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "You're sharing your live location with selected people. " +
                        "Sharing stops automatically at the selected time."
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(
                            BlinkNotificationHelper.EXTRA_ACTION,
                            BlinkNotificationHelper.ACTION_OPEN_ITEMS
                        )
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                "Stop sharing",
                PendingIntent.getService(
                    this,
                    NOTIFICATION_ID + 1,
                    Intent(this, BlinkLiveLocationService::class.java).apply {
                        action = ACTION_STOP
                        putExtra(EXTRA_SESSION_ID, sessionId)
                        putExtra(EXTRA_EXPIRES_AT, expiresAt)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        callback?.let(fused::removeLocationUpdates)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(5_000L)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val session = activeSessionId
                if (session.isBlank()) return
                if (System.currentTimeMillis() >= expiresAtMillis) {
                    stopSharing(session)
                    return
                }

                scope.launch {
                    repository.updatePosition(
                        sessionId = session,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy
                    )
                }
            }
        }
        callback = locationCallback
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun startExpiryTimer(sessionId: String) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            val remaining = (expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(remaining)
            stopSharing(sessionId)
        }
    }

    private fun stopSharing(sessionId: String) {
        callback?.let(fused::removeLocationUpdates)
        callback = null
        expiryJob?.cancel()
        expiryJob = null

        if (sessionId.isNotBlank()) {
            scope.launch {
                repository.stopSession(sessionId)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        callback?.let(fused::removeLocationUpdates)
        callback = null
        expiryJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
