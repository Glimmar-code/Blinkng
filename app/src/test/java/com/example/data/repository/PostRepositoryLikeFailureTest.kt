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
}
