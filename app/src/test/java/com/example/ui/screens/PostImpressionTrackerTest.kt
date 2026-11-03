package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostImpressionTrackerTest {
    @Test
    fun `counts a post again only after it leaves qualified visibility`() {
        val tracker = PostImpressionTracker()

        assertEquals(setOf("post-a"), tracker.update(setOf("post-a")))
        assertEquals(emptySet<String>(), tracker.update(setOf("post-a")))
        assertEquals(emptySet<String>(), tracker.update(emptySet()))
        assertEquals(setOf("post-a"), tracker.update(setOf("post-a")))
    }

    @Test
    fun `requires half of the post or viewport to be visible`() {
        assertFalse(
            qualifiesForPostImpression(
                itemOffset = 801,
                itemSize = 400,
                viewportStart = 0,
                viewportEnd = 1000
            )
        )
        assertTrue(
            qualifiesForPostImpression(
                itemOffset = 800,
                itemSize = 400,
                viewportStart = 0,
                viewportEnd = 1000
            )
        )
        assertTrue(
            qualifiesForPostImpression(
                itemOffset = 0,
                itemSize = 1600,
                viewportStart = 0,
                viewportEnd = 1000
            )
        )
    }
}
