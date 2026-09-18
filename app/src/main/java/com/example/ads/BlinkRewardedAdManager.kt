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
 * Rewarded ads are never auto-shown. The caller must explicitly request display after a user
 * taps "Watch ad". Debug builds use Google's rewarded test ad unit through BuildConfig.
 */
class BlinkRewardedAdManager(
    private val activity: Activity,
    private val adUnitId: String
) {
    companion object {
        private const val TAG = "BlinkRewardedAds"
        const val BLINK_COIN_REWARD = 10
    }

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    val isReady: Boolean
        get() = BlinkAdsRuntime.canRequestAds.value && rewardedAd != null

    fun load() {
        if (!BlinkAdsRuntime.canRequestAds.value) return
        if (loading || rewardedAd != null) return
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
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    rewardedAd = null
                    BlinkAdAnalytics.failed(activity, "coin_reward", "rewarded", error.code)
                    Log.w(TAG, "Rewarded ad failed to load: ${error.code} ${error.message}")
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

        val ad = rewardedAd
        if (ad == null) {
            load()
            onUnavailable("The rewarded ad is still loading. Try again in a moment.")
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
