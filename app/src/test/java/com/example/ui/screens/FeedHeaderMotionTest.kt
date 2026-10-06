package com.example.ui.screens

import org.junit.Assert.assertEquals
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
                atFeedTop = true
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
    fun settleTarget_usesMidpointWhenDirectionIsNeutral() {
        assertEquals(
            0f,
            feedHeaderSettleTarget(-49f, 100f, direction = 0, atFeedTop = false),
            0.001f
        )
        assertEquals(
            -100f,
            feedHeaderSettleTarget(-51f, 100f, direction = 0, atFeedTop = false),
            0.001f
        )
    }
}
