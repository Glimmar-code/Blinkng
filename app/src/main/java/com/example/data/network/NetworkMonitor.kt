package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Android's NET_CAPABILITY_VALIDATED flag is useful for UI hints, but it is not a
     * reliable gate for whether Blink should even attempt a Supabase request. Some OEMs,
     * mobile networks, VPNs and freshly switched transports can expose a working internet
     * path before (or without) Android marking it VALIDATED.
     *
     * Treat the active default network as usable when it advertises INTERNET and is not a
     * known captive portal. Supabase request results remain the source of truth for the
     * separate "live server" state in BlinkViewModel.
     */
    fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        return connectivityManager
            .getNetworkCapabilities(activeNetwork)
            .hasUsableInternetPath()
    }

    val isOnline: Flow<Boolean> = callbackFlow {
        fun publishCurrentState() {
            trySend(isCurrentlyOnline())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publishCurrentState()

            override fun onLost(network: Network) = publishCurrentState()

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork == network) {
                    trySend(networkCapabilities.hasUsableInternetPath())
                } else {
                    publishCurrentState()
                }
            }
        }

        publishCurrentState()
        try {
            // We care about the device's actual default route used by OkHttp/Supabase,
            // rather than every network that merely advertises INTERNET capability.
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (_: SecurityException) {
            trySend(false)
        }

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }
        .distinctUntilChanged()
        .conflate()
}

private fun NetworkCapabilities?.hasUsableInternetPath(): Boolean =
    this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        !this.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
