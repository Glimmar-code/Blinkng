package com.example.ads

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Small, privacy-conscious ad telemetry helper.
 *
 * It intentionally avoids user IDs, ad-unit IDs and advertiser content. Only placement,
 * format and coarse result information are recorded.
 */
object BlinkAdAnalytics {
    fun loaded(context: Context, placement: String, format: String) =
        log(context, "blink_ad_loaded", placement, format)

    fun impression(context: Context, placement: String, format: String) =
        log(context, "blink_ad_impression", placement, format)

    fun clicked(context: Context, placement: String, format: String) =
        log(context, "blink_ad_click", placement, format)

    fun failed(context: Context, placement: String, format: String, errorCode: Int) =
        log(context, "blink_ad_failed", placement, format, errorCode)

    fun rewardedStarted(context: Context) =
        log(context, "blink_rewarded_started", "coin_reward", "rewarded")

    fun rewardedEarned(context: Context, amount: Int) {
        runCatching {
            FirebaseAnalytics.getInstance(context).logEvent(
                "blink_rewarded_earned",
                Bundle().apply {
                    putString("placement", "coin_reward")
                    putString("ad_format", "rewarded")
                    putLong("reward_amount", amount.toLong())
                }
            )
        }
    }

    private fun log(
        context: Context,
        event: String,
        placement: String,
        format: String,
        errorCode: Int? = null
    ) {
        runCatching {
            FirebaseAnalytics.getInstance(context).logEvent(
                event,
                Bundle().apply {
                    putString("placement", placement)
                    putString("ad_format", format)
                    errorCode?.let { putLong("error_code", it.toLong()) }
                }
            )
        }
    }
}
