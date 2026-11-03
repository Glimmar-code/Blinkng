package com.blinkng.shared.live

/**
 * Shared limits for locally compressed BLINK campaign artwork.
 * Supabase always enforces the independent, stricter output limit.
 */
object BlinkLiveBannerImageLimits {
    const val MAX_UPLOAD_BYTES = 2 * 1024 * 1024
    const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
    const val MIN_DIMENSION = 64
    const val MAX_SOURCE_DIMENSION = 12000
    const val MAX_SOURCE_PIXELS = 100_000_000L
    const val MAX_OUTPUT_DIMENSION = 1600

    fun validateSource(width: Int, height: Int, sizeBytes: Long): Boolean =
        width in MIN_DIMENSION..MAX_SOURCE_DIMENSION &&
            height in MIN_DIMENSION..MAX_SOURCE_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS &&
            sizeBytes in 1..MAX_SOURCE_BYTES.toLong()

    fun targetSize(width: Int, height: Int, maxDimension: Int = MAX_OUTPUT_DIMENSION): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxDimension >= MIN_DIMENSION)
        val maximum = maxOf(width, height)
        if (maximum <= maxDimension) return width to height
        val scale = maxDimension.toDouble() / maximum
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /** Power-of-two subsampling bounds memory before Android bitmap allocation. */
    fun androidSampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0)
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_OUTPUT_DIMENSION * 2 && sample < 64)
            sample *= 2
        return sample
    }
}
