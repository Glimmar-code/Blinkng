package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BlinkChannelPolicyTest {
    @Test fun trimsUpdates() {
        assertEquals("Hello followers", BlinkChannelPolicy.validatedUpdate(" Hello followers "))
    }

    @Test fun rejectsEmptyAndOversizedUpdates() {
        assertThrows(IllegalArgumentException::class.java) { BlinkChannelPolicy.validatedUpdate(" ") }
        assertThrows(IllegalArgumentException::class.java) {
            BlinkChannelPolicy.validatedUpdate("x".repeat(5001))
        }
    }

    @Test fun limitsReactionsToKnownEmojis() {
        assertEquals("❤️", BlinkChannelPolicy.validatedReaction("❤️"))
        assertThrows(IllegalArgumentException::class.java) { BlinkChannelPolicy.validatedReaction("spam") }
    }
}
