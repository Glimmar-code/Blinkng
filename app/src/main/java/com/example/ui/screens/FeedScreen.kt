package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.Story
import com.example.ui.components.PostCard
import com.example.ui.components.StoryBar
import com.example.ui.theme.BlinkBlack
import com.example.ui.theme.BlinkCream
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    stories: List<Story>,
    userAvatar: String,
    currentSubTab: Int,
    onSubTabChanged: (Int) -> Unit,
    isDark: Boolean,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onProfileClick: (String) -> Unit,
    onAddStoryClick: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMenu: () -> Unit,
    onToggleTheme: () -> Unit,
    isServerConnected: Boolean = true,
    onViewedPost: (String) -> Unit = {},
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit = { _, _, _ -> },

    // New bottom navigation actions
    onSearchClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onMarketClick: () -> Unit = {},
    onMessageClick: () -> Unit = {}
) {

    var selectedTopTab by remember(currentSubTab) {
        mutableIntStateOf(currentSubTab)
    }

    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        when (selectedTopTab) {
            1 -> {
                VideoReelsScreen(
                    reels = reels,
                    isDark = isDark,
                    onLike = onLikePost,
                    onComment = onCommentPost,
                    onBookmark = onBookmarkPost,
                    onShare = onSharePost,
                    onProfileClick = onProfileClick,
                    onBackToPosts = {
                        selectedTopTab = 0
                        onSubTabChanged(0)
                    },
                    onHomeClick = {
                        selectedTopTab = 0
                        onSubTabChanged(0)
                    },
                    onConnectClick = {
                        selectedTopTab = 2
                        onSubTabChanged(2)
                    },
                    onGameClick = {
                        selectedTopTab = 3
                        onSubTabChanged(3)
                    }
                )
            }

            2 -> {
                ConnectSection(
                    userAvatar = userAvatar,
                    isDark = isDark,
                    onOpenMenu = onOpenMenu,
                    onOpenActivity = onOpenActivity,
                    onProfileClick = onProfileClick,
                    onDirectMessage = onDirectMessage,
                    selectedTopTab = 2,
                    onHomeClick = {
                        selectedTopTab = 0
                        onSubTabChanged(0)
                    },
                    onReelClick = {
                        selectedTopTab = 1
                        onSubTabChanged(1)
                    },
                    onConnectClick = {
                        selectedTopTab = 2
                        onSubTabChanged(2)
                    },
                    onGameClick = {
                        selectedTopTab = 3
                        onSubTabChanged(3)
                    }
                )
            }

            3 -> {
                GameSection(
                    userAvatar = userAvatar,
                    isDark = isDark,
                    onOpenMenu = onOpenMenu,
                    onOpenActivity = onOpenActivity,
                    onProfileClick = onProfileClick,
                    selectedTopTab = 3,
                    onHomeClick = {
                        selectedTopTab = 0
                        onSubTabChanged(0)
                    },
                    onReelClick = {
                        selectedTopTab = 1
                        onSubTabChanged(1)
                    },
                    onConnectClick = {
                        selectedTopTab = 2
                        onSubTabChanged(2)
                    },
                    onGameClick = {
                        selectedTopTab = 3
                        onSubTabChanged(3)
                    }
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 115.dp
                    )
                ) {

                    // Header / status area
                    item {
                        HomeHeader(
                            userAvatar = userAvatar,
                            onMenuClick = onOpenMenu,
                            onNotificationClick = onOpenActivity,
                            onProfileClick = {
                                onProfileClick("you")
                            }
                        )
                    }

                    // Top navigation
                    item {
                        TopNavigation(
                            selected = 0,
                            onHome = {
                                selectedTopTab = 0
                                onSubTabChanged(0)
                            },
                            onReel = {
                                selectedTopTab = 1
                                onSubTabChanged(1)
                            },
                            onConnect = {
                                selectedTopTab = 2
                                onSubTabChanged(2)
                            },
                            onGame = {
                                selectedTopTab = 3
                                onSubTabChanged(3)
                            }
                        )
                    }

                    item {
                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )
                    }

                    // Stories
                    item {
                        StoryBar(
                            stories = stories,
                            userAvatar = userAvatar,
                            onAddStory = onAddStoryClick,
                            onStoryClick = onStoryClick
                        )
                    }

                    item {
                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )
                    }

                    // Feed
                    items(
                        items = posts,
                        key = { it.id }
                    ) { post ->

                        onViewedPost(post.id)

                        PostCard(
                            post = post,
                            isDark = isDark,
                            onLike = {
                                onLikePost(post.id)
                            },
                            onComment = {
                                onCommentPost(post.id)
                            },
                            onBookmark = {
                                onBookmarkPost(post.id)
                            },
                            onShare = {
                                onSharePost(post.id)
                            },
                            onOptionsClick = {
                                onOptionsClick(post)
                            },
                            onProfileClick = onProfileClick,
                            onViewed = {
                                onViewedPost(post.id)
                            },
                            onVotePoll = onVotePoll
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )
                    }

                    if (posts.isEmpty()) {
                        item {
                            EmptyHomeFeed(
                                onCreatePost = onOpenCreatePost
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onOpenCreatePost,
                    containerColor = if (isDark) BlinkCream else BlinkBlack,
                    contentColor = if (isDark) BlinkBlack else BlinkCream,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(
                            end = 20.dp,
                            bottom = 90.dp
                        )
                        .testTag("create_post_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Post",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* HEADER                                                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = 38.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Three dots on the left.
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Menu",
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(
            modifier = Modifier.weight(1f)
        )

        // Home in the middle.
        Text(
            text = "Home",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(
            modifier = Modifier.weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Notification.
            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier.size(44.dp)
            ) {

                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = BlinkGold,
                            contentColor = BlinkBlack
                        ) {
                            Text("3", fontWeight = FontWeight.Bold)
                        }
                    }
                ) {

                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "Notifications",
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier.width(2.dp)
            )

            // Profile.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        onProfileClick()
                    }
            ) {

                AsyncImage(
                    model = userAvatar,
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(
                modifier = Modifier.width(4.dp)
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* TOP NAVIGATION                                                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun TopNavigation(
    selected: Int,
    onHome: () -> Unit,
    onReel: () -> Unit,
    onConnect: () -> Unit,
    onGame: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(
                androidx.compose.foundation.rememberScrollState()
            )
            .padding(
                horizontal = 20.dp,
                vertical = 2.dp
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        TopTab(
            text = "Home",
            selected = selected == 0,
            onClick = onHome
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        TopTab(
            text = "Reel",
            selected = selected == 1,
            onClick = onReel
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        TopTab(
            text = "Connect",
            selected = selected == 2,
            onClick = onConnect
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        TopTab(
            text = "Game",
            selected = selected == 3,
            onClick = onGame
        )
    }
}

@Composable
private fun TopTab(
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
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        }
    ) {

        Text(
            text = text,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 13.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        )
    }
}

/* -------------------------------------------------------------------------- */
/* EMPTY FEED                                                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun EmptyHomeFeed(
    onCreatePost: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 30.dp,
                vertical = 60.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Surface(
            shape = CircleShape,
            color = BlinkPink.copy(alpha = 0.12f)
        ) {

            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = BlinkPink,
                modifier = Modifier
                    .size(58.dp)
                    .padding(14.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(15.dp)
        )

        Text(
            text = "Opps nothing here yet",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        Text(
            text = "Be the first person to share something.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Surface(
            modifier = Modifier.clickable {
                onCreatePost()
            },
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.primary
        ) {

            Text(
                text = "Create Post",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 11.dp
                )
            )
        }
    }
}