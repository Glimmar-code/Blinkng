package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPaginationPolicyTest {
    @Test fun samePageCannotRefetchAfterLoadingStopsOrAdChanges() {
        val cursor = feedPaginationCursor(listOf("a", "b", "c"))
        assertTrue(shouldRequestFeedPage(cursor, null))
        assertFalse(shouldRequestFeedPage(cursor, cursor))
        assertEquals("3:c", cursor)
    }

    @Test fun newOrganicPageOrExplicitResetAllowsNextRequest() {
        val old = feedPaginationCursor(listOf("a", "b"))
        val extended = feedPaginationCursor(listOf("a", "b", "c"))
        assertTrue(shouldRequestFeedPage(extended, old))
        assertTrue(shouldRequestFeedPage(old, null))
    }

    @Test fun rapidRefreshDoesNotIssueDuplicateRequests() {
        assertTrue(canRequestFeedRefresh(10_000L, 0L))
        assertFalse(canRequestFeedRefresh(10_120L, 10_000L))
        assertFalse(canRequestFeedRefresh(10_799L, 10_000L))
        assertTrue(canRequestFeedRefresh(10_800L, 10_000L))
        assertTrue(canRequestFeedRefresh(10L, 10_000L))
    }

    @Test fun emptyFeedDoesNotProduceAccidentalInvalidCursor() {
        assertEquals("", feedPaginationCursor(emptyList()))
        assertEquals("", feedPaginationCursor(listOf("", "")))
        assertFalse(shouldRequestFeedPage("", ""))
    }
}
