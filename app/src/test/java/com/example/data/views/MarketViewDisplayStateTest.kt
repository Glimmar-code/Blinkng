package com.example.data.views

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketViewDisplayStateTest {
    @Test
    fun `Marketplace view counter uses the exact two minute display delay`() {
        assertEquals(120_000L, MARKET_VIEW_REFLECTION_DELAY_MS)
    }

    @Test
    fun `Market listing count remains at baseline during the display window`() {
        val state = MarketViewDisplayState(
            revealedCounts = mapOf("listing-a" to 10),
            suppressedBaselines = mapOf("listing-a" to 10),
            activeWindows = setOf("listing-a"),
        )
        assertEquals(10, state.displayedCount("listing-a", 25))
    }

    @Test
    fun `Market listing reveals the accumulated authoritative count after the window`() {
        val state = MarketViewDisplayState(
            revealedCounts = mapOf("listing-a" to 25),
        )
        assertEquals(25, state.displayedCount("listing-a", 10))
    }

    @Test
    fun `newer server model count is never hidden after reveal`() {
        val state = MarketViewDisplayState(revealedCounts = mapOf("listing-a" to 25))
        assertEquals(31, state.displayedCount("listing-a", 31))
    }
}
