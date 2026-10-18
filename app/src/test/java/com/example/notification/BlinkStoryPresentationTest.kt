package com.example.notification

import com.blinkng.shared.notification.BlinkStoryPresentation
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BlinkStoryPresentationTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    @Test fun expiryAndRings() {
        assertTrue(BlinkStoryPresentation.active("2026-10-18T11:00:00Z", true, now))
        assertFalse(BlinkStoryPresentation.active("2026-10-16T11:00:00Z", true, now))
        assertFalse(BlinkStoryPresentation.active("2026-10-18T11:00:00Z", false, now))
        assertEquals(BlinkStoryPresentation.Ring.UNVIEWED, BlinkStoryPresentation.ring(true, true))
        assertEquals(BlinkStoryPresentation.Ring.VIEWED, BlinkStoryPresentation.ring(true, false))
        assertEquals(BlinkStoryPresentation.Ring.NONE, BlinkStoryPresentation.ring(false, false))
    }
    @Test fun previewRoundTrip() {
        val id = "03f26876-1746-4b3b-b9c0-5bb1689aba11"
        val txt = BlinkStoryPresentation.reply(id, "private caption", "Good luck!")!!
        assertEquals("Good luck!", BlinkStoryPresentation.parseReply(txt)?.text)
        assertEquals(id, BlinkStoryPresentation.parseReply(txt)?.storyId)
        assertFalse(txt.contains("private caption"))
        assertNull(BlinkStoryPresentation.reply("invalid", "", "hello"))
        assertNull(BlinkStoryPresentation.parseReply("ordinary message"))
    }
}
