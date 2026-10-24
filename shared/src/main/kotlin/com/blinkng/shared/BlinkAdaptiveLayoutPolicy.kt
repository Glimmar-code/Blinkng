package com.blinkng.shared

/** Shared layout thresholds for narrow screens and enlarged accessibility text. */
object BlinkAdaptiveLayoutPolicy {
    fun marketColumns(availableWidthDp: Int, fontScale: Float): Int =
        if (availableWidthDp < 390 || fontScale >= 1.4f) 1 else 2

    /** Adaptive native phone profile threshold, shared across supported layouts. */
    fun useCompactPhoneLayout(availableWidthDp: Float, fontScale: Float): Boolean =
        availableWidthDp < 380f || fontScale >= 1.3f

    fun compactToolbar(availableWidthDp: Int, fontScale: Float): Boolean =
        availableWidthDp < 390 || fontScale >= 1.35f
}
