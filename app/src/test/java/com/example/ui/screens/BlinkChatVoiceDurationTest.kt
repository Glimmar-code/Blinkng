package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkChatVoiceDurationTest {
    @Test fun durationUsesClockFormatForReferenceVoicePlayer() {
        assertEquals("0:00", formatBlinkVoiceDuration(0))
        assertEquals("0:24", formatBlinkVoiceDuration(24_000))
        assertEquals("1:01", formatBlinkVoiceDuration(61_000))
        assertEquals("10:05", formatBlinkVoiceDuration(605_000))
    }

    @Test fun invalidOrShortPositionNeverDisplaysNegativeTime() {
        assertEquals("0:00", formatBlinkVoiceDuration(-1000))
        assertEquals("0:00", formatBlinkVoiceDuration(999))
    }
}
