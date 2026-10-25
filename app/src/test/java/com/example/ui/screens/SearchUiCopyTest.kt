package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiCopyTest {
    @Test fun emptyResultsDoNotClaimMorePages() {
        val label = searchResultsSummary(0, null, false)
        assertEquals("0 results · all categories", label)
        assertFalse(label.contains("more"))
    }

    @Test fun singleResultUsesCorrectGrammar() {
        assertEquals("1 result · Posts", searchResultsSummary(1, "Posts", false))
    }

    @Test fun moreIsShownOnlyWhenPaginationExists() {
        val label = searchResultsSummary(20, "Reels", true)
        assertTrue(label.endsWith("scroll for more"))
        assertEquals("20 results · Reels · scroll for more", label)
    }

    @Test fun malformedCountAndCategoryAreSafe() {
        assertEquals("0 results · all categories", searchResultsSummary(-5, "  ", false))
    }
}
