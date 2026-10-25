package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkMenuDestinationTest {
    @Test fun approvedReferenceOrderIsStable() {
        assertEquals(
            listOf(
                "My Profile", "Refer & Grow", "Leaderboard", "Wallet & Coins", "Notifications",
                "Saved Items", "Verification", "Update Center", "Settings",
                "Help & Support", "Log Out"
            ), BlinkMenuDestination.entries.map { it.title }
        )
        assertEquals(11, BlinkMenuDestination.entries.map { it.key }.distinct().size)
    }

    @Test fun invalidRouteNeverMeansLogout() {
        assertEquals(BlinkMenuDestination.LOGOUT, BlinkMenuDestination.fromKey("logout"))
        assertNull(BlinkMenuDestination.fromKey(""))
        assertNull(BlinkMenuDestination.fromKey("log_out"))
    }
}
