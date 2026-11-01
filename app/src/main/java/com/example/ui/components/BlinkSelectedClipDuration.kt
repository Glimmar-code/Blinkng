package com.example.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Duration is read from the actual selected local clip. A remote/signed HTTP URL
 * is deliberately not opened here, because displaying a thumbnail must not
 * trigger an unbounded private-media download.
 */
internal suspend fun blinkSelectedClipDurationMs(
    context: Context, uri: String
): Long? = withContext(Dispatchers.IO) {
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
    if (parsed.scheme !in listOf("content", "file", "android.resource")) {
        return@withContext null
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, parsed)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.takeIf { it > 0 }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** Unknown durations are not guessed or shown as 0:00. */
internal fun blinkSelectedClipDurationLabel(milliseconds: Long?): String? {
    val ms = milliseconds?.takeIf { it > 0L } ?: return null
    val seconds = ms / 1000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
