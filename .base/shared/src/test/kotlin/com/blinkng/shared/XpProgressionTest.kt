package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XpProgressionTest {
    @Test
    fun tierBoundariesStayStable() {
        assertEquals("New", xpTierLabel(10))
        assertEquals("Active", xpTierLabel(11))
        assertEquals("Established", xpTierLabel(26))
        assertEquals("Highly Active", xpTierLabel(51))
        assertEquals("Long-term", xpTierLabel(76))
    }

    @Test
    fun thresholdsAreMonotonicThroughLevel100() {
        var previous = -1L
        for (level in 1..100) {
            val current = xpThresholdForLevel(level)
            assertTrue(current > previous)
            previous = current
        }
    }

    @Test
    fun levelIsDerivedOnlyFromXp() {
        assertEquals(1, xpLevelForTotalXp(0))
        assertEquals(10, xpLevelForTotalXp(xpThresholdForLevel(10)))
        assertEquals(25, xpLevelForTotalXp(xpThresholdForLevel(25)))
        assertEquals(50, xpLevelForTotalXp(xpThresholdForLevel(50)))
        assertEquals(75, xpLevelForTotalXp(xpThresholdForLevel(75)))
        assertEquals(100, xpLevelForTotalXp(xpThresholdForLevel(100)))
    }
}
