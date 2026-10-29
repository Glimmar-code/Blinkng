package com.example.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Derives 24 actual peak-amplitude bars from a voice note after the user plays it.
 * Private signed URLs are downloaded to the private cache temporarily; neither
 * the URL nor media bytes leave the device for analytics.
 */
internal object BlinkVoiceWaveformAnalyzer {
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).build()
    suspend fun analyze(cache: File, url: String): List<Int>? =
        withContext(Dispatchers.IO) {
            if (Uri.parse(url).scheme != "https") return@withContext null
            val tmp = File.createTempFile("voice-wave-", ".audio", cache)
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    if (body.contentLength() > 6_291_456) return@withContext null
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var count = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                count += read
                                if (count > 6_291_456) return@withContext null
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(tmp.absolutePath)
                    val track = (0 until extractor.trackCount).firstOrNull {
                        extractor.getTrackFormat(it)
                            .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    } ?: return@withContext null
                    extractor.selectTrack(track)
                    val format = extractor.getTrackFormat(track)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
                    val duration = if (format.containsKey(MediaFormat.KEY_DURATION))
                        format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
                    else 30_000_000L
                    val codec = MediaCodec.createDecoderByType(mime)
                    try {
                        codec.configure(format, null, null, 0)
                        codec.start()
                        val buckets = IntArray(24)
                        val info = MediaCodec.BufferInfo()
                        var inputEnd = false
                        var outputEnd = false
                        var count = 0
                        while (!outputEnd && count++ < 5000) {
                            if (!inputEnd) {
                                val inputId = codec.dequeueInputBuffer(8000L)
                                if (inputId >= 0) {
                                    val inputBuffer = codec.getInputBuffer(inputId) ?: break
                                    val size = extractor.readSampleData(inputBuffer, 0)
                                    if (size < 0) {
                                        codec.queueInputBuffer(inputId, 0, 0, 0,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        inputEnd = true
                                    } else {
                                        codec.queueInputBuffer(inputId, 0, size,
                                            extractor.sampleTime.coerceAtLeast(0L), 0)
                                        extractor.advance()
                                    }
                                }
                            }
                            val out = codec.dequeueOutputBuffer(info, 8000L)
                            if (out >= 0) {
                                val buf = codec.getOutputBuffer(out)
                                if (buf != null && info.size > 1) {
                                    val bin = ((info.presentationTimeUs * 24L) / duration)
                                        .toInt().coerceIn(0, 23)
                                    buf.position(info.offset)
                                    buf.limit(info.offset + info.size)
                                    buf.order(ByteOrder.LITTLE_ENDIAN)
                                    var max = buckets[bin]
                                    while (buf.remaining() >= 2) {
                                        val peak = kotlin.math.abs(buf.short.toInt())
                                        if (peak > max) max = peak
                                    }
                                    buckets[bin] = max
                                }
                                outputEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(out, false)
                            }
                        }
                        val peak = buckets.maxOrNull() ?: 0
                        if (peak <= 0) null
                        else buckets.map { value ->
                            (5 + 23.0 * kotlin.math.sqrt(value.toDouble() / peak))
                                .toInt().coerceIn(5, 28)
                        }
                    } finally { runCatching { codec.stop() }; codec.release() }
                } finally { extractor.release() }
            } catch (_: Exception) {
                null
            } finally { tmp.delete() }
        }
}
