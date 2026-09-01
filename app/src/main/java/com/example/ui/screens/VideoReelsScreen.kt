package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.models.FeedPost
import com.example.ui.components.formatNumber
import com.example.ui.theme.BlinkPink

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun VideoReelsScreen(reels: List<FeedPost>, isDark: Boolean, onLike: (String) -> Unit, onComment: (String) -> Unit, onBookmark: (String) -> Unit, onShare: (String) -> Unit, onProfileClick: (String) -> Unit, onBackToPosts: () -> Unit, onHomeClick: () -> Unit = onBackToPosts, onConnectClick: () -> Unit = {}, onGameClick: () -> Unit = {}) {
    if (reels.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No reels yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp)); Button(onClick = onBackToPosts) { Text("Back to feed") }
            }
        }
        return
    }
    val pager = rememberPagerState(pageCount = { reels.size })
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            ReelPage(reels[index], index == pager.currentPage, onLike, onComment, onBookmark, onShare, onProfileClick)
        }
        ReelsTopNavigation(onPosts = onBackToPosts, onConnect = onConnectClick, onGame = onGameClick)
        Text("Reels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 45.dp))
    }
}

@Composable
private fun ReelPage(reel: FeedPost, isActive: Boolean, onLike: (String) -> Unit, onComment: (String) -> Unit, onBookmark: (String) -> Unit, onShare: (String) -> Unit, onProfileClick: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val url = reel.videoUrl?.trim()
        if (!url.isNullOrBlank()) ReelVideo(url, isActive) else Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Text("Video unavailable", color = Color.White) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)))
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncAvatar(reel.authorAvatar) { onProfileClick(reel.author) }
            Spacer(Modifier.height(10.dp)); ReelAction(Icons.Default.Favorite, formatNumber(reel.likes), BlinkPink) { onLike(reel.id) }
            Spacer(Modifier.height(10.dp)); ReelAction(Icons.Default.ChatBubble, formatNumber(reel.commentsCount), Color.White) { onComment(reel.id) }
            Spacer(Modifier.height(10.dp)); ReelAction(Icons.Default.Bookmark, "Save", Color.White) { onBookmark(reel.id) }
            Spacer(Modifier.height(10.dp)); ReelAction(Icons.Default.Share, "Share", Color.White) { onShare(reel.id) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 85.dp, bottom = 35.dp)) {
            Text("@${reel.author}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (reel.text.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(reel.text, color = Color.White, fontSize = 13.sp, maxLines = 4) }
        }
    }
}

@Composable
private fun ReelVideo(url: String, isActive: Boolean) {
    val context = LocalContext.current
    var playbackError by remember(url) { mutableStateOf<String?>(null) }
    var buffering by remember(url) { mutableStateOf(true) }
    val player = remember(url) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1f
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE }
                    override fun onPlayerError(error: PlaybackException) { buffering = false; playbackError = error.errorCodeName }
                })
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = isActive
            }
    }
    LaunchedEffect(isActive, player) {
        player.playWhenReady = isActive
        if (isActive) player.play() else player.pause()
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = true; controllerAutoShow = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; keepScreenOn = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (buffering && playbackError == null) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
        if (playbackError != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Unable to play this reel", color = Color.White, fontWeight = FontWeight.Bold)
                Text(playbackError!!, color = Color.LightGray, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { playbackError = null; buffering = true; player.stop(); player.clearMediaItems(); player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.playWhenReady = isActive; if (isActive) player.play() }) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun AsyncAvatar(url: String, onClick: () -> Unit) {
    coil.compose.AsyncImage(model = url, contentDescription = "Creator", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(50.dp).clip(CircleShape).clickable { onClick() })
}

@Composable
private fun ReelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, text, tint = tint, modifier = Modifier.size(28.dp)) }
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReelsTopNavigation(onPosts: () -> Unit, onConnect: () -> Unit, onGame: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 82.dp, start = 18.dp, end = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ReelNavButton("Post", false, onPosts)
        Spacer(Modifier.width(6.dp))
        ReelNavButton("Reel", true) {}
        Spacer(Modifier.width(6.dp))
        ReelNavButton("Connect", false, onConnect)
        Spacer(Modifier.width(6.dp))
        ReelNavButton("Game", false, onGame)
    }
}

@Composable
private fun ReelNavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.clickable { onClick() }, shape = CircleShape, color = if (selected) Color.White else Color.White.copy(alpha = 0.14f)) {
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
    }
}
