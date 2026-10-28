package com.blinkng.shared

/** Aggregated from every visible active post and Reel, never from a loaded UI page. */
data class ProfileContentMetrics(
    val posts: Long = 0L,
    val reels: Long = 0L,
    val views: Long = 0L,
    val likes: Long = 0L,
    val comments: Long = 0L,
    val shares: Long = 0L
)
