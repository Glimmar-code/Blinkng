package com.example.items

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Reads Android's hardware step counter without raising BLINK's existing minSdk.
 *
 * TYPE_STEP_COUNTER is a low-power cumulative counter maintained by the device since boot.
 * BLINK converts that cumulative value into an approximate daily count using a local baseline.
 * Nothing is uploaded unless another feature explicitly asks for a daily summary.
 */
class BlinkStepRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun isAvailable(): Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun requiresRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun hasReadPermission(): Boolean =
        !requiresRuntimePermission() ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED

    suspend fun readTodaySteps(now: Instant = Instant.now()): Result<Long> = runCatching {
        check(isAvailable()) { "This device does not provide a hardware step counter." }
        check(hasReadPermission()) { "Physical activity permission has not been granted." }

        val cumulative = readCumulativeCounter()
            ?: error("The device step counter did not respond.")
        val date = LocalDate.ofInstant(now, ZoneId.systemDefault()).toString()
        BlinkItemPreferences.stepsFromSensorCounter(
            context = appContext,
            date = date,
            cumulativeCounter = cumulative
        )
    }

    private suspend fun readCumulativeCounter(): Long? {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null

        return withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (!continuation.isActive) return
                        val raw = event.values.firstOrNull()?.toLong() ?: return
                        sensorManager.unregisterListener(this)
                        continuation.resume(raw.coerceAtLeast(0L))
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                val registered = sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                if (!registered) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }
}
