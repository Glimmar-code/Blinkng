package com.blinkng.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopContentViewTrackerTest {
    @Test fun `desktop waits for a short visible dwell before counting`() {
        assertEquals(1_500L, DESKTOP_CONTENT_EXPOSURE_DWELL_MS)
        val gate = DesktopVisibilityGate()
        assertTrue(gate.update(true))
        assertFalse(gate.update(true))
        assertFalse(gate.update(false))
        assertTrue(gate.update(true))
    }

    @Test fun `recording does not wait for visible count reveal`() {
        assertEquals(120_000L, DESKTOP_VIEW_REFLECTION_DELAY_MS)
        val visible = DesktopContentViewState(
            baselines = mapOf("post" to 10),
            active = setOf("post"),
            revealed = mapOf("post" to 40),
        )
        assertEquals(10,visible.displayed("post",42))
        assertEquals(42,visible.copy(active=emptySet()).displayed("post",42))
    }
    @Test fun `scrolling away and back counts a new exposure`() {
        val gate = DesktopVisibilityGate()
        assertTrue(gate.update(true))
        assertFalse(gate.update(true))
        assertFalse(gate.update(false))
        assertTrue(gate.update(true))
    }
}
