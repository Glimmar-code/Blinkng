package com.example.ui.screens

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Small lifecycle-owned voice-note recorder.
 *
 * Files live in app cache until MessageMediaService uploads them to the private
 * conversation bucket. Cancelling always removes the local file.
 */
internal class ChatVoiceRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start(): Result<Unit> = runCatching {
        stopInternal(deleteFile = true)
        val folder = File(context.cacheDir, "chat_voice").apply { mkdirs() }
        val file = File(folder, "voice-${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val next = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        outputFile = file
        recorder = next
        startedAt = System.currentTimeMillis()
    }

    fun elapsedSeconds(): Int {
        if (!isRecording || startedAt <= 0L) return 0
        return ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(0)
    }

    fun stopAndKeep(): Result<Pair<Uri, Int>> = runCatching {
        val duration = elapsedSeconds()
        val file = outputFile ?: error("No voice recording is active.")
        val active = recorder ?: error("No voice recording is active.")
        try {
            active.stop()
        } finally {
            runCatching { active.reset() }
            runCatching { active.release() }
            recorder = null
            outputFile = null
            startedAt = 0L
        }
        require(file.exists() && file.length() > 0L) { "Voice recording is empty." }
        Uri.fromFile(file) to duration
    }

    fun cancel() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        val active = recorder
        recorder = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.reset() }
            runCatching { active.release() }
        }
        val file = outputFile
        outputFile = null
        startedAt = 0L
        if (deleteFile) runCatching { file?.delete() }
    }
}
