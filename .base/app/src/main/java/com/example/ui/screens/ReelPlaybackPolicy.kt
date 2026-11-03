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

/** Scrubbing or jumping to the end must not earn a completed-view signal. */
internal fun qualifiedReelCompletion(watchedMs: Long, durationMs: Long): Boolean =
    durationMs > 0L && watchedMs >= (durationMs * 9L / 10L)

/** Avoid wasting data repeatedly requesting a missing, restricted or unsupported file. */
internal fun shouldAutomaticallyRetryReel(errorCode: String?): Boolean =
    errorCode != null && (
        errorCode.contains("IO_NETWORK_CONNECTION") ||
            errorCode.contains("IO_UNSPECIFIED") ||
            errorCode.contains("TIMEOUT")
        )

/** Actionable messages for offline, removed, private and unsupported media. */
internal fun reelPlaybackErrorMessage(errorCode: String?): String = when {
    errorCode?.contains("IO_BAD_HTTP_STATUS") == true ||
        errorCode?.contains("IO_FILE_NOT_FOUND") == true ->
        "This reel is unavailable or access is restricted."
    errorCode?.contains("DECODING") == true ||
        errorCode?.contains("PARSING") == true ->
        "This video format cannot be played on this device."
    errorCode?.contains("IO") == true || errorCode?.contains("TIMEOUT") == true ->
        "Can't load the reel. Check your connection and retry."
    else -> "Unable to play this reel."
}

/** Preview stays silent, immersive playback can be manually muted. */
internal fun reelShouldBeMuted(expanded: Boolean, manuallyMuted: Boolean): Boolean =
    !expanded || manuallyMuted
