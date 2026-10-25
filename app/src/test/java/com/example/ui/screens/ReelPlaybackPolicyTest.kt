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

    @Test fun seekingClampsTouchPositionsAndRejectsInvalidDimensions() {
        assertEquals(0f, reelSeekFraction(-25f, 100), 0f)
        assertEquals(0.5f, reelSeekFraction(50f, 100), 0f)
        assertEquals(1f, reelSeekFraction(200f, 100), 0f)
        assertEquals(0f, reelSeekFraction(Float.NaN, 100), 0f)
        assertEquals(0f, reelSeekFraction(50f, 0), 0f)
    }
}
