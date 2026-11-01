package com.example.ui.screens

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.sqrt

data class VoiceRecording(val file: File, val durationMs: Long, val waveform: List<Float>)

/**
 * Thin MediaRecorder wrapper with Compose-observable state.
 * Needs RECORD_AUDIO permission to be granted BEFORE start() (the chat screen handles that).
 */
class ChatVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var segmentStart = 0L
    private var accumulated = 0L

    var isRecording by mutableStateOf(false); private set
    var isPaused by mutableStateOf(false); private set
    var elapsedMs by mutableStateOf(0L); private set
    val amplitudes = mutableStateListOf<Float>()

    fun start(): Boolean {
        cancel()
        val out = File(context.cacheDir, "blink_voice_${System.currentTimeMillis()}.m4a")
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            accumulated = 0L
            segmentStart = System.currentTimeMillis()
            amplitudes.clear()
            elapsedMs = 0L
            isPaused = false
            isRecording = true
            true
        } catch (e: Exception) {
            releaseQuietly()
            out.delete()
            false
        }
    }

    fun pause() {
        if (!isRecording || isPaused || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching {
            recorder?.pause()
            accumulated += System.currentTimeMillis() - segmentStart
            elapsedMs = accumulated
            isPaused = true
        }
    }

    fun resume() {
        if (!isRecording || !isPaused || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching {
            recorder?.resume()
            segmentStart = System.currentTimeMillis()
            isPaused = false
        }
    }

    /** Call ~12x per second while recording to update timer + waveform. */
    fun sample() {
        if (!isRecording || isPaused) return
        val raw = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        amplitudes += sqrt((raw / 32767f).coerceIn(0f, 1f)).coerceAtLeast(0.06f)
        elapsedMs = accumulated + (System.currentTimeMillis() - segmentStart)
    }

    /** Stops and returns the recording, or null if it was too short / failed. */
    fun stop(): VoiceRecording? {
        if (!isRecording) return null
        val duration = if (isPaused) accumulated else accumulated + (System.currentTimeMillis() - segmentStart)
        val f = file
        val ok = runCatching { recorder?.stop() }.isSuccess
        releaseQuietly()
        val wave = downsample(amplitudes.toList(), 40)
        reset()
        if (!ok || f == null || duration < 800L) { f?.delete(); return null }
        return VoiceRecording(f, duration, wave)
    }

    fun cancel() {
        if (recorder != null) runCatching { recorder?.stop() }
        releaseQuietly()
        file?.delete()
        reset()
    }

    private fun reset() {
        file = null; isRecording = false; isPaused = false; elapsedMs = 0L; accumulated = 0L
        amplitudes.clear()
    }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        recorder = null
    }

    private fun downsample(src: List<Float>, n: Int): List<Float> {
        if (src.isEmpty()) return List(n) { 0.1f }
        return List(n) { i ->
            val from = i * src.size / n
            val to = ((i + 1) * src.size / n).coerceAtLeast(from + 1).coerceAtMost(src.size)
            src.subList(from, to).average().toFloat()
        }
    }
}

/** Shared waveform: used for message bubbles (seekable) and live recording. */
@Composable
fun BlinkWaveform(
    levels: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier,
    playedColor: Color = BlinkChatColors.Accent,
    idleColor: Color = Color(0x66FFFFFF),
    bars: Int = 36,
    onSeek: ((Float) -> Unit)? = null,
) {
    val seekMod = if (onSeek != null) Modifier.pointerInput(Unit) {
        detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
    } else Modifier
    Canvas(modifier.then(seekMod)) {
        val gap = 2.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val lvl = if (levels.isEmpty()) 0.3f
            else levels[(i * levels.size / bars).coerceIn(0, levels.lastIndex)].coerceIn(0.1f, 1f)
            val h = size.height * lvl
            drawRoundRect(
                color = if (i / bars.toFloat() < progress) playedColor else idleColor,
                topLeft = Offset(i * (bw + gap), (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f),
            )
        }
    }
}

/** Composer replacement while recording: delete · dot + timer · live waveform · pause/resume · send. */
@Composable
fun ChatVoiceRecordingBar(
    recorder: ChatVoiceRecorder,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot",
    )
    val live = recorder.amplitudes.takeLast(36).let { tail ->
        List((36 - tail.size).coerceAtLeast(0)) { 0.06f } + tail
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BlinkCircleButton(Icons.Filled.Delete, "Cancel recording", onCancel, tint = BlinkChatColors.Danger)
        Row(
            Modifier.weight(1f).height(48.dp).clip(CircleShape)
                .background(BlinkChatColors.Panel).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(BlinkChatColors.Danger.copy(alpha = if (recorder.isPaused) 0.4f else pulse))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatDuration(recorder.elapsedMs), color = Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(12.dp))
            BlinkWaveform(
                levels = live, progress = 1f,
                modifier = Modifier.weight(1f).height(26.dp),
                playedColor = if (recorder.isPaused) BlinkChatColors.Muted else BlinkChatColors.Accent,
            )
        }
        BlinkCircleButton(
            if (recorder.isPaused) Icons.Filled.Mic else Icons.Filled.Pause,
            if (recorder.isPaused) "Resume" else "Pause",
            { if (recorder.isPaused) recorder.resume() else recorder.pause() },
        )
        BlinkCircleButton(Icons.Filled.Send, "Send voice message", onSend, container = BlinkChatColors.Accent)
    }
}
