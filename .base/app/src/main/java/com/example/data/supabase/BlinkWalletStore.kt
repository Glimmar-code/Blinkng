package com.example.data.supabase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide owner wallet cache.
 *
 * The server remains authoritative. This object only mirrors the latest authenticated
 * balance so Profile, Store, Boost and Drops never drift apart while the app is open.
 */
object BlinkWalletStore {
    private val _balance = MutableStateFlow<Long?>(null)
    val balance: StateFlow<Long?> = _balance.asStateFlow()

    fun publish(value: Long?) {
        if (value == null) return
        _balance.value = value.coerceAtLeast(0L)
    }

    fun clear() {
        _balance.value = null
    }
}
