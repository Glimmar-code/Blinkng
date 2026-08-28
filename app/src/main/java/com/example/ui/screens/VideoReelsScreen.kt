package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.components.formatNumber
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun VideoReelsScreen(
    reels: List<FeedPost>,
    isDark: Boolean,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onShare: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,

    // Shared top navigation
    onHomeClick: () -> Unit = onBackToPosts,
    onConnectClick: () -> Unit = {},
    onGameClick: () -> Unit = {}
) {
    if (reels.isEmpty()) {
        EmptyReelsState(
            onBack = onBackToPosts
        )
        return
    }

    val pagerState = rememberPagerState(
        pageCount = {
            reels.size
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->

            val reel = reels[page]

            SingleReelPage(
                reel = reel,
                isDark = isDark,
                onLike = {
                    onLike(reel.id)
                },
                onComment = {
                    onComment(reel.id)
                },
                onBookmark = {
                    onBookmark(reel.id)
                },
                onShare = {
                    onShare(reel.id)
                },
                onProfileClick = {
                    onProfileClick(reel.author)
                }
            )
        }

        // Top navigation over the video
        ReelTopArea(
            selected = 1,
            onHome = onHomeClick,
            onReel = {},
            onConnect = onConnectClick,
            onGame = onGameClick
        )
    }
}

/* -------------------------------------------------------------------------- */
/* SINGLE REEL                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun SingleReelPage(
    reel: FeedPost,
    isDark: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onProfileClick: () -> Unit
) {

    var liked by remember {
        mutableStateOf(reel.isLiked)
    }

    var bookmarked by remember {
        mutableStateOf(reel.isBookmarked)
    }

    var showBigHeart by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(reel.id) {

                detectTapGestures(
                    onDoubleTap = {

                        if (!liked) {
                            liked = true
                            onLike()
                        }

                        showBigHeart = true
                    }
                )
            }
    ) {

        AsyncImage(
            model = reel.images.firstOrNull()
                ?: "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=1200&fit=crop",
            contentDescription = "Reel from ${reel.author}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark overlay for readability.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // Large double-tap heart.
        if (showBigHeart) {

            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = BlinkPink.copy(alpha = 0.96f),
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.Center)
            )
        }

        // Right-side actions.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    end = 10.dp,
                    bottom = 92.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {

            ReelProfileButton(
                avatar = reel.authorAvatar,
                onClick = onProfileClick
            )

            ReelSideAction(
                icon = if (liked) {
                    Icons.Default.Favorite
                } else {
                    Icons.Outlined.FavoriteBorder
                },
                count = formatNumber(
                    if (liked) {
                        maxOf(reel.likes, 1)
                    } else {
                        reel.likes
                    }
                ),
                tint = if (liked) BlinkPink else Color.White,
                onClick = {
                    liked = !liked
                    onLike()
                }
            )

            ReelSideAction(
                icon = Icons.Outlined.Comment,
                count = formatNumber(reel.commentsCount),
                tint = Color.White,
                onClick = onComment
            )

            ReelSideAction(
                icon = if (bookmarked) {
                    Icons.Default.Bookmark
                } else {
                    Icons.Outlined.BookmarkBorder
                },
                count = "Save",
                tint = if (bookmarked) BlinkPurple else Color.White,
                onClick = {
                    bookmarked = !bookmarked
                    onBookmark()
                }
            )

            ReelSideAction(
                icon = Icons.Default.Share,
                count = "Share",
                tint = Color.White,
                onClick = onShare
            )

            IconButton(
                onClick = {}
            ) {

                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Creator and caption at bottom-left.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 14.dp,
                    end = 90.dp,
                    bottom = 86.dp
                )
        ) {

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        Color.Black.copy(alpha = 0.35f)
                    )
                    .clickable {
                        onProfileClick()
                    }
                    .padding(
                        horizontal = 7.dp,
                        vertical = 5.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                AsyncImage(
                    model = reel.authorAvatar,
                    contentDescription = reel.author,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                )

                Spacer(
                    modifier = Modifier.width(7.dp)
                )

                Text(
                    text = "@${reel.author}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )

                if (reel.verificationBadge != VerificationBadge.NONE) {
                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )
                    VerifiedMark(
                        badge = reel.verificationBadge,
                        size = 14.dp
                    )
                } else if (reel.isVerified) {
                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )
                    VerifiedMark(
                        badge = VerificationBadge.BLUE,
                        size = 14.dp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(9.dp)
            )

            if (reel.text.isNotBlank()) {

                Text(
                    text = reel.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(
                    modifier = Modifier.width(5.dp)
                )

                Text(
                    text = "Reel • ${reel.author}",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* TOP AREA                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReelTopArea(
    selected: Int,
    onHome: () -> Unit,
    onReel: () -> Unit,
    onConnect: () -> Unit,
    onGame: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 38.dp
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onHome
            ) {

                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Reel",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {}
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "Notifications",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
            )
        }

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp
                ),
            horizontalArrangement = Arrangement.Center
        ) {

            ReelTopTab(
                text = "Home",
                selected = selected == 0,
                onClick = onHome
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )

            ReelTopTab(
                text = "Reel",
                selected = selected == 1,
                onClick = onReel
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )

            ReelTopTab(
                text = "Connect",
                selected = selected == 2,
                onClick = onConnect
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )

            ReelTopTab(
                text = "Game",
                selected = selected == 3,
                onClick = onGame
            )
        }
    }
}

@Composable
private fun ReelTopTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        modifier = Modifier.clickable {
            onClick()
        },
        shape = RoundedCornerShape(100.dp),
        color = if (selected) {
            Color.White
        } else {
            Color.Black.copy(alpha = 0.30f)
        }
    ) {

        Text(
            text = text,
            color = if (selected) {
                Color.Black
            } else {
                Color.White
            },
            fontSize = 12.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            modifier = Modifier.padding(
                horizontal = 15.dp,
                vertical = 7.dp
            )
        )
    }
}

/* -------------------------------------------------------------------------- */
/* REEL SIDE BUTTONS                                                           */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReelProfileButton(
    avatar: String,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = Color.White,
                shape = CircleShape
            )
            .clickable {
                onClick()
            }
    ) {

        AsyncImage(
            model = avatar,
            contentDescription = "Creator profile",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ReelSideAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String,
    tint: Color,
    onClick: () -> Unit
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Color.Black.copy(alpha = 0.25f)
                )
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = icon,
                contentDescription = count,
                tint = tint,
                modifier = Modifier.size(25.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(3.dp)
        )

        Text(
            text = count,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/* -------------------------------------------------------------------------- */
/* EMPTY REELS                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun EmptyReelsState(
    onBack: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Surface(
                shape = CircleShape,
                color = BlinkPink.copy(alpha = 0.14f)
            ) {

                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = BlinkPink,
                    modifier = Modifier
                        .size(62.dp)
                        .padding(17.dp)
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = "No Reels yet",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = "New videos will appear here.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            Surface(
                modifier = Modifier.clickable {
                    onBack()
                },
                shape = RoundedCornerShape(100.dp),
                color = BlinkPink
            ) {

                Text(
                    text = "Back to Home",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(
                        horizontal = 18.dp,
                        vertical = 10.dp
                    )
                )
            }
        }
    }
}