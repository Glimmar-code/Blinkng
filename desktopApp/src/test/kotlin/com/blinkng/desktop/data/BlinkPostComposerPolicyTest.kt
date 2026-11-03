package com.blinkng.desktop.data

import com.blinkng.shared.BlinkPostComposerPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BlinkPostComposerPolicyTest {
    @Test fun privateAudienceAndUnknownAliasesFailClosed() {
        BlinkPostComposerPolicy.audiences.forEach { BlinkPostComposerPolicy.requireAudience(it) }
        listOf("Only me", "private", "", "EVERYONE").forEach { audience ->
            assertThrows(IllegalArgumentException::class.java) { BlinkPostComposerPolicy.requireAudience(audience) }
        }
    }

    @Test fun pollRejectsDuplicateAnswersUnsupportedDurationsAndInvalidLengths() {
        assertFalse(BlinkPostComposerPolicy.validPoll("Choose?", listOf(" Design 1 ", "design 1")))
        assertFalse(BlinkPostComposerPolicy.validPoll("Choose?", listOf("Yes", " ")))
        assertFalse(BlinkPostComposerPolicy.validPoll("x".repeat(241), listOf("Yes", "No")))
        assertFalse(BlinkPostComposerPolicy.validPoll("Choose?", List(5) { "Answer $it" }))
        assertThrows(IllegalArgumentException::class.java) { BlinkPostComposerPolicy.preparePoll("Choose?", listOf("Yes", "No"), 2) }
        val poll = BlinkPostComposerPolicy.preparePoll(" Choose? ", listOf(" Yes ", "No", ""), 24)
        assertEquals(listOf("Yes", "No"), poll.options)
        assertEquals("Choose?", poll.question)
        // Expiry is anchored to acknowledged publication, including on a retry.
        assertEquals(86_405_000L, poll.expiresAtMillis(5_000L))
    }
}
