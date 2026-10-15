package com.example.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions

/**
 * Owns Blink's opt-in rewarded-ad lifecycle.
 *
 * Rewarded ads are never auto-shown. The caller must explicitly request display after a user
 * taps "Watch ad". Debug builds use Google's rewarded test ad unit through BuildConfig.
 */
class BlinkRewardedAdManager(
    private val activity: Activity,
    private val adUnitId: String,
    private val requestAd: (Activity, String, AdRequest, RewardedAdLoadCallback) -> Unit =
        { host, unit, request, callback -> RewardedAd.load(host, unit, request, callback) }
) {
    companion object {
        private const val TAG = "BlinkRewardedAds"
        const val BLINK_COIN_REWARD = 10
    }

    private var rewardedAd: RewardedAd? = null
    private var loading = false
    private var lastLoadFailure: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var closed = false
    private var loadedAt = 0L
    private var timeout: Runnable? = null

    private fun cancelTimeout() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
    }

    fun close() {
        closed = true
        generation++
        cancelTimeout()
        rewardedAd = null
        loading = false
    }

    val loadFailureMessage: String?
        get() = lastLoadFailure

    val isReady: Boolean
        get() {
            if (rewardedAd != null && SystemClock.elapsedRealtime() - loadedAt >= 55 * 60_000L) {
                rewardedAd = null
            }
            return !closed && BlinkAdsRuntime.canRequestAds.value && rewardedAd != null
        }

    fun load() {
        if (closed || activity.isFinishing || activity.isDestroyed || !BlinkAdsRuntime.canRequestAds.value) return
        if (loading || isReady) return
        loading = true
        val request = ++generation
        timeout = Runnable {
            if (request != generation || closed) return@Runnable
            generation++
            loading = false
            lastLoadFailure = "The ad service timed out. Tap Earn coin to retry."
            timeout = null
            BlinkAdAnalytics.failed(activity, "coin_reward", "rewarded", -1)
        }.also { handler.postDelayed(it, 30_000L) }
        requestAd(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (closed || request != generation) return
                    cancelTimeout()
                    loading = false
                    if (!BlinkAdsRuntime.canRequestAds.value) return
                    loadedAt = SystemClock.elapsedRealtime()
                    lastLoadFailure = null
                    rewardedAd = ad
                    BlinkAdAnalytics.loaded(activity, "coin_reward", "rewarded")
                    Log.d(TAG, "Rewarded ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (closed || request != generation) return
                    cancelTimeout()
                    loading = false
                    rewardedAd = null
                    lastLoadFailure = "Ad failed to load (code ${error.code}, domain ${error.domain}). ${error.message}"
                    BlinkAdAnalytics.failed(activity, "coin_reward", "rewarded", error.code)
                    Log.w(TAG, "Rewarded ad failed to load: ${error.code} ${error.domain} ${error.message}; response=${error.responseInfo}")
                }
            }
        )
    }

    fun show(
        userId: String,
        claimId: String,
        onRewardEarned: (Int) -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        if (!BlinkAdsRuntime.canRequestAds.value) {
            onUnavailable("Ads are unavailable until your privacy choices are complete.")
            return
        }

        if (closed || activity.isFinishing || activity.isDestroyed) {
            onUnavailable("Reopen BLINK to watch an ad.")
            return
        }
        val ad = if (isReady) rewardedAd else null
        if (ad == null) {
            load()
            onUnavailable(lastLoadFailure ?: "The rewarded ad is still loading. Try again in a moment.")
            return
        }

        rewardedAd = null

        if (userId.isNotBlank() || claimId.isNotBlank()) {
            val options = ServerSideVerificationOptions.Builder().apply {
                if (userId.isNotBlank()) setUserId(userId)
                if (claimId.isNotBlank()) setCustomData(claimId)
            }.build()
            ad.setServerSideVerificationOptions(options)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load()
            }

            override fun onAdImpression() {
                BlinkAdAnalytics.impression(activity, "coin_reward", "rewarded")
            }

            override fun onAdClicked() {
                BlinkAdAnalytics.clicked(activity, "coin_reward", "rewarded")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                BlinkAdAnalytics.failed(activity, "coin_reward", "rewarded", error.code)
                load()
                onUnavailable("The ad could not be shown. Please try again.")
                Log.w(TAG, "Rewarded ad failed to show: ${error.code} ${error.message}")
            }
        }

        BlinkAdAnalytics.rewardedStarted(activity)
        ad.show(activity) {
            BlinkAdAnalytics.rewardedEarned(activity, BLINK_COIN_REWARD)
            onRewardEarned(BLINK_COIN_REWARD)
        }
    }
}
