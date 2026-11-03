package com.example.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentVisibilityGateTest {
    @Test
    fun `qualifying exposure triggers once and leaving allows another`() {
        val gate = ContentVisibilityGate()

        assertTrue(gate.update(true))
        assertFalse(gate.update(true))
        assertFalse(gate.update(false))
        assertTrue(gate.update(true))
        assertFalse(gate.update(true))
    }

    @Test
    fun `fresh gate represents recycled content displayed again`() {
        val firstVisit = ContentVisibilityGate()
        assertTrue(firstVisit.update(true))
        assertFalse(firstVisit.update(true))

        // LazyColumn can dispose a distant row and restore it later.
        // Each new composition begins with a fresh gate rather than saveable 'true'.
        val revisited = ContentVisibilityGate()
        assertTrue(revisited.update(true))
    }
}
