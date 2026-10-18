package com.example.shared

import com.blinkng.shared.discovery.BlinkNigerianUniversities
import com.blinkng.shared.discovery.BlinkUniversitySearch
import org.junit.Assert.*
import org.junit.Test

class BlinkUniversityDirectoryTest {
    @Test fun directoryIsUniqueAndSupportsAllOwnershipTypes() {
        val records = BlinkNigerianUniversities.records
        assertTrue("At least several hundred institutions expected", records.size >= 290)
        assertEquals(records.size, records.map { it.id }.toSet().size)
        assertEquals(records.size, records.map { it.name.lowercase() }.toSet().size)
        assertTrue(records.any { it.ownership == BlinkNigerianUniversities.Ownership.FEDERAL })
        assertTrue(records.any { it.ownership == BlinkNigerianUniversities.Ownership.STATE })
        assertTrue(records.any { it.ownership == BlinkNigerianUniversities.Ownership.PRIVATE })
        assertEquals(records.size, BlinkNigerianUniversities.byId.size)
    }

    @Test fun futaAndMajorCampusAcronymsFindCorrectInstitution() {
        assertEquals("Federal University of Technology, Akure", BlinkUniversitySearch.canonical("futa"))
        assertEquals("Federal University of Technology, Akure", BlinkUniversitySearch.canonical("FUT AKURE"))
        assertEquals("Federal University of Technology, Akure", BlinkUniversitySearch.search("FUTA").first())
        assertEquals("University of Lagos", BlinkUniversitySearch.canonical("UNILAG"))
        assertEquals("Obafemi Awolowo University, Ile-Ife", BlinkUniversitySearch.canonical("OAU"))
        assertEquals("University of Ibadan", BlinkUniversitySearch.canonical("UI"))
        assertEquals("Lagos University of Science and Technology", BlinkUniversitySearch.canonical("LASUSTECH"))
        assertEquals("Lagos University of Education", BlinkUniversitySearch.canonical("LASUED"))
        assertEquals("Federal University of Technology, Owerri", BlinkUniversitySearch.canonical("FUTO"))
    }

    @Test fun stableIdsAndLegacyUniversityNamesRemainCompatible() {
        val id = BlinkUniversitySearch.idOf("FUTA")
        assertNotNull(id)
        assertEquals(id, BlinkUniversitySearch.idOf("Federal University of Technology Akure"))
        assertEquals("Federal University of Technology, Akure", BlinkUniversitySearch.record(id)?.name)
        assertTrue(BlinkUniversitySearch.sameCampus("FUTA", "Federal University of Technology, Akure"))
        assertFalse(BlinkUniversitySearch.sameCampus("FUTA", "UNILAG"))
        assertFalse(BlinkUniversitySearch.sameCampus(null, "UNILAG"))
    }

    @Test fun missingInstitutionsAreNeverFabricated() {
        assertTrue(BlinkUniversitySearch.search("Nonsense Fake University XYZ").isEmpty())
        assertNull(BlinkUniversitySearch.record("invented school name"))
        assertEquals("", BlinkUniversitySearch.searchRecords("  ", 0).joinToString())
        assertTrue(BlinkUniversitySearch.search("University of Fortune").isNotEmpty())
        assertTrue(BlinkUniversitySearch.search("Sani Bello University").isNotEmpty())
    }

    @Test fun typoToleranceIsBounded() {
        assertTrue(BlinkUniversitySearch.search("Akurr").contains("Federal University of Technology, Akure"))
        assertTrue(BlinkUniversitySearch.searchRecords("FUTA").size >= 1)
    }
}
