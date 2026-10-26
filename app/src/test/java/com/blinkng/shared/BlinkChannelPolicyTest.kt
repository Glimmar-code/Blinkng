package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkChannelPolicyTest {
    @Test fun trimsUpdates() {
        assertEquals("Hello followers", BlinkChannelPolicy.validatedUpdate(" Hello followers "))
    }

    @Test fun rejectsEmptyAndOversizedUpdates() {
        assertTrue(runCatching { BlinkChannelPolicy.validatedUpdate(" ") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { BlinkChannelPolicy.validatedUpdate("x".repeat(5001)) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun limitsReactionsToKnownEmojis() {
        assertEquals("❤️", BlinkChannelPolicy.validatedReaction("❤️"))
        assertTrue(runCatching { BlinkChannelPolicy.validatedReaction("spam") }.exceptionOrNull() is IllegalArgumentException)
    }
}
