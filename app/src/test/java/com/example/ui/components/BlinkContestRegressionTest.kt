package com.example.ui.components

import com.blinkng.shared.BlinkContestPolicy
import org.junit.Assert.*
import org.junit.Test

class BlinkContestRegressionTest {
    @Test fun missingJsonVoteAllowsVotingButExistingAndOwnVotesDoNot() {
        assertTrue(BlinkContestPolicy.canVote("voting", "null", "", "candidate"))
        assertTrue(BlinkContestPolicy.canVote("voting", null, "", "candidate"))
        assertFalse(BlinkContestPolicy.canVote("voting", "used", "", "candidate"))
        assertFalse(BlinkContestPolicy.canVote("voting", null, "candidate", "candidate"))
    }
    @Test fun cannotVoteInClosedContestsOrForMissingCandidates() {
        assertFalse(BlinkContestPolicy.canVote("completed", null, "", "candidate"))
        assertFalse(BlinkContestPolicy.canVote("voting", null, "", "null"))
        assertFalse(BlinkContestPolicy.canVote("voting", null, "", ""))
    }
    @Test fun historyGroupsCompletedAndCancelledContests() {
        assertEquals("Completed", BlinkContestPolicy.section("completed"))
        assertEquals("Completed", BlinkContestPolicy.section("cancelled"))
        assertEquals("Upcoming", BlinkContestPolicy.section("upcoming"))
    }
}
