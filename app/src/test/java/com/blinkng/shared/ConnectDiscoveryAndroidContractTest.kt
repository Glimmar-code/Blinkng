package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard the exact shared discovery policy shipped inside the Android APK. */
class ConnectDiscoveryAndroidContractTest {
    @Test fun publicSharedInterestsBeatAnUnrelatedOnlineCandidate() {
        val unrelated = ConnectDiscoverySignals(id = "u1", displayName = "Online", online = true)
        val compatible = ConnectDiscoverySignals(id = "u2", displayName = "Student", interests = listOf("Photography", "Coding"))
        val ranked = ConnectDiscoveryPolicy.rank(
            listOf(unrelated, compatible),
            "", "", myInterests = listOf("coding", "photography")
        ) { it }
        assertEquals("u2", ranked.first().id)
        assertTrue(ConnectDiscoveryPolicy.reasons(compatible, "", "", listOf("coding")).contains("Shared interest: Coding"))
    }

    @Test fun dismissedAndDuplicateProfilesNeverAppear() {
        val profiles = listOf(
            ConnectDiscoverySignals("private", "Private"),
            ConnectDiscoverySignals("A", "First"),
            ConnectDiscoverySignals("a", "Duplicate")
        )
        val listed = ConnectDiscoveryPolicy.rank(
            profiles, "", "", excludedIds = setOf("PRIVATE")
        ) { it }
        assertEquals(1, listed.size)
        assertEquals("A", listed.single().id)
        assertFalse(listed.any { it.id == "private" })
    }
}
