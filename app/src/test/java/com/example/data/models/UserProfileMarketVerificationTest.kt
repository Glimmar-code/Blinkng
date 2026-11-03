package com.example.data.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileMarketVerificationTest {
    private val now = 1_800_000_000_000L

    @Test fun currentBlueTickMayRegister() {
        assertTrue(UserProfile(
            verificationBadge = VerificationBadge.BLUE,
            verificationExpiresAtMillis = now + 86_400_000
        ).hasActiveMarketVerification(now))
    }

    @Test fun expiredBlueTickMustRenew() {
        assertFalse(UserProfile(
            isSellerActive = true,
            verificationBadge = VerificationBadge.BLUE,
            verificationExpiresAtMillis = now - 1
        ).hasActiveMarketVerification(now))
    }

    @Test fun unverifiedAccountCannotSell() {
        assertFalse(UserProfile(verificationBadge = VerificationBadge.NONE).hasActiveMarketVerification(now))
    }

    @Test fun permanentGoldSellerAndExpiredGoldCases() {
        assertTrue(UserProfile(
            verificationBadge = VerificationBadge.GOLD
        ).hasActiveMarketVerification(now))
        assertFalse(UserProfile(
            verificationBadge = VerificationBadge.GOLD,
            verificationExpiresAtMillis = now - 1
        ).hasActiveMarketVerification(now))
    }
}
