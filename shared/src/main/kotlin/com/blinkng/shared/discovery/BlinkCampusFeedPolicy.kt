package com.blinkng.shared.discovery

/**
 * Shared campus discovery presentation rules for the Android and desktop clients.
 * University identity comes from registered profile data, never GPS or IP.
 */
object BlinkCampusFeedPolicy {
    fun isSameCampus(viewerUniversity: String?, authorUniversity: String?): Boolean =
        BlinkUniversitySearch.sameCampus(viewerUniversity, authorUniversity)

    /** Presentation-only metric for a small, already-loaded campus post preview. */
    fun previewScore(likes: Int, comments: Int, shares: Int): Long =
        likes.coerceAtLeast(0).toLong() +
            comments.coerceAtLeast(0).toLong() * 2L +
            shares.coerceAtLeast(0).toLong() * 3L
}
