package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedNewPostPolicyTest {
    @Test fun newHeadOfFeedIsIdentifiedWithoutReordering() {
        assertEquals(
            setOf("new1", "new2"),
            newlyPrependedFeedIds(setOf("a", "b"), listOf("new1", "new2", "a", "b"))
        )
    }

    @Test fun paginationAndEditedItemsDoNotNotify() {
        assertTrue(newlyPrependedFeedIds(setOf("a", "b"), listOf("a", "b", "old")).isEmpty())
        assertTrue(newlyPrependedFeedIds(setOf("a", "b"), listOf("a", "x", "b")).isEmpty())
    }

    @Test fun coldStartAndEntirelyReplacedFeedDoNotNotify() {
        assertTrue(newlyPrependedFeedIds(emptySet(), listOf("new")).isEmpty())
        assertTrue(newlyPrependedFeedIds(setOf("old"), listOf("different")).isEmpty())
        assertTrue(newlyPrependedFeedIds(setOf("a"), emptyList()).isEmpty())
    }
}
