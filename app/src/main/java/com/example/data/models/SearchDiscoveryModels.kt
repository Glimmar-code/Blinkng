package com.example.data.models

/** Backend categories exposed by search_discovery_v2. */
enum class DiscoveryResultType(val backendValue: String, val label: String) {
    PROFILE("profile", "People"),
    POST("post", "Posts"),
    REEL("reel", "Reels"),
    COMMUNITY("community", "Communities"),
    EVENT("event", "Events"),
    PAGE("page", "Pages & Brands"),
    MARKET_ITEM("market_item", "Marketplace");

    companion object {
        fun fromBackend(value: String): DiscoveryResultType? =
            entries.firstOrNull { it.backendValue.equals(value, ignoreCase = true) }
    }
}

enum class DiscoverySort(val backendValue: String, val label: String) {
    RELEVANT("relevant", "Most relevant"),
    RECENT("recent", "Most recent"),
    TRENDING("trending", "Trending now"),
    GROWING("growing", "Fastest growing"),
    DISTANCE("distance", "Closest to me")
}

data class DiscoveryCapabilities(
    val communities: Boolean = false,
    val events: Boolean = false,
    val pagesBrands: Boolean = false,
    val marketplace: Boolean = false,
    val saved: Boolean = false,
    val following: Boolean = false,
    val syncedHistory: Boolean = false,
    val mutualRanking: Boolean = false,
    val distanceSort: Boolean = false,
    val growthMetrics: Boolean = false,
    val trendMetrics: Boolean = false,
    val cursorPagination: Boolean = false,
    val reelMatchedMoments: Boolean = false,
    val imageSimilarity: Boolean = false,
    val autoplayPreviews: Boolean = false,
    val heroTransitions: Boolean = false,
) {
    val phase3Ready: Boolean
        get() = communities && events && pagesBrands && marketplace && cursorPagination
}

data class SearchHistoryEntry(
    val id: String,
    val query: String,
    val category: String,
    val searchCount: Int = 1,
    val pinned: Boolean = false,
    val lastSearchedAt: String = "",
)

data class DiscoveryCursor(
    val score: Double,
    val type: String,
    val id: String,
    val asOf: String,
)

data class DiscoverySearchRequest(
    val query: String = "",
    val types: Set<DiscoveryResultType> = DiscoveryResultType.entries.toSet(),
    val limit: Int = 24,
    val cursor: DiscoveryCursor? = null,
    val followingOnly: Boolean = false,
    val savedOnly: Boolean = false,
    val sort: DiscoverySort = DiscoverySort.RELEVANT,
    /** Ephemeral device coordinates. They are never written to profile storage by search. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class DiscoverySearchPage(
    val results: List<DiscoveryResult>,
    val nextCursor: DiscoveryCursor?,
    val hasMore: Boolean,
)

data class DiscoveryResult(
    val type: DiscoveryResultType,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val avatarUrl: String? = null,
    val username: String = "",
    val videoUrl: String? = null,
    val score: Double = 0.0,
    val reason: String = "",
    val asOf: String = "",
    val mutualCount: Int = 0,
    val distanceKm: Double? = null,
    val trendPercent: Double = 0.0,
    val matchedMomentMs: Int? = null,
    val saved: Boolean = false,
    val following: Boolean = false,
    val verified: Boolean = false,
    val memberCount: Int = 0,
    val attendeeCount: Int = 0,
    val followerCount: Int = 0,
    val price: Long? = null,
    val currency: String = "NGN",
    val location: String = "",
    val startTime: String = "",
    val category: String = "",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val viewCount: Int = 0,
) {
    /**
     * Search navigation only needs a stable post id today. Supplying a complete-enough
     * FeedPost also keeps the callback compatible if a caller starts rendering previews.
     */
    fun asFeedPost(): FeedPost = FeedPost(
        id = id,
        author = title.ifBlank { username },
        authorAvatar = avatarUrl.orEmpty(),
        timeAgo = "",
        text = body.ifBlank { subtitle },
        images = imageUrl?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
        likes = likeCount,
        commentsCount = commentCount,
        sharesCount = shareCount,
        viewsCount = viewCount,
        isBookmarked = saved,
        isReel = type == DiscoveryResultType.REEL,
        videoUrl = videoUrl,
        location = location.takeIf { it.isNotBlank() },
        authorUsername = username,
    )
}
