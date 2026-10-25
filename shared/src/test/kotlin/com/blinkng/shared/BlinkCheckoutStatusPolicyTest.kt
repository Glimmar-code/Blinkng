package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkCheckoutStatusPolicyTest {
    @Test
    fun successfulOrdersAreTheOnlyConfirmedPurchases() {
        assertTrue(BlinkCheckoutStatusPolicy.parse("fulfilled").isConfirmed)
        listOf("pending", "processing", "ongoing", "abandoned", "failed", "reversed", "success", "unknown", null)
            .forEach { assertFalse(BlinkCheckoutStatusPolicy.parse(it).isConfirmed) }
    }

    @Test
    fun cancelledAndFailedPaymentsDoNotKeepPrompting() {
        listOf("abandoned", "cancelled", "FAILED", "reversed").forEach {
            assertTrue(BlinkCheckoutStatusPolicy.parse(it).stopsLocalReminder)
        }
        listOf("pending", "processing", "ongoing", "unrecognized").forEach {
            assertFalse(BlinkCheckoutStatusPolicy.parse(it).stopsLocalReminder)
        }
    }

    @Test
    fun unknownAndInFlightStatesCannotBeMistakenForPaid() {
        assertEquals(BlinkCheckoutVerificationState.UNKNOWN, BlinkCheckoutStatusPolicy.parse(null))
        assertEquals(BlinkCheckoutVerificationState.UNKNOWN, BlinkCheckoutStatusPolicy.parse("success"))
        assertTrue(BlinkCheckoutStatusPolicy.parse("pending").maySettleLater)
        assertTrue(BlinkCheckoutStatusPolicy.parse("processing").maySettleLater)
        assertTrue(BlinkCheckoutStatusPolicy.parse("ongoing").maySettleLater)
    }
}
