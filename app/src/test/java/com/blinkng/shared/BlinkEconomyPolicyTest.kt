package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkEconomyPolicyTest {
    private val policy = BlinkEconomyDefaults.policy

    @Test
    fun rewardedMilestonesMatchLaunchEconomy() {
        assertEquals(0, policy.totalRewardForAds(0))
        assertEquals(10, policy.totalRewardForAds(1))
        assertEquals(60, policy.totalRewardForAds(5))
        assertEquals(130, policy.totalRewardForAds(10))
        assertEquals(210, policy.totalRewardForAds(15))
        assertEquals(210, policy.totalRewardForAds(99))
    }

    @Test
    fun milestoneAdsPayOnlyTheIncrementNeededForTargetTotal() {
        assertEquals(10, policy.rewardForCompletedAd(1))
        assertEquals(20, policy.rewardForCompletedAd(5))
        assertEquals(30, policy.rewardForCompletedAd(10))
        assertEquals(40, policy.rewardForCompletedAd(15))
        assertEquals(0, policy.rewardForCompletedAd(16))
    }

    @Test
    fun verificationProgressUsesThreeThousandCoinGoal() {
        assertEquals(3_000L, policy.verificationCoinsRemaining(0))
        assertEquals(860L, policy.verificationCoinsRemaining(2_140))
        assertEquals(0L, policy.verificationCoinsRemaining(3_100))
        assertEquals(2_140f / 3_000f, policy.verificationProgress(2_140), 0.0001f)
    }

    @Test
    fun dailyLimitStopsAdditionalRewardedAds() {
        assertTrue(policy.canWatchRewardedAd(14))
        assertFalse(policy.canWatchRewardedAd(15))
    }
}
