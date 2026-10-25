package com.example.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Plays received private voice notes in the conversation without sharing the
 * authenticated/signed storage URL with another application.
 *
 * MediaPlayer uses asynchronous prepare so a slow network doesn't block Compose.
 * Only a user tap creates a player, and changing conversations releases it.
 */
@Composable
internal fun BlinkChatVoicePlayer(
    url: String?,
    durationLabel: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var preparing by remember(url) { mutableStateOf(false) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var elapsed by remember(url) { mutableIntStateOf(0) }
    var duration by remember(url) { mutableIntStateOf(0) }
    DisposableEffect(url) {
        onDispose {
            player?.let { runCatching { it.reset(); it.release() } }
            player = null
        }
    }
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            val p = player
            if (p == null) break
            elapsed = runCatching { p.currentPosition }.getOrDefault(0)
            duration = runCatching { p.duration.coerceAtLeast(1) }.getOrDefault(duration)
            delay(350L)
        }
    }
    Row(
        modifier = modifier.testTag("chat_voice_playback"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            enabled = url?.startsWith("https://") == true && !preparing && !failed,
            onClick = {
                val live = player
                if (live != null && ready) {
                    runCatching {
                        if (live.isPlaying) {
                            live.pause()
                            isPlaying = false
                        } else {
                            live.start()
                            isPlaying = true
                        }
                    }.onFailure {
                        isPlaying = false
                        failed = true
                    }
                } else if (live == null && !url.isNullOrBlank()) {
                    val next = MediaPlayer()
                    player = next
                    preparing = true
                    next.setOnPreparedListener { current ->
                        if (player === current) {
                            duration = runCatching { current.duration }.getOrDefault(0)
                            preparing = false
                            ready = true
                            runCatching { current.start(); isPlaying = true }
                                .onFailure { isPlaying = false; failed = true }
                        }
                    }
                    next.setOnCompletionListener {
                        isPlaying = false
                        elapsed = 0
                    }
                    next.setOnErrorListener { _, _, _ ->
                        preparing = false
                        isPlaying = false
                        failed = true
                        true
                    }
                    runCatching {
                        next.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        next.setDataSource(url)
                        next.prepareAsync()
                    }.onFailure {
                        preparing = false
                        failed = true
                        runCatching { next.release() }
                        player = null
                    }
                }
            }
        ) {
            Icon(
                when {
                    failed -> Icons.Rounded.ErrorOutline
                    isPlaying -> Icons.Rounded.PauseCircle
                    else -> Icons.Rounded.PlayCircle
                },
                contentDescription = when {
                    failed -> "Voice note failed to play"
                    isPlaying -> "Pause voice note"
                    preparing -> "Loading voice note"
                    url.isNullOrBlank() -> "Voice note unavailable"
                    else -> "Play voice note"
                },
                tint = tint,
                modifier = Modifier.size(29.dp)
            )
        }
        Column(Modifier.widthIn(min = 106.dp, max = 170.dp)) {
            val progress = if (duration > 0) {
                (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = tint,
                trackColor = tint.copy(alpha = .22f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    failed -> "Unable to play · refresh chat"
                    preparing -> "Loading voice note…"
                    url.isNullOrBlank() -> "Voice note unavailable"
                    duration > 0 -> "${elapsed / 1000}s / ${duration / 1000}s"
                    else -> durationLabel.ifBlank { "Voice note" }
                },
                color = tint.copy(alpha = .85f),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
