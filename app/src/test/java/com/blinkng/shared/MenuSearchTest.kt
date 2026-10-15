package com.blinkng.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuSearchTest {
    @Test fun blankQueryShowsAllOptions() {
        assertTrue(MenuSearch.matches("  ", "Settings & privacy"))
    }

    @Test fun labelsAndDescriptionsAreSearchable() {
        assertTrue(MenuSearch.matches("notIF", "Calls & notification sounds"))
        assertTrue(MenuSearch.matches("  cache  ", "Data & storage", "Cache and account data"))
        assertTrue(MenuSearch.matches("seller", "Seller hub"))
        assertFalse(MenuSearch.matches("orders", "Account security", "Private messaging"))
    }
}
