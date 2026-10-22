package com.blinkng.shared.notification

/** Pure layout policy shared by Android's local-only photo editor and tests. */
object BlinkStatusPhotoEditPolicy {
    data class Crop(val x: Int, val y: Int, val width: Int, val height: Int)

    fun quarterTurns(turns: Int): Int = ((turns % 4) + 4) % 4

    fun portraitCrop(width: Int, height: Int): Crop {
        require(width > 0 && height > 0)
        // Center-crop to 9:16. Use Long to avoid intermediate overflow.
        val cropWidth: Int
        val cropHeight: Int
        if (width.toLong() * 16L > height.toLong() * 9L) {
            cropWidth = (height.toLong() * 9L / 16L).toInt().coerceAtLeast(1)
            cropHeight = height
        } else {
            cropWidth = width
            cropHeight = (width.toLong() * 16L / 9L).toInt().coerceAtLeast(1)
        }
        return Crop(
            (width - cropWidth) / 2,
            (height - cropHeight) / 2,
            cropWidth,
            cropHeight
        )
    }

    fun decodeSampleSize(width: Int, height: Int, maxSide: Int = 2048): Int {
        require(width > 0 && height > 0 && maxSide > 0)
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) {
            if (sample >= (1 shl 20)) break
            sample *= 2
        }
        return sample
    }
}
