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

    /** `fraction` selects the actual cover frame (0..1) from a local video. */
    fun extract(context: Context, localUri: Uri): ByteArray? =
        extractAt(context, localUri, 0.25f)

    fun extractAt(context: Context, localUri: Uri, fraction: Float): ByteArray? {
        if (localUri.scheme !in setOf("content", "file")) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, localUri)
            // Scale to the original source ratio instead of requesting a square frame.
            // A video's EXIF/track rotation may reverse its display dimensions.
            val sourceWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull()?.coerceAtLeast(1) ?: MAX_EDGE
            val sourceHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull()?.coerceAtLeast(1) ?: MAX_EDGE
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val width = if (rotation % 180 != 0) sourceHeight else sourceWidth
            val height = if (rotation % 180 != 0) sourceWidth else sourceHeight
            val ratio = (MAX_EDGE.toFloat() / maxOf(width, height)).coerceAtMost(1f)
            val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
            val durationUs = (retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L).coerceAtLeast(0L) * 1_000L
            val frameTimeUs = if (durationUs <= 0L) 0L else
                (durationUs * fraction.coerceIn(0f, 0.99f)).toLong()
            // Prefer the requested frame rather than an earlier keyframe.
            // Some low-end decoders cannot seek precisely; fall back to a sync frame.
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST,
                    targetWidth, targetHeight
                ) ?: retriever.getScaledFrameAtTime(
                    frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth, targetHeight
                )
            } else {
                retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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

    /** Private cover is uploaded with the existing post-images contract, never shared directly. */
    fun saveCover(context: Context, localUri: Uri, fraction: Float): String? {
        val bytes = extractAt(context, localUri, fraction) ?: return null
        val directory = java.io.File(context.filesDir, "reel_edits").apply { mkdirs() }
        val output = java.io.File(directory, "reel_cover_${java.util.UUID.randomUUID()}.jpg")
        return try {
            output.writeBytes(bytes)
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.chatfiles", output
            ).toString()
        } catch (_: Exception) {
            output.delete()
            null
        }
    }
}
