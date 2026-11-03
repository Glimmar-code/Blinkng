package com.example.notification

import com.blinkng.shared.notification.BlinkLoginAnnouncementPolicy as P
import org.junit.Assert.*
import org.junit.Test

class BlinkLoginAnnouncementPolicyTest {
    @Test fun onlyOfficialUnreadCampaignsCanBecomePopups() {
        assertNull(P.fromCampaignActivity("1", "post", "Hello", "", true))
        assertNull(P.fromCampaignActivity("1", "system", "Blink • hello", "", true))
        assertNull(P.fromCampaignActivity("1", "notification", "Hello", "", false))
        assertNotNull(P.fromCampaignActivity("1", "notification", "Important", "Body", true))
    }

    @Test fun neverShowTwoOrInterruptBusyApp() {
        val first = P.fromCampaignActivity("n1", "notification", "Update", null, true)!!
        assertNull(P.nextUnseen(listOf(first), emptySet(), false, false))
        assertNull(P.nextUnseen(listOf(first), emptySet(), true, true))
        assertNull(P.nextUnseen(listOf(first), setOf("n1"), true, false))
        assertEquals("n1", P.nextUnseen(listOf(first), emptySet(), true, false)?.id)
    }

    @Test fun boundedDeduplication() {
        var ids = emptyList<String>()
        repeat(120) { ids = P.remember(ids, "id-$it") }
        ids = P.remember(ids, "id-119")
        assertEquals(100, ids.size)
        assertEquals("id-119", ids.first())
        assertEquals(ids.size, ids.distinct().size)
    }
}
