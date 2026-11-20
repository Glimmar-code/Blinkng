package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumUploadStateTest {
    @Test
    fun displayValuesAreClampedToValidRanges() {
        val state = AlbumUploadState(total = 4, completed = 9, progress = 1.7f)

        assertEquals(4, state.safeTotal)
        assertEquals(4, state.safeCompleted)
        assertEquals(1f, state.safeProgress, 0.0001f)
    }

    @Test
    fun negativeProgressAndCountsCannotLeakIntoUi() {
        val state = AlbumUploadState(total = -2, completed = -5, progress = -0.4f)

        assertEquals(0, state.safeTotal)
        assertEquals(0, state.safeCompleted)
        assertEquals(0f, state.safeProgress, 0.0001f)
        assertFalse(state.isDone)
    }

    @Test
    fun albumIsDoneOnlyWhenEveryItemCompletedAndNoFailureExists() {
        assertFalse(AlbumUploadState(total = 3, completed = 2, progress = 0.9f).isDone)
        assertTrue(AlbumUploadState(total = 3, completed = 3, progress = 1f).isDone)
        assertFalse(AlbumUploadState(total = 3, completed = 3, progress = 1f, failed = true).isDone)
        assertFalse(AlbumUploadState(total = 0, completed = 0, progress = 1f).isDone)
    }
}
