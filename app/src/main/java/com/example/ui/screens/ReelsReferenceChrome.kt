package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.Story
import com.example.data.supabase.BlinkFeaturedReel
import androidx.compose.ui.res.painterResource

/**
 * The reference-style chrome is composed ABOVE the same pager that plays the fullscreen reels.
 * No second preview player is created, so expanding does not replay the video.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReelsReferenceChrome(
    stories: List<Story>,
    userAvatar: String,
    currentUsername: String,
    isDark: Boolean,
    hasUnreadNotifications: Boolean,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onAddStory: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onProfileClick: (String) -> Unit,
    onSearch: () -> Unit,
    onGame: () -> Unit,
    onNotifications: () -> Unit,
    onMenu: () -> Unit,
    featuredReels: List<BlinkFeaturedReel> = emptyList(),
    onFeaturedReelClick: (String) -> Unit = {},
    autoSlideEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val foreground = if (isDark) Color.White else Color(0xFF101014)
    val background = if (isDark) Color(0xFF121214) else Color.White
    val featured = remember(featuredReels) { featuredReels.take(5) }
    val featuredPager = rememberPagerState(pageCount = { featured.size })
    LaunchedEffect(featured.map { it.reel.id }, autoSlideEnabled) {
        if (autoSlideEnabled && featured.size > 1) {
            while (true) {
                delay(5_000L)
                if (!featuredPager.isScrollInProgress) {
                    featuredPager.animateScrollToPage((featuredPager.currentPage + 1) % featured.size)
                }
            }
        }
    }
    Column(modifier.fillMaxWidth().background(background)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        val actionSize = if (compact) 34.dp else 40.dp
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = if (compact) 8.dp else 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = userAvatar,
                fallback = painterResource(R.drawable.ic_default_profile),
                error = painterResource(R.drawable.ic_default_profile),
                contentDescription = "My profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(if (compact) 31.dp else 35.dp).clip(CircleShape)
                    .clickable { onProfileClick(currentUsername) }
            )
            Spacer(Modifier.width(if (compact) 7.dp else 13.dp))
            Text(
                "B L I N K",
                fontSize = if (compact) 16.sp else 20.sp,
                fontWeight = FontWeight.Black,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearch, modifier = Modifier.size(actionSize)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = foreground)
            }
            IconButton(onClick = onGame, modifier = Modifier.size(actionSize)) {
                Icon(Icons.Default.SportsEsports, contentDescription = "Games", tint = foreground)
            }
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onNotifications, modifier = Modifier.size(actionSize)) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = foreground)
                }
                if (hasUnreadNotifications) {
                    Box(Modifier.padding(top = 3.dp, end = 4.dp).size(9.dp)
                        .clip(CircleShape).background(Color(0xFFFF315C)))
                }
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(actionSize)) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = foreground)
            }
        }
        }

        // Replace only the Reels preview's story row. Stories elsewhere stay untouched.
        Box(
            Modifier.fillMaxWidth().height(184.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = "Top Reels: five featured videos refresh every twelve hours" }
        ) {
            if (featured.isNotEmpty()) {
                HorizontalPager(
                    state = featuredPager,
                    pageSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = featured[page]
                    val caption = item.caption.ifBlank { "Featured Reel" }.replace("\\n", " ").take(84)
                    Box(
                        Modifier.fillMaxSize()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(19.dp))
                            .border(1.dp, Color(0xFF8048E7),
                                androidx.compose.foundation.shape.RoundedCornerShape(19.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF48072A), Color(0xFF271A57), Color(0xFF00477E))
                                )
                            )
                            .clickable { onFeaturedReelClick(item.reel.id) }
                    ) {
                        AsyncImage(
                            model = item.coverUrl.ifBlank { item.avatarUrl },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(.58f).align(Alignment.CenterEnd)
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF100818).copy(alpha = .97f),
                                        Color(0xFF1B0C32).copy(alpha = .88f),
                                        Color.Transparent)
                                )
                            )
                        )
                        Column(
                            Modifier.fillMaxWidth(.72f).padding(start = 14.dp, top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = item.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(30.dp).clip(CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        item.displayName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "@${item.username}",
                                        color = Color.White.copy(alpha = .76f),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                "🔥 Top Reel · 12h",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .background(Color(0xFFD92051)).padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                            Text(
                                caption,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Visibility, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${item.windowViews} views", color = Color.White, fontSize = 11.sp)
                            }
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 9.dp)
                                .size(32.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = .57f))
                                .border(1.dp, Color.White.copy(alpha = .75f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play this featured reel",
                                tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(featured.size) { index ->
                        Box(
                            Modifier.size(if (featuredPager.currentPage == index) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (featuredPager.currentPage == index) Color(0xFFAA72FF)
                                    else Color.White.copy(alpha = .65f))
                        )
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(19.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF250F33),Color(0xFF101225)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Top Reels · New highlights every 12 hours",
                        color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                }
            }
        }
        val tabs = listOf("Reels", "Following", "For You")
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val active = tab == selectedTab
                Column(
                    Modifier.weight(1f).clickable { onSelectTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab,
                        color = if (active) foreground else foreground.copy(alpha = .48f),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(9.dp))
                    Box(
                        Modifier.width(if (active) 64.dp else 0.dp).height(3.dp)
                            .clip(CircleShape).background(foreground)
                    )
                }
            }
        }
    }
}
