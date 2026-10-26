package com.blinkng.shared

/**
 * Shared, advisory copy for the Android and Windows feed composer.
 * Suggestions describe good practice; they never promise reach or ranking.
 */
object BlinkPostGrowthTips {
    val feedCategories = listOf("Campus Life", "Academic", "Events", "Sports", "Entertainment")

    fun disallowedFeedCategory(category: String): Boolean =
        category.trim().lowercase() in setOf("marketplace", "market", "buy & sell")

    /**
     * All Feed composers must apply one rule, including saved and scheduled posts.
     * This is a conservative text/metadata guard, not an image-recognition system.
     * Server-side enforcement remains authoritative.
     */
    fun marketOnlyReason(
        category: String,
        text: String,
        caption: String? = null,
        altText: String? = null
    ): String? {
        val description = listOfNotNull(text, caption, altText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return if (disallowedFeedCategory(category) ||
            MarketIntentPolicy.likelyProductAdvertisement(description)
        ) marketHint else null
    }

    fun suggestion(hasImages: Boolean, hasVideo: Boolean, hasPoll: Boolean): String = when {
        hasVideo -> "Capture attention in the opening seconds, and add a clear caption."
        hasImages -> "Use a clear photo and a caption that explains why it matters."
        hasPoll -> "Ask one specific question and offer distinct choices."
        else -> "Start with a useful idea or question that encourages a genuine discussion."
    }

    const val hashtagHint = "Use relevant hashtags so interested people can find your post."
    const val marketHint = "Selling an item? Create a listing in BLINK Market instead of a regular post."
}
