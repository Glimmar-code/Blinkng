package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkRichLinksTest {
    @Test fun extractsAndTrimsLink() {
        val link = BlinkRichLinks.first("See (https://example.com/post?a=1). Thanks!")
        assertEquals("https://example.com/post?a=1", link?.url)
        assertEquals("example.com", link?.host)
    }

    @Test fun recognisesBlinkHostsAndRejectsImpersonation() {
        assertTrue(BlinkRichLinks.parse("https://blink.com.ng/post/123")!!.internal)
        assertTrue(BlinkRichLinks.parse("https://www.blink.com.ng/@example")!!.internal)
        assertFalse(BlinkRichLinks.parse("https://blink.com.ng.evil.test/post")!!.internal)
    }

    @Test fun multipleLinksDeduplicateAndRespectLimit() {
        val text = "https://a.example/ and https://b.example/ and https://a.example/ and https://c.example/"
        assertEquals(listOf("a.example", "b.example", "c.example"), BlinkRichLinks.all(text).map { it.host })
        assertEquals(2, BlinkRichLinks.all(text, 2).size)
        assertEquals(0, BlinkRichLinks.all(text, 0).size)
    }

    @Test fun rejectsUnsafeAndMalformedUrls() {
        assertNull(BlinkRichLinks.parse("javascript:alert(1)"))
        assertNull(BlinkRichLinks.parse("https://user:pass@example.com/"))
        assertNull(BlinkRichLinks.parse("https://"))
        assertNull(BlinkRichLinks.first("No link in this post"))
    }
}
