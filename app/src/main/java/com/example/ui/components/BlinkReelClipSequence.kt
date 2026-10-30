package com.example.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Actual concatenation of 2–4 device-selected clips into one Reel.
 * Unlike an array of URLs in feed_posts, this produces one playable MP4 compatible
 * with the existing single-video post API. Originals remain untouched on failure.
 */
internal object BlinkReelClipPolicy {
    const val MAX_CLIPS = com.blinkng.shared.BlinkReelEditPolicy.MAX_CLIPS
    const val MAX_TOTAL_DURATION_MS = com.blinkng.shared.BlinkReelEditPolicy.MAX_TOTAL_DURATION_MS
    fun validate(count: Int, totalDurationMs: Long) {
        require(count in 2..MAX_CLIPS) { "Select 2–4 videos for one Reel." }
        require(totalDurationMs in 1_000..MAX_TOTAL_DURATION_MS) {
            "Keep the combined Reel between 1 and 180 seconds."
        }
    }
}

@OptIn(UnstableApi::class)
internal suspend fun exportReelClipSequence(
    context: Context,
    sourceUris: List<String>,
    onProgress: (Int) -> Unit = {}
): String {
    val durations = withContext(Dispatchers.IO) {
        sourceUris.map { source ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(source))
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L)
                    ?: throw IllegalArgumentException("One selected video has no readable duration.")
            } finally {
                retriever.release()
            }
        }
    }
    BlinkReelClipPolicy.validate(sourceUris.size, durations.sum())
    return withContext(Dispatchers.Main.immediate) {
        val directory = File(context.filesDir, "reel_edits").apply { mkdirs() }
        val output = File(directory, "reel_${UUID.randomUUID()}.mp4")
        val clips = sourceUris.map { uri ->
            EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence(clips)).build()
        try {
            suspendCancellableCoroutine<String> { continuation ->
                var progressJob: Job? = null
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob?.cancel()
                        if (!continuation.isActive) return
                        if (!output.isFile || output.length() == 0L) {
                            continuation.resumeWithException(
                                IllegalStateException("Video export produced an empty file.")
                            )
                        } else {
                            onProgress(100)
                            continuation.resume(
                                FileProvider.getUriForFile(
                                    context, "${context.packageName}.chatfiles", output
                                ).toString()
                            )
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressJob?.cancel()
                        output.delete()
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                }
                val transformer = Transformer.Builder(context).addListener(listener).build()
                continuation.invokeOnCancellation {
                    progressJob?.cancel()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        transformer.cancel()
                        output.delete()
                    }
                }
                runCatching {
                    transformer.start(composition, output.absolutePath)
                    progressJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                        val holder = ProgressHolder()
                        while (continuation.isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress.coerceIn(0, 99))
                            }
                            delay(250)
                        }
                    }
                }.onFailure { problem ->
                    progressJob?.cancel()
                    output.delete()
                    if (continuation.isActive) continuation.resumeWithException(problem)
                }
            }
        } catch (problem: Exception) {
            output.delete()
            throw problem
        }
    }
}
