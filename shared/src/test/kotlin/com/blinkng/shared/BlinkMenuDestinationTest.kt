package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkMenuDestinationTest {
    @Test fun referenceMenuUsesElevenStableUniqueRoutes() {
        val options = BlinkMenuDestination.entries
        assertEquals(11, options.size)
        assertEquals(11, options.map { it.key }.distinct().size)
        assertEquals(
            listOf("My Profile", "Refer & Grow", "Leaderboard", "Wallet & Coins", "Notifications",
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
