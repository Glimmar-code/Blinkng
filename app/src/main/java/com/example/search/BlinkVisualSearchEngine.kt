package com.example.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.blinkng.shared.BlinkSearchPhase3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces the same privacy-preserving 512-value visual descriptor used by the
 * server indexer and Windows client. The original image never needs to leave the
 * device when a user runs visual search.
 */
object BlinkVisualSearchEngine {
    private const val WIDTH = 16
    private const val HEIGHT = 8

    suspend fun descriptor(context: Context, uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return@withContext FloatArray(0)
        try {
            descriptor(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun descriptor(bitmap: Bitmap): FloatArray {
        if (bitmap.width <= 0 || bitmap.height <= 0) return FloatArray(0)
        val scaled = if (bitmap.width == WIDTH && bitmap.height == HEIGHT) bitmap
        else Bitmap.createScaledBitmap(bitmap, WIDTH, HEIGHT, true)
        return try {
            val values = FloatArray(BlinkSearchPhase3.VISUAL_DESCRIPTOR_DIMENSIONS)
            var offset = 0
            for (y in 0 until HEIGHT) {
                for (x in 0 until WIDTH) {
                    val color = scaled.getPixel(x, y)
                    val r = android.graphics.Color.red(color) / 255f
                    val g = android.graphics.Color.green(color) / 255f
                    val b = android.graphics.Color.blue(color) / 255f
                    val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    values[offset++] = r
                    values[offset++] = g
                    values[offset++] = b
                    values[offset++] = luma
                }
            }
            BlinkSearchPhase3.normalizeDescriptor(values)
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }
}
