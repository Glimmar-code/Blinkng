package com.example.data.views

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentViewDisplayStateTest {
    @Test
    fun `pending exposure suppresses a realtime jump until reveal`() {
        val state = ContentViewDisplayState(
            revealedCounts = emptyMap(),
            suppressedBaselines = mapOf("post-a" to 10),
            pendingByContent = mapOf("post-a" to 1)
        )
        assertEquals(10, state.displayedCount("post-a", 11))
    }

    @Test
    fun `revealed authoritative count can advance while another exposure is pending`() {
        val state = ContentViewDisplayState(
            revealedCounts = mapOf("post-a" to 11),
            suppressedBaselines = mapOf("post-a" to 10),
            pendingByContent = mapOf("post-a" to 1)
        )
        assertEquals(11, state.displayedCount("post-a", 12))
    }

    @Test
    fun `model count is authoritative once no local reveal is pending`() {
        val state = ContentViewDisplayState(revealedCounts = mapOf("post-a" to 11))
        assertEquals(25, state.displayedCount("post-a", 25))
    }
}
