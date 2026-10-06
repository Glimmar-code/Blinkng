package com.example.ads

import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkRewardedAdManagerTest {
    @Test
    fun networkFailureExplainsConnectionProblem() {
        val message = BlinkRewardedAdManager.loadFailureMessage(2)
        assertTrue(message.contains("network", ignoreCase = true))
    }

    @Test
    fun noFillExplainsTemporaryAvailability() {
        val message = BlinkRewardedAdManager.loadFailureMessage(3)
        assertTrue(message.contains("available", ignoreCase = true))
    }

    @Test
    fun unknownFailureStillHasUsefulFallback() {
        val message = BlinkRewardedAdManager.loadFailureMessage(999)
        assertTrue(message.contains("could not load", ignoreCase = true))
    }
}
