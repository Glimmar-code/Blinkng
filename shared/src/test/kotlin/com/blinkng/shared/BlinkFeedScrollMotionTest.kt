package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class BlinkFeedScrollMotionTest {
    @Test fun proportionsForEightyPixels() {
        assertEquals(20f, BlinkFeedScrollMotion.advance(0f,80f,BlinkFeedScrollMotion.TOP_PER_POST,120f))
        assertEquals(50f, BlinkFeedScrollMotion.advance(0f,80f,BlinkFeedScrollMotion.NAV_PER_POST,80f))
    }
    @Test fun reverseRestoresAndClampsBothRails() {
        assertEquals(0f, BlinkFeedScrollMotion.advance(20f,-80f,BlinkFeedScrollMotion.TOP_PER_POST,120f))
        assertEquals(0f, BlinkFeedScrollMotion.advance(50f,-80f,BlinkFeedScrollMotion.NAV_PER_POST,80f))
        assertEquals(80f, BlinkFeedScrollMotion.advance(70f,100f,BlinkFeedScrollMotion.NAV_PER_POST,80f))
    }
}
