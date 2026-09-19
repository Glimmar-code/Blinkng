package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkDailyMissionTest {
    @Test
    fun missionCompletionAndClaimabilityAreDerivedFromProgress() {
        val mission = BlinkDailyMission(
            key = "creator_move",
            title = "Creator Move",
            description = "Create 1 post or reel today.",
            progress = 1,
            target = 1,
            coinReward = 5,
            xpReward = 20,
            claimed = false,
        )

        assertTrue(mission.completed)
        assertTrue(mission.claimable)
        assertEquals(1f, mission.progressFraction, 0.0001f)
    }

    @Test
    fun claimedMissionCannotBeClaimedAgain() {
        val mission = BlinkDailyMission(
            key = "support_community",
            title = "Support the Community",
            description = "Complete 5 meaningful interactions.",
            progress = 5,
            target = 5,
            coinReward = 5,
            xpReward = 20,
            claimed = true,
        )

        assertTrue(mission.completed)
        assertFalse(mission.claimable)
    }

    @Test
    fun missionProgressIsClampedForPresentation() {
        val mission = BlinkDailyMission(
            key = "discover_blink",
            title = "Discover BLINK",
            description = "Explore 5 posts or reels.",
            progress = 12,
            target = 5,
            coinReward = 5,
            xpReward = 20,
            claimed = false,
        )

        assertEquals(1f, mission.progressFraction, 0.0001f)
    }
}
