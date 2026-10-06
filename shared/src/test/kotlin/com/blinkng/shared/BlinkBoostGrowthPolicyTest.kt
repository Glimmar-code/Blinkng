package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class BlinkBoostGrowthPolicyTest {
    @Test
    fun pricingExamplesStayStable() {
        assertEquals(
            6_930,
            BlinkBoostGrowthDefaults.quoteCoins(
                BlinkBoostQuoteInput(
                    targetType = BlinkBoostTargetType.REEL,
                    boostPower = 50,
                    objective = BlinkBoostObjective.ENGAGEMENT,
                    audienceScope = BlinkBoostAudienceScope.ALL_CAMPUSES,
                    durationDays = 7,
                )
            )
        )
        assertEquals(
            1_134,
            BlinkBoostGrowthDefaults.quoteCoins(
                BlinkBoostQuoteInput(
                    targetType = BlinkBoostTargetType.POST,
                    boostPower = 20,
                    objective = BlinkBoostObjective.REACH,
                    audienceScope = BlinkBoostAudienceScope.MY_UNIVERSITY,
                    durationDays = 7,
                )
            )
        )
    }

    @Test
    fun boostPowerIsClampedAndCampaignHasMinimum() {
        val quote = BlinkBoostGrowthDefaults.quoteCoins(
            BlinkBoostQuoteInput(
                BlinkBoostTargetType.PROFILE,
                boostPower = 0,
                BlinkBoostObjective.REACH,
                BlinkBoostAudienceScope.MY_UNIVERSITY,
                durationDays = 1,
            )
        )
        assertEquals(50, quote)
    }

    @Test
    fun missionRewardsAreOneTimeAndDailyCapped() {
        assertEquals(3, BlinkBoostGrowthDefaults.missionAward(BlinkBoostMissionAction.FOLLOW, 0, false))
        assertEquals(0, BlinkBoostGrowthDefaults.missionAward(BlinkBoostMissionAction.FOLLOW, 0, true))
        assertEquals(1, BlinkBoostGrowthDefaults.missionAward(BlinkBoostMissionAction.COMMENT, 19, false))
        assertEquals(0, BlinkBoostGrowthDefaults.missionAward(BlinkBoostMissionAction.SAVE, 20, false))
    }
}
