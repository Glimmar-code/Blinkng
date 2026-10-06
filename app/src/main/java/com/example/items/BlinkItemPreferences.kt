package com.example.items

import android.content.Context
import com.example.util.safeBoolean
import com.example.util.safeInt
import com.example.util.safeLong
import com.example.util.safeString

object BlinkItemPreferences {
    private const val PREFS = "blink_items"

    private const val STEPS_ENABLED = "steps_enabled"
    private const val WEATHER_ENABLED = "weather_enabled"
    private const val STEP_MILESTONES_ENABLED = "step_milestones_enabled"
    private const val STEP_GOAL_ENABLED = "step_goal_enabled"
    private const val WEATHER_RAIN_ALERTS_ENABLED = "weather_rain_alerts_enabled"
    private const val WEATHER_SEVERE_ALERTS_ENABLED = "weather_severe_alerts_enabled"
    private const val DAILY_STEP_GOAL = "daily_step_goal"

    private const val LAST_LATITUDE = "last_latitude"
    private const val LAST_LONGITUDE = "last_longitude"
    private const val LAST_WEATHER_JSON = "last_weather_json"
    private const val LAST_WEATHER_FETCH_MS = "last_weather_fetch_ms"
    private const val LAST_STEP_COUNT = "last_step_count"
    private const val LAST_STEP_DATE = "last_step_date"
    private const val LAST_STEP_MILESTONE = "last_step_milestone"
    private const val LAST_WEATHER_ALERT_ID = "last_weather_alert_id"
    private const val LAST_RAIN_EVENT = "last_rain_event"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stepsEnabled(context: Context): Boolean = prefs(context).safeBoolean(STEPS_ENABLED, false)

    fun setStepsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(STEPS_ENABLED, enabled).apply()
    }

    fun weatherEnabled(context: Context): Boolean = prefs(context).safeBoolean(WEATHER_ENABLED, false)

    fun setWeatherEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(WEATHER_ENABLED, enabled).apply()
    }

    fun stepMilestonesEnabled(context: Context): Boolean =
        prefs(context).safeBoolean(STEP_MILESTONES_ENABLED, true)

    fun setStepMilestonesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(STEP_MILESTONES_ENABLED, enabled).apply()
    }

    fun stepGoalEnabled(context: Context): Boolean = prefs(context).safeBoolean(STEP_GOAL_ENABLED, true)

    fun setStepGoalEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(STEP_GOAL_ENABLED, enabled).apply()
    }

    fun rainAlertsEnabled(context: Context): Boolean =
        prefs(context).safeBoolean(WEATHER_RAIN_ALERTS_ENABLED, true)

    fun setRainAlertsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(WEATHER_RAIN_ALERTS_ENABLED, enabled).apply()
    }

    fun severeAlertsEnabled(context: Context): Boolean =
        prefs(context).safeBoolean(WEATHER_SEVERE_ALERTS_ENABLED, true)

    fun setSevereAlertsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(WEATHER_SEVERE_ALERTS_ENABLED, enabled).apply()
    }

    fun dailyStepGoal(context: Context): Int =
        prefs(context).safeInt(DAILY_STEP_GOAL, 10_000).coerceIn(1_000, 100_000)

    fun setDailyStepGoal(context: Context, goal: Int) {
        prefs(context).edit().putInt(DAILY_STEP_GOAL, goal.coerceIn(1_000, 100_000)).apply()
    }

    fun saveWeatherLocation(context: Context, latitude: Double, longitude: Double) {
        prefs(context).edit()
            .putLong(LAST_LATITUDE, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(LAST_LONGITUDE, java.lang.Double.doubleToRawLongBits(longitude))
            .apply()
    }

    fun weatherLocation(context: Context): Pair<Double, Double>? {
        val p = prefs(context)
        if (!p.contains(LAST_LATITUDE) || !p.contains(LAST_LONGITUDE)) return null
        return java.lang.Double.longBitsToDouble(p.safeLong(LAST_LATITUDE, 0L)) to
            java.lang.Double.longBitsToDouble(p.safeLong(LAST_LONGITUDE, 0L))
    }

    fun saveWeatherCache(context: Context, rawJson: String) {
        prefs(context).edit()
            .putString(LAST_WEATHER_JSON, rawJson)
            .putLong(LAST_WEATHER_FETCH_MS, System.currentTimeMillis())
            .apply()
    }

    fun cachedWeatherJson(context: Context): String? =
        prefs(context).safeString(LAST_WEATHER_JSON, null)

    fun lastWeatherFetchMillis(context: Context): Long =
        prefs(context).safeLong(LAST_WEATHER_FETCH_MS, 0L)

    fun saveStepSnapshot(context: Context, date: String, steps: Long) {
        prefs(context).edit()
            .putString(LAST_STEP_DATE, date)
            .putLong(LAST_STEP_COUNT, steps.coerceAtLeast(0L))
            .apply()
    }

    fun lastStepDate(context: Context): String = prefs(context).safeString(LAST_STEP_DATE, "").orEmpty()

    fun lastStepCount(context: Context): Long = prefs(context).safeLong(LAST_STEP_COUNT, 0L)

    fun lastStepMilestone(context: Context): Int = prefs(context).safeInt(LAST_STEP_MILESTONE, 0)

    fun setLastStepMilestone(context: Context, milestone: Int) {
        prefs(context).edit().putInt(LAST_STEP_MILESTONE, milestone.coerceAtLeast(0)).apply()
    }

    fun resetDailyMilestoneIfNeeded(context: Context, date: String) {
        if (lastStepDate(context) != date) {
            prefs(context).edit()
                .putInt(LAST_STEP_MILESTONE, 0)
                .putLong(LAST_STEP_COUNT, 0L)
                .putString(LAST_STEP_DATE, date)
                .apply()
        }
    }

    fun lastWeatherAlertId(context: Context): String =
        prefs(context).safeString(LAST_WEATHER_ALERT_ID, "").orEmpty()

    fun setLastWeatherAlertId(context: Context, id: String) {
        prefs(context).edit().putString(LAST_WEATHER_ALERT_ID, id).apply()
    }

    fun lastRainEvent(context: Context): Long = prefs(context).safeLong(LAST_RAIN_EVENT, 0L)

    fun setLastRainEvent(context: Context, epochSeconds: Long) {
        prefs(context).edit().putLong(LAST_RAIN_EVENT, epochSeconds).apply()
    }
}
