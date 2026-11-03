package com.example.data.supabase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Local-only bandwidth preferences; account data never leaves the device. */
object BlinkChatMediaPreferences {
    private const val PREFS = "blink_chat_media_downloads"
    private const val WIFI = "automatic_wifi"
    private const val MOBILE = "automatic_mobile"
    private const val DATA_SAVER = "data_saver"

    fun wifiAuto(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(WIFI, true)
    fun mobileAuto(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(MOBILE, false)
    fun dataSaver(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DATA_SAVER, false)

    fun update(context: Context, wifi: Boolean? = null, mobile: Boolean? = null,
               saver: Boolean? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            wifi?.let { putBoolean(WIFI, it) }
            mobile?.let { putBoolean(MOBILE, it) }
            saver?.let { putBoolean(DATA_SAVER, it) }
        }.apply()
    }

    fun allowAutoDownload(context: Context): Boolean {
        if (dataSaver(context)) return false
        val networks = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val active = networks.getNetworkCapabilities(networks.activeNetwork) ?: return false
        return when {
            active.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifiAuto(context)
            active.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> wifiAuto(context)
            active.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> mobileAuto(context)
            else -> false
        }
    }
}
