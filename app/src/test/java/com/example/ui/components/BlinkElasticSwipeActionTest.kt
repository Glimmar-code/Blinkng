package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkElasticSwipeActionTest {
    @Test fun horizontalDirectionalLockHonorsRequestedDirection() {
        assertEquals(70f, directionalSwipeDistance(70f, 8f, BlinkElasticSwipeDirection.RIGHT), .001f)
        assertEquals(0f, directionalSwipeDistance(-70f, 8f, BlinkElasticSwipeDirection.RIGHT), .001f)
        assertEquals(-70f, directionalSwipeDistance(-70f, 8f, BlinkElasticSwipeDirection.LEFT), .001f)
    }
    @Test fun verticalAndDiagonalScrollRemainIndependent() {
        assertEquals(80f, directionalSwipeDistance(5f, 80f, BlinkElasticSwipeDirection.DOWN), .001f)
        assertEquals(0f, directionalSwipeDistance(80f, 80f, BlinkElasticSwipeDirection.DOWN), .001f)
        assertEquals(0f, directionalSwipeDistance(Float.NaN, 10f, BlinkElasticSwipeDirection.DOWN), .001f)
    }
}
