package com.blinkng.shared.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verify the current Testlab 3.2s continuous-B timeline, not the superseded
 * 6.4s animation that the original PR branch was based on.
 */
class BlinkIntroTimelineTest {
    @Test
    fun timelineUsesCurrentApprovedThreePointTwoSecondDuration() {
        assertEquals(3_200, BLINK_INTRO_DURATION_MS)
        assertEquals(0f, introFraction(200f, 230, 560), 0.001f)
        assertEquals(0.5f, introFraction(395f, 230, 560), 0.001f)
        assertEquals(1f, introFraction(570f, 230, 560), 0.001f)
        assertEquals(0f, introFraction(-100f, 230, 560), 0.001f)
    }

    @Test
    fun glyphCollapseKeepsCurrentOrderedWindows() {
        assertEquals(
            listOf(1_080 to 1_340, 1_190 to 1_480, 1_300 to 1_610,
                   1_480 to 1_810, 1_620 to 1_950),
            introLetterExitTimings
        )
        assertTrue(introLetterExitTimings.all { (start, end) -> start < end })
        assertTrue(introLetterExitTimings.zipWithNext().all { (left, right) -> left.first < right.first })
        assertEquals(0f, introFraction(1_080f, 1_080, 1_340), 0.001f)
        assertEquals(1f, introFraction(1_340f, 1_080, 1_340), 0.001f)
    }
}
