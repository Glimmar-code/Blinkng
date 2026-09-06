package com.example.data.views

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentViewDisplayStateTest {
    @Test
    fun `active window freezes realtime model count at its baseline`() {
        val state = ContentViewDisplayState(
            revealedCounts = emptyMap(),
            suppressedBaselines = mapOf("post-a" to 10),
            activeWindows = setOf("post-a")
        )

        assertEquals(10, state.displayedCount("post-a", 24))
    }

    @Test
    fun `active window does not reveal an accumulated authoritative count early`() {
        val state = ContentViewDisplayState(
            revealedCounts = mapOf("post-a" to 40),
            suppressedBaselines = mapOf("post-a" to 10),
            activeWindows = setOf("post-a")
        )

        assertEquals(10, state.displayedCount("post-a", 40))
    }

    @Test
    fun `closing the window exposes the newest authoritative count in one update`() {
        val state = ContentViewDisplayState(
            revealedCounts = mapOf("post-a" to 40)
        )

        assertEquals(40, state.displayedCount("post-a", 24))
    }

    @Test
    fun `newer server model remains authoritative after a window closes`() {
        val state = ContentViewDisplayState(
            revealedCounts = mapOf("post-a" to 40)
        )

        assertEquals(55, state.displayedCount("post-a", 55))
    }
}
