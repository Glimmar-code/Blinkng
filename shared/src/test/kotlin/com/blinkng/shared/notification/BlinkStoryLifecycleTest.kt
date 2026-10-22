package com.blinkng.shared.notification

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkStoryLifecycleTest {
    private val now = Instant.parse("2026-10-22T03:00:00Z")

    @Test fun remainsActiveJustBefore24Hours() {
        assertTrue(BlinkStoryLifecycle.isActive("2026-10-21T03:00:01Z", true, now))
    }

    @Test fun expiresAtExactly24Hours() {
        assertFalse(BlinkStoryLifecycle.isActive("2026-10-21T03:00:00Z", true, now))
        assertFalse(BlinkStoryLifecycle.isActive("2026-10-20T03:00:00Z", true, now))
    }

    @Test fun ignoresFutureDisabledOrInvalidStories() {
        assertFalse(BlinkStoryLifecycle.isActive("2026-10-22T04:00:00Z", true, now))
        assertFalse(BlinkStoryLifecycle.isActive("2026-10-22T02:00:00Z", false, now))
        assertFalse(BlinkStoryLifecycle.isActive("invalid", true, now))
        assertFalse(BlinkStoryLifecycle.isActive(null, true, now))
    }

    @Test fun parsesSupabaseOffsetTimestamps() {
        assertTrue(BlinkStoryLifecycle.isActive("2026-10-21T04:00:00+01:00", true, now))
    }
}
