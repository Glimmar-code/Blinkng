package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.ads.BlinkAdAnalytics
import com.example.ads.BlinkAdsRuntime
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

enum class BlinkNativeAdPlacement {
    FEED,
    REEL
}

/**
 * Google Mobile Ads native placement used for Blink's explicitly-labelled Sponsored content.
 *
 * The SDK owns clicks/impressions through NativeAdView. Blink never wraps the ad in its own
 * clickable surface, and the SDK-provided AdChoices overlay remains enabled.
 */
@Composable
fun BlinkSponsoredNativeAd(
    adUnitId: String,
    placement: BlinkNativeAdPlacement,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canRequestAds by BlinkAdsRuntime.canRequestAds.collectAsState()
    var nativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }
    var loadFailed by remember(adUnitId) { mutableStateOf(false) }

    DisposableEffect(adUnitId, canRequestAds) {
        if (!canRequestAds) {
            nativeAd?.destroy()
            nativeAd = null
            loadFailed = false
            return@DisposableEffect onDispose {}
        }

        val placementName = if (placement == BlinkNativeAdPlacement.FEED) "feed" else "reels"
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { loaded ->
                nativeAd?.destroy()
                nativeAd = loaded
                loadFailed = false
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdLoaded() {
                        BlinkAdAnalytics.loaded(context, placementName, "native")
                    }

                    override fun onAdImpression() {
                        BlinkAdAnalytics.impression(context, placementName, "native")
                    }

                    override fun onAdClicked() {
                        BlinkAdAnalytics.clicked(context, placementName, "native")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadFailed = true
                        BlinkAdAnalytics.failed(context, placementName, "native", error.code)
                    }
                }
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd
    when {
        !canRequestAds -> Box(modifier = modifier)

        ad != null -> {
            AndroidView(
                factory = { createBlinkNativeAdView(it, placement) },
                update = { bindBlinkNativeAd(it, ad) },
                modifier = modifier
                    .then(
                        if (placement == BlinkNativeAdPlacement.FEED) {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp)
                        } else {
                            Modifier.fillMaxSize()
                        }
                    )
            )
        }

        placement == BlinkNativeAdPlacement.REEL -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        loadFailed -> {
            // A no-fill or network failure should not leave a blank card in the feed.
            Box(modifier = modifier)
        }

        else -> Box(modifier = modifier)
    }
}

private fun createBlinkNativeAdView(
    context: android.content.Context,
    placement: BlinkNativeAdPlacement
): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val adView = NativeAdView(context)
    adView.setBackgroundColor(
        if (placement == BlinkNativeAdPlacement.REEL) AndroidColor.BLACK else AndroidColor.rgb(20, 20, 24)
    )

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(14))
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    val attribution = TextView(context).apply {
        text = "Sponsored"
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.rgb(98, 70, 234))
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    val advertiser = TextView(context).apply {
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(AndroidColor.WHITE)
        maxLines = 1
    }

    val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            attribution,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            advertiser,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            }
        )
    }

    val headline = TextView(context).apply {
        textSize = if (placement == BlinkNativeAdPlacement.REEL) 20f else 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(AndroidColor.WHITE)
        setPadding(0, dp(10), 0, dp(6))
        maxLines = 2
    }

    val media = MediaView(context).apply {
        setBackgroundColor(AndroidColor.BLACK)
    }

    val body = TextView(context).apply {
        textSize = 13f
        setTextColor(AndroidColor.LTGRAY)
        setPadding(0, dp(8), 0, dp(10))
        maxLines = if (placement == BlinkNativeAdPlacement.REEL) 3 else 2
    }

    val cta = Button(context).apply {
        isAllCaps = false
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.rgb(98, 70, 234))
        minHeight = dp(44)
    }

    root.addView(
        topRow,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )
    root.addView(
        headline,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    if (placement == BlinkNativeAdPlacement.REEL) {
        root.addView(
            media,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(8)
            }
        )
    } else {
        root.addView(
            media,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(210)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        )
    }

    root.addView(
        body,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )
    root.addView(
        cta,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    // Register all advertiser-controlled assets with NativeAdView so the Google SDK owns
    // click handling and impression measurement. The explicit "Sponsored" label is Blink UI.
    adView.headlineView = headline
    adView.bodyView = body
    adView.callToActionView = cta
    adView.advertiserView = advertiser
    adView.mediaView = media
    adView.addView(root)

    return adView
}

private fun bindBlinkNativeAd(
    adView: NativeAdView,
    nativeAd: NativeAd
) {
    (adView.headlineView as? TextView)?.text = nativeAd.headline

    (adView.advertiserView as? TextView)?.apply {
        val value = nativeAd.advertiser
        text = value.orEmpty()
        visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    (adView.bodyView as? TextView)?.apply {
        val value = nativeAd.body
        text = value.orEmpty()
        visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    (adView.callToActionView as? Button)?.apply {
        val value = nativeAd.callToAction
        text = value.orEmpty()
        visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    adView.mediaView?.mediaContent = nativeAd.mediaContent
    adView.setNativeAd(nativeAd)
}
