package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import com.example.performance.rememberBlinkReduceMotion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.blinkng.shared.BlinkChannelUpdate
import com.example.data.repository.BlinkChannelMediaService
import kotlin.math.abs

/**
 * Full-screen, on-demand channel media viewer.
 * The signed object URL is obtained only when media is opened.
 * No signed URLs are cached after leaving a private channel.
 */
@Composable
fun BlinkChannelMediaViewer(
    updates: List<BlinkChannelUpdate>,
    initialUpdateId: String,
    onDismiss: () -> Unit
) {
    val gallery = remember(updates) {
        updates.filter { it.mediaKind == "image" || it.mediaKind == "video" || it.mediaKind == "audio" }
    }
    var current by remember(initialUpdateId) {
        mutableIntStateOf(gallery.indexOfFirst { it.id == initialUpdateId }.coerceAtLeast(0))
    }
    val entry = gallery.getOrNull(current)
    if (entry == null) return
    var url by remember(entry.id) { mutableStateOf<String?>(null) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    var loading by remember(entry.id) { mutableStateOf(true) }
    var zoom by remember(entry.id) { mutableFloatStateOf(1f) }
    var x by remember(entry.id) { mutableFloatStateOf(0f) }
    var y by remember(entry.id) { mutableFloatStateOf(0f) }
    var verticalDrag by remember(entry.id) { mutableFloatStateOf(0f) }
    var horizontalDrag by remember(entry.id) { mutableFloatStateOf(0f) }
    val reducedMotion = rememberBlinkReduceMotion()
    val imageReveal = remember(entry.id) { Animatable(0f) }

    LaunchedEffect(entry.id, loading, url, error, reducedMotion) {
        if (loading || error != null || url == null) {
            imageReveal.snapTo(0f)
        } else if (reducedMotion) {
            imageReveal.snapTo(1f)
        } else {
            imageReveal.animateTo(1f, animationSpec = tween(190))
        }
    }

    LaunchedEffect(entry.id, entry.mediaPath) {
        loading = true
        try {
            url = entry.mediaPath?.let { path -> BlinkChannelMediaService.signedUrl(path) }
            if (url.isNullOrBlank()) error = "This attachment is not available."
        } catch (e: Exception) {
            error = "Could not open this attachment."
        } finally { loading = false }
    }
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) { Text("‹ Back", color = Color.White) }
                Text("${current + 1} / ${gallery.size}", color = Color.White)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val signed = url
                when {
                    loading -> CircularProgressIndicator()
                    error != null || signed == null ->
                        Text(error ?: "Unavailable media", color = Color.White)
                    entry.mediaKind == "image" -> {
                        AsyncImage(
                            model = signed,
                            contentDescription = "Full-screen Channel picture",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clipToBounds()
                                .pointerInput(entry.id) {
                                    detectTransformGestures { _, pan, scale, _ ->
                                        zoom = (zoom * scale).coerceIn(1f, 4f)
                                        if (zoom > 1f) {
                                            x += pan.x
                                            y += pan.y
                                        } else { x = 0f; y = 0f }
                                    }
                                }
                                .pointerInput(entry.id, zoom) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            if (zoom <= 1f && abs(verticalDrag) > 180f) onDismiss()
                                            else if (zoom <= 1f && abs(horizontalDrag) > 130f) {
                                                val next = if (horizontalDrag < 0) current + 1 else current - 1
                                                if (next in gallery.indices) current = next
                                            }
                                            verticalDrag = 0f
                                            horizontalDrag = 0f
                                        },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            if (zoom <= 1f) {
                                                verticalDrag += drag.y
                                                horizontalDrag += drag.x
                                            }
                                        }
                                    )
                                }
                                .graphicsLayer(
                                    scaleX = zoom, scaleY = zoom,
                                    translationX = x, translationY = y,
                                    alpha = imageReveal.value
                                )
                        )
                    }
                    entry.mediaKind == "video" -> BlinkChannelVideoPlayer(signed)
                    else -> BlinkChannelAudioPlayer(signed)
                }
            }
            if (gallery.size > 1) {
                Row(Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { current-- }, enabled = current > 0) {
                        Text("‹ Previous", color = Color.White)
                    }
                    TextButton(onClick = { current++ }, enabled = current < gallery.lastIndex) {
                        Text("Next ›", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlinkChannelVideoPlayer(uri: String) {
    val context = LocalContext.current
    val video = remember(uri) { VideoView(context) }
    DisposableEffect(video) { onDispose { video.stopPlayback() } }
    AndroidView(
        factory = {
            video.apply {
                setVideoURI(Uri.parse(uri))
                setOnPreparedListener { media ->
                    media.isLooping = false
                    start()
                }
                setMediaController(android.widget.MediaController(context).apply {
                    setAnchorView(video)
                })
            }
        },
        modifier = Modifier.fillMaxWidth().height(460.dp)
    )
}

@Composable
private fun BlinkChannelAudioPlayer(uri: String) {
    var playing by remember(uri) { mutableStateOf(false) }
    var ready by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) { MediaPlayer() }
    DisposableEffect(player) {
        player.setOnPreparedListener { ready = true }
        player.setOnCompletionListener { playing = false }
        player.setOnErrorListener { _, _, _ -> playing = false; true }
        runCatching {
            player.setDataSource(uri)
            player.prepareAsync()
        }
        onDispose { runCatching { player.release() } }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Channel voice / audio", color = Color.White)
        Button(enabled = ready, onClick = {
            if (playing) player.pause() else player.start()
            playing = !playing
        }) { Text(if (playing) "Pause" else "Play") }
    }
}
