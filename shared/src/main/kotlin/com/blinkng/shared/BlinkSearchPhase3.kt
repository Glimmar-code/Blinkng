package com.blinkng.shared

/**
 * Platform-neutral contract shared by Android and Windows Search.
 * Backend strings are centralized here so both clients call the same Supabase RPCs.
 */
object BlinkSearchPhase3 {
    const val CAPABILITIES_RPC = "search_capabilities_v2"
    const val DISCOVERY_RPC = "search_discovery_v2"
    const val HISTORY_GET_RPC = "get_search_history_v2"
    const val HISTORY_UPSERT_RPC = "upsert_search_history_v2"
    const val IMAGE_SEARCH_RPC = "search_image_similarity_v3"
    const val IMAGE_UPSERT_RPC = "upsert_search_image_embedding_v2"

    const val MEDIA_INDEXER_FUNCTION = "search-media-indexer"
    const val REEL_INDEXER_FUNCTION = "search-reel-indexer"

    const val VISUAL_DESCRIPTOR_DIMENSIONS = 512
    const val VISUAL_DESCRIPTOR_MODEL = "blink-perceptual-rgb-luma-v1"

    val resultTypes = listOf("profile", "post", "reel", "community", "event", "page", "market_item")
    val sortModes = listOf("relevant", "recent", "trending", "growing", "distance")

    fun normalizeDescriptor(values: FloatArray): FloatArray {
        if (values.size != VISUAL_DESCRIPTOR_DIMENSIONS) return FloatArray(0)
        var sumSquares = 0.0
        for (value in values) {
            val finite = if (value.isFinite()) value else 0f
            sumSquares += finite * finite
        }
        if (sumSquares <= 1e-12) return FloatArray(0)
        val norm = kotlin.math.sqrt(sumSquares).toFloat()
        return FloatArray(values.size) { index ->
            val finite = if (values[index].isFinite()) values[index] else 0f
            finite / norm
        }
    }
}
