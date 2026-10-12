package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkHorizontalPageSwipeTest {
    @Test
    fun deliberateHorizontalSwipeNavigatesOnlyOnceOnRelease() {
        assertEquals(-1, horizontalPageSwipeDirection(-180f, 5f, 120f, false))
        assertEquals(1, horizontalPageSwipeDirection(180f, -6f, 120f, false))
    }

    @Test
    fun scrollingAndChildCarouselsKeepPriority() {
        assertEquals(0, horizontalPageSwipeDirection(95f, 5f, 120f, false))
        assertEquals(0, horizontalPageSwipeDirection(-180f, 260f, 120f, false))
        assertEquals(0, horizontalPageSwipeDirection(-200f, 3f, 120f, true))
        assertEquals(0, horizontalPageSwipeDirection(Float.NaN, 0f, 120f, false))
    }
}
