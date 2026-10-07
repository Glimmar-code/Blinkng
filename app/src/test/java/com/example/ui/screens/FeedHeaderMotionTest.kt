package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedHeaderMotionTest {

    @Test
    fun collapseOffset_tracksDeltaAndClampsToHeaderBounds() {
        assertEquals(-50f, clampFeedHeaderOffset(-20f, -30f, 100f), 0.001f)
        assertEquals(-100f, clampFeedHeaderOffset(-80f, -50f, 100f), 0.001f)
        assertEquals(0f, clampFeedHeaderOffset(-20f, 50f, 100f), 0.001f)
    }

    @Test
    fun settleTarget_alwaysOpensAtRealFeedTop() {
        assertEquals(
            0f,
            feedHeaderSettleTarget(
                currentOffsetPx = -90f,
                headerHeightPx = 100f,
                direction = -1,
                atFeedTop = true,
                velocityYPxPerSecond = -4_000f
            ),
            0.001f
        )
    }

    @Test
    fun settleTarget_followsIntentionalScrollDirection() {
        assertEquals(
            -100f,
            feedHeaderSettleTarget(-20f, 100f, direction = -1, atFeedTop = false),
            0.001f
        )
        assertEquals(
            0f,
            feedHeaderSettleTarget(-80f, 100f, direction = 1, atFeedTop = false),
            0.001f
        )
    }

    @Test
    fun settleTarget_fastFlingOverridesPartialPosition() {
        assertEquals(
            -100f,
            feedHeaderSettleTarget(
                currentOffsetPx = -10f,
                headerHeightPx = 100f,
                direction = 1,
                atFeedTop = false,
                velocityYPxPerSecond = -2_400f
            ),
            0.001f
        )
        assertEquals(
            0f,
            feedHeaderSettleTarget(
                currentOffsetPx = -90f,
                headerHeightPx = 100f,
                direction = -1,
                atFeedTop = false,
                velocityYPxPerSecond = 2_400f
            ),
            0.001f
        )
    }

    @Test
    fun autoplayRequiresMostOfPreviewToBeVisible() {
        assertTrue(feedAutoplayEligible(20, 100, 0, 100))
        assertFalse(feedAutoplayEligible(45, 100, 0, 100))
    }

    @Test
    fun prefetchLookaheadScalesWithScrollSpeed() {
        assertEquals(3, feedPrefetchLookahead(itemDelta = 1, elapsedMs = 500))
        assertEquals(5, feedPrefetchLookahead(itemDelta = 3, elapsedMs = 500))
        assertEquals(7, feedPrefetchLookahead(itemDelta = 5, elapsedMs = 500))
    }

    @Test
    fun restorePrefersStableRowKeyOverChangingIndex() {
        val keys = listOf("post:a", "sponsored:0", "post:b", "post:c")
        assertEquals(2, resolveFeedRestoreIndex("post:b", keys, savedIndex = 0))
        assertEquals(3, resolveFeedRestoreIndex("missing", keys, savedIndex = 99))
    }
}
