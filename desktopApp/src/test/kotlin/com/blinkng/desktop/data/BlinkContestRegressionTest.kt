package com.blinkng.desktop.data

import com.blinkng.shared.BlinkContestPolicy
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BlinkContestRegressionTest {
    @Test fun jsonNullVoteDoesNotDisableAnEligibleVoter() {
        val response = JSONObject().put("my_vote_entry_id", JSONObject.NULL)
        assertTrue(BlinkContestPolicy.canVote("voting", response.optString("my_vote_entry_id"), "", "candidate"))
        assertTrue(BlinkContestPolicy.canVote("voting", null, "my-entry", "another-entry"))
        assertFalse(BlinkContestPolicy.canVote("voting", "used-vote", "", "candidate"))
    }
    @Test fun selfVotingClosedVotingAndMissingCandidatesRemainDisabled() {
        assertFalse(BlinkContestPolicy.canVote("voting", null, "candidate", "candidate"))
        assertFalse(BlinkContestPolicy.canVote("completed", null, "", "candidate"))
        assertFalse(BlinkContestPolicy.canVote("voting", null, "", ""))
        assertFalse(BlinkContestPolicy.canVote("voting", null, "", "null"))
    }
    @Test fun mediaLimitsMatchTheContestStorageContract() {
        com.blinkng.shared.BlinkContestMediaPolicy.validate("image/jpeg", 8_388_608)
        com.blinkng.shared.BlinkContestMediaPolicy.validate("video/mp4", 26_214_400)
        assertThrows(IllegalArgumentException::class.java) { com.blinkng.shared.BlinkContestMediaPolicy.validate("image/jpeg", 8_388_609) }
        assertThrows(IllegalArgumentException::class.java) { com.blinkng.shared.BlinkContestMediaPolicy.validate("video/mp4", 0) }
        assertThrows(IllegalArgumentException::class.java) { com.blinkng.shared.BlinkContestMediaPolicy.validate("application/pdf", 10) }
    }
    @Test fun completedHistoryIncludesCancelledContests() {
        assertEquals("Completed", BlinkContestPolicy.section("completed"))
        assertEquals("Completed", BlinkContestPolicy.section("cancelled"))
        assertEquals("Upcoming", BlinkContestPolicy.section("upcoming"))
        assertEquals("Ongoing", BlinkContestPolicy.section("registration"))
    }
}
