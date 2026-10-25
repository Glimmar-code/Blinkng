package com.example.items

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notification.BlinkNotificationHelper
import java.time.LocalDate

/**
 * User-enabled, offline step counter notification.
 *
 * Uses the built-in low-power TYPE_STEP_COUNTER sensor. No network, GPS, Supabase,
 * wake locks or auto-reposting dismissed notifications. Android 14+ may allow users
 * to swipe an ongoing notification away; apps cannot override that system choice.
 */
class BlinkStepNotificationService : Service(), SensorEventListener {
    companion object {
        private const val ACTION_START = "com.example.items.START_STEP_NOTIFICATION"
        private const val ACTION_STOP = "com.example.items.STOP_STEP_NOTIFICATION"
        private const val ACTION_DISMISS = "com.example.items.DISMISS_STEP_NOTIFICATION"
        private const val NOTIFICATION_ID = 63_002
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 30_000L
        private const val DAILY_RECHECK_INTERVAL_MS = 60_000L

        /** Call only while the user is interacting with the app. */
        fun start(context: Context): Result<Unit> = runCatching {
            val repository = BlinkStepRepository(context)
            check(BlinkItemPreferences.stepsEnabled(context)) { "Enable Steps first." }
            check(repository.isAvailable()) { "This device has no step counter." }
            check(repository.hasReadPermission()) { "Physical activity permission is needed." }
            BlinkItemPreferences.setStepNotificationEnabled(context, true)
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BlinkStepNotificationService::class.java).setAction(ACTION_START)
                )
            } catch (error: Exception) {
                BlinkItemPreferences.setStepNotificationEnabled(context, false)
                throw error
            }
            Unit
        }

        fun stop(context: Context) {
            BlinkItemPreferences.setStepNotificationEnabled(context, false)
            // No background service launch is needed to stop a foreground service.
            context.stopService(Intent(context, BlinkStepNotificationService::class.java))
        }
    }

    private val sensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var trackingSensor = false
    private var lastPostedSteps = -1L
    private var lastPostedDay = ""
    private var lastPostedElapsed = 0L

    private val periodicUpdate = object : Runnable {
        override fun run() {
            if (!shouldRun()) {
                stopTracking()
                return
            }
            updateNotification(BlinkItemPreferences.todayStepCount(this@BlinkStepNotificationService))
            mainHandler.postDelayed(this, DAILY_RECHECK_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BlinkItemPreferences.setStepNotificationEnabled(this, false)
            stopTracking()
            return START_NOT_STICKY
        }

        if (!shouldRun()) {
            stopTracking()
            return START_NOT_STICKY
        }

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            BlinkItemPreferences.setStepNotificationEnabled(this, false)
            stopTracking()
            return START_NOT_STICKY
        }

        // Android requires an immediate foreground notification after startForegroundService.
        val started = runCatching {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(BlinkItemPreferences.todayStepCount(this))
            )
        }.isSuccess
        if (!started) {
            BlinkItemPreferences.setStepNotificationEnabled(this, false)
            stopTracking()
            return START_NOT_STICKY
        }

        if (!trackingSensor) {
            val registered = runCatching {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }.getOrDefault(false)
            if (!registered) {
                BlinkItemPreferences.setStepNotificationEnabled(this, false)
                stopTracking()
                return START_NOT_STICKY
            }
            trackingSensor = true
        }
        mainHandler.removeCallbacks(periodicUpdate)
        mainHandler.postDelayed(periodicUpdate, DAILY_RECHECK_INTERVAL_MS)
        return START_STICKY
    }

    private fun shouldRun(): Boolean {
        if (!BlinkItemPreferences.stepsEnabled(this) ||
            !BlinkItemPreferences.stepNotificationEnabled(this)
        ) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER || !shouldRun()) return
        val raw = event.values.firstOrNull()?.toLong() ?: return
        val count = BlinkItemPreferences.stepsFromSensorCounter(
            context = this,
            date = LocalDate.now().toString(),
            cumulativeCounter = raw
        )
        updateNotification(count)
        BlinkItemNotificationManager.evaluateStepMilestone(this, count)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateNotification(count: Long) {
        val today = LocalDate.now().toString()
        val elapsed = SystemClock.elapsedRealtime()
        if (count == lastPostedSteps && today == lastPostedDay) return
        if (lastPostedElapsed != 0L && today == lastPostedDay &&
            elapsed - lastPostedElapsed < NOTIFICATION_UPDATE_INTERVAL_MS
        ) return

        val notification = buildNotification(count)
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
        lastPostedSteps = count
        lastPostedDay = today
        lastPostedElapsed = elapsed
    }

    private fun buildNotification(steps: Long): Notification {
        val goal = BlinkItemPreferences.dailyStepGoal(this)
        val progress = BlinkItemsPolicy.stepProgressPercent(steps, goal)
        val formatted = "%,d".format(steps.coerceAtLeast(0L))
        val goalText = "%,d".format(goal)

        val open = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(BlinkNotificationHelper.EXTRA_ACTION, BlinkNotificationHelper.ACTION_OPEN_ITEMS)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val turnOff = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID + 1,
            Intent(this, BlinkStepNotificationControlsReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissed = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID + 2,
            Intent(this, BlinkStepNotificationControlsReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BlinkItemNotificationManager.CHANNEL_STEP_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_blink)
            .setContentTitle("BLINK Steps • $formatted today")
            .setContentText("$formatted / $goalText steps • $progress% of goal")
            .setProgress(100, progress, false)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(R.drawable.ic_stat_blink, "Turn off", turnOff)
            .build().apply { flags = flags or Notification.FLAG_NO_CLEAR }
    }

    private fun stopTracking() {
        if (trackingSensor) sensorManager.unregisterListener(this)
        trackingSensor = false
        mainHandler.removeCallbacks(periodicUpdate)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (trackingSensor) sensorManager.unregisterListener(this)
        trackingSensor = false
        mainHandler.removeCallbacks(periodicUpdate)
        super.onDestroy()
    }
}
