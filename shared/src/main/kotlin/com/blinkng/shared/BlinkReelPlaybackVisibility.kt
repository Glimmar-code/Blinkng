package com.blinkng.shared

/** Whether a reel is allowed to make sound or accumulate watch time. */
object BlinkReelPlaybackVisibility {
    fun mayPlay(reelSelected: Boolean, destinationVisible: Boolean, appForeground: Boolean): Boolean =
        reelSelected && destinationVisible && appForeground
}
