package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.SpannableString
import androidx.core.content.FileProvider
import androidx.media3.transformer.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
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
    val musicUri: String? = null,
    val originalAudioVolume: Float = 1f,
    val musicVolume: Float = 1f,
    val overlayStartMs: Long = 0L,
    val overlayEndMs: Long = Long.MAX_VALUE,
    val overlayX: Float = 0f,
    val overlayY: Float = 0f,
    val overlayScale: Float = 1f
) {
    fun validate(durationMs: Long): ReelEditSpec {
        require(durationMs >= 500L) { "Video is too short to edit." }
        require(startMs >= 0 && startMs < durationMs) { "Invalid clip start." }
        require(endMs <= durationMs && endMs - startMs >= 500L) {
            "Keep at least half a second of video."
        }
        require(rotationDegrees in listOf(0f, 90f, 180f, 270f)) { "Invalid rotation." }
        require(overlayText.length <= 120 && sticker.length <= 16) { "Overlay text too long." }
        require(overlayX in -1f..1f && overlayY in -1f..1f &&
            overlayScale in 0.5f..2.0f) { "Overlay position or size is invalid." }
        require(originalAudioVolume in 0f..1f && musicVolume in 0f..1f) {
            "Audio volume must be between 0 and 100%."
        }
        require(overlayStartMs >= 0L && overlayStartMs <= durationMs) { "Overlay starts outside the Reel." }
        require(overlayEndMs == Long.MAX_VALUE ||
            (overlayEndMs > overlayStartMs && overlayEndMs <= durationMs)) {
            "Overlay end must be after its start."
        }
        return this
    }

    fun overlayVisibleAt(positionMs: Long): Boolean =
        (overlayText.isNotBlank() || sticker.isNotBlank()) &&
            positionMs >= overlayStartMs && positionMs <= overlayEndMs

    fun hasChanges(durationMs: Long): Boolean =
        startMs > 0 || endMs < durationMs - 100 ||
            muteOriginalAudio || grayscale || rotationDegrees != 0f ||
            overlayText.isNotBlank() || sticker.isNotBlank() || musicUri != null ||
            originalAudioVolume != 1f
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
                    // Decode scaled frames on Android 8.1+ to avoid allocating 4K bitmaps
                    // repeatedly on budget student phones while scrolling the editor.
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        retriever.getScaledFrameAtTime(
                            timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            84, 132
                        )
                    } else {
                        retriever.getFrameAtTime(
                            timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )?.let { full ->
                            val scaled = Bitmap.createScaledBitmap(full, 84, 132, true)
                            if (scaled !== full) full.recycle()
                            scaled
                        }
                    }
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

    val videoEffects = mutableListOf<androidx.media3.common.Effect>()
    if (edit.grayscale) videoEffects += RgbFilter.createGrayscaleFilter()
    if (edit.rotationDegrees != 0f) {
        videoEffects += ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(edit.rotationDegrees).build()
    }
    // Text and sticker are independent render layers, matching their stacked
    // positions in the editor. Never return empty text (Media3 zero-size texture).
    val overlays = listOfNotNull(
        edit.sticker.trim().takeIf { it.isNotBlank() },
        edit.overlayText.trim().takeIf { it.isNotBlank() }
    )
    if (overlays.isNotEmpty()) {
        val startUs = (edit.overlayStartMs - edit.startMs).coerceAtLeast(0L) * 1_000L
        val endUs = if (edit.overlayEndMs == Long.MAX_VALUE) Long.MAX_VALUE else
            (edit.overlayEndMs - edit.startMs).coerceAtLeast(0L) * 1_000L
        val layers = overlays.mapIndexed { index, content ->
            val anchorY = (edit.overlayY +
                if (overlays.size == 2) (if (index == 0) 0.12f else -0.12f) else 0f)
                .coerceIn(-0.9f, 0.9f)
            object : TextOverlay() {
                override fun getText(presentationTimeUs: Long): SpannableString =
                    SpannableString(content)

                override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
                    StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(edit.overlayX, anchorY)
                        .setScale(edit.overlayScale, edit.overlayScale)
                        .setAlphaScale(
                            if (presentationTimeUs in startUs..endUs) 1f else 0f
                        )
                        .build()
            }
        }
        videoEffects += OverlayEffect(layers)
    }
    fun volumeEffect(level: Float): List<androidx.media3.common.audio.AudioProcessor> {
        if (level == 1f) return emptyList()
        val processor = ChannelMixingAudioProcessor()
        for (channels in 1..6) {
            processor.putChannelMixingMatrix(
                ChannelMixingMatrix.createForConstantPower(channels, channels)
                    .scaleBy(level)
            )
        }
        return listOf(processor)
    }
    val videoItem = EditedMediaItem.Builder(input)
        .setRemoveAudio(edit.muteOriginalAudio)
        .setEffects(Effects(
            if (edit.muteOriginalAudio) emptyList() else volumeEffect(edit.originalAudioVolume),
            videoEffects
        ))
        .build()
    val music = edit.musicUri?.takeIf(String::isNotBlank)
    val composition = if (music != null) {
        // Media3 1.8.0 uses the pre-1.9 sequence constructors.
        val videoSequence = EditedMediaItemSequence(listOf(videoItem))
        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(music))
            .setEffects(Effects(volumeEffect(edit.musicVolume), emptyList()))
            .build()
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
