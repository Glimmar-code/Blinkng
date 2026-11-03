package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkHorizontalPageSwipeTest {
    @Test
    fun deliberateFastHorizontalSwipeNavigatesOnlyOnceOnRelease() {
        assertEquals(-1, horizontalPageSwipeDirection(-180f, 5f, 120f, false, -1900f))
        assertEquals(1, horizontalPageSwipeDirection(180f, -6f, 120f, false, 1900f))
    }

    @Test
    fun quickFlickCommitsBeforeFullDistanceAndWrongDirectionDoesNot() {
        assertEquals(-1, horizontalPageSwipeDirection(-46f, 2f, 120f, false, -1900f))
        assertEquals(0, horizontalPageSwipeDirection(-46f, 2f, 120f, false, -100f))
        assertEquals(0, horizontalPageSwipeDirection(-46f, 2f, 120f, false, 1900f))
    }

    @Test
    fun scrollingAndChildCarouselsKeepPriority() {
        assertEquals(0, horizontalPageSwipeDirection(95f, 5f, 120f, false))
        assertEquals(0, horizontalPageSwipeDirection(240f, 2f, 120f, false, 200f))
        assertEquals(0, horizontalPageSwipeDirection(-180f, 260f, 120f, false, -2500f))
        assertEquals(0, horizontalPageSwipeDirection(-200f, 3f, 120f, true, -2500f))
        assertEquals(0, horizontalPageSwipeDirection(Float.NaN, 0f, 120f, false))
    }
}
