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
    @Test fun staleContestResponsesCannotOverwriteSelectedContestOrFlyer() {
        val contestA = "11111111-1111-4111-8111-111111111111"
        val contestB = "22222222-2222-4222-8222-222222222222"
        val entryA = "33333333-3333-4333-8333-333333333333"
        val entryB = "44444444-4444-4444-8444-444444444444"
        assertTrue(BlinkContestPolicy.isCurrentContest(contestA, contestA))
        assertFalse(BlinkContestPolicy.isCurrentContest(contestA, contestB))
        assertFalse(BlinkContestPolicy.isCurrentContest("", contestA))
        assertFalse(BlinkContestPolicy.isCurrentContest(contestA, null))
        assertTrue(BlinkContestPolicy.isRequestedCandidate(
            contestA, entryA, contestA, contestA, entryA
        ))
        assertTrue(BlinkContestPolicy.isRequestedCandidate(
            contestA.uppercase(), entryA.uppercase(), contestA, contestA, entryA
        ))
        assertFalse(BlinkContestPolicy.isRequestedCandidate(
            contestA, entryA, contestB, contestA, entryA
        ))
        assertFalse(BlinkContestPolicy.isRequestedCandidate(
            contestA, entryA, contestA, contestB, entryA
        ))
        assertFalse(BlinkContestPolicy.isRequestedCandidate(
            contestA, entryA, contestA, contestA, entryB
        ))
    }
    @Test fun historyGroupsCompletedAndCancelledContests() {
        assertEquals("Completed", BlinkContestPolicy.section("completed"))
        assertEquals("Completed", BlinkContestPolicy.section("cancelled"))
        assertEquals("Upcoming", BlinkContestPolicy.section("upcoming"))
    }
}
