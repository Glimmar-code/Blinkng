package com.blinkng.shared

import kotlin.math.abs

/**
 * Single source of truth for the five swipeable BLINK surfaces.
 *
 * Extra destinations (search, leaderboard, game, full-screen chat) are deliberately
 * excluded: they are opened explicitly and must not enter the main swipe sequence.
 */
enum class BlinkSwipeSurface(val desktopRoute: String) {
    FEED("home"),
    REELS("reels"),
    MARKET("marketplace"),
    CONNECT("connect"),
    MESSAGES("messages")
}

object BlinkSwipeNavigation {
    val surfaces: List<BlinkSwipeSurface> = BlinkSwipeSurface.entries

    fun pageForDesktopRoute(route: String): Int? =
        surfaces.indexOfFirst { it.desktopRoute == route }.takeIf { it >= 0 }

    /** Positive direction is a swipe left / forward navigation. */
    fun adjacent(page: Int, direction: Int): Int? {
        if (page !in surfaces.indices || direction == 0) return null
        return (page + if (direction > 0) 1 else -1).takeIf { it in surfaces.indices }
    }

    /** Adjacent cards subtly shrink while the destination expands under the finger. */
    fun scale(distanceFromCenter: Float): Float =
        1f - 0.06f * abs(distanceFromCenter).coerceIn(0f, 1f)

    fun alpha(distanceFromCenter: Float): Float =
        1f - 0.08f * abs(distanceFromCenter).coerceIn(0f, 1f)
}
