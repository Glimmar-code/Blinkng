package com.example.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer

/**
 * Visibility-controlled, muted reel preview for Search.
 * The parent decides visibility from LazyListState so off-screen players are paused.
 */
@Composable
internal fun SearchMutedAutoplayPreview(
    videoUrl: String,
    autoplay: Boolean,
    matchedMomentMs: Int?,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
        }
    }

    LaunchedEffect(player, matchedMomentMs) {
        matchedMomentMs?.takeIf { it > 0 }?.let { player.seekTo(it.toLong()) }
    }

    LaunchedEffect(player, autoplay) {
        player.playWhenReady = autoplay
        if (autoplay) player.play() else player.pause()
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = player },
        )
    }
}
