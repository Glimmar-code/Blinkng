package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource

/**
 * The reference-style chrome is composed ABOVE the same pager that plays the fullscreen reels.
 * No second preview player is created, so expanding does not replay the video.
 */
@Composable
internal fun ReelsReferenceChrome(
    stories: List<Story>,
    userAvatar: String,
    currentUsername: String,
    isDark: Boolean,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onAddStory: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onProfileClick: (String) -> Unit,
    onSearch: () -> Unit,
    onGame: () -> Unit,
    onNotifications: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = if (isDark) Color.White else Color(0xFF101014)
    val background = if (isDark) Color(0xFF121214) else Color.White
    val visibleStories = remember(stories) {
        stories.filterNot { it.id == "story_me" && it.storyImage.isBlank() }
            .distinctBy { it.id }
    }
    Column(modifier.fillMaxWidth().background(background)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = userAvatar,
                fallback = painterResource(R.drawable.ic_default_profile),
                error = painterResource(R.drawable.ic_default_profile),
                contentDescription = "My profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(35.dp).clip(CircleShape)
                    .clickable { onProfileClick(currentUsername) }
            )
            Spacer(Modifier.width(13.dp))
            Text(
                "B L I N K",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = foreground,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = foreground)
            }
            IconButton(onClick = onGame, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SportsEsports, contentDescription = "Games", tint = foreground)
            }
            IconButton(onClick = onNotifications, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = foreground)
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = foreground)
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(108.dp)
        ) {
            item(key = "add_story") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = userAvatar,
                            fallback = painterResource(R.drawable.ic_default_profile),
                            error = painterResource(R.drawable.ic_default_profile),
                            contentScale = ContentScale.Crop,
                            contentDescription = "Add to your story",
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                                .border(1.dp, foreground.copy(alpha = .13f), CircleShape)
                                .clickable(onClick = onAddStory)
                        )
                        Box(
                            Modifier.size(23.dp).clip(CircleShape).background(background)
                                .padding(2.dp).clip(CircleShape)
                                .background(foreground)
                                .clickable(onClick = onAddStory),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add story", tint = background,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("Your Story", color = foreground, fontSize = 11.sp, maxLines = 1)
                }
            }
            items(visibleStories, key = { it.id }) { story ->
                Column(
                    modifier = Modifier.width(68.dp).clickable { onStoryClick(story) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(66.dp)
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (story.hasUnseen) Brush.sweepGradient(
                                        listOf(Color(0xFFFFC44A), Color(0xFFFF477D),
                                            Color(0xFFB51EF8), Color(0xFF5A6EFB),
                                            Color(0xFFFFC44A))
                                    ) else Brush.linearGradient(listOf(Color.Gray, Color.Gray))
                                ),
                                CircleShape
                            ).padding(4.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = story.avatar,
                            fallback = painterResource(R.drawable.ic_default_profile),
                            error = painterResource(R.drawable.ic_default_profile),
                            contentDescription = "View ${story.displayLabel}'s story",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(58.dp).clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        story.displayLabel,
                        fontSize = 11.sp,
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
