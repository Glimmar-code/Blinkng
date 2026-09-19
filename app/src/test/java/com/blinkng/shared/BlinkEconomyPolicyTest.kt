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
    @Test
    fun coinPacksAndVerificationPricingMatchLaunchEconomy() {
        assertEquals(800, policy.blueVerificationCashNgn)
        assertEquals(3_000, policy.blueVerificationCoinCost)
        assertEquals(30, policy.blueVerificationValidDays)
        assertEquals(
            listOf(
                Triple("coins_100", 100, 100),
                Triple("coins_500", 500, 550),
                Triple("coins_1000", 1_000, 1_200),
                Triple("coins_2000", 2_000, 2_600),
                Triple("coins_5000", 5_000, 7_000),
            ),
            policy.coinPacks.map { Triple(it.id, it.priceNgn, it.coins) },
        )
        assertFalse(policy.cashCheckoutEnabled)
    }

}
