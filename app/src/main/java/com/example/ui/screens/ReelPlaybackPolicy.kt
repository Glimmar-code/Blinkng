package com.example.ui.screens

/** Playback is never allowed behind the app or under an interaction overlay. */
internal fun shouldPlayReel(
    selected: Boolean,
    foreground: Boolean,
    pausedByUser: Boolean,
    overlayOpen: Boolean
): Boolean = selected && foreground && !pausedByUser && !overlayOpen

/** Fast-forward never sticks after release, pause, navigation or backgrounding. */
internal fun reelSpeedForHold(held: Boolean, playbackAllowed: Boolean): Float =
    if (held && playbackAllowed) 2f else 1f

/** Convert horizontal seek-bar touches and drags into safe percentages. */
internal fun reelSeekFraction(touchX: Float, widthPx: Int): Float {
    if (!touchX.isFinite() || widthPx <= 0) return 0f
    return (touchX / widthPx.toFloat()).coerceIn(0f, 1f)
}
