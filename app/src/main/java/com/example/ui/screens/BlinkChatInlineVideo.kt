package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.blinkng.shared.BlinkChatReferenceStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Chat video playback is opt-in: there is no network decoding/player instance until
 * the user taps Play. A player lives only while the visible bubble is playing and
 * is released on disposal. The lifecycle observer stops audio when BLINK backgrounds.
 */
@Composable
internal fun BlinkChatInlineVideo(
    url: String,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failed by remember(url) { mutableStateOf(false) }
    var retryVersion by remember(url) { androidx.compose.runtime.mutableIntStateOf(0) }
    var buffering by remember(url) { mutableStateOf(false) }
    var fullScreen by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(BlinkChatReferenceStyle.Canvas))
            .testTag("chat_inline_video"),
        contentAlignment = Alignment.Center,
    ) {
        if (isActive) {
            DisposableEffect(url) {
                onDispose { onDeactivate() }
            }
            val player = remember(url, retryVersion) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                    playWhenReady = true
                    prepare()
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        failed = true
                        buffering = false
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) failed = false
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }
            DisposableEffect(player, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) player.pause()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        controllerAutoShow = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = if (fullScreen) null else player },
                modifier = Modifier.fillMaxSize(),
            )
            if (!fullScreen) {
                IconButton(
                    onClick = { fullScreen = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .background(Color(0xAA06101C), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Fullscreen,
                        contentDescription = "Open video full screen",
                        tint = Color.White)
                }
            }
            if (fullScreen) {
                Dialog(
                    onDismissRequest = { fullScreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color(BlinkChatReferenceStyle.Canvas))
                            .testTag("chat_reference_fullscreen_video")
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    controllerAutoShow = true
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            Modifier.align(Alignment.TopStart)
                                .fillMaxWidth().background(Color(0x99060D16))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { fullScreen = false }) {
                                Icon(Icons.Default.Close, "Close full screen video",
                                    tint = Color.White)
                            }
                            Text("Video message", color = Color.White, fontSize = 15.sp,
                                modifier = Modifier.weight(1f))
                            BlinkChatDownloadAction(
                                url = url, kind = "video", extension = "mp4"
                            )
                        }
                    }
                }
            }
            if (buffering && !failed) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (!isActive) {
            AsyncImage(
                model = remember(url) {
                    ImageRequest.Builder(context).data(url)
                        .videoFrameMillis(1000L).build()
                },
                contentDescription = "Video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(Color(0x44060D16))
                    .clickable(onClickLabel = "Play video message") { onActivate() }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.PlayCircle, "Play video message in chat",
                    tint = Color.White, modifier = Modifier.size(48.dp))
                Text("Tap to play video", color = Color.White, fontSize = 12.sp)
            }
        }
        if (failed) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xE5111216)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Video unavailable", color = Color.White, fontSize = 13.sp)
                TextButton(onClick = {
                    failed = false
                    retryVersion += 1
                }) {
                    Text("Retry", color = Color.White)
                }
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No compatible video player", Toast.LENGTH_SHORT).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, "Cannot open this video", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Open with another app", color = Color.White)
                }
            }
        }
    }
}
