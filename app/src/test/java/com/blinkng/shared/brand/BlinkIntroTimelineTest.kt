package com.blinkng.shared.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkIntroTimelineTest {
    @Test
    fun timelineMatchesApprovedSixPointFourSecondAnimation() {
        assertEquals(6400, BLINK_INTRO_DURATION_MS)
        assertEquals(0f, introFraction(200f, 230, 560), 0.001f)
        assertEquals(0.5f, introFraction(395f, 230, 560), 0.001f)
        assertEquals(1f, introFraction(570f, 230, 560), 0.001f)
        assertEquals(0f, introFraction(-100f, 230, 560), 0.001f)
    }

    @Test
    fun intermediateGlyphCollapseKeepsApprovedIntervals() {
        assertEquals(
            listOf(1950 to 2240, 2080 to 2340, 2190 to 2450, 2460 to 2920, 2640 to 3160),
            introLetterExitTimings
        )
        assertTrue(introLetterExitTimings.all { (from, until) -> from < until })
        assertEquals(0f, introFraction(1950f, 1950, 2240), 0.001f)
        assertEquals(1f, introFraction(2240f, 1950, 2240), 0.001f)
    }
}
