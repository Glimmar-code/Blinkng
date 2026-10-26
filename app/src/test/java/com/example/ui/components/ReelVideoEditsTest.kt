package com.example.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression: a seek preview cannot masquerade as an exported clip. */
class ReelVideoEditsTest {
    @Test fun trimMustBeInsideDurationAndNonEmpty() {
        assertEquals(1_000L, ReelEditSpec(1_000, 8_000).validate(10_000).startMs)
        assertTrue(runCatching { ReelEditSpec(-1, 1_000).validate(10_000) }.isFailure)
        assertTrue(runCatching { ReelEditSpec(4_900, 5_100).validate(10_000) }.isFailure)
        assertTrue(runCatching { ReelEditSpec(1_000, 11_000).validate(10_000) }.isFailure)
    }

    @Test fun unchangedClipNeedsNoExport() {
        assertFalse(ReelEditSpec(0, 10_000).hasChanges(10_000))
        assertTrue(ReelEditSpec(0, 8_000).hasChanges(10_000))
        assertTrue(ReelEditSpec(1_000, 10_000).hasChanges(10_000))
    }

    @Test fun muteFilterTextStickerAndMusicAreActualExportChanges() {
        assertTrue(ReelEditSpec(0, 10_000, muteOriginalAudio = true).hasChanges(10_000))
        assertTrue(ReelEditSpec(0, 10_000, grayscale = true).hasChanges(10_000))
        assertTrue(ReelEditSpec(0, 10_000, overlayText = "BLINK").hasChanges(10_000))
        assertTrue(ReelEditSpec(0, 10_000, sticker = "✨").hasChanges(10_000))
        assertTrue(ReelEditSpec(0, 10_000, musicUri = "content://audio").hasChanges(10_000))
        assertTrue(runCatching { ReelEditSpec(0, 10_000, rotationDegrees = 31f).validate(10_000) }.isFailure)
    }
}
