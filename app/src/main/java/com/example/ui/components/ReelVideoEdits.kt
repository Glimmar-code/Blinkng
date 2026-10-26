package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.SpannableString
import androidx.core.content.FileProvider
import androidx.media3.common.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Actual edits baked into the uploaded MP4, never decorative UI-only edits. */
internal data class ReelEditSpec(
    val startMs: Long,
    val endMs: Long,
    val muteOriginalAudio: Boolean = false,
    val grayscale: Boolean = false,
    val rotationDegrees: Float = 0f,
    val overlayText: String = "",
    val sticker: String = "",
    val musicUri: String? = null
) {
    fun validate(durationMs: Long): ReelEditSpec {
        require(durationMs >= 500L) { "Video is too short to edit." }
        require(startMs >= 0 && startMs < durationMs) { "Invalid clip start." }
        require(endMs <= durationMs && endMs - startMs >= 500L) {
            "Keep at least half a second of video."
        }
        require(rotationDegrees in listOf(0f, 90f, 180f, 270f)) { "Invalid rotation." }
        require(overlayText.length <= 120 && sticker.length <= 16) { "Overlay text too long." }
        return this
    }

    fun hasChanges(durationMs: Long): Boolean =
        startMs > 0 || endMs < durationMs - 100 ||
            muteOriginalAudio || grayscale || rotationDegrees != 0f ||
            overlayText.isNotBlank() || sticker.isNotBlank() || musicUri != null
}

/** Creates small filmstrip previews off the UI thread; errors leave an empty timeline, not a crash. */
internal suspend fun reelFilmstrip(context: Context, uri: Uri, count: Int = 6): List<Bitmap> =
    withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                (0 until count).mapNotNull { index ->
                    val timestampUs = (durationMs * index / count) * 1_000L
                    retriever.getFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { Bitmap.createScaledBitmap(it, 84, 132, true) }
                }
            } finally {
                retriever.release()
            }
        }.getOrDefault(emptyList())
    }

/** Transformer is thread-affine. Build/start/cancel on Main and allow genuine cancellation. */
@OptIn(UnstableApi::class)
internal suspend fun exportEditedReel(
    context: Context,
    sourceUri: String,
    durationMs: Long,
    spec: ReelEditSpec
): String = withContext(Dispatchers.Main.immediate) {
    val edit = spec.validate(durationMs)
    val directory = File(context.filesDir, "reel_edits").apply { mkdirs() }
    val out = File(directory, "reel_${UUID.randomUUID()}.mp4")
    val input = MediaItem.Builder().setUri(Uri.parse(sourceUri))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(edit.startMs)
                .setEndPositionMs(edit.endMs)
                .build()
        ).build()

    val videoEffects = mutableListOf<androidx.media3.common.VideoEffect>()
    if (edit.grayscale) videoEffects += RgbFilter.createGrayscaleFilter()
    if (edit.rotationDegrees != 0f) {
        videoEffects += ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(edit.rotationDegrees).build()
    }
    val overlayString = listOf(edit.overlayText.trim(), edit.sticker.trim())
        .filter(String::isNotBlank).joinToString("  ")
    if (overlayString.isNotBlank()) {
        videoEffects += OverlayEffect(
            listOf(TextOverlay.createStaticTextOverlay(SpannableString(overlayString)))
        )
    }
    val videoItem = EditedMediaItem.Builder(input)
        .setRemoveAudio(edit.muteOriginalAudio)
        .setEffects(Effects(emptyList(), videoEffects))
        .build()
    val music = edit.musicUri?.takeIf(String::isNotBlank)
    val composition = if (music != null) {
        // Media3 1.8.0 uses the pre-1.9 sequence constructors.
        val videoSequence = EditedMediaItemSequence(listOf(videoItem))
        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(music)).build()
        val loopMusic = EditedMediaItemSequence(listOf(audioItem), true)
        Composition.Builder(videoSequence, loopMusic).build()
    } else null

    try {
        suspendCancellableCoroutine<String> { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (!continuation.isActive) return
                    if (!out.isFile || out.length() == 0L) {
                        continuation.resumeWithException(IllegalStateException("Reel export produced no video."))
                    } else {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.chatfiles",
                            out
                        )
                        continuation.resume(uri.toString())
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    out.delete()
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            val transformer = Transformer.Builder(context)
                .addListener(listener)
                .build()
            continuation.invokeOnCancellation {
                // Transformer requires its original looper for cancellation.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    transformer.cancel()
                    out.delete()
                }
            }
            runCatching {
                if (composition != null) transformer.start(composition, out.absolutePath)
                else transformer.start(videoItem, out.absolutePath)
            }.onFailure {
                out.delete()
                if (continuation.isActive) continuation.resumeWithException(it)
            }
        }
    } catch (e: Exception) {
        out.delete()
        throw e
    }
}
