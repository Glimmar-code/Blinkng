package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketQueryPolicyTest {
    @Test fun validatesRangesWithoutSilentlyChangingThem() {
        assertTrue(MarketQueryPolicy.validPriceRange(null, null))
        assertTrue(MarketQueryPolicy.validPriceRange(0, 0))
        assertTrue(MarketQueryPolicy.validPriceRange(100, 500))
        assertFalse(MarketQueryPolicy.validPriceRange(500, 100))
        assertFalse(MarketQueryPolicy.validPriceRange(-1, null))
    }
    @Test fun keepsCategoryContractStable() {
        assertEquals(null, MarketQueryPolicy.category("All Categories"))
        assertEquals(null, MarketQueryPolicy.category("   "))
        assertEquals("Laptops & Computers", MarketQueryPolicy.category(" Laptops & Computers "))
    }
    @Test fun allSortOrdersHaveUniqueIdTieBreaker() {
        listOf("newest", "popular", "price_low", "price_high").forEach {
            assertTrue(MarketQueryPolicy.order(it).endsWith("id.desc"))
        }
    }
    @Test fun pageOffsetUsesFetchedRowsNotVisibleRows() {
        assertEquals(80, MarketQueryPolicy.nextOffset(40, 40))
        assertEquals(40, MarketQueryPolicy.nextOffset(40, 0))
        assertEquals(Int.MAX_VALUE, MarketQueryPolicy.nextOffset(Int.MAX_VALUE, 40))
    }
}
