package com.example.ui.screens

/**
 * UI request deduplication; the repository remains responsible for server cursors.
 * Use organic content identifiers so sponsored rows and live counters do not
 * provoke repeated database requests when the user reaches the end.
 */
internal fun feedPaginationCursor(ids: List<String>): String =
    ids.lastOrNull { it.isNotBlank() }?.let { last ->
        "${ids.size}:${last}"
    } ?: ""

internal fun shouldRequestFeedPage(
    currentCursor: String,
    previouslyRequestedCursor: String?,
): Boolean = currentCursor != previouslyRequestedCursor

/** UI-side throttle; prevents consecutive taps and pull gestures racing network state. */
internal fun canRequestFeedRefresh(
    nowElapsedMs: Long,
    previousRequestElapsedMs: Long,
    minimumGapMs: Long = 800L,
): Boolean {
    if (previousRequestElapsedMs <= 0L) return true
    if (nowElapsedMs < previousRequestElapsedMs) return true // reboot/clock reset
    return nowElapsedMs - previousRequestElapsedMs >= minimumGapMs
}
