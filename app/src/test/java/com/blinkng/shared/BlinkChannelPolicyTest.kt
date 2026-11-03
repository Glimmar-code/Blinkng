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
    @Test fun pollsValidateOptionCountUniquenessAndTrimming() {
        val (question, options) = BlinkChannelPolicy.validatedPoll(
            " Which campus? ", listOf(" FUTA ", "UNILAG"))
        assertEquals("Which campus?", question)
        assertEquals(listOf("FUTA", "UNILAG"), options)
        assertTrue(runCatching {
            BlinkChannelPolicy.validatedPoll("Valid?", listOf("A", "A"))
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            BlinkChannelPolicy.validatedPoll("Valid?", listOf("Only one"))
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun unknownMediaKindsAreRejected() {
        assertEquals("image", BlinkChannelPolicy.validatedMediaKind("image"))
        assertTrue(runCatching { BlinkChannelPolicy.validatedMediaKind("unsupported") }
            .exceptionOrNull() is IllegalArgumentException)
    }

}
