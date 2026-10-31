package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkContestPolicyTest {
    @Test fun sectionsGroupContestLifecycle() {
        assertEquals("Upcoming", BlinkContestPolicy.section("upcoming"))
        assertEquals("Ongoing", BlinkContestPolicy.section("registration"))
        assertEquals("Ongoing", BlinkContestPolicy.section("voting"))
        assertEquals("Completed", BlinkContestPolicy.section("completed"))
    }

    @Test fun statementLengthIsBounded() {
        assertFalse(BlinkContestPolicy.validStatement("short"))
        assertTrue(BlinkContestPolicy.validStatement("My creative contest entry"))
        assertFalse(BlinkContestPolicy.validStatement("x".repeat(501)))
        assertFalse(BlinkContestPolicy.validStatement("  "))
    }

    @Test fun applicationRequiresValidPhotoOrVideoLink() {
        assertFalse(BlinkContestPolicy.canApply("My contest statement", false, ""))
        assertFalse(BlinkContestPolicy.canApply("My contest statement", false, "http://unsafe.example/photo.jpg"))
        assertFalse(BlinkContestPolicy.canApply("My contest statement", false, "https://"))
        assertTrue(BlinkContestPolicy.canApply("My contest statement", false, "https://blink.com.ng/photo.jpg"))
        assertTrue(BlinkContestPolicy.canApply("My contest statement", true, ""))
        assertFalse(BlinkContestPolicy.canApply("short", true, ""))
    }

    @Test fun freeVoteMustBeInVotingPhaseAndUnused() {
        assertTrue(BlinkContestPolicy.canVote("voting", null, "mine", "other"))
        assertFalse(BlinkContestPolicy.canVote("registration", null, "mine", "other"))
        assertFalse(BlinkContestPolicy.canVote("review", null, "mine", "other"))
        assertFalse(BlinkContestPolicy.canVote("voting", "already-voted", "mine", "other"))
        assertFalse(BlinkContestPolicy.canVote("voting", null, "mine", "mine"))
    }
}
