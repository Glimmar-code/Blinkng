package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlinkVerifiedIdentitySignatureTest {
    @Test fun onlyActiveSubscriptionsShowIncludedSignature() {
        assertEquals("BLUE · BLINK MEMBER", BlinkVerifiedIdentitySignature.label("blue", true))
        assertEquals("GOLD · CREATOR SIGNATURE", BlinkVerifiedIdentitySignature.label("GOLD", true))
        assertNull(BlinkVerifiedIdentitySignature.label("GOLD", false))
        assertNull(BlinkVerifiedIdentitySignature.label("NONE", true))
        assertNull(BlinkVerifiedIdentitySignature.label(null, true))
    }
}
