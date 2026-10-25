package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkReplySwipePhysicsTest {
    @Test fun dragFollowsFingerBeforeThresholdThenResists() {
        assertEquals(20f, BlinkReplySwipePhysics.visualOffset(20f, 76f), 0.001f)
        assertEquals(81f, BlinkReplySwipePhysics.visualOffset(96f, 76f), 0.001f)
        assertEquals(95f, BlinkReplySwipePhysics.visualOffset(500f, 76f), 0.001f)
        assertEquals(0f, BlinkReplySwipePhysics.visualOffset(-10f, 76f), 0.001f)
    }
    @Test fun onlyDeliberateSwipeArmsReply() {
        assertFalse(BlinkReplySwipePhysics.shouldReply(75f, 76f))
        assertTrue(BlinkReplySwipePhysics.shouldReply(76f, 76f))
    }
}
