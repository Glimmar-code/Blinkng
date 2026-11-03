package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkSelectedClipDurationTest {
    @Test fun formatsOnlyRealPositiveDurations() {
        assertEquals("0:28", blinkSelectedClipDurationLabel(28_987L))
        assertEquals("1:05", blinkSelectedClipDurationLabel(65_000L))
        assertEquals("12:03", blinkSelectedClipDurationLabel(723_000L))
        assertNull(blinkSelectedClipDurationLabel(null))
        assertNull(blinkSelectedClipDurationLabel(0))
        assertNull(blinkSelectedClipDurationLabel(-1000))
    }
}
