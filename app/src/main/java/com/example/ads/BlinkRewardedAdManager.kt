package com.example.ads

import android.app.Activity
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
 * Rewarded ads are never auto-shown without a user tap. If the user taps while a preload is
 * still running, the manager keeps that single intent and opens the ad as soon as loading
 * succeeds instead of requiring a second tap.
 */
class BlinkRewardedAdManager(
    private val activity: Activity,
    private val adUnitId: String
) {
    companion object {
        private const val TAG = "BlinkRewardedAds"
        const val BLINK_COIN_REWARD = 10

        internal fun loadFailureMessage(errorCode: Int): String = when (errorCode) {
            2 -> "The rewarded ad could not load because of a network problem. Check your connection and try again."
            3 -> "No rewarded ad is available right now. Try again in a little while."
            1 -> "The rewarded ad request was rejected. Please try again after reopening BLINK."
            else -> "The rewarded ad could not load. Please try again."
        }
    }

    private var rewardedAd: RewardedAd? = null
    private var loading = false
    private var pendingReady: (() -> Unit)? = null
    private var pendingUnavailable: ((String) -> Unit)? = null

    val isReady: Boolean
        get() = BlinkAdsRuntime.canRequestAds.value && rewardedAd != null

    val isLoading: Boolean
        get() = loading

    /**
     * Preloads a rewarded ad.
     *
     * Supplying [onReady] turns a preload into a user-requested wait: if a load is already
     * running, the callback is attached to that load and fires once rather than starting a
     * second request.
     */
    fun load(
        onReady: (() -> Unit)? = null,
        onUnavailable: ((String) -> Unit)? = null
    ) {
        if (!BlinkAdsRuntime.canRequestAds.value) {
            onUnavailable?.invoke("Ads are unavailable until your privacy choices are complete.")
            return
        }

        if (rewardedAd != null) {
            onReady?.invoke()
            return
        }

        if (onReady != null) {
            pendingReady = onReady
            pendingUnavailable = onUnavailable
        }

        if (loading) return

        loading = true
        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    rewardedAd = ad
                    BlinkAdAnalytics.loaded(activity, "coin_reward", "rewarded")
                    Log.d(TAG, "Rewarded ad loaded")

                    val callback = pendingReady
                    pendingReady = null
                    pendingUnavailable = null
                    callback?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    rewardedAd = null
                    BlinkAdAnalytics.failed(activity, "coin_reward", "rewarded", error.code)
                    Log.w(TAG, "Rewarded ad failed to load: ${error.code} ${error.message}")

                    val unavailable = pendingUnavailable
                    pendingReady = null
                    pendingUnavailable = null
                    unavailable?.invoke(loadFailureMessage(error.code))
                }
            }
        )
    }

    fun show(
        userId: String,
        claimId: String,
        onRewardEarned: (Int) -> Unit,
        onUnavailable: (String) -> Unit,
        onClosed: () -> Unit = {}
    ) {
        if (!BlinkAdsRuntime.canRequestAds.value) {
            onUnavailable("Ads are unavailable until your privacy choices are complete.")
            onClosed()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            load(
                onReady = {
                    show(
                        userId = userId,
                        claimId = claimId,
                        onRewardEarned = onRewardEarned,
                        onUnavailable = onUnavailable,
                        onClosed = onClosed
                    )
                },
                onUnavailable = { message ->
                    onUnavailable(message)
                    onClosed()
                }
            )
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
                onClosed()
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
                onClosed()
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
