package com.blinkng.shared

/** Shared layout thresholds for narrow screens and enlarged accessibility text. */
object BlinkAdaptiveLayoutPolicy {
    /** Phone layout breakpoint used by compact Auth, Inbox, Profile and Leaderboard.
     * Market retains its independently tuned marketColumns rule.
     */
    fun useCompactPhoneLayout(availableWidthDp: Float, fontScale: Float): Boolean =
        availableWidthDp < 380f || fontScale >= 1.3f

    fun marketColumns(availableWidthDp: Int, fontScale: Float): Int =
        if (availableWidthDp < 390 || fontScale >= 1.4f) 1 else 2

    fun compactToolbar(availableWidthDp: Int, fontScale: Float): Boolean =
        availableWidthDp < 390 || fontScale >= 1.35f
}
