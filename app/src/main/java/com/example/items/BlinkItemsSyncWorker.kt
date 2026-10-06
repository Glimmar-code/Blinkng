package com.example.items

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic, battery-conscious Item refresh.
 *
 * Steps are read only when the user enabled Steps and explicitly granted Health Connect
 * background access. Weather uses the last user-approved coarse location and never starts
 * a location request by itself.
 */
class BlinkItemsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (BlinkItemPreferences.stepsEnabled(applicationContext)) {
            runCatching {
                val steps = BlinkStepRepository(applicationContext)
                if (steps.isAvailable() && steps.hasReadPermission(requireBackground = true)) {
                    steps.readTodaySteps().getOrNull()?.let {
                        BlinkItemNotificationManager.evaluateStepMilestone(applicationContext, it)
                    }
                }
            }
        }

        if (BlinkItemPreferences.weatherEnabled(applicationContext)) {
            val location = BlinkItemPreferences.weatherLocation(applicationContext)
            if (location != null) {
                runCatching {
                    BlinkWeatherRepository(applicationContext)
                        .fetch(location.first, location.second)
                        .getOrNull()
                        ?.let {
                            BlinkItemNotificationManager.evaluateWeather(applicationContext, it)
                        }
                }
            }
        }

        // A provider outage must not turn BLINK into a tight retry loop. The next normal
        // periodic pass will reconcile the latest values.
        return Result.success()
    }
}
