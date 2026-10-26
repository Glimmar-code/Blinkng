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

    @Test fun rejectsUnsafeAndMalformedUrls() {
        assertNull(BlinkRichLinks.parse("javascript:alert(1)"))
        assertNull(BlinkRichLinks.parse("https://user:pass@example.com/"))
        assertNull(BlinkRichLinks.parse("https://"))
        assertNull(BlinkRichLinks.first("No link in this post"))
    }
}
