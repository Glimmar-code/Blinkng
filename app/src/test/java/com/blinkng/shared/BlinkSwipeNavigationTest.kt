package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkSwipeNavigationTest {
    @Test
    fun pagesFollowFiveTabOrder() {
        assertEquals(
            listOf("home", "reels", "marketplace", "connect", "messages"),
            BlinkSwipeNavigation.surfaces.map { it.desktopRoute }
        )
        assertEquals(0, BlinkSwipeNavigation.pageForDesktopRoute("home"))
        assertEquals(4, BlinkSwipeNavigation.pageForDesktopRoute("messages"))
        assertNull(BlinkSwipeNavigation.pageForDesktopRoute("search"))
    }

    @Test
    fun swipesAreBoundedAndAdvanceExactlyOnePage() {
        assertEquals(1, BlinkSwipeNavigation.adjacent(0, 1))
        assertEquals(4, BlinkSwipeNavigation.adjacent(3, 1))
        assertEquals(3, BlinkSwipeNavigation.adjacent(4, -1))
        assertEquals(0, BlinkSwipeNavigation.adjacent(1, -1))
        assertNull(BlinkSwipeNavigation.adjacent(4, 1))
        assertNull(BlinkSwipeNavigation.adjacent(0, -1))
        assertNull(BlinkSwipeNavigation.adjacent(-1, 1))
        assertNull(BlinkSwipeNavigation.adjacent(2, 0))
    }

    @Test
    fun gestureDepthKeepsCardsReadable() {
        assertEquals(1f, BlinkSwipeNavigation.scale(0f), 0.0001f)
        assertEquals(.97f, BlinkSwipeNavigation.scale(.5f), 0.0001f)
        assertEquals(.94f, BlinkSwipeNavigation.scale(1f), 0.0001f)
        assertEquals(.94f, BlinkSwipeNavigation.scale(-2f), 0.0001f)
        assertTrue(BlinkSwipeNavigation.alpha(1f) >= .90f)
    }
}
