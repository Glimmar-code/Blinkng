package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.PostPoll
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

@Composable
fun PostCard(post: FeedPost, isDark: Boolean, onLike: () -> Unit, onComment: () -> Unit, onBookmark: () -> Unit, onShare: () -> Unit, onOptionsClick: () -> Unit, onProfileClick: (String) -> Unit, onViewed: () -> Unit = {}, onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> }, isAuthor: Boolean = false, onDelete: () -> Unit = {}, modifier: Modifier = Modifier) {
    var showImageFullscreen by remember(post.id) { mutableStateOf(false) }
    var showVideoFullscreen by remember(post.id) { mutableStateOf(false) }
    var imagePage by remember(post.id) { mutableIntStateOf(0) }
    var expanded by remember(post.id) { mutableStateOf(false) }
    var likedPulse by remember(post.id) { mutableStateOf(false) }
    val likeScale by animateFloatAsState(if (likedPulse) 1.16f else 1f, spring(), label = "postLikeScale")
    LaunchedEffect(likedPulse) { if (likedPulse) { kotlinx.coroutines.delay(140); likedPulse = false } }
    LaunchedEffect(post.id) { onViewed() }
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White), elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            if (post.isSponsored) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Campaign, contentDescription = null, tint = BlinkPink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(post.adLabel ?: "Sponsored", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = post.authorAvatar, contentDescription = "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onProfileClick(post.author) })
                Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(post.author, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onProfileClick(post.author) }); if (post.isVerified || post.verificationBadge != VerificationBadge.NONE) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Verified, contentDescription = null, tint = BlinkPurple, modifier = Modifier.size(15.dp)) } }
                    Text("${post.timeAgo} • ${post.facultyTag}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOptionsClick) { Icon(Icons.Default.MoreHoriz, "Post options") }
            }
            if (post.text.isNotBlank()) { Text(post.text, modifier = Modifier.padding(horizontal = 14.dp), fontSize = 14.sp, lineHeight = 20.sp, maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis); if (post.text.length > 300) Text(if (expanded) "Show less" else "See more", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 4.dp).clickable { expanded = !expanded }) }
            post.poll?.let { PollCard(it) { optionId -> onVotePoll(post.id, optionId) } }
            if (post.images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp))) {
                    LazyRow(Modifier.fillMaxWidth()) { itemsIndexed(post.images) { index, image -> AsyncImage(model = image, contentDescription = "Post image ${index + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillParentMaxWidth().height(300.dp).clickable { imagePage = index; showImageFullscreen = true }) } }
                    if (post.images.size > 1) Surface(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp), shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) { Text("${imagePage + 1}/${post.images.size}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
                }
            }
            post.videoUrl?.takeIf { it.isNotBlank() && post.isReel }?.let { url ->
                Spacer(Modifier.height(10.dp)); VideoPreview(url, Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(330.dp).clip(RoundedCornerShape(14.dp))) { showVideoFullscreen = true }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Visibility, contentDescription = "Views", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${post.viewsCount}", fontSize = 10.sp)
                }
                TextButton(onClick = { likedPulse = true; onLike() }, modifier = Modifier.scale(likeScale)) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLiked) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("${post.likes}", fontSize = 10.sp)
                }
                TextButton(onClick = onComment) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${post.commentsCount}", fontSize = 10.sp)
                }
                TextButton(onClick = onBookmark) {
                    Icon(
                        imageVector = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(if (post.isBookmarked) "Saved" else "Save", fontSize = 10.sp)
                }
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${post.sharesCount}", fontSize = 10.sp)
                }
                if (isAuthor) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete your post",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
    if (showImageFullscreen) ImageFullscreenDialog(post.images, imagePage) { showImageFullscreen = false }
    if (showVideoFullscreen && !post.videoUrl.isNullOrBlank() && post.isReel) VideoFullscreenDialog(post.videoUrl!!, { showVideoFullscreen = false })
}

@Composable
private fun PollCard(poll: PostPoll, onVote: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Poll, null, tint = BlinkPink); Spacer(Modifier.width(7.dp)); Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.height(8.dp))
            poll.options.forEach { option ->
                val total = poll.options.sumOf { it.votes }.coerceAtLeast(1); val progress = option.votes.toFloat() / total
                Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !poll.hasVoted && !option.isVotedByMe) { onVote(option.id) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(11.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(option.text, Modifier.weight(1f), fontSize = 12.sp); if (poll.hasVoted || option.isVotedByMe) Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 11.sp) }; if (poll.hasVoted || option.isVotedByMe) { Spacer(Modifier.height(5.dp)); LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()) } }
                }
            }
            Text("${poll.totalVotes} votes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoPreview(url: String, modifier: Modifier, onFullscreen: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(url)); repeatMode = Player.REPEAT_MODE_ONE; prepare(); playWhenReady = false } }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
        IconButton(onClick = { player.play(); onFullscreen() }, Modifier.align(Alignment.TopEnd).padding(8.dp)) { Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) { Icon(imageVector = Icons.Default.Fullscreen, contentDescription = "Fullscreen video", tint = Color.White, modifier = Modifier.padding(8.dp)) } }
    }
}

@Composable
private fun ImageFullscreenDialog(images: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    )
    var downwardDrag by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0f) {
                                downwardDrag += dragAmount
                                if (downwardDrag > 140f) onDismiss()
                            } else {
                                downwardDrag = (downwardDrag + dragAmount).coerceAtLeast(0f)
                            }
                        },
                        onDragEnd = { downwardDrag = 0f },
                        onDragCancel = { downwardDrag = 0f }
                    )
                },
            color = Color.Black
        ) {
            Box(Modifier.fillMaxSize()) {
                LazyRow(state = state, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(images) { _, image ->
                        AsyncImage(
                            model = image,
                            contentDescription = "Fullscreen image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillParentMaxWidth().fillMaxHeight()
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoFullscreenDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) { Box(Modifier.fillMaxSize()) { VideoPreview(url, Modifier.fillMaxSize(), onDismiss); IconButton(onClick = onDismiss, Modifier.align(Alignment.TopEnd).padding(12.dp)) { Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) { Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.padding(9.dp)) } } } } }
}
