package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * Creates a low-data poster for an Android-picked Reel.
 * Called on the existing IO upload dispatcher; never fetches arbitrary remote URLs.
 * No new backend tables/storage buckets or privileged credentials are needed.
 */
internal object BlinkReelPoster {
    private const val MAX_EDGE = 640

    fun extract(context: Context, localUri: Uri): ByteArray? {
        if (localUri.scheme !in setOf("content", "file")) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, localUri)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    MAX_EDGE, MAX_EDGE
                )
            } else {
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return null
            val longest = maxOf(frame.width, frame.height)
            val scale = (MAX_EDGE.toFloat() / longest.coerceAtLeast(1)).coerceAtMost(1f)
            val target = if (scale == 1f) frame else Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true
            )
            val bytes = ByteArrayOutputStream().use { output ->
                target.compress(Bitmap.CompressFormat.JPEG, 74, output)
                output.toByteArray()
            }
            if (target !== frame) target.recycle()
            frame.recycle()
            bytes.takeIf { it.size in 1..400_000 }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
