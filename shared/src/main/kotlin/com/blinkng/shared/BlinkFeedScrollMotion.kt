package com.blinkng.shared

/** Feed movement is a ratio, not physical mph: posts 80 : header 20 : nav 50. */
object BlinkFeedScrollMotion {
    const val TOP_PER_POST = 0.25f
    const val NAV_PER_POST = 0.625f

    /** Positive scrollUpPx means the user scrolls deeper into posts. */
    fun advance(previous: Float, scrollUpPx: Float, ratio: Float, maxTravelPx: Float): Float =
        (previous + scrollUpPx * ratio).coerceIn(0f, maxTravelPx.coerceAtLeast(0f))
}
