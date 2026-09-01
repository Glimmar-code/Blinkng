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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.Story
import com.example.ui.components.PostCard
import com.example.ui.components.StoryBar
import com.example.ui.theme.BlinkBlack
import com.example.ui.theme.BlinkCream
import com.example.ui.theme.BlinkPink

@Composable
fun FeedScreen(
    posts: List<FeedPost>, reels: List<FeedPost>, stories: List<Story>, userAvatar: String,
    currentSubTab: Int, onSubTabChanged: (Int) -> Unit, isDark: Boolean,
    onLikePost: (String) -> Unit, onCommentPost: (String) -> Unit, onBookmarkPost: (String) -> Unit,
    onSharePost: (String) -> Unit, onOptionsClick: (FeedPost) -> Unit, onProfileClick: (String) -> Unit,
    onAddStoryClick: () -> Unit, onStoryClick: (Story) -> Unit, onOpenCreatePost: () -> Unit,
    onOpenActivity: () -> Unit, onOpenMenu: () -> Unit, onToggleTheme: () -> Unit,
    isServerConnected: Boolean = true, onViewedPost: (String) -> Unit = {},
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit = { _, _, _ -> },
    onSearchClick: () -> Unit = {}, onLeaderboardClick: () -> Unit = {}, onMarketClick: () -> Unit = {},
    onMessageClick: () -> Unit = {}, onBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
    // Parent navigation is the single source of truth. Local state only mirrors it for immediate UI updates.
    var selectedTopTab by remember { mutableStateOf(currentSubTab) }
    LaunchedEffect(currentSubTab) { if (selectedTopTab != currentSubTab) selectedTopTab = currentSubTab }
    val listState = rememberLazyListState()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) onBottomBarVisibilityChange(false)
                else if (available.y > 8f) onBottomBarVisibilityChange(true)
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> if (index == 0 && offset < 50) onBottomBarVisibilityChange(true) }
    }
    LaunchedEffect(selectedTopTab) { onBottomBarVisibilityChange(true) }

    fun navigate(tab: Int) { selectedTopTab = tab; if (currentSubTab != tab) onSubTabChanged(tab) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (selectedTopTab) {
            1 -> VideoReelsScreen(reels = reels, isDark = isDark, onLike = onLikePost, onComment = onCommentPost,
                onBookmark = onBookmarkPost, onShare = onSharePost, onProfileClick = onProfileClick,
                onBackToPosts = { navigate(0) }, onHomeClick = { navigate(0) }, onConnectClick = { navigate(2) }, onGameClick = { navigate(3) })
            2 -> ConnectSection(userAvatar = userAvatar, isDark = isDark, onOpenMenu = onOpenMenu, onOpenActivity = onOpenActivity,
                onProfileClick = onProfileClick, onDirectMessage = onDirectMessage, selectedTopTab = 2,
                onHomeClick = { navigate(0) }, onReelClick = { navigate(1) }, onConnectClick = { navigate(2) }, onGameClick = { navigate(3) })
            3 -> GameSection(userAvatar = userAvatar, isDark = isDark, onOpenMenu = onOpenMenu, onOpenActivity = onOpenActivity,
                onProfileClick = onProfileClick, selectedTopTab = 3,
                onHomeClick = { navigate(0) }, onReelClick = { navigate(1) }, onConnectClick = { navigate(2) }, onGameClick = { navigate(3) })
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection), contentPadding = PaddingValues(bottom = 115.dp)) {
                    item { HomeHeader(userAvatar, onOpenMenu, onOpenActivity) { onProfileClick("you") } }
                    item { TopNavigation(selected = selectedTopTab, onHome = { navigate(0) }, onReel = { navigate(1) }, onConnect = { navigate(2) }, onGame = { navigate(3) }) }
                    item { Spacer(Modifier.height(4.dp)) }
                    item { StoryBar(stories = stories, userAvatar = userAvatar, onAddStory = onAddStoryClick, onStoryClick = onStoryClick) }
                    item { Spacer(Modifier.height(6.dp)) }
                    items(items = posts, key = { it.id }) { post ->
                        PostCard(post = post, isDark = isDark, onLike = { onLikePost(post.id) }, onComment = { onCommentPost(post.id) },
                            onBookmark = { onBookmarkPost(post.id) }, onShare = { onSharePost(post.id) }, onOptionsClick = { onOptionsClick(post) },
                            onProfileClick = onProfileClick, onViewed = { onViewedPost(post.id) }, onVotePoll = onVotePoll)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (posts.isEmpty()) item { EmptyHomeFeed(onOpenCreatePost) }
                }
                FloatingActionButton(onClick = onOpenCreatePost, containerColor = if (isDark) BlinkCream else BlinkBlack,
                    contentColor = if (isDark) BlinkBlack else BlinkCream, modifier = Modifier.align(Alignment.BottomEnd)
                        .navigationBarsPadding().padding(end = 20.dp, bottom = 90.dp).testTag("create_post_fab")) {
                    Icon(Icons.Default.Add, contentDescription = "Create Post", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(userAvatar: String, onMenuClick: () -> Unit, onNotificationClick: () -> Unit, onProfileClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.MoreHoriz, "Menu", modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.weight(1f)); Text("Home", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotificationClick, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.NotificationsNone, "Notifications", modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(2.dp)); Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { onProfileClick() }) {
                AsyncImage(model = userAvatar, contentDescription = "Profile", modifier = Modifier.fillMaxSize())
            }; Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun TopNavigation(selected: Int, onHome: () -> Unit, onReel: () -> Unit, onConnect: () -> Unit, onGame: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        TopTab("Home", selected == 0, onHome); Spacer(Modifier.width(8.dp)); TopTab("Reel", selected == 1, onReel); Spacer(Modifier.width(8.dp));
        TopTab("Connect", selected == 2, onConnect); Spacer(Modifier.width(8.dp)); TopTab("Game", selected == 3, onGame)
    }
}

@Composable
private fun TopTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.clickable { onClick() }, shape = androidx.compose.foundation.shape.RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun EmptyHomeFeed(onCreatePost: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = BlinkPink.copy(alpha = 0.12f)) {
            Icon(Icons.Default.Home, contentDescription = null, tint = BlinkPink, modifier = Modifier.size(58.dp).padding(14.dp))
        }
        Spacer(Modifier.height(15.dp)); Text("Oops, nothing here yet.", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(5.dp))
        Text("Be the first person to share something.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp))
        Surface(modifier = Modifier.clickable { onCreatePost() }, shape = androidx.compose.foundation.shape.RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.primary) {
            Text("Create Post", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp))
        }
    }
}
