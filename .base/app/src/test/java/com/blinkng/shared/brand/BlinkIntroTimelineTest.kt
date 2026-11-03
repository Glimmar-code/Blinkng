package com.blinkng.shared.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Align the production baseline test with the explicitly shortened intro. */
class BlinkIntroTimelineTest {
    @Test
    fun introductionHasNoArtificialBlankLeadIn() {
        assertEquals(3_200, BLINK_INTRO_DURATION_MS)
        // A time fraction must clamp correctly for all timestamps; the artwork
        // itself is visible on the first rendered Compose frame.
        assertEquals(0f, introFraction(-100f, 0, 400), 0.001f)
        assertEquals(0f, introFraction(0f, 0, 400), 0.001f)
        assertEquals(0.5f, introFraction(200f, 0, 400), 0.001f)
        assertEquals(1f, introFraction(600f, 0, 400), 0.001f)
    }

    @Test
    fun eachBeyondLetterCollapsesInOrderBeforeCompletion() {
        val expected = listOf(
            1_080 to 1_340,
            1_190 to 1_480,
            1_300 to 1_610,
            1_480 to 1_810,
            1_620 to 1_950,
        )
        assertEquals(expected, introLetterExitTimings)
        assertEquals(5, introLetterExitTimings.size)
        assertTrue(introLetterExitTimings.zipWithNext().all { (a, b) -> a.first < b.first })
        assertTrue(introLetterExitTimings.all { (from, until) ->
            from >= 0 && from < until && until < BLINK_INTRO_DURATION_MS
        })
        assertEquals(0f, introFraction(1_080f, 1_080, 1_340), 0.001f)
        assertEquals(1f, introFraction(1_340f, 1_080, 1_340), 0.001f)
    }
}
