package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkPostGrowthTipsTest {
    @Test fun marketplaceIsNotAnOrdinaryFeedCategory() {
        assertFalse("Marketplace" in BlinkPostGrowthTips.feedCategories)
        assertTrue(BlinkPostGrowthTips.disallowedFeedCategory("marketplace"))
        assertFalse(BlinkPostGrowthTips.disallowedFeedCategory("Academic"))
    }

    @Test fun contextTipsChangeWithoutPromisingVirality() {
        assertTrue(BlinkPostGrowthTips.suggestion(false, true, false).contains("opening seconds"))
        assertTrue(BlinkPostGrowthTips.suggestion(true, false, false).contains("photo"))
        assertTrue(BlinkPostGrowthTips.suggestion(false, false, true).contains("question"))
        assertTrue(BlinkPostGrowthTips.suggestion(false, false, false).contains("discussion"))
        assertEquals(5, BlinkPostGrowthTips.feedCategories.size)
    }
}
