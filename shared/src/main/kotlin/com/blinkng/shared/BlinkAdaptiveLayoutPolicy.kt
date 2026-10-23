package com.blinkng.shared

/** Shared layout thresholds for narrow screens and enlarged accessibility text. */
object BlinkAdaptiveLayoutPolicy {
    fun marketColumns(availableWidthDp: Int, fontScale: Float): Int =
        if (availableWidthDp < 390 || fontScale >= 1.4f) 1 else 2

    fun compactToolbar(availableWidthDp: Int, fontScale: Float): Boolean =
        availableWidthDp < 390 || fontScale >= 1.35f
}
