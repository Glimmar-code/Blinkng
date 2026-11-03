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

    private data class Candidate(val id: String, val author: String, val sponsored: Boolean = false)

    @Test
    fun organicDiscoveriesDiversifyAuthorsWithoutLosingAnyOrChangingOlderPosts() {
        val fetched = listOf(
            Candidate("a1", "Alex"), Candidate("a2", "Alex"),
            Candidate("a3", "Alex"), Candidate("b1", "Ben"),
            Candidate("old", "Cora")
        )
        val result = planSmartRefresh(
            previous = listOf(Candidate("old", "Cora")),
            fetched = fetched,
            idOf = { it.id },
            prioritizeNew = true,
            organicAuthorKey = { it.author },
            isPromoted = { it.sponsored }
        )
        assertEquals(listOf("a1", "a2", "b1", "a3", "old"), result.items.map { it.id })
        assertEquals(4, result.newlyDiscoveredCount)
    }

    @Test
    fun paidPlacementsStayPinnedAndFollowingPreservesChronology() {
        val source = listOf(
            Candidate("a1", "Alex"), Candidate("a2", "Alex"),
            Candidate("a3", "Alex"), Candidate("paid", "Advertiser", true),
            Candidate("b1", "Ben")
        )
        val diversify = planSmartRefresh(
            previous = emptyList<Candidate>(),
            fetched = source,
            idOf = { it.id },
            prioritizeNew = true,
            organicAuthorKey = { it.author },
            isPromoted = { it.sponsored }
        )
        assertEquals(source.map { it.id }, diversify.items.map { it.id })
        val following = planSmartRefresh(
            previous = listOf(source[0]),
            fetched = source,
            idOf = { it.id },
            prioritizeNew = false,
            organicAuthorKey = { it.author },
            isPromoted = { it.sponsored }
        )
        assertEquals(source.map { it.id }, following.items.map { it.id })
    }

    @Test
    fun repeatsDoNotShiftAnOrganicPostBeyondAProtectedPromotion() {
        val source = listOf(
            Candidate("a1", "Alex"), Candidate("a2", "Alex"),
            Candidate("a3", "Alex"), Candidate("b1", "Ben"),
            Candidate("paid", "Advertiser", true), Candidate("c1", "Cora")
        )
        val result = diversifyNewOrganicAuthors(source, { it.author }, { it.sponsored })
        assertEquals(listOf("a1", "a2", "b1", "a3", "paid", "c1"), result.map { it.id })
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
