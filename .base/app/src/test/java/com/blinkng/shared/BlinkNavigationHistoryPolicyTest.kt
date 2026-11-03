package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkNavigationHistoryPolicyTest {
    @Test fun visitsAreReversedWithoutLosingIntermediateRoutes() {
        val home = "home"
        val search = "search"
        val profile = "profile"
        val dm = "dm"
        val routes = BlinkNavigationHistoryPolicy.push(
            BlinkNavigationHistoryPolicy.push(
                BlinkNavigationHistoryPolicy.push(emptyList(), home, search),
                search, profile
            ), profile, dm
        )
        assertEquals(listOf(home, search, profile), routes)
        val (remainder, destination) = BlinkNavigationHistoryPolicy.pop(routes)!!
        assertEquals(profile, destination)
        assertEquals(search, BlinkNavigationHistoryPolicy.pop(remainder)!!.second)
    }

    @Test fun repeatedTargetDoesNotDuplicateRouteAndStackIsBounded() {
        assertEquals(emptyList<String>(), BlinkNavigationHistoryPolicy.push(emptyList(), "home", "home"))
        val longHistory = (0..100).fold(emptyList<Int>()) { history, number ->
            BlinkNavigationHistoryPolicy.push(history, number, number + 1)
        }
        assertEquals(BlinkNavigationHistoryPolicy.MAX_DEPTH, longHistory.size)
        assertNull(BlinkNavigationHistoryPolicy.pop(emptyList<String>()))
    }
}
