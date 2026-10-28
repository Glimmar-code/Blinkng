package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlinkVerifiedChannelProTest {
    @Test fun summarizesOnlyLoadedUpdatesAndDeduplicates() {
        val one = BlinkChannelUpdate(
            id = "a", channelId = "c", authorId = "author", body = "First", createdAt = "date",
            viewCount = 20, reactions = mapOf("👍" to 2, "❤️" to -2),
            mediaKind = "poll", pollCounts = mapOf(0 to 4, 1 to -1)
        )
        val two = BlinkChannelUpdate(
            id = "b", channelId = "c", authorId = "author", body = "", createdAt = "date",
            viewCount = 4, mediaKind = "image"
        )
        val result = BlinkVerifiedChannelPro.summarize(listOf(one, one, two))
        assertEquals(2, result.loadedUpdates)
        assertEquals(24L, result.totalReportedViews)
        assertEquals(2L, result.totalReactions)
        assertEquals(4L, result.pollVotes)
        assertEquals(1, result.polls)
        assertEquals(1, result.mediaUpdates)
        assertEquals("a", result.topUpdates.first().id)
    }

    @Test fun schedulePresetsAreFutureUtcAndBounded() {
        val baseline = 1_700_000_000_000L
        assertEquals("2023-11-15T22:13:20Z",
            BlinkVerifiedChannelPro.utcPreset(baseline, 24))
        assertFailsWith<IllegalArgumentException> {
            BlinkVerifiedChannelPro.utcPreset(baseline, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BlinkVerifiedChannelPro.utcPreset(baseline, 169)
        }
    }

    @Test fun templatesNeverAutoPostAndContainPlaceholders() {
        val draft = BlinkVerifiedChannelPro.template("event", "My Channel")
        assertTrue(draft.contains("My Channel"))
        assertTrue(draft.contains("Date and time:"))
        assertTrue(draft.contains("Venue:"))
        assertEquals("", BlinkVerifiedChannelPro.template("unknown", "My Channel"))
    }
}
