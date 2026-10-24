package com.blinkng.shared.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlinkStoryReplyReferenceTest {
    private val id = "4af24111-34fa-49b4-88c1-20ed928aa341"

    @Test fun roundTripsStatusAndMessageWithoutMediaLinks() {
        val encoded = BlinkStoryReplyReference.encode(id, " Great day on campus! ")!!
        assertEquals(BlinkStoryReplyReference.Reply(id, "Great day on campus!"),
            BlinkStoryReplyReference.parse(encoded))
        assertEquals("Status reply: Great day on campus!",
            BlinkStoryReplyReference.inboxPreview(encoded))
    }

    @Test fun rejectsInvalidOrMissingStatusReference() {
        assertNull(BlinkStoryReplyReference.encode("not-a-uuid", "hello"))
        assertNull(BlinkStoryReplyReference.encode(id, "    "))
        assertNull(BlinkStoryReplyReference.parse("[BLINK story:fake]\nhello"))
        assertNull(BlinkStoryReplyReference.parse("ordinary text"))
        assertEquals("ordinary text", BlinkStoryReplyReference.inboxPreview("ordinary text"))
    }

    @Test fun limitsMessageLength() {
        val encoded = BlinkStoryReplyReference.encode(id, "x".repeat(1_500))!!
        assertEquals(1_000, BlinkStoryReplyReference.parse(encoded)!!.text.length)
    }
}
