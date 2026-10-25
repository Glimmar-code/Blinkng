package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkMentionTokenPolicyTest {
    @Test fun supportsIncrementalPrefixesAndExactReplacement() {
        assertEquals("", BlinkMentionTokenPolicy.active("Meet @")?.query)
        assertEquals("a", BlinkMentionTokenPolicy.active("Meet @a")?.query)
        val token = BlinkMentionTokenPolicy.active("Meet @abd")!!
        assertEquals("Meet @abd_campus ", BlinkMentionTokenPolicy.replace("Meet @abd", token, "abd_campus"))
    }

    @Test fun ignoresEmailAndCompletedMentions() {
        assertNull(BlinkMentionTokenPolicy.active("support@blink.com.ng"))
        assertNull(BlinkMentionTokenPolicy.active("Hi @abd welcome"))
    }
}
