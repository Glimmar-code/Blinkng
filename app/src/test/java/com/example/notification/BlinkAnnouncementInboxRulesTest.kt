package com.example.notification

import com.blinkng.shared.notification.BlinkAnnouncementInboxRules as Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkAnnouncementInboxRulesTest {
    @Test fun onlyOfficialUpdatesQualify() {
        assertTrue(Rules.isOfficialCategory("admin", null))
        assertTrue(Rules.isOfficialCategory("system", null))
        assertTrue(Rules.isOfficialCategory("social", "notification"))
        assertFalse(Rules.isOfficialCategory("message", "chat"))
        assertFalse(Rules.isOfficialCategory("coin", "reward"))
    }

    @Test fun rejectsInvalidIdsAndEmptyContent() {
        assertNull(Rules.create("", "Title", "Body"))
        assertNull(Rules.create("../secret", "Title", "Body"))
        assertNull(Rules.create("valid-id", " ", " "))
        assertNotNull(Rules.create("valid-id", "A title", ""))
    }

    @Test fun capsHistoryAndDeduplicatesNewest() {
        var current = emptyList<com.blinkng.shared.notification.BlinkAnnouncementPreview>()
        repeat(60) { index ->
            current = Rules.upsert(current, Rules.create("id-$index", "Notice $index", "Body")!!)
        }
        assertEquals(Rules.MAX_CACHED, current.size)
        assertEquals("id-59", current.first().id)
        current = Rules.upsert(current, Rules.create("id-55", "Updated", "New content")!!)
        assertEquals(50, current.size)
        assertEquals(1, current.count { it.id == "id-55" })
        assertEquals("Updated", current.first().title)
    }

    @Test fun truncatesUntrustedDisplayText() {
        val item = Rules.create("abc", "A".repeat(500), "B".repeat(1_000))!!
        assertEquals(Rules.MAX_TITLE, item.title.length)
        assertEquals(Rules.MAX_MESSAGE, item.message.length)
    }
}
