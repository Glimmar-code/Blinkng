package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelPlaybackPolicyTest {
    @Test fun foregroundVisibleUnpausedReelIsOnlyActivePlayer() {
        assertTrue(shouldPlayReel(true, true, false, false))
        assertFalse(shouldPlayReel(false, true, false, false))
        assertFalse(shouldPlayReel(true, false, false, false))
        assertFalse(shouldPlayReel(true, true, true, false))
        assertFalse(shouldPlayReel(true, true, false, true))
    }

    @Test fun fastForwardResetsOnReleasePauseAndBackground() {
        assertEquals(2f, reelSpeedForHold(true, true), 0f)
        assertEquals(1f, reelSpeedForHold(false, true), 0f)
        assertEquals(1f, reelSpeedForHold(true, false), 0f)
    }

    @Test fun upwardSwipeExpandsOnlyAfterIntentionalMovement() {
        assertFalse(shouldExpandReelPreview(0f, 12f, 65f))
        assertTrue(shouldExpandReelPreview(0.17f, 12f, 65f))
        assertTrue(shouldExpandReelPreview(0f, 65f, 65f))
        assertFalse(shouldExpandReelPreview(Float.NaN, Float.NaN, 65f))
        assertFalse(shouldExpandReelPreview(0f, 90f, 0f))
    }

    @Test fun downwardPullAtFirstReelReversesOnlyAfterThreshold() {
        assertTrue(shouldKeepReelFullscreen(1f))
        assertTrue(shouldKeepReelFullscreen(0.85f))
        assertFalse(shouldKeepReelFullscreen(0.83f))
        assertFalse(shouldKeepReelFullscreen(0f))
        assertTrue(shouldKeepReelFullscreen(Float.NaN))
    }

    @Test fun reelCompletionRequiresActualWatchedTimeNotJustPosition() {
        assertFalse(qualifiedReelCompletion(0L, 10_000L))
        assertFalse(qualifiedReelCompletion(100L, 10_000L)) // Scrubbed to 99% is not a watch.
        assertFalse(qualifiedReelCompletion(8_999L, 10_000L))
        assertTrue(qualifiedReelCompletion(9_000L, 10_000L))
        assertTrue(qualifiedReelCompletion(15_000L, 10_000L))
        assertFalse(qualifiedReelCompletion(9_000L, 0L))
    }

    @Test fun failedVideoDistinguishesNetworkFromMissingMedia() {
        assertTrue(shouldAutomaticallyRetryReel("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
        assertTrue(shouldAutomaticallyRetryReel("ERROR_CODE_IO_UNSPECIFIED"))
        assertFalse(shouldAutomaticallyRetryReel("ERROR_CODE_IO_BAD_HTTP_STATUS"))
        assertFalse(shouldAutomaticallyRetryReel("ERROR_CODE_DECODING_FAILED"))
        assertFalse(shouldAutomaticallyRetryReel(null))
        assertTrue(reelPlaybackErrorMessage("ERROR_CODE_IO_BAD_HTTP_STATUS").contains("unavailable"))
        assertTrue(reelPlaybackErrorMessage("ERROR_CODE_DECODING_FAILED").contains("format"))
        assertTrue(reelPlaybackErrorMessage("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED").contains("connection"))
    }

    @Test fun seekingClampsTouchPositionsAndRejectsInvalidDimensions() {
        assertEquals(0f, reelSeekFraction(-25f, 100), 0f)
        assertEquals(0.5f, reelSeekFraction(50f, 100), 0f)
        assertEquals(1f, reelSeekFraction(200f, 100), 0f)
        assertEquals(0f, reelSeekFraction(Float.NaN, 100), 0f)
        assertEquals(0f, reelSeekFraction(50f, 0), 0f)
    }
}

class ReelPreviewAudioPolicyTest {
    @org.junit.Test fun previewMutedFullscreenAudibleUnlessManuallyMuted() {
        org.junit.Assert.assertTrue(reelShouldBeMuted(expanded = false, manuallyMuted = false))
        org.junit.Assert.assertTrue(reelShouldBeMuted(expanded = false, manuallyMuted = true))
        org.junit.Assert.assertFalse(reelShouldBeMuted(expanded = true, manuallyMuted = false))
        org.junit.Assert.assertTrue(reelShouldBeMuted(expanded = true, manuallyMuted = true))
    }
}
