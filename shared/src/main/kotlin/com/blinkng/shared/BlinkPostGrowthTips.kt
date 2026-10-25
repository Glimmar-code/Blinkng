package com.blinkng.shared

/**
 * Shared, advisory copy for the Android and Windows feed composer.
 * Suggestions describe good practice; they never promise reach or ranking.
 */
object BlinkPostGrowthTips {
    val feedCategories = listOf("Campus Life", "Academic", "Events", "Sports", "Entertainment")

    fun disallowedFeedCategory(category: String): Boolean =
        category.equals("Marketplace", ignoreCase = true)

    fun suggestion(hasImages: Boolean, hasVideo: Boolean, hasPoll: Boolean): String = when {
        hasVideo -> "Capture attention in the opening seconds, and add a clear caption."
        hasImages -> "Use a clear photo and a caption that explains why it matters."
        hasPoll -> "Ask one specific question and offer distinct choices."
        else -> "Start with a useful idea or question that encourages a genuine discussion."
    }

    const val hashtagHint = "Use relevant hashtags so interested people can find your post."
    const val marketHint = "Selling an item? Create a listing in BLINK Market instead of a regular post."
}
