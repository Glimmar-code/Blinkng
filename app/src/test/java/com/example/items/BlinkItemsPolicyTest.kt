package com.example.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkItemsPolicyTest {
    @Test
    fun milestoneDoesNotRepeat() {
        assertEquals(
            1_000,
            BlinkItemsPolicy.nextReachedMilestone(
                steps = 1_250,
                lastNotifiedMilestone = 0,
                goal = 10_000
            )
        )
        assertNull(
            BlinkItemsPolicy.nextReachedMilestone(
                steps = 1_250,
                lastNotifiedMilestone = 1_000,
                goal = 10_000
            )
        )
    }

    @Test
    fun crossingSeveralMilestonesEmitsHighestReachedOnce() {
        assertEquals(
            5_000,
            BlinkItemsPolicy.nextReachedMilestone(
                steps = 5_420,
                lastNotifiedMilestone = 1_000,
                goal = 10_000
            )
        )
    }

    @Test
    fun customGoalBecomesMilestone() {
        assertEquals(
            8_000,
            BlinkItemsPolicy.nextReachedMilestone(
                steps = 8_010,
                lastNotifiedMilestone = 7_500,
                goal = 8_000
            )
        )
    }

    @Test
    fun officialWeatherSeveritySortsAboveMinorAlerts() {
        val severe = BlinkItemsPolicy.weatherPriority("severe", "expected")
        val minor = BlinkItemsPolicy.weatherPriority("minor", "immediate")
        assert(severe > minor)
    }
}
