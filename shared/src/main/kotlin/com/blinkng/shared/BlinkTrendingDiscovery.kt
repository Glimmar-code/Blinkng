package com.blinkng.shared

/**
 * Authenticated, public-content-only trend discovery contract.
 * No client calculates creator ranking from locally cached posts: only the
 * ranked server RPC may populate the 30-day creator section.
 */
data class BlinkTrendingCreator(
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val university: String,
    val isVerified: Boolean,
    val trendScore: Double,
)

data class BlinkTrendingTags(
    val tags: List<String> = emptyList(),
    val windowDays: Int = 7,
) {
    val label: String get() = "Trending hashtags · $windowDays days"
}

object BlinkTrendingDiscovery {
    const val CURRENT_TAGS_RPC = "blink_trending_tags"
    const val THIRTY_DAY_TAGS_RPC = "blink_trending_tags_30d"
    const val THIRTY_DAY_CREATORS_RPC = "blink_trending_creators_30d"

    fun validCreator(id: String, username: String): Boolean =
        id.isNotBlank() && username.isNotBlank()

    fun windowDays(fromThirtyDayRpc: Boolean): Int =
        if (fromThirtyDayRpc) 30 else 7
}
