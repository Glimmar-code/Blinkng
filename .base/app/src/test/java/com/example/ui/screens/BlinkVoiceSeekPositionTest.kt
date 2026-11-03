package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkVoiceSeekPositionTest {
    @Test fun cannotSeekOutsideMediaDuration() {
        assertEquals(0, blinkVoiceSeekPositionMs(-0.5f, 24_000))
        assertEquals(12_000, blinkVoiceSeekPositionMs(0.5f, 24_000))
        assertEquals(24_000, blinkVoiceSeekPositionMs(1.3f, 24_000))
        assertEquals(0, blinkVoiceSeekPositionMs(0.8f, -5))
        assertEquals(0, blinkVoiceSeekPositionMs(0.8f, 0))
    }
}
