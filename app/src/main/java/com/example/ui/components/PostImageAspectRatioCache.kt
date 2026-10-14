package com.example.ui.components

/**
 * Small process-local cache of decoded post-image aspect ratios.
 *
 * A LazyColumn removes card composition as posts leave the viewport; holding
 * only the ratios lets returning cards lay out at the correct height before
 * Coil finishes loading, without retaining any image bitmaps or drawables.
 */
internal object PostImageAspectRatioCache {
    private const val MAX_RATIOS = 128

    private val ratios = object : LinkedHashMap<String, Float>(MAX_RATIOS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
            size > MAX_RATIOS
    }

    @Synchronized
    fun get(url: String): Float? = ratios[url]

    @Synchronized
    fun put(url: String, ratio: Float) {
        if (url.isBlank() || !ratio.isFinite()) return
        ratios[url] = ratio.coerceIn(0.50f, 1.91f)
    }
}
