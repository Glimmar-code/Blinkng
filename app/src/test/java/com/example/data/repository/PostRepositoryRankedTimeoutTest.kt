package com.example.data.repository

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostRepositoryRankedTimeoutTest {
    @Test
    fun postgresStatementTimeoutIsRecognized() {
        assertTrue(isRecoverableRankedFeedTimeout(
            IllegalStateException("canceling statement due to statement timeout")
        ))
    }

    @Test
    fun nestedPostgrestTimeoutIsRecognized() {
        assertTrue(isRecoverableRankedFeedTimeout(
            IllegalStateException("Request failed", IllegalArgumentException("statement timeout"))
        ))
    }

    @Test
    fun explicitCancellationIsNotConfusedWithTimeout() {
        assertFalse(isRecoverableRankedFeedTimeout(
            CancellationException("operation canceled")
        ))
        assertFalse(isRecoverableRankedFeedTimeout(
            IllegalStateException("SQLSTATE 57014: user requested cancellation")
        ))
    }

    @Test
    fun permissionAndNetworkErrorsCannotTriggerRankedFallback() {
        assertFalse(isRecoverableRankedFeedTimeout(
            IllegalStateException("permission denied for feed_posts")
        ))
        assertFalse(isRecoverableRankedFeedTimeout(
            IllegalStateException("network unreachable")
        ))
    }
}
