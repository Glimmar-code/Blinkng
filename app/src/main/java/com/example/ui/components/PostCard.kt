package com.example.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.PollOption
import com.example.data.models.PostPoll
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay

@Composable
fun PostCard(
    post: FeedPost,
    isDark: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onOptionsClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onViewed: () -> Unit = {},
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showMedia by remember(post.id) { mutableStateOf(false) }
    var showVideoFullscreen by remember(post.id) { mutableStateOf(false) }
    var page by remember(post.id) { mutableIntStateOf(0) }
    var textExpanded by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(post.id) {
        onViewed()
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (post.isSponsored) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, null, tint = BlinkPink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(post.adLabel ?: "Sponsored", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorAvatar,
                    contentDescription = "Profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onProfileClick(post.author) }
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.author, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onProfileClick(post.author) })
                        if (post.isVerified || post.verificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Verified, null, tint = BlinkPurple, modifier = Modifier.size(15.dp))
                        }
                    }
                    Text("${post.timeAgo} • ${post.facultyTag}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOptionsClick) { Icon(Icons.Default.MoreHoriz, "Post options") }
            }

            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (textExpanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis
                )
                if (post.text.length > 300) {
                    Text(if (textExpanded) "Show less" else "See more", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 4.dp).clickable { textExpanded = !textExpanded })
                }
            }

            if (post.tags.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(post.tags.take(10)) { _, tag ->
                        AssistChip(onClick = {}, label = { Text("#$tag", fontSize = 10.sp) })
                    }
                }
            }

            post.poll?.let { poll ->
                PollCard(poll = poll, onVote = { option -> onVotePoll(post.id, option) })
            }

            val hasImages = post.images.isNotEmpty()
            val hasVideo = !post.videoUrl.isNullOrBlank()
            if (hasImages || hasVideo) {
                Spacer(Modifier.height(10.dp))
                if (hasImages) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp))) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            itemsIndexed(post.images) { index, image ->
                                AsyncImage(
                                    model = image,
                                    contentDescription = "Post image ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillParentMaxWidth().height(300.dp).clickable { page = index; showMedia = true }
                                )
                            }
                        }
                        if (post.images.size > 1) {
                            Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = CircleShape, color = Color.Black.copy(alpha = .55f)) {
                                Text("${page + 1}/${post.images.size}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }
                        IconButton(onClick = { showMedia = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = .55f)) { Icon(Icons.Default.Fullscreen, "Open image fullscreen", tint = Color.White, modifier = Modifier.padding(8.dp)) }
                        }
                    }
                } else if (hasVideo) {
                    VideoPreview(
                        url = post.videoUrl!!,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(330.dp).clip(RoundedCornerShape(14.dp)),
                        onFullscreen = { showVideoFullscreen = true }
                    )
                }
            }

            if (post.images.size > 1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(post.images.size.coerceAtMost(8)) { i ->
                        Box(Modifier.padding(2.dp).size(if (i == page) 7.dp else 5.dp).background(if (i == page) BlinkPink else MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text("${post.likes} likes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onLike) { Icon(if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (post.isLiked) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(3.dp)); Text("Like", fontSize = 10.sp) }
                TextButton(onClick = onComment) { Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(3.dp)); Text("${post.commentsCount}", fontSize = 10.sp) }
                TextButton(onClick = onBookmark) { Icon(if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(3.dp)); Text("Save", fontSize = 10.sp) }
                TextButton(onClick = onShare) { Icon(Icons.Default.Share, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(3.dp)); Text("${post.sharesCount}", fontSize = 10.sp) }
            }
        }
    }

    if (showMedia) {
        ImageFullscreenDialog(images = post.images, initialPage = page, onDismiss = { showMedia = false })
    }
    if (showVideoFullscreen && !post.videoUrl.isNullOrBlank()) {
        VideoFullscreenDialog(url = post.videoUrl!!, onDismiss = { showVideoFullscreen = false })
    }
}

@Composable
private fun PollCard(poll: PostPoll, onVote: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, null, tint = BlinkPink)
                Spacer(Modifier.width(7.dp))
                Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(9.dp))
            poll.options.forEach { option ->
                val total = poll.options.sumOf { it.votes }.coerceAtLeast(1)
                val percent = option.votes.toFloat() / total
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !poll.hasVoted && !option.isVotedByMe) { onVote(option.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option.text, Modifier.weight(1f), fontSize = 12.sp)
                            if (poll.hasVoted || option.isVotedByMe) Text("${(percent * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        if (poll.hasVoted || option.isVotedByMe) {
                            Spacer(Modifier.height(5.dp))
                            LinearProgressIndicator(progress = { percent }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Text("${poll.totalVotes} votes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun VideoPreview(url: String, modifier: Modifier, onFullscreen: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = true; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM; player = this@rememberPlayerHack(player) } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = .55f)) { Icon(Icons.Default.Fullscreen, "Fullscreen video", tint = Color.White, modifier = Modifier.padding(8.dp)) }
        }
    }
}

private fun rememberPlayerHack(player: ExoPlayer): ExoPlayer = player

@Composable
private fun ImageFullscreenDialog(images: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                LazyRow(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
                    itemsIndexed(images) { _, image ->
                        AsyncImage(model = image, contentDescription = "Fullscreen image", contentScale = ContentScale.Fit, modifier = Modifier.fillParentMaxWidth().fillMaxHeight())
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = .6f)) { Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.padding(9.dp)) }
                }
            }
        }
    }
}

@Composable
private fun VideoFullscreenDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                VideoPreview(url, Modifier.fillMaxSize(), onDismiss)
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = .6f)) { Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.padding(9.dp)) }
                }
            }
        }
    }
}
