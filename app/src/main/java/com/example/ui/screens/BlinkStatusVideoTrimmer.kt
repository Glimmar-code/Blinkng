package com.example.ui.screens

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.blinkng.shared.notification.BlinkStatusVideoTrimPolicy
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * On-device, no-transcode MP4 clip exporter. Starts at the next video keyframe,
 * so boundaries may vary slightly; never represents this as frame-exact.
 *
 * Source media remains untouched. The result is private app-cache content.
 */
internal object BlinkStatusVideoTrimmer {
    private const val MAX_SAMPLE_BYTES = 8 * 1024 * 1024
    private val videoMimes = setOf("video/avc", "video/hevc", "video/mp4v-es")

    fun durationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Cannot read video duration.")
        } finally {
            retriever.release()
        }
    }

    suspend fun export(
        context: Context,
        source: Uri,
        window: BlinkStatusVideoTrimPolicy.Window
    ): Uri = withContext(Dispatchers.IO) {
        require(window.durationMs in BlinkStatusVideoTrimPolicy.MIN_CLIP_MS..
            BlinkStatusVideoTrimPolicy.MAX_CLIP_MS) { "Unsupported video trim range." }

        val folder = File(context.cacheDir, "blink-status-clips").apply { mkdirs() }
        // This app owns only the derivatives in its private cache directory.
        folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.drop(12)?.forEach { it.delete() }
        val output = File(folder, "${UUID.randomUUID()}.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        var samplesWritten = 0
        try {
            extractor.setDataSource(context, source, null)
            val tracks = mutableMapOf<Int, Int>()
            var videoFound = false
            muxer = MediaMuxer(
                output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                // Do not silently emit audio-only results for unsupported video.
                if (mime.startsWith("video/") && mime !in videoMimes) {
                    throw IllegalArgumentException("This video codec cannot be trimmed without re-encoding.")
                }
                if (mime in videoMimes || mime == "audio/mp4a-latm") {
                    if (mime in videoMimes) videoFound = true
                    tracks[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }
            require(videoFound) { "No supported video track found." }
            muxer.start()
            started = true

            val startUs = window.startMs * 1_000L
            val endUs = window.endMs * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
            val buffer = ByteBuffer.allocateDirect(MAX_SAMPLE_BYTES)
            val info = MediaCodec.BufferInfo()
            var firstUs = -1L
            val lastByTrack = mutableMapOf<Int, Long>()
            while (true) {
                coroutineContext.ensureActive()
                val inputTrack = extractor.sampleTrackIndex
                if (inputTrack < 0) break
                val ptsUs = extractor.sampleTime
                if (ptsUs < 0 || ptsUs > endUs) break
                val outputTrack = tracks[inputTrack]
                if (outputTrack != null) {
                    if (extractor.sampleSize > MAX_SAMPLE_BYTES) {
                        throw IllegalArgumentException("Video sample exceeds safe memory limit.")
                    }
                    buffer.clear()
                    val count = extractor.readSampleData(buffer, 0)
                    if (count <= 0) break
                    if (firstUs < 0) firstUs = ptsUs
                    val newTime = (ptsUs - firstUs).coerceAtLeast(0)
                    // Muxer requires increasing timestamps for each encoded track.
                    val monotonicTime = maxOf(newTime, (lastByTrack[outputTrack] ?: -1L) + 1L)
                    lastByTrack[outputTrack] = monotonicTime
                    info.set(0, count, monotonicTime, extractor.sampleFlags)
                    muxer.writeSampleData(outputTrack, buffer, info)
                    samplesWritten++
                }
                if (!extractor.advance()) break
            }
            require(samplesWritten > 0) { "Video has no samples in this trim window." }
            muxer.stop()
            started = false
            require(output.length() in 1..BlinkStatusVideoTrimPolicy.MAX_INPUT_BYTES) {
                "Edited video is too large to share."
            }
            Uri.fromFile(output)
        } catch (failure: Exception) {
            output.delete()
            throw failure
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            // Failed muxing can leave an invalid partial file.
            if (samplesWritten == 0) output.delete()
        }
    }
}
