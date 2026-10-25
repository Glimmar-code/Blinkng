package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkUserSuggestionPolicyTest {
    data class Candidate(val username: String, val name: String)

    @Test fun exactHandleBeatsPrefixAndSimilarNames() {
        val candidates = listOf(
            Candidate("abdcampus", "Campus"), Candidate("teacher", "Abd"),
            Candidate("abd", "Person"), Candidate("abda", "Abd")
        )
        val ranked = BlinkUserSuggestionPolicy.page(candidates, "abd",
            username = { it.username }, displayName = { it.name })
        assertEquals(listOf("abd", "abda", "abdcampus", "teacher"),
            ranked.map { it.username })
    }

    @Test fun paginatesWithoutInventingAccounts() {
        val candidates = (0..24).map { Candidate("alex$it", "Alex") }
        val first = BlinkUserSuggestionPolicy.page(candidates, "al", pageSize = 10,
            username = { it.username }, displayName = { it.name })
        val second = BlinkUserSuggestionPolicy.page(candidates, "al", offset = 10, pageSize = 10,
            username = { it.username }, displayName = { it.name })
        assertEquals(10, first.size)
        assertEquals(10, second.size)
        assertEquals(20, (first + second).map { it.username }.distinct().size)
    }
}
