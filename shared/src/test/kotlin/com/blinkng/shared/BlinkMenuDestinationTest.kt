package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkMenuDestinationTest {
    @Test fun referenceMenuUsesTenStableUniqueRoutes() {
        val options = BlinkMenuDestination.entries
        assertEquals(10, options.size)
        assertEquals(10, options.map { it.key }.distinct().size)
        assertEquals(
            listOf("My Profile", "Leaderboard", "Wallet & Coins", "Notifications",
                "Saved Items", "Verification", "Update Center", "Settings",
                "Help & Support", "Log Out"),
            options.map { it.title }
        )
    }

    @Test fun unknownRoutesCannotAccidentallyLogOutUsers() {
        assertEquals(BlinkMenuDestination.LOGOUT, BlinkMenuDestination.fromKey("logout"))
        assertNull(BlinkMenuDestination.fromKey("log-out"))
        assertNull(BlinkMenuDestination.fromKey(""))
        assertTrue(BlinkMenuDestination.entries.all { it.title.isNotBlank() })
    }
}
