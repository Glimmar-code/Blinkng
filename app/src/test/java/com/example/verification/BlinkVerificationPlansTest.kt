package com.example.verification

import com.blinkng.shared.verification.BlinkVerificationPlans
import com.blinkng.shared.verification.BlinkVerificationTier
import org.junit.Assert.*
import org.junit.Test

class BlinkVerificationPlansTest {
    @Test fun allPricesAndSavingsAreCorrect() {
        val blue = listOf(800, 2160, 3840, 6720)
        val gold = listOf(2000, 5400, 9600, 16800)
        BlinkVerificationPlans.durations.forEachIndexed { i, duration ->
            assertEquals(blue[i], BlinkVerificationPlans.quote(BlinkVerificationTier.BLUE, duration.months).totalNgn)
            assertEquals(gold[i], BlinkVerificationPlans.quote(BlinkVerificationTier.GOLD, duration.months).totalNgn)
        }
        assertEquals(2880, BlinkVerificationPlans.quote(BlinkVerificationTier.BLUE, 12).savingsNgn)
        assertEquals(7200, BlinkVerificationPlans.quote(BlinkVerificationTier.GOLD, 12).savingsNgn)
        assertEquals(0, BlinkVerificationPlans.quote(BlinkVerificationTier.FREE, 12).totalNgn)
    }

    @Test fun noSurpriseRecurringCharges() {
        assertFalse(BlinkVerificationPlans.authorizedAutoRenew(false, true, true))
        assertFalse(BlinkVerificationPlans.authorizedAutoRenew(true, false, true))
        assertFalse(BlinkVerificationPlans.authorizedAutoRenew(true, true, false))
        assertTrue(BlinkVerificationPlans.authorizedAutoRenew(true, true, true))
        assertFalse(BlinkVerificationPlans.isCheckoutReady(false, true))
        assertFalse(BlinkVerificationPlans.isCheckoutReady(true, false))
    }

    @Test fun onlyContractDurationsAreSupported() {
        try {
            BlinkVerificationPlans.quote(BlinkVerificationTier.GOLD, 24)
            fail("Expected duration rejection")
        } catch (_: IllegalArgumentException) { }
    }
}
