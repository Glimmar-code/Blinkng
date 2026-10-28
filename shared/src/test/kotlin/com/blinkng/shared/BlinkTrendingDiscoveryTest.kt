package com.blinkng.shared

import org.junit.Assert.*
import org.junit.Test

class BlinkTrendingDiscoveryTest {
    @Test fun labels_legacy_window_without_claiming_thirty_days() {
        assertEquals(7, BlinkTrendingDiscovery.windowDays(false))
        assertEquals("Trending hashtags · 7 days", BlinkTrendingTags(listOf("futa"), 7).label)
    }

    @Test fun labels_new_backend_window_only_after_its_successful_response() {
        assertEquals(30, BlinkTrendingDiscovery.windowDays(true))
        assertEquals("Trending hashtags · 30 days", BlinkTrendingTags(listOf("blink"), 30).label)
    }

    @Test fun invalid_server_people_are_not_promoted_as_real_creators() {
        assertFalse(BlinkTrendingDiscovery.validCreator("", "known_user"))
        assertFalse(BlinkTrendingDiscovery.validCreator("abc", ""))
        assertTrue(BlinkTrendingDiscovery.validCreator("profile-123", "known_user"))
    }

    @Test fun unavailable_backend_has_no_fabricated_tags() {
        val empty = BlinkTrendingTags()
        assertTrue(empty.tags.isEmpty())
        assertEquals(7, empty.windowDays)
        assertEquals("blink_trending_tags_30d", BlinkTrendingDiscovery.THIRTY_DAY_TAGS_RPC)
        assertEquals("blink_trending_creators_30d", BlinkTrendingDiscovery.THIRTY_DAY_CREATORS_RPC)
    }
}
