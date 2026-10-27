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

    @Test fun timedTextAndStickersUseCorrectVisibilityWindow() {
        val edit = ReelEditSpec(
            0L, 10_000L, overlayText = "Campus day",
            overlayStartMs = 2_000L, overlayEndMs = 5_000L
        ).validate(10_000L)
        assertFalse(edit.overlayVisibleAt(1_999L))
        assertTrue(edit.overlayVisibleAt(2_000L))
        assertTrue(edit.overlayVisibleAt(5_000L))
        assertFalse(edit.overlayVisibleAt(5_001L))
        assertTrue(runCatching {
            ReelEditSpec(0L, 10_000L, overlayText = "No",
                overlayStartMs = 6_000L, overlayEndMs = 5_000L).validate(10_000L)
        }.isFailure)
        assertFalse(ReelEditSpec(0L, 10_000L).overlayVisibleAt(1_000L))
    }

    @Test fun fullDurationOverlayRemainsBackwardsCompatible() {
        val edit = ReelEditSpec(0L, 4_000L, sticker = "★").validate(4_000L)
        assertTrue(edit.overlayVisibleAt(0L))
        assertTrue(edit.overlayVisibleAt(4_000L))
    }
}
