package com.example.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun yesterdayStepSnapshotNeverShowsAsToday() {
        assertEquals(0L, BlinkItemsPolicy.currentDaySteps("2026-10-13", "2026-10-14", 7_200L))
        assertEquals(7_200L, BlinkItemsPolicy.currentDaySteps("2026-10-14", "2026-10-14", 7_200L))
        assertEquals(0L, BlinkItemsPolicy.currentDaySteps("2026-10-14", "2026-10-14", -1L))
    }

    @Test
    fun milestonesAboveCustomGoalDoNotClaimGoalRepeatedly() {
        assertTrue(BlinkItemsPolicy.isGoalMilestone(3_000, 3_000))
        assertTrue(!BlinkItemsPolicy.isGoalMilestone(5_000, 3_000))
        assertEquals("Great progress", BlinkItemsPolicy.stepMessage(5_000, 3_000).first)
    }

    @Test
    fun stepProgressIsBoundedAndWorksOffline() {
        assertEquals(0, BlinkItemsPolicy.stepProgressPercent(0L, 10_000))
        assertEquals(57, BlinkItemsPolicy.stepProgressPercent(5_700L, 10_000))
        assertEquals(100, BlinkItemsPolicy.stepProgressPercent(12_000L, 10_000))
        assertEquals(0, BlinkItemsPolicy.stepProgressPercent(-500L, 10_000))
        assertEquals(100, BlinkItemsPolicy.stepProgressPercent(Long.MAX_VALUE, 1_000))
    }

    @Test
    fun expiredWeatherAlertsAreNotActive() {
        assertTrue(!BlinkItemsPolicy.isActiveWeatherAlert(1_000L, 1_001L))
        assertTrue(BlinkItemsPolicy.isActiveWeatherAlert(2_000L, 1_001L))
        assertTrue(BlinkItemsPolicy.isActiveWeatherAlert(0L, 1_001L))
    }

    @Test
    fun officialWeatherSeveritySortsAboveMinorAlerts() {
        val severe = BlinkItemsPolicy.weatherPriority("severe", "expected")
        val minor = BlinkItemsPolicy.weatherPriority("minor", "immediate")
        assertTrue(severe > minor)
    }
}
