package com.blinkng.shared

/**
 * Controls opportunistic foreground refreshes. Content already on screen stays visible;
 * the caller decides which currently visible data source is refreshed.
 * Times use a monotonic clock, never wall-clock timestamps.
 */
object BlinkRefreshPolicy {
    const val FOREGROUND_REFRESH_INTERVAL_MS: Long = 3 * 60_000L

    fun isStale(
        lastRefreshElapsedMs: Long,
        nowElapsedMs: Long,
        maxAgeMillis: Long = FOREGROUND_REFRESH_INTERVAL_MS
    ): Boolean =
        lastRefreshElapsedMs <= 0L ||
            nowElapsedMs < lastRefreshElapsedMs ||
            nowElapsedMs - lastRefreshElapsedMs >= maxAgeMillis.coerceAtLeast(0L)
}
