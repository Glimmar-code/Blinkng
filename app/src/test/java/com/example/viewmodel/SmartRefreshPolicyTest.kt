package com.example.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartRefreshPolicyTest {
    @Test
    fun rankedRefreshShowsUnseenFirstWithoutRepeats() {
        val result = planSmartRefresh(
            previous = listOf("old1", "old2"),
            fetched = listOf("old1", "new1", "new1", "old2", "new2"),
            idOf = { it },
            prioritizeNew = true
        )
        assertEquals(listOf("new1", "new2", "old1", "old2"), result.items)
        assertEquals(2, result.newlyDiscoveredCount)
    }

    @Test
    fun followingRefreshPreservesServerChronology() {
        val result = planSmartRefresh(
            previous = listOf("old"),
            fetched = listOf("old", "new", "old"),
            idOf = { it },
            prioritizeNew = false
        )
        assertEquals(listOf("old", "new"), result.items)
        assertEquals(1, result.newlyDiscoveredCount)
    }

    @Test
    fun repeatedOrEmptyRefreshDoesNotInventPosts() {
        val same = planSmartRefresh(listOf("a", "b"), listOf("a", "b"), { it }, true)
        assertEquals(listOf("a", "b"), same.items)
        assertEquals(0, same.newlyDiscoveredCount)
        val empty = planSmartRefresh(listOf("a"), emptyList<String>(), { it }, true)
        assertEquals(emptyList<String>(), empty.items)
    }
}
