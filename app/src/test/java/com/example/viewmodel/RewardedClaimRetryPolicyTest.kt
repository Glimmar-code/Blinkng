package com.example.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardedClaimRetryPolicyTest {
    @Test
    fun waitsForSignedServerSideAdVerification() {
        assertTrue(isPendingRewardClaimError("REWARDED_AD_VERIFICATION_PENDING"))
    }

    @Test
    fun retriesVeryShortRewardedTestAdsRatherThanLosingCoins() {
        assertTrue(isPendingRewardClaimError("The ad reward is not ready yet."))
        assertTrue(isPendingRewardClaimError("REWARDED_AD_TOO_SOON"))
    }

    @Test
    fun doesNotRetryInvalidClaimsOrHardErrorsAsPendingVerification() {
        assertFalse(isPendingRewardClaimError("That ad reward expired. Please watch another ad."))
        assertFalse(isPendingRewardClaimError("You've reached today's 15-ad rewarded limit."))
    }
}
