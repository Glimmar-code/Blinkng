package com.example.ui.components

import androidx.compose.foundation.border

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.Story
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay

@Composable
fun StoryBar(
    stories: List<Story>,
    userAvatar: String,
    onAddStory: () -> Unit,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleStories = remember(stories) {
        stories.filter { !it.isUser }
    }

    val listState = rememberLazyListState()

    var showStoryHeader by rememberSaveable {
        mutableStateOf(true)
    }

    var pressedStoryId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var showNewBadge by rememberSaveable {
        mutableStateOf(
            visibleStories.any { it.hasUnseen }
        )
    }

    LaunchedEffect(pressedStoryId) {
        if (pressedStoryId != null) {
            delay(250)
            pressedStoryId = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    280,
                    easing = FastOutSlowInEasing
                )
            )
    ) {

        // ------------------------------------------------------------
        // PREMIUM STORY HEADER
        // ------------------------------------------------------------

        androidx.compose.animation.AnimatedVisibility(
            visible = showStoryHeader,
            enter = fadeIn() + slideInHorizontally(
                initialOffsetX = { -20 }
            ),
            exit = fadeOut()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 14.dp,
                        top = 7.dp,
                        bottom = 2.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {

                    StoryHeaderIcon()

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Column {

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "Stories",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(
                                modifier = Modifier.width(5.dp)
                            )

                            if (showNewBadge) {

                                Surface(
                                    shape = RoundedCornerShape(
                                        100.dp
                                    ),
                                    color = BlinkPink
                                ) {

                                    Text(
                                        text = "NEW",
                                        color = Color.White,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp,
                                            vertical = 3.dp
                                        )
                                    )
                                }
                            }
                        }

                        Text(
                            text = when {
                                visibleStories.isEmpty() ->
                                    "Be the first to share"

                                visibleStories.count {
                                    it.hasUnseen
                                } == 1 ->
                                    "1 new story"

                                else ->
                                    "${visibleStories.count { it.hasUnseen }} new stories"
                            },
                            fontSize = 9.5.sp,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        )
                    }
                }

                if (visibleStories.isNotEmpty()) {

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme
                            .colorScheme
                            .surfaceVariant
                            .copy(alpha = 0.55f),
                        modifier = Modifier.clickable {
                            showNewBadge = false
                        }
                    ) {

                        Row(
                            modifier = Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 6.dp
                            ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text = "${visibleStories.size}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(
                                modifier = Modifier.width(4.dp)
                            )

                            Icon(
                                imageVector =
                                    Icons.Default.KeyboardArrowRight,
                                contentDescription = "View all stories",
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------
        // STORY RAIL
        // ------------------------------------------------------------

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = 14.dp,
                vertical = 8.dp
            ),
            horizontalArrangement =
                Arrangement.spacedBy(11.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("story_bar")
        ) {

            // ========================================================
            // ADD YOUR STORY
            // ========================================================

            item(
                key = "your_story"
            ) {

                AddStoryCard(
                    userAvatar = userAvatar,
                    onClick = {
                        showNewBadge = false
                        onAddStory()
                    }
                )
            }

            // ========================================================
            // OTHER STORIES
            // ========================================================

            items(
                items = visibleStories,
                key = { story ->
                    story.username
                }
            ) { story ->

                PremiumStoryItem(
                    story = story,
                    pressed = pressedStoryId == story.username,
                    onPressed = {
                        pressedStoryId = story.username
                    },
                    onClick = {
                        showNewBadge = false
                        onStoryClick(story)
                    }
                )
            }
        }

        // ------------------------------------------------------------
        // BOTTOM STORY HINT
        // ------------------------------------------------------------

        androidx.compose.animation.AnimatedVisibility(
            visible =
                visibleStories.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 3.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(
                            RoundedCornerShape(100.dp)
                        )
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    BlinkPink,
                                    BlinkPurple
                                )
                            )
                        )
                )

                Spacer(
                    modifier = Modifier.width(7.dp)
                )

                Text(
                    text = "Tap a story to watch",
                    fontSize = 8.5.sp,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Visibility,
                        contentDescription =
                            "Story views",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.width(3.dp)
                    )

                    Text(
                        text = "Live",
                        fontSize = 8.5.sp,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ====================================================================
// HEADER ICON
// ====================================================================

@Composable
private fun StoryHeaderIcon() {
    Box(
        modifier = Modifier
            .size(31.dp)
            .background(
                Brush.sweepGradient(
                    listOf(
                        BlinkPink,
                        BlinkPurple,
                        BlinkGold,
                        BlinkPink
                    )
                ),
                CircleShape
            )
            .padding(2.dp)
            .clip(CircleShape)
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme
                        .colorScheme
                        .surface,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector =
                    Icons.Default.AutoAwesome,
                contentDescription =
                    "Stories",
                tint = BlinkPink,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ====================================================================
// ADD STORY
// ====================================================================

@Composable
private fun AddStoryCard(
    userAvatar: String,
    onClick: () -> Unit
) {

    var pressed by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(pressed) {

        if (pressed) {
            delay(180)
            pressed = false
        }
    }

    val addScale by animateFloatAsState(
        targetValue =
            if (pressed) 0.91f else 1f,
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy,
                stiffness =
                    Spring.StiffnessMedium
            ),
        label = "add_story_scale"
    )

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .testTag("add_story_card")
    ) {

        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(addScale)
                .clickable {
                    pressed = true
                    onClick()
                }
        ) {

            AsyncImage(
                model = userAvatar,
                contentDescription = "Your Story",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(59.dp)
                    .align(Alignment.TopCenter)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme
                            .colorScheme
                            .outlineVariant,
                        shape = CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .size(23.dp)
                    .align(Alignment.BottomEnd)
                    .offset(
                        x = (-1).dp,
                        y = (-2).dp
                    )
                    .clip(CircleShape)
                    .background(BlinkPink)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme
                            .colorScheme
                            .surface,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Story",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = "Your Story",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme
                .colorScheme
                .onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Add",
            fontSize = 8.sp,
            color = BlinkPink,
            fontWeight = FontWeight.Bold
        )
    }
}

// ====================================================================
// PREMIUM STORY ITEM
// ====================================================================

@Composable
private fun PremiumStoryItem(
    story: Story,
    pressed: Boolean,
    onPressed: () -> Unit,
    onClick: () -> Unit
) {

    val scale by animateFloatAsState(
        targetValue =
            if (pressed) 0.91f else 1f,
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy,
                stiffness =
                    Spring.StiffnessMedium
            ),
        label = "story_scale"
    )

    val ringBrush = if (story.hasUnseen) {

        Brush.sweepGradient(
            listOf(
                BlinkPink,
                BlinkPurple,
                BlinkGold,
                BlinkPink,
                BlinkPurple,
                BlinkPink
            )
        )

    } else {

        Brush.linearGradient(
            listOf(
                MaterialTheme
                    .colorScheme
                    .outlineVariant,
                MaterialTheme
                    .colorScheme
                    .outlineVariant
            )
        )
    }

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .scale(scale)
            .testTag(
                "story_item_${story.username}"
            )
            .pointerInput(story.username) {

                detectTapGestures(

                    onPress = {

                        onPressed()

                        tryAwaitRelease()

                    },

                    onTap = {
                        onClick()
                    }
                )
            }
            .semantics {

                contentDescription =
                    "${story.username}'s " +
                            if (story.hasUnseen)
                                "unseen story"
                            else
                                "story"

                role = Role.Button
            }
    ) {

        // ============================================================
        // AVATAR / RING
        // ============================================================

        Box(
            modifier = Modifier
                .size(68.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
                    .background(
                        ringBrush,
                        CircleShape
                    )
                    .padding(
                        if (story.hasUnseen)
                            2.5.dp
                        else
                            2.dp
                    )
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme
                                .colorScheme
                                .surface,
                            CircleShape
                        )
                        .padding(2.dp)
                        .clip(CircleShape)
                ) {

                    AsyncImage(
                        model = story.avatar,
                        contentDescription =
                            story.username,
                        contentScale =
                            ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxSize()
                    )
                }
            }

            // ========================================================
            // UNSEEN BADGE
            // ========================================================

            androidx.compose.animation.AnimatedVisibility(
                visible = story.hasUnseen,
                enter =
                    fadeIn() +
                            scaleIn(),
                exit =
                    fadeOut() +
                            scaleOut(),
                modifier =
                    Modifier.align(
                        Alignment.TopEnd
                    )
            ) {

                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme
                                .colorScheme
                                .surface
                        )
                        .padding(2.dp)
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                BlinkPink
                            )
                    )
                }
            }

            // ========================================================
            // STORY PLAY INDICATOR
            // ========================================================

            if (story.hasUnseen) {

                Surface(
                    modifier = Modifier
                        .align(
                            Alignment.BottomStart
                        )
                        .size(19.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(
                        alpha = 0.62f
                    )
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.PlayArrow,
                        contentDescription =
                            "New story",
                        tint = Color.White,
                        modifier =
                            Modifier.padding(5.dp)
                    )
                }
            }

            // ========================================================
            // LIVE-STYLE DOT
            // ========================================================

            androidx.compose.animation.AnimatedVisibility(
                visible = false,
                enter =
                    fadeIn() +
                            scaleIn(),
                exit =
                    fadeOut() +
                            scaleOut(),
                modifier =
                    Modifier.align(
                        Alignment.BottomEnd
                    )
            ) {

                Surface(
                    shape =
                        RoundedCornerShape(
                            100.dp
                        ),
                    color =
                        Color(0xFFE53935),
                    border =
                        BorderStroke(
                            2.dp,
                            MaterialTheme
                                .colorScheme
                                .surface
                        )
                ) {

                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 6.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.padding(
                                horizontal = 4.dp,
                                vertical = 2.dp
                            )
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        // ============================================================
        // USERNAME
        // ============================================================

        Row(
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                text = story.username,
                fontSize = 10.5.sp,
                fontWeight =
                    if (story.hasUnseen)
                        FontWeight.Bold
                    else
                        FontWeight.Medium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (story.verificationBadge != VerificationBadge.NONE) {
                Spacer(modifier = Modifier.width(3.dp))
                VerifiedMark(
                    badge = story.verificationBadge,
                    size = 10.dp
                )
            }
        }

        // ============================================================
        // STATUS LABEL
        // ============================================================

        AnimatedContent(
            targetState =
                story.hasUnseen,
            label = "story_status"
        ) { unseen ->

            Text(
                text =
                    if (unseen)
                        "New"
                    else
                        "Viewed",
                fontSize = 7.5.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    if (unseen)
                        BlinkPink
                    else
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
            )
        }
    }
}
