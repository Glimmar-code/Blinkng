package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectDiscoveryPolicyTest {
    private fun student(id: String, campus: String = "", dept: String = "", online: Boolean = false, interests: List<String> = emptyList()) =
        ConnectDiscoverySignals(id = id, displayName = id, university = campus, department = dept, online = online, interests = interests)

    @Test fun strongCampusAndDepartmentMatchIsPrioritized() {
        val ranked = ConnectDiscoveryPolicy.rank(listOf(student("online", online = true), student("campus", campus = "FUTA", dept = "Statistics")), "FUTA", "Statistics") { it }
        assertEquals("campus", ranked.first().id)
        assertEquals(listOf("Same department", "Same campus"), ConnectDiscoveryPolicy.reasons(ranked.first(), "FUTA", "Statistics"))
    }

    @Test fun sharedInterestsMatterAndAreExplanatory() {
        val candidate = student("interest", interests = listOf("Coding", "reading", "coding"))
        val expected = ConnectDiscoveryPolicy.score(candidate, "", "", listOf("CODING", "Reading"))
        assertEquals(28, expected)
        assertEquals(listOf("Shared interest: Coding"), ConnectDiscoveryPolicy.reasons(candidate, "", "", listOf("coding")))
    }

    @Test fun unknownFieldsDoNotScoreAndNoSensitiveTraitsAreUsed() {
        assertEquals(0, ConnectDiscoveryPolicy.score(student("x", campus = "null", dept = "null"), "null", ""))
        assertTrue(ConnectDiscoveryPolicy.reasons(student("x"), "", "").isEmpty())
    }

    @Test fun duplicateBlankAndHiddenCandidatesCannotAppear() {
        val ranked = ConnectDiscoveryPolicy.rank(
            listOf(student("A"), student("a"), student(""), student("b"), student("C")),
            "", "", excludedIds = setOf("c")
        ) { it }
        assertEquals(listOf("A", "b"), ranked.map { it.id })
    }

    @Test fun tiesAreDeterministicRegardlessOfInputOrder() {
        val people = listOf(student("z"), student("a"), student("b"))
        val first = ConnectDiscoveryPolicy.rank(people, "", "") { it }.map { it.id }
        val second = ConnectDiscoveryPolicy.rank(people.reversed(), "", "") { it }.map { it.id }
        assertEquals(listOf("a", "b", "z"), first)
        assertEquals(first, second)
    }
}
