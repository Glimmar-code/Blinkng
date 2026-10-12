package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedHeaderMotionTest {

    @Test
    fun headerConsumesOnlyItsMovementToAvoidDoubleScroll() {
        assertEquals(-30f, feedHeaderConsumedScrollDelta(0f, -30f, -30f), 0.001f)
        // Header was only 20px from its collapsed stop: list gets the other 30px.
        assertEquals(-20f, feedHeaderConsumedScrollDelta(-80f, -100f, -50f), 0.001f)
        assertEquals(25f, feedHeaderConsumedScrollDelta(-60f, -35f, 40f), 0.001f)
        assertEquals(0f, feedHeaderConsumedScrollDelta(0f, 0f, 50f), 0.001f)
        // Reversals below the dead zone are left to the list.
        assertEquals(0f, feedHeaderConsumedScrollDelta(-70f, -70f, 5f), 0.001f)
        // No more than the received delta may be consumed.
        assertEquals(-10f, feedHeaderConsumedScrollDelta(0f, -40f, -10f), 0.001f)
        assertEquals(0f, feedHeaderConsumedScrollDelta(Float.NaN, -10f, -10f), 0.001f)
    }

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
            feedHeaderSettleTarget(-40f, 100f, direction = -1, atFeedTop = false),
            0.001f
        )
        assertEquals(
            0f,
            feedHeaderSettleTarget(-60f, 100f, direction = 1, atFeedTop = false),
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
    fun tinyScrollDoesNotSnapHeaderAwayOrBack() {
        assertEquals(0f, feedHeaderSettleTarget(-10f, 100f, direction = -1, atFeedTop = false), 0.001f)
        assertEquals(-100f, feedHeaderSettleTarget(-90f, 100f, direction = 1, atFeedTop = false), 0.001f)
    }

    @Test
    fun headerOpacityIsSmoothAndClamped() {
        assertEquals(1f, feedHeaderOpacity(0f, 100f), 0.001f)
        assertEquals(1f, feedHeaderOpacity(-10f, 100f), 0.001f)
        assertTrue(feedHeaderOpacity(-40f, 100f) in 0f..1f)
        assertTrue(feedHeaderOpacity(-40f, 100f) > feedHeaderOpacity(-60f, 100f))
        assertEquals(0f, feedHeaderOpacity(-100f, 100f), 0.001f)
        assertEquals(0f, feedHeaderOpacity(-500f, 100f), 0.001f)
        assertEquals(1f, feedHeaderOpacity(-50f, 0f), 0.001f)
    }

    @Test
    fun autoplayRequiresMostOfPreviewToBeVisible() {
        assertTrue(feedAutoplayEligible(20, 100, 0, 100))
        assertFalse(feedAutoplayEligible(45, 100, 0, 100))
    }

    @Test
    fun prefetchLookaheadScalesWithScrollSpeed() {
        assertEquals(3, feedPrefetchLookahead(itemDelta = 1, elapsedMs = 500))
        assertEquals(2, feedPrefetchLookahead(itemDelta = 3, elapsedMs = 500))
        assertEquals(1, feedPrefetchLookahead(itemDelta = 5, elapsedMs = 500))
    }

    @Test
    fun restorePrefersStableRowKeyOverChangingIndex() {
        val keys = listOf("post:a", "sponsored:0", "post:b", "post:c")
        assertEquals(2, resolveFeedRestoreIndex("post:b", keys, savedIndex = 0))
        assertEquals(3, resolveFeedRestoreIndex("missing", keys, savedIndex = 99))
    }
}
