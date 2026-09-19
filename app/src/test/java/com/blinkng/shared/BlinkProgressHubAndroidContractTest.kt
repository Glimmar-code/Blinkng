package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkProgressHubAndroidContractTest {
    @Test
    fun weeklyMissionClaimabilityFollowsProgressAndClaimState() {
        val mission = BlinkWeeklyMission(
            key = "creator_consistency",
            title = "Creator Consistency",
            description = "",
            progress = 2,
            target = 3,
            coinReward = 15,
            xpReward = 100,
            claimed = false,
        )
        assertFalse(mission.claimable)
        assertEquals(2f / 3f, mission.progressFraction, 0.0001f)

        val complete = mission.copy(progress = 3)
        assertTrue(complete.claimable)
        assertFalse(complete.copy(claimed = true).claimable)
    }

    @Test
    fun achievementAndRewardContractsStayDeterministic() {
        assertEquals(BlinkAchievementRarity.EPIC, BlinkAchievementRarity.fromWire("epic"))
        assertEquals(BlinkAchievementRarity.COMMON, BlinkAchievementRarity.fromWire("unexpected"))

        val reward = BlinkProgressReward(
            key = "streak_30_reward",
            type = "streak",
            threshold = 30,
            title = "30-Day Streak",
            description = "",
            coinReward = 100,
            xpReward = 200,
            eligible = true,
            claimed = false,
        )
        assertTrue(reward.claimable)
        assertEquals("30-day streak", progressRewardLabel(reward.type, reward.threshold))
    }
}
