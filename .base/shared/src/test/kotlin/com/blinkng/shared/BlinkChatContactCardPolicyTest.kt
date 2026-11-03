package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkChatContactCardPolicyTest {
    @Test fun encodedContactCanBeReadOnBothPlatforms() {
        val raw = BlinkChatContactCardPolicy.encode("Alex", "+234 800 123 4567")
        assertEquals("Alex", BlinkChatContactCardPolicy.parse(raw)?.displayName)
        assertEquals("+234 800 123 4567", BlinkChatContactCardPolicy.parse(raw)?.phoneNumber)
    }

    @Test fun ordinaryChatMessagesAreNotSpoofedAsContacts() {
        assertNull(BlinkChatContactCardPolicy.parse("Phone: 1234"))
        assertNull(BlinkChatContactCardPolicy.parse("👤 Contact: Alex\nPhone: invalid"))
        assertNull(BlinkChatContactCardPolicy.parse("👤 Contact: Alex\nPhone: 1234\nOther field"))
    }

    @Test fun unsafeNewlinesCannotAddExtraContactFields() {
        val raw = BlinkChatContactCardPolicy.encode("Alex\nOther", "+234 800\n123")
        assertEquals("Alex Other", BlinkChatContactCardPolicy.parse(raw)?.displayName)
        assertEquals("+234 800 123", BlinkChatContactCardPolicy.parse(raw)?.phoneNumber)
    }
}
