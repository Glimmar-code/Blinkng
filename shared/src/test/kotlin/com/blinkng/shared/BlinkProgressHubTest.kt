package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkProgressHubTest {
    @Test
    fun weeklyMissionOnlyBecomesClaimableAfterTarget() {
        val inProgress = BlinkWeeklyMission(
            key = "weekly",
            title = "Weekly",
            description = "",
            progress = 3,
            target = 4,
            coinReward = 10,
            xpReward = 100,
            claimed = false,
        )
        assertFalse(inProgress.completed)
        assertFalse(inProgress.claimable)
        assertEquals(0.75f, inProgress.progressFraction)

        val complete = inProgress.copy(progress = 4)
        assertTrue(complete.completed)
        assertTrue(complete.claimable)

        val claimed = complete.copy(claimed = true)
        assertFalse(claimed.claimable)
    }

    @Test
    fun achievementRarityParsingIsStableAndSafe() {
        assertEquals(BlinkAchievementRarity.EPIC, BlinkAchievementRarity.fromWire("epic"))
        assertEquals(BlinkAchievementRarity.LEGENDARY, BlinkAchievementRarity.fromWire("LEGENDARY"))
        assertEquals(BlinkAchievementRarity.COMMON, BlinkAchievementRarity.fromWire("unknown"))
        assertEquals(BlinkAchievementRarity.COMMON, BlinkAchievementRarity.fromWire(null))
    }

    @Test
    fun progressRewardRequiresEligibilityAndUnclaimedState() {
        val reward = BlinkProgressReward(
            key = "level_10",
            type = "level",
            threshold = 10,
            title = "Level 10",
            description = "",
            coinReward = 50,
            xpReward = 100,
            eligible = true,
            claimed = false,
        )
        assertTrue(reward.claimable)
        assertFalse(reward.copy(eligible = false).claimable)
        assertFalse(reward.copy(claimed = true).claimable)
        assertEquals("Level 10", progressRewardLabel("level", 10))
        assertEquals("30-day streak", progressRewardLabel("streak", 30))
    }
}
