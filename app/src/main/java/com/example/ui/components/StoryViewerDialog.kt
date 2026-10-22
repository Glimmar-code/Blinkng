package com.example.ui.components

import com.example.data.local.rememberPersistentTextState
import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.blinkng.shared.notification.BlinkStoryLifecycle
import com.example.data.models.Story
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkRed
import kotlinx.coroutines.delay

/**
 * Fullscreen Interactive Story Viewer Dialog.
 * Allows users to:
 * - Watch stories by different creators with animated timed progress bars.
 * - Tap left to go to previous story, tap right to go to next story.
 * - Long-press / hold to pause the story progress.
 * - Like stories with heart animation & counter increment.
 * - Send quick emoji reactions (🔥, ❤️, 👏, 😂, ⚡, 🙌) that float up with feedback.
 * - Send direct DM message replies to the story creator.
 * - Navigate to creator profile.
 */
@Composable
fun StoryViewerDialog(
    stories: List<Story>,
    initialStory: Story,
    currentUserId: String = "you",
    onDismiss: () -> Unit,
    onStoryViewed: (String) -> Unit = {},
    onLikeStory: (String) -> Unit = {},
    onReactStory: (storyId: String, emoji: String) -> Unit = { _, _ -> },
    onReplyStory: (storyUsername: String, replyText: String) -> Unit = { _, _ -> },
    onProfileClick: (String) -> Unit = {}
) {
    // Synthetic "Your Story" cards are not media; actual owner stories are viewable.
    val storyList = remember(stories) {
        stories.filter {
            it.id != "story_me" &&
                (it.storyImage.isNotBlank() || it.caption.isNotBlank()) &&
                BlinkStoryLifecycle.isActive(it.createdAt, true)
        }
    }
    if (storyList.isEmpty()) return

    var currentIndex by rememberSaveable(initialStory.id) {
        val idx = storyList.indexOfFirst { it.id == initialStory.id || it.username == initialStory.username }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    val currentStory = storyList.getOrNull(currentIndex) ?: storyList.first()

    // Mark current story as viewed
    LaunchedEffect(currentStory.id) {
        onStoryViewed(currentStory.id)
    }

    // Story timer progress (0f to 1f)
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var replyText by rememberPersistentTextState(key = "com/example/ui/components/StoryViewerDialog.kt:replyText:1")
    var floatingReaction by remember { mutableStateOf<String?>(null) }
    var showQuickReactions by rememberSaveable { mutableStateOf(false) }
    val viewerLifecycleOwner = LocalLifecycleOwner.current
    var viewerForeground by remember(viewerLifecycleOwner) {
        mutableStateOf(viewerLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(viewerLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewerForeground = true
                Lifecycle.Event.ON_STOP -> viewerForeground = false
                else -> Unit
            }
        }
        viewerLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { viewerLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Floating reaction animation effect
    LaunchedEffect(floatingReaction) {
        if (floatingReaction != null) {
            delay(1200)
            floatingReaction = null
        }
    }

    // Pause/resume must not reset progress. Video duration follows its real runtime.
    var videoDurationMs by remember(currentStory.id) { mutableLongStateOf(5_000L) }
    var videoReady by remember(currentStory.id) { mutableStateOf(!currentStory.isVideo) }
    var showInsights by rememberSaveable(currentStory.id) { mutableStateOf(false) }
    LaunchedEffect(currentStory.id) {
        progress = 0f
        var elapsedMs = 0L
        val stepMs = 50L
        while (progress < 1f) {
            delay(stepMs)
            if (!isPaused && viewerForeground && videoReady && !showInsights) elapsedMs += stepMs
            val totalMs = if (currentStory.isVideo) videoDurationMs.coerceAtLeast(1_000L) else 5_000L
            progress = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
        }

        if (!isPaused && progress >= 1f) {
            if (currentIndex < storyList.size - 1) {
                currentIndex += 1
            } else {
                onDismiss()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("story_viewer_dialog")
    ) {
        // Story Background Image / Media
        Crossfade(targetState = currentStory.id, animationSpec = tween(180), label = "storyMediaReveal") { storyId ->
            val displayedStory = storyList.firstOrNull { it.id == storyId } ?: currentStory
            if (displayedStory.isVideo && displayedStory.storyImage.isNotBlank()) {
                BlinkStoryVideoPlayer(
                    story = displayedStory,
                    paused = isPaused || !viewerForeground || showInsights || displayedStory.id != currentStory.id,
                    onDuration = { if (displayedStory.id == currentStory.id) videoDurationMs = it },
                    onReady = { if (displayedStory.id == currentStory.id) videoReady = it }
                )
            } else if (displayedStory.storyImage.isNotBlank()) {
            AsyncImage(
                model = displayedStory.storyImage,
                contentDescription = "Story by ${displayedStory.displayLabel}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback rich gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                // Auth-style purple/black fallback only in Dark Mode.
                                if (MaterialTheme.colorScheme.background == Color.Black) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color(0xFF1E140F),
                                if (MaterialTheme.colorScheme.background == Color.Black) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFF2C1810),
                                if (MaterialTheme.colorScheme.background == Color.Black) MaterialTheme.colorScheme.background else Color(0xFF0F0E0C)
                            )
                        )
                    )
            )
        }

        }

        // Dark gradients top and bottom for readable controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Gesture Detection Overlay (Tap left/right, hold to pause)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp, top = 80.dp)
        ) {
            // Left half: previous story
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = {
                                if (currentIndex > 0) {
                                    currentIndex -= 1
                                } else {
                                    progress = 0f
                                }
                            }
                        )
                    }
            )

            // Right half: next story
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = {
                                if (currentIndex < storyList.size - 1) {
                                    currentIndex += 1
                                } else {
                                    onDismiss()
                                }
                            }
                        )
                    }
            )
        }

        // Floating Reaction animation badge
        floatingReaction?.let { emoji ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = emoji,
                        fontSize = 54.sp,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }

        // Header controls (Segments bar + User Info + Close Button)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            // Segmented Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                storyList.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentIndex -> 1f
                        index == currentIndex -> progress
                        else -> 0f
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(segmentProgress)
                                .background(Color.White)
                        )
                    }
                }
            }

            // User Info & Story Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onDismiss()
                            onProfileClick(currentStory.username)
                        }
                ) {
                    AsyncImage(
                        model = currentStory.avatar,
                        error = painterResource(R.drawable.ic_default_profile),
                        fallback = painterResource(R.drawable.ic_default_profile),
                        contentDescription = currentStory.displayLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentStory.displayLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            if (currentStory.verificationBadge != VerificationBadge.NONE) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerifiedMark(badge = currentStory.verificationBadge, size = 11.dp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${currentStory.timeAgo}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        if (currentStory.faculty.isNotBlank()) {
                            Text(
                                text = "${currentStory.faculty} • ${currentStory.university}",
                                color = BlinkGold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Story Index counter badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1}/${storyList.size}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Story",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Caption Overlay (if available)
        if (currentStory.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = currentStory.caption,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Bottom Interaction Bar (Reply field, Reaction quick tray, Like button)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (currentStory.isUser) {
                OutlinedButton(
                    onClick = { showInsights = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Viewers & Likes")
                }
            } else {
            // Quick Emojis Row (Togglable or permanent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val reactionEmojis = listOf("🔥", "❤️", "👏", "😂", "⚡", "🙌")
                reactionEmojis.forEach { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                floatingReaction = emoji
                                onReactStory(currentStory.id, emoji)
                            }
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Input Row: Reply to Story + Heart Like Button + Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reply Text Field
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        TextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = {
                                Text(
                                    text = "Send message to ${currentStory.displayLabel}...",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.5.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        if (replyText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onReplyStory(currentStory.username, replyText.trim())
                                    replyText = ""
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send message",
                                    tint = BlinkGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Heart Like Button
                IconButton(
                    onClick = {
                        onLikeStory(currentStory.id)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = if (currentStory.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like story",
                        tint = if (currentStory.isLiked) BlinkRed else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            }
        }
    }
    if (showInsights && currentStory.isUser) {
        BlinkStoryInsightsDialog(
            storyId = currentStory.id,
            onDismiss = { showInsights = false },
            onProfileClick = onProfileClick
        )
    }
}

/**
 * The player is scoped to a single status and releases codecs on dismissal.
 * Backgrounding the app always pauses playback; this is not a looped Reel.
 */
@Composable
private fun BlinkStoryVideoPlayer(
    story: Story,
    paused: Boolean,
    onDuration: (Long) -> Unit,
    onReady: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPaused by rememberUpdatedState(paused)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnDuration by rememberUpdatedState(onDuration)
    var mediaError by remember(story.id) { mutableStateOf(false) }
    val player = remember(story.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(story.storyImage))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                latestOnReady(mediaError ||
                    playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED)
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                mediaError = true
                latestOnDuration(5_000L)
                latestOnReady(true)
            }
        }
        player.addListener(listener)
        latestOnReady(player.playbackState == Player.STATE_READY)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (!latestPaused) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, paused, lifecycleOwner) {
        if (!paused && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            player.play()
        } else {
            player.pause()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            val durationMs = player.duration
            if (durationMs > 0 && durationMs != androidx.media3.common.C.TIME_UNSET) {
                onDuration(durationMs)
                break
            }
            delay(200)
        }
    }
    if (mediaError) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("This video couldn't load. Moving to the next story.",
                color = Color.White, modifier = Modifier.padding(20.dp))
        }
    } else {
        AndroidView(
            factory = { androidContext ->
                PlayerView(androidContext).apply {
                    this.player = player
                    useController = false
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
    }
}
