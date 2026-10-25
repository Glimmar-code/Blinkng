package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminNavigationGroupTest {
    @Test fun knownSectionsAreGroupedByTask() {
        assertEquals("Overview", adminNavigationGroup("dashboard"))
        assertEquals("People & access", adminNavigationGroup("users"))
        assertEquals("People & access", adminNavigationGroup("verification"))
        assertEquals("Content", adminNavigationGroup("reels"))
        assertEquals("Safety", adminNavigationGroup("reports"))
        assertEquals("Messaging", adminNavigationGroup("announcements"))
        assertEquals("Coins & commerce", adminNavigationGroup("coins_balances"))
        assertEquals("Campus & connect", adminNavigationGroup("study_circles"))
        assertEquals("System & insights", adminNavigationGroup("developer_ops"))
    }

    @Test fun unknownServerSectionRemainsReachable() {
        assertEquals("System & insights", adminNavigationGroup("new_admin_vertical"))
    }
}
