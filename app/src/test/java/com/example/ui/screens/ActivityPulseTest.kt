package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityPulseTest {
    @Test
    fun communityActivity_usesThirtyToFiftyPerRealOnlineUser() {
        assertEquals(30..50, communityActivityRange(0))
        assertEquals(30..50, communityActivityRange(1))
        assertEquals(60..100, communityActivityRange(2))
        assertEquals(90..150, communityActivityRange(3))
    }

    @Test
    fun rankPulse_usesTenToFifteenPerRankUpAndKeepsBaseline() {
        assertEquals(10..15, rankPulseRange(0))
        assertEquals(10..15, rankPulseRange(1))
        assertEquals(20..30, rankPulseRange(2))
        assertEquals(30..45, rankPulseRange(3))
    }

    @Test
    fun nextPulseValue_neverLeavesItsBand() {
        assertEquals(30, nextPulseValue(31, 30..50, -4))
        assertEquals(50, nextPulseValue(49, 30..50, 4))
        assertEquals(34, nextPulseValue(32, 30..50, 2))
    }
}
