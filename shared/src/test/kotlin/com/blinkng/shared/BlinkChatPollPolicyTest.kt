package com.blinkng.shared

import org.junit.Assert.*
import org.junit.Test

class BlinkChatPollPolicyTest {
    @Test fun trims_a_valid_single_choice_poll_without_reordering_options() {
        val draft = BlinkChatPollPolicy.prepare(
            "  When should we meet?  ",
            listOf("  Morning ", "Afternoon  ", "Evening")
        )
        assertEquals("When should we meet?", draft.question)
        assertEquals(listOf("Morning", "Afternoon", "Evening"), draft.options)
    }

    @Test fun rejects_blank_duplicate_and_out_of_range_inputs() {
        val invalid = listOf(
            "" to listOf("Yes", "No"),
            "Question" to listOf("Yes"),
            "Question" to (1..9).map { "Option $it" },
            "Question" to listOf("One", " one "),
            "Question" to listOf("Yes", " "),
            "Question" to listOf("Yes", "x".repeat(81)),
            "x".repeat(201) to listOf("Yes", "No")
        )
        invalid.forEach { (question, options) ->
            assertTrue(
                "Expected invalid poll: $question",
                runCatching { BlinkChatPollPolicy.prepare(question, options) }.isFailure
            )
        }
    }

    @Test fun fallback_is_consistent_and_requires_validated_input() {
        val poll = BlinkChatPollPolicy.prepare(" Meet? ", listOf(" Today ", " Tomorrow "))
        assertEquals(
            "📊 Poll question: Meet?\n1. Today\n2. Tomorrow\n(Reply with your choice number)",
            BlinkChatPollPolicy.textFallback(poll)
        )
        assertTrue(runCatching {
            BlinkChatPollPolicy.textFallback(BlinkChatPollDraft("Q", listOf("Yes", "yes")))
        }.isFailure)
    }

    @Test fun handles_min_and_max_option_bounds() {
        assertEquals(8, BlinkChatPollPolicy.prepare("Q", (1..8).map(Int::toString)).options.size)
        assertEquals(2, BlinkChatPollPolicy.prepare("Q", listOf("Y", "N")).options.size)
    }

    @Test fun indices_are_zero_based_and_respect_actual_option_count() {
        assertTrue(BlinkChatPollPolicy.validChoice(0, 2))
        assertTrue(BlinkChatPollPolicy.validChoice(7, 8))
        assertFalse(BlinkChatPollPolicy.validChoice(2, 2))
        assertFalse(BlinkChatPollPolicy.validChoice(-1, 2))
        assertFalse(BlinkChatPollPolicy.validChoice(0, 1))
        assertFalse(BlinkChatPollPolicy.validChoice(0, 9))
    }

    @Test fun stopped_or_departed_participants_cannot_vote() {
        assertTrue(BlinkChatPollPolicy.canVote(true, false, false))
        assertFalse(BlinkChatPollPolicy.canVote(false, false, false))
        assertFalse(BlinkChatPollPolicy.canVote(true, true, false))
        assertFalse(BlinkChatPollPolicy.canVote(true, false, true))
    }
}
