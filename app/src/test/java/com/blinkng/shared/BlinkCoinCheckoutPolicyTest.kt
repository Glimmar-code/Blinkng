package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkCoinCheckoutPolicyTest {
    @Test fun onlyCompleteServerOffersCanBeDisplayed() {
        val offer = BlinkCoinCheckoutPolicy.validOffer("coins_500", 500, 550)
        assertNotNull(offer)
        assertEquals(50, offer!!.bonusCoins)
        assertNull(BlinkCoinCheckoutPolicy.validOffer("fake", 500, 550))
        assertNull(BlinkCoinCheckoutPolicy.validOffer("coins_500", 0, 550))
        assertNull(BlinkCoinCheckoutPolicy.validOffer("coins_500", 500, -1))
        assertNull(BlinkCoinCheckoutPolicy.validOffer("coins_500", 99999999, 1))
    }

    @Test fun checkoutRemainsOffUntilBackendEnablesPayments() {
        val offer = BlinkCoinCheckoutPolicy.validOffer("coins_500", 500, 550)
        assertFalse(BlinkCoinCheckoutPolicy.canCheckout(false, offer))
        assertFalse(BlinkCoinCheckoutPolicy.canCheckout(true, null))
        assertTrue(BlinkCoinCheckoutPolicy.canCheckout(true, offer))
    }

    @Test fun onlyRealPaystackHostedHttpsPagesCanOpen() {
        assertTrue(BlinkCoinCheckoutPolicy.trustedHostedCheckoutUrl("https://checkout.paystack.com/8cfx"))
        for (url in listOf(
            "http://checkout.paystack.com/x",
            "https://checkout.paystack.com.evil.example/x",
            "https://fake.example/paystack",
            "https://evil.example@checkout.paystack.com/x",
            "https://checkout.paystack.com:444/x",
            "javascript:alert(1)",
            "",
        )) {
            assertFalse(url, BlinkCoinCheckoutPolicy.trustedHostedCheckoutUrl(url))
        }
    }
}
