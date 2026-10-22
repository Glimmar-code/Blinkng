package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkReelPlaybackVisibilityTest {
    @Test fun onlyForegroundSelectedVisibleReelMayPlay() {
        assertTrue(BlinkReelPlaybackVisibility.mayPlay(true, true, true))
        assertFalse(BlinkReelPlaybackVisibility.mayPlay(false, true, true))
        assertFalse(BlinkReelPlaybackVisibility.mayPlay(true, false, true))
        assertFalse(BlinkReelPlaybackVisibility.mayPlay(true, true, false))
    }
}
