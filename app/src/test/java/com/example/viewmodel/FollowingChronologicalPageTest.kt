package com.example.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowingChronologicalPageTest {
    @Test fun fullPageWithContiguousOlderCacheKeepsOnlyTheKnownSuffix() {
        val cached = listOf("8", "7", "6", "5", "4")
        val fetched = listOf("10", "9", "8", "7", "6")
        assertEquals(
            listOf("10", "9", "8", "7", "6", "5", "4"),
            reconcileFollowingChronologicalPage(cached, fetched, 5) { it }
        )
    }

    @Test fun disjointFirstPageMustNotAppendStaleRowsAcrossMissingPages() {
        assertEquals(
            listOf("10", "9", "8"),
            reconcileFollowingChronologicalPage(listOf("6", "5", "4"), listOf("10", "9", "8"), 3) { it }
        )
    }

    @Test fun shortOrEmptyPageReplacesOlderCache() {
        assertEquals(
            listOf("2", "1"),
            reconcileFollowingChronologicalPage(listOf("5", "4"), listOf("2", "1"), 3) { it }
        )
        assertEquals(
            emptyList<String>(),
            reconcileFollowingChronologicalPage(listOf("5", "4"), emptyList<String>(), 3) { it }
        )
    }

    @Test fun deduplicatesFetchedIdsAndAvoidsDoubleAppend() {
        assertEquals(
            listOf("7", "6", "5", "4"),
            reconcileFollowingChronologicalPage(listOf("6", "5", "4"), listOf("7", "6", "5", "5"), 3) { it }
        )
    }
}
