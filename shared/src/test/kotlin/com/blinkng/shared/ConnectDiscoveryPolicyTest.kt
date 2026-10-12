package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectDiscoveryPolicyTest {
    private fun user(id: String, name: String = id, campus: String = "", dept: String = "", online: Boolean = false) =
        ConnectDiscoverySignals(id = id, displayName = name, university = campus, department = dept, online = online)

    @Test fun departmentAndCampusOutrankGenericOnline() {
        val ranked = ConnectDiscoveryPolicy.rank(listOf(user("online", online = true), user("match", campus = "FUTA", dept = "Statistics")), "FUTA", "Statistics") { it }
        assertEquals("match", ranked.first().id)
        assertEquals(listOf("Same department", "Same campus"), ConnectDiscoveryPolicy.reasons(ranked.first(), "FUTA", "Statistics"))
    }

    @Test fun unknownFieldsAreNotMatches() {
        assertEquals(0, ConnectDiscoveryPolicy.score(user("a", campus = "null", dept = "null"), "null", ""))
        assertTrue(ConnectDiscoveryPolicy.reasons(user("a"), "", "").isEmpty())
    }

    @Test fun blankAndDuplicateIdsCannotDominate() {
        val ranked = ConnectDiscoveryPolicy.rank(listOf(user("A"), user("a", "Duplicate"), user(""), user("b")), "", "") { it }
        assertEquals(listOf("A", "b"), ranked.map { it.id })
    }

    @Test fun tiesHaveStableAlphabeticalOrder() {
        val ranked = ConnectDiscoveryPolicy.rank(listOf(user("z", "Zoe"), user("a", "Amara"), user("b", "Ben")), "", "") { it }
        assertEquals(listOf("a", "b", "z"), ranked.map { it.id })
    }
}
