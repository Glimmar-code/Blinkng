package com.example.shared

import com.example.data.models.NigerianUniversitySearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NigerianUniversitySearchTest {
    @Test fun aliasesResolveCanonicalInstitutions() {
        assertEquals("Federal University of Technology, Akure", NigerianUniversitySearch.canonical("FUTA"))
        assertEquals("University of Lagos", NigerianUniversitySearch.canonical("unilag"))
        assertTrue(NigerianUniversitySearch.search("FUTA").contains("Federal University of Technology, Akure"))
        assertTrue(NigerianUniversitySearch.search("technology akure").contains("Federal University of Technology, Akure"))
    }

    @Test fun searchNeverInventsUniversities() {
        assertTrue(NigerianUniversitySearch.search("this does not exist").isEmpty())
        assertEquals(null, NigerianUniversitySearch.canonical("this does not exist"))
    }
}
