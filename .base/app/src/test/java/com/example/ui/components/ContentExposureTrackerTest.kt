package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExposureTrackerTest {
    @Test
    fun `feed and profile exposure qualifies after a short visible dwell`() {
        assertEquals(1_500L, POST_QUALIFICATION_MS)
    }

    @Test
    fun `continuous visibility records once and leaving permits a genuine repeat`() {
        val gate = ContentVisibilityGate()

        assertTrue(gate.update(true))
        assertFalse(gate.update(true))
        assertFalse(gate.update(true))
        assertFalse(gate.update(false))
        assertTrue(gate.update(true))
    }
}
