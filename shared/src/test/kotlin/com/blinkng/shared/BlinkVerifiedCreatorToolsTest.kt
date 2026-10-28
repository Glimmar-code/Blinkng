package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkVerifiedCreatorToolsTest {
    @Test fun assistantPromptsAreEditableAndNeverClaimAutomaticPosting() {
        val prompt = BlinkVerifiedCreatorTools.prompt("Channel announcement", "Exam timetable", "FUTA students")
        assertTrue(prompt.contains("Exam timetable"))
        assertTrue(prompt.contains("FUTA students"))
        assertTrue(prompt.contains("Do not invent"))
        assertFalse(prompt.contains("Your post has been published"))
        assertFailsWith<IllegalArgumentException> {
            BlinkVerifiedCreatorTools.prompt("unknown", "topic", "students")
        }
    }

    @Test fun eventStarterIncludesConsentAndSafetyChecks() {
        val prompt = BlinkVerifiedCreatorTools.prompt("Event plan", "departmental seminar", "all students")
        assertTrue(prompt.contains("venue approval"))
        assertTrue(prompt.contains("accessibility"))
        assertTrue(prompt.contains("privacy"))
    }

    @Test fun portfolioContainsOnlyProvidedPublicFields() {
        val text = BlinkVerifiedCreatorTools.portfolio(
            username = "@writer", displayName = "A Writer",
            headline = "Campus media", bio = "Publishing original essays",
            topTopic = "stats"
        )
        assertTrue(text.contains("@writer"))
        assertTrue(text.contains("Campus media"))
        assertTrue(text.contains("stats"))
        assertFalse(text.contains("phone"))
    }
}
