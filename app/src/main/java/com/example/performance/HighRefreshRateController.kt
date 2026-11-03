package com.example.performance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.blinkng.shared.BlinkPerformanceMode
import com.blinkng.shared.BlinkPerformancePolicy
import kotlin.math.abs

/**
 * Respect display hardware, Android dynamic scheduling and user performance preference.
 * Automatic (default) explicitly clears any forced 120Hz override.
 */
object HighRefreshRateController : Application.ActivityLifecycleCallbacks {
    private const val PREF_NAME = "blink_performance_settings"
    private const val PREF_KEY = "preferred_mode"
    private const val RATE_EPSILON = 0.1f

    val currentModeState = mutableStateOf(BlinkPerformanceMode.AUTOMATIC)

    fun mode(context: Context): BlinkPerformanceMode =
        BlinkPerformanceMode.fromStorage(
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(PREF_KEY, null)
        )

    fun setMode(context: Context, selected: BlinkPerformanceMode) {
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, selected.name).apply()
        currentModeState.value = selected
        var current: Context? = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        (current as? Activity)?.let(::applyPreferredRefreshRate)
    }

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        currentModeState.value = mode(application)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentModeState.value = mode(activity)
        applyPreferredRefreshRate(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentModeState.value = mode(activity)
        applyPreferredRefreshRate(activity)
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredRefreshRate(activity: Activity) {
        val available = runCatching {
            activity.windowManager.defaultDisplay.supportedModes
                .map { it.refreshRate }
                .filter { it > 0f }
        }.getOrDefault(emptyList())

        val rate = BlinkPerformancePolicy.targetRefreshRate(
            currentModeState.value, available
        )
        val attributes = activity.window.attributes
        if (abs(attributes.preferredRefreshRate - rate) > RATE_EPSILON) {
            attributes.preferredRefreshRate = rate
            activity.window.attributes = attributes
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
