package com.example.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide ad privacy/runtime state.
 *
 * All ad request surfaces observe [canRequestAds]. This prevents rewarded/native requests
 * from being made before Google's UMP consent flow says ad requests are allowed.
 */
object BlinkAdsRuntime {
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired = _privacyOptionsRequired.asStateFlow()

    private val mobileAdsInitialized = AtomicBoolean(false)

    fun updateConsent(canRequestAds: Boolean, privacyOptionsRequired: Boolean) {
        _canRequestAds.value = canRequestAds
        _privacyOptionsRequired.value = privacyOptionsRequired
    }

    fun initializeMobileAds(context: Context) {
        if (!_canRequestAds.value) return
        if (mobileAdsInitialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext) {}
        }
    }
}
