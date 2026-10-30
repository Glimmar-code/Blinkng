package com.example.ui.components

import com.example.data.models.PostPoll
import com.example.data.models.PollOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BlinkAdvancedComposerPolicyTest {
    @Test fun reelClipPolicyAcceptsShortMontageAndCapsClipCount() {
        BlinkReelClipPolicy.validate(2, 30_000L)
        BlinkReelClipPolicy.validate(4, 180_000L)
        assertThrows(IllegalArgumentException::class.java) {
            BlinkReelClipPolicy.validate(5, 35_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlinkReelClipPolicy.validate(2, 180_001L)
        }
    }

    @Test fun expiredPollVotingIsDisabledButLegacyPollsStayOpen() {
        assertEquals(false, pollVotingClosed(null, 2_000L))
        assertEquals(false, pollVotingClosed(2_500L, 2_499L))
        assertEquals(true, pollVotingClosed(2_500L, 2_500L))
        assertEquals(true, pollVotingClosed(2_500L, 3_000L))
    }

    @Test fun pollExpiryValueIsCarriedWithOptions() {
        val poll = PostPoll(
            question = "Which topic?",
            options = listOf(PollOption("1", "Maths"), PollOption("2", "Physics")),
            expiresAtMillis = 1_900_000_000_000L
        )
        assertEquals(1_900_000_000_000L, poll.expiresAtMillis)
        assertEquals(false, poll.allowsMultiple)
    }
}
