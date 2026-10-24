package com.example.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkUniversitySearchTest {
    @Test fun universityDirectoryPreservesEveryOriginalSchoolExactlyOnce() {
        assertEquals(NigerianUniversities.all.toSet(), BlinkUniversitySearch.entries.map { it.name }.toSet())
        assertEquals(BlinkUniversitySearch.entries.size, BlinkUniversitySearch.entries.map { it.name }.distinct().size)
    }

    @Test fun instantSearchSupportsCommonCampusAcronyms() {
        assertEquals(
            "Federal University of Technology, Akure",
            BlinkUniversitySearch.search("FUTA").first().name
        )
        assertEquals(
            "University of Lagos",
            BlinkUniversitySearch.search("UNILAG").first().name
        )
    }

    @Test fun stateFilterExcludesOtherStatesButNeverDeletesUnmappedSchools() {
        val ondo = BlinkUniversitySearch.search("", "Ondo")
        assertTrue(ondo.any { it.name == "Federal University of Technology, Akure" })
        assertFalse(ondo.any { it.name == "University of Lagos" })
        val unclassified = BlinkUniversitySearch.entries.filter { it.state == BlinkUniversitySearch.OTHER }
        assertTrue(unclassified.isNotEmpty())
        assertTrue(unclassified.all { BlinkUniversitySearch.search(it.name).any { school -> school.name == it.name } })
    }

    @Test fun searchIgnoresCaseAndWhitespace() {
        assertTrue(BlinkUniversitySearch.search("  unilorin  ").any { it.name == "University of Ilorin" })
        assertTrue(BlinkUniversitySearch.states.contains(BlinkUniversitySearch.ALL_STATES))
    }
}
