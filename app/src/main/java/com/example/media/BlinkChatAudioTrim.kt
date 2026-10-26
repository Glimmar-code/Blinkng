package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Cut supported AAC audio without transcoding or modifying the original. */
internal object BlinkChatAudioTrim {
    suspend fun export(
        context: Context,
        source: Uri,
        startMs: Long,
        endMs: Long
    ): Uri = withContext(Dispatchers.IO) {
        require(startMs >= 0 && endMs - startMs >= 500) {
            "Keep at least half a second of audio."
        }
        val folder = File(context.cacheDir, "blink-share").apply { mkdirs() }
        val output = File.createTempFile("blink-audio-trim-", ".m4a", folder)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var finished = false
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) ==
                    MediaFormat.MIMETYPE_AUDIO_AAC
            } ?: error("Trimming currently supports AAC/M4A audio. Send this file unedited.")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION) / 1000
            } else Long.MAX_VALUE
            require(startMs < durationMs && endMs <= durationMs) {
                "The selected audio range exceeds the recording."
            }
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxedTrack = muxer.addTrack(format)
            muxer.start()
            val maxSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceIn(8192, 4 * 1024 * 1024)
            } else 1024 * 1024
            val buffer = ByteBuffer.allocateDirect(maxSize)
            val info = MediaCodec.BufferInfo()
            val originUs = startMs * 1000
            var wrote = false
            while (true) {
                currentCoroutineContext().ensureActive()
                buffer.clear()
                val bytes = extractor.readSampleData(buffer, 0)
                if (bytes < 0) break
                val sampleUs = extractor.sampleTime
                if (sampleUs >= endMs * 1000) break
                if (sampleUs >= originUs && bytes > 0) {
                    info.offset = 0
                    info.size = bytes
                    info.presentationTimeUs = sampleUs - originUs
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxedTrack, buffer, info)
                    wrote = true
                }
                if (!extractor.advance()) break
            }
            require(wrote) { "No supported audio samples in the selected range." }
            // MP4/M4A metadata is finalized by stop(); validate only afterwards.
            muxer.stop()
            muxer.release()
            muxer = null
            com.blinkng.shared.BlinkChatAttachmentKind.VOICE.verify(
                "audio/mp4", output.length()
            )
            finished = true
            FileProvider.getUriForFile(
                context, "${context.packageName}.chatfiles", output
            )
        } finally {
            extractor.release()
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!finished) output.delete()
        }
    }
}
