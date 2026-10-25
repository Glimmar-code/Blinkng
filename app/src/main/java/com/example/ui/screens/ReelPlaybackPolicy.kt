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

/** Decide the gesture release without tying navigation to frame timing or hardware refresh rate. */
internal fun shouldExpandReelPreview(
    visibleFraction: Float,
    upwardTravelPx: Float,
    swipeThresholdPx: Float
): Boolean =
    (visibleFraction.isFinite() && visibleFraction >= 0.16f) ||
        (upwardTravelPx.isFinite() && swipeThresholdPx > 0f && upwardTravelPx >= swipeThresholdPx)

/** A small first-page overscroll returns to fullscreen; a deliberate pull collapses. */
internal fun shouldKeepReelFullscreen(visibleFraction: Float): Boolean =
    !visibleFraction.isFinite() || visibleFraction >= 0.84f
