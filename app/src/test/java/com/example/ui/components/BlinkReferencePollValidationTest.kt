package com.example.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkReferencePollValidationTest {
    @Test fun validPollRequiresTwoDistinctAnswersAndAllowedDuration() {
        assertTrue(isBlinkPollReady("Which FUTA event?", listOf("Tech meetup", "Sports day"), 72))
        assertTrue(isBlinkPollReady("Choose one", listOf("A", "B", "C", "D"), 24))
        assertFalse(isBlinkPollReady("", listOf("A", "B"), 24))
        assertFalse(isBlinkPollReady("Choose one", listOf("A", ""), 24))
        assertFalse(isBlinkPollReady("Choose one", listOf("Tech meetup", "tech meetup "), 24))
        assertFalse(isBlinkPollReady("Choose one", listOf("One"), 24))
        assertFalse(isBlinkPollReady("Choose one", listOf("A", "B"), 48))
        assertFalse(isBlinkPollReady("Choose one", listOf("A", "B", "C", "D", "E"), 24))
    }
}
