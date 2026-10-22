package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkAdaptiveLayoutPolicyTest {
    @Test fun marketUsesOneColumnWhenCardsWouldBeClipped() {
        assertEquals(2, BlinkAdaptiveLayoutPolicy.marketColumns(412, 1f))
        assertEquals(1, BlinkAdaptiveLayoutPolicy.marketColumns(360, 1f))
        assertEquals(1, BlinkAdaptiveLayoutPolicy.marketColumns(412, 1.5f))
        assertEquals(1, BlinkAdaptiveLayoutPolicy.marketColumns(360, 2f))
    }
    @Test fun compactToolbarsHandleLargeFontOrNarrowViewport() {
        assertFalse(BlinkAdaptiveLayoutPolicy.compactToolbar(412, 1f))
        assertTrue(BlinkAdaptiveLayoutPolicy.compactToolbar(360, 1f))
        assertTrue(BlinkAdaptiveLayoutPolicy.compactToolbar(412, 1.5f))
    }
}
