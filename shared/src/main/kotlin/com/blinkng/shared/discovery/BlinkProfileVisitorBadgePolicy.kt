package com.blinkng.shared.discovery

/**
 * UI-side expectation used by Android and Windows. The database is authoritative
 * for counting eligible, opted-in profile visitors.
 */
object BlinkProfileVisitorBadgePolicy {
    data class Visit(val visitorId: String, val visitedAtMs: Long, val visible: Boolean = true)

    fun unreadCount(visits: List<Visit>, seenThroughMs: Long, currentId: String): Int =
        visits.asSequence()
            .filter { it.visible && it.visitorId.isNotBlank() && it.visitorId != currentId }
            .filter { it.visitedAtMs > seenThroughMs }
            .distinctBy { it.visitorId }
            .count()

    /** Read acknowledgments can only advance; events after the snapshot remain unread. */
    fun acknowledgedThrough(previousMs: Long, snapshotMs: Long): Long =
        maxOf(previousMs, snapshotMs)
}
