package com.example.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostRepositoryLikeFailureTest {
    @Test
    fun countRefreshFailureAfterCommittedLikeIsIgnorable() {
        val error = IllegalStateException("Like count refresh failed. Network timeout")

        assertTrue(isIgnorableLikeCountRefreshFailure(error))
    }

    @Test
    fun actualLikeWriteFailureIsNotIgnorable() {
        val error = IllegalStateException("Like update failed. Permission denied")

        assertFalse(isIgnorableLikeCountRefreshFailure(error))
    }

    @Test
    fun unrelatedFailureIsNotIgnorable() {
        assertFalse(isIgnorableLikeCountRefreshFailure(RuntimeException("offline")))
    }
    @Test
    fun rankedFeedStatementTimeoutCanFallback() {
        assertTrue(isRecoverableRankedFeedTimeout(
            IllegalStateException("canceling statement due to statement timeout")
        ))
        assertTrue(isRecoverableRankedFeedTimeout(
            IllegalStateException("Query failed", IllegalStateException("SQLSTATE 57014"))
        ))
    }

    @Test
    fun rankedFeedFallbackDoesNotMaskOtherErrors() {
        assertFalse(isRecoverableRankedFeedTimeout(IllegalStateException("Permission denied")))
        assertFalse(isRecoverableRankedFeedTimeout(IllegalStateException("Session expired")))
    }
}
