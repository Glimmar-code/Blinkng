package com.example.performance

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import kotlin.math.abs

/**
 * Requests a 120 Hz-class display refresh rate for every Blink activity.
 *
 * The Android scheduler can still lower the actual refresh rate when the device does not
 * support 120 Hz, Battery Saver is active, thermal limits are reached, or another surface
 * has a higher-priority frame-rate requirement. On Android 14+ the platform can match the
 * requested 120 Hz intent to the closest supported render rate. Older Android versions are
 * given the supported rate closest to 120 Hz so the request is always valid.
 */
object HighRefreshRateController : Application.ActivityLifecycleCallbacks {
    private const val TARGET_REFRESH_RATE_HZ = 120f
    private const val RATE_EPSILON = 0.1f

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyPreferredRefreshRate(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        // Re-apply after returning from another app or a system screen because OEM display
        // policies can reset the window preference while Blink is not in the foreground.
        applyPreferredRefreshRate(activity)
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredRefreshRate(activity: Activity) {
        val targetRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ accepts an arbitrary intended rate and lets Android choose the best match.
            TARGET_REFRESH_RATE_HZ
        } else {
            // Before API 34 preferredRefreshRate must be one of the display's supported rates.
            activity.windowManager.defaultDisplay.supportedModes
                .asSequence()
                .map { it.refreshRate }
                .filter { it > 0f }
                .minByOrNull { abs(it - TARGET_REFRESH_RATE_HZ) }
                ?: activity.windowManager.defaultDisplay.refreshRate
        }

        val attributes = activity.window.attributes
        if (abs(attributes.preferredRefreshRate - targetRate) > RATE_EPSILON) {
            attributes.preferredRefreshRate = targetRate
            activity.window.attributes = attributes
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
