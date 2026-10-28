package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BlinkContestShareTest {
    private val contest = "12345678-aaaa-4bbb-8ccc-123456789000"
    private val entry = "12345678-bbbb-4ccc-8ddd-123456789000"
    @Test fun linksAreValidatedAndPortable() {
        assertEquals("https://blink.com.ng/contest/$contest/$entry", BlinkContestShare.link(contest, entry))
        assertFalse(BlinkContestShare.isValid(contest, "../admin"))
        assertFailsWith<IllegalArgumentException> { BlinkContestShare.link(contest, "not-a-uuid") }
    }
    @Test fun shareWordingDoesNotInventRank() {
        val url = BlinkContestShare.link(contest, entry)
        val caption = BlinkContestShare.message("Ade", "Campus Creators", 6, 152, url)
        assertTrue(caption.contains("ranked #6 with 152 votes"))
        assertTrue(caption.contains(url))
        assertTrue(BlinkContestShare.message("Ade","Campus Creators",0,0,url).contains("top spot"))
    }
}
