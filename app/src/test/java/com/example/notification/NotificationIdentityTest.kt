package com.example.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdentityTest {
    @Test fun chatIdsAreStableAndAvoidReservedRanges() {
        val id = NotificationIdentity.conversationId("conversation-one")
        assertEquals(id, NotificationIdentity.conversationId("conversation-one"))
        assertTrue(id >= 100_000)
        assertTrue(id < 1_000_100_000)
        assertNotEquals(id, NotificationIdentity.conversationId("conversation-two"))
    }

    @Test fun recoveryCursorKeepsSameTimestampEvents() {
        val date = "2026-10-14T10:00:00+00:00"
        assertTrue(NotificationIdentity.isAfterCursor(date, "b", date, "a"))
        assertFalse(NotificationIdentity.isAfterCursor(date, "a", date, "b"))
        assertTrue(NotificationIdentity.isAfterCursor(date, "a", date, ""))
        assertTrue(NotificationIdentity.isAfterCursor("2026-10-15T10:00:00+00:00", "a", date, "z"))
        assertFalse(NotificationIdentity.isAfterCursor("2026-10-13T10:00:00+00:00", "z", date, "a"))
    }

    @Test fun missingCreationTimeCannotAdvanceCursor() {
        assertFalse(NotificationIdentity.isAfterCursor("", "a", "", ""))
    }
}
