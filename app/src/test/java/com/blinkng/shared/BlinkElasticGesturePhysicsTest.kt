package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkElasticGesturePhysicsTest {
    @Test fun resistancePreservesDirectionAndIsBounded() {
        assertEquals(45f, BlinkElasticGesturePhysics.visualDistance(45f, 100f), 0.001f)
        assertEquals(-45f, BlinkElasticGesturePhysics.visualDistance(-45f, 100f), 0.001f)
        assertTrue(BlinkElasticGesturePhysics.visualDistance(1000f, 100f) < 150f)
        assertEquals(0f, BlinkElasticGesturePhysics.visualDistance(Float.NaN, 100f), 0.001f)
    }

    @Test fun incompleteSlowDragReturnsWithoutAction() {
        assertFalse(BlinkElasticGesturePhysics.shouldCommit(40f, 180f, 100f, 28f, 1100f))
        assertFalse(BlinkElasticGesturePhysics.shouldCommit(-40f, -180f, 100f, 28f, 1100f))
    }

    @Test fun fastFlickOrLongDeliberateSwipeCommits() {
        assertTrue(BlinkElasticGesturePhysics.shouldCommit(40f, 1200f, 100f, 28f, 1100f))
        assertTrue(BlinkElasticGesturePhysics.shouldCommit(-40f, -1200f, 100f, 28f, 1100f))
        assertTrue(BlinkElasticGesturePhysics.shouldCommit(110f, 0f, 100f, 28f, 1100f))
    }

    @Test fun reverseFlingSmallMovementAndInvalidInputsNeverCommit() {
        assertFalse(BlinkElasticGesturePhysics.shouldCommit(40f, -1200f, 100f, 28f, 1100f))
        assertFalse(BlinkElasticGesturePhysics.shouldCommit(10f, 5000f, 100f, 28f, 1100f))
        assertFalse(BlinkElasticGesturePhysics.shouldCommit(Float.NaN, 1400f, 100f, 28f, 1100f))
    }
}
