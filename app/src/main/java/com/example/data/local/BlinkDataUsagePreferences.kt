package com.example.data.local

import android.content.Context

/** User-controlled policy for reducing image/media downloads on metered networks. */
object BlinkDataUsagePreferences {
    private const val FILE = "blink_data_usage_preferences"
    private const val KEY_REDUCE_MOBILE_DATA = "reduce_mobile_data"

    fun isReduceMobileDataEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_REDUCE_MOBILE_DATA, false)

    fun setReduceMobileDataEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REDUCE_MOBILE_DATA, enabled)
            .apply()
    }
}
