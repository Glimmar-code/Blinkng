package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.PersistentTextDraftStore
import com.example.data.models.ChatConversation
import com.example.data.models.Story
import com.example.data.models.VerificationBadge
import com.example.ui.components.BlinkVipMarkForUsername
import com.example.ui.components.VerifiedMark
import androidx.compose.ui.platform.LocalContext

/**
 * Reference-matched inbox ONLY. Chat detail, calls, and message backend remain untouched.
 * No mock accounts or mock messages are rendered in production.
 */
@Composable
internal fun BlinkReferenceInbox(
    conversations: List<ChatConversation>,
    stories: List<Story>,
    myAvatar: String,
    myName: String,
    isDark: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onMyProfileClick: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onGameClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onComposeMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    isConnected: Boolean,
    isLoading: Boolean,
    filterContent: @Composable () -> Unit,
) {
    val bg = if (isDark) Color(0xFF0C0D0E) else Color.White
    val primary = if (isDark) Color.White else Color(0xFF121212)
    val secondary = if (isDark) Color(0xFFACADB0) else Color(0xFF858585)
    val border = if (isDark) Color(0xFF313236) else Color(0xFFEEEEEE)
    Column(Modifier.fillMaxSize().background(bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.clickable(role = Role.Button, onClick = onMyProfileClick)
                    .testTag("inbox_my_avatar")
            ) {
                InboxAvatar(myAvatar, myName, 40.dp, bg)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(12.dp)
                        .border(2.dp, bg, CircleShape).clip(CircleShape)
                        .background(Color(0xFF14D76D))
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "B L I N K",
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = primary,
                maxLines = 1
            )
            IconButton(onClick = onToggleSearch, modifier = Modifier.size(43.dp).testTag("inbox_search")) {
                Icon(Icons.Default.Search, contentDescription = "Search messages", tint = primary, modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = onGameClick, modifier = Modifier.size(43.dp).testTag("inbox_game")) {
                Icon(Icons.Default.SportsEsports, contentDescription = "Open BLINK games", tint = primary, modifier = Modifier.size(27.dp))
            }
            Box {
                IconButton(onClick = onNotificationsClick, modifier = Modifier.size(43.dp).testTag("inbox_notifications")) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = primary, modifier = Modifier.size(25.dp))
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Messages", modifier = Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = primary)
            TextButton(onClick = onToggleAll, modifier = Modifier.testTag("inbox_see_all")) {
                Text(if (showAll) "Close" else "See all", fontSize = 13.sp, color = secondary)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
            }
        }

        BlinkInboxStories(
            stories = stories,
            conversations = conversations,
            bg = bg,
            primary = primary,
            secondary = secondary,
            onAddStoryClick = onAddStoryClick,
            onStoryClick = onStoryClick
        )
        if (showSearch) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search messages", color = secondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = secondary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = bg,
                    unfocusedContainerColor = bg,
                    focusedTextColor = primary,
                    unfocusedTextColor = primary,
                    cursorColor = primary,
                    focusedIndicatorColor = border,
                    unfocusedIndicatorColor = border
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).testTag("inbox_search_field")
            )
        }
        if (showAll) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onComposeMessage) { Text("New message", color = primary) }
                TextButton(onClick = onOpenSettings) { Text("Message settings", color = primary) }
            }
            filterContent()
        }
        HorizontalDivider(thickness = 1.dp, color = border)
        if (!isConnected) {
            Text("You're offline. Some messages may be delayed.",
                color = secondary, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp))
        }
        if (isLoading && conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary, modifier = Modifier.size(28.dp))
            }
        } else if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No conversations found", color = secondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("inbox_conversation_list"),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(conversations, key = { "inbox_${it.id}" }) { conversation ->
                    BlinkInboxConversationRow(
                        conversation = conversation,
                        isDark = isDark,
                        bg = bg,
                        primary = primary,
                        secondary = secondary,
                        border = border,
                        onOpen = { onOpenConversation(conversation.partnerUsername) },
                        onAvatarClick = { onProfileClick(conversation.partnerUsername) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlinkInboxStories(
    stories: List<Story>,
    conversations: List<ChatConversation>,
    bg: Color,
    primary: Color,
    secondary: Color,
    onAddStoryClick: () -> Unit,
    onStoryClick: (Story) -> Unit
) {
    // Only server-provided, uploaded stories appear; online status never creates a fake ring.
    val visible = remember(stories) {
        stories.asSequence().filter { it.storyImage.isNotBlank() }
            .filterNot { it.isUser }
            .distinctBy { it.username.lowercase() }
            .toList()
    }
    val onlineUsers = remember(conversations) {
        conversations.filter { it.isOnline }
            .map { it.partnerUsername.removePrefix("@").lowercase() }.toSet()
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("inbox_stories"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 12.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "add_note") {
            Column(
                modifier = Modifier.width(64.dp).clickable(role = Role.Button, onClick = onAddStoryClick)
                    .testTag("inbox_your_note"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(62.dp).border(1.dp, secondary.copy(alpha = .22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add your story", tint = primary, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.height(5.dp))
                Text("Your note", color = secondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        items(visible, key = { "inbox_story_${it.id}" }) { story ->
            Column(
                modifier = Modifier.width(66.dp).clickable(role = Role.Button, onClick = { onStoryClick(story) }),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val ring = Brush.sweepGradient(
                    if (story.hasUnseen) listOf(
                        Color(0xFFFFC344), Color(0xFFFF5B98), Color(0xFF922DDE),
                        Color(0xFF5064DB), Color(0xFFFFC344)
                    ) else listOf(secondary.copy(alpha = .35f), secondary.copy(alpha = .35f))
                )
                Box(
                    Modifier.size(63.dp).border(2.dp, ring, CircleShape).padding(3.dp)
                ) {
                    InboxAvatar(story.avatar, story.displayLabel, 57.dp, bg)
                    if (story.username.removePrefix("@").lowercase() in onlineUsers) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(15.dp)
                                .border(2.dp, bg, CircleShape).clip(CircleShape)
                                .background(Color(0xFF14D76D))
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    story.displayLabel.substringBefore(" "), color = primary,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BlinkInboxConversationRow(
    conversation: ChatConversation,
    isDark: Boolean,
    bg: Color,
    primary: Color,
    secondary: Color,
    border: Color,
    onOpen: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val context = LocalContext.current
    val draft = remember(conversation.id, conversation.partnerUsername, conversation.lastMessageRawTime) {
        PersistentTextDraftStore.readDraft(
            context = context,
            key = "chat_composer_text",
            scope = conversation.id.ifBlank { conversation.partnerUsername.lowercase() }
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)
            .testTag("inbox_chat_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(58.dp).clickable(role = Role.Button, onClick = onAvatarClick)) {
                InboxAvatar(conversation.partnerAvatar, conversation.partnerName, 58.dp, bg)
                if (conversation.isOnline) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(15.dp)
                            .border(2.dp, bg, CircleShape).clip(CircleShape)
                            .background(Color(0xFF14D76D))
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation.partnerName,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (conversation.verificationBadge != VerificationBadge.NONE || conversation.isVerified) {
                        VerifiedMark(
                            badge = if (conversation.verificationBadge != VerificationBadge.NONE)
                                conversation.verificationBadge else VerificationBadge.BLUE,
                            size = 15.dp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    BlinkVipMarkForUsername(
                        username = conversation.partnerUsername,
                        knownVip = if (conversation.partnerIsVip) true else null,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                val preview = when {
                    conversation.presenceLabel.isNotBlank() -> conversation.presenceLabel
                    !draft.isNullOrBlank() -> "Draft: $draft"
                    else -> conversation.lastMessage.ifBlank { "Start the conversation" }
                }
                Text(
                    preview, color = secondary,
                    fontSize = 13.sp, maxLines = 2,
                    lineHeight = 17.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(7.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(conversation.lastMessageTime, fontSize = 11.sp, color = secondary, maxLines = 1)
                if (conversation.unreadCount > 0 || conversation.isMarkedUnread) {
                    Box(
                        Modifier.size(23.dp).clip(CircleShape)
                            .background(if (isDark) Color.White else Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (conversation.unreadCount > 0)
                                conversation.unreadCount.coerceAtMost(99).toString()
                            else "•",
                            color = if (isDark) Color.Black else Color.White,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 89.dp, end = 18.dp),
            thickness = 1.dp, color = border
        )
    }
}

@Composable
private fun InboxAvatar(url: String, name: String, size: Dp, background: Color) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(
            if (background == Color.White) Color(0xFFF1F2F3) else Color(0xFF343537)
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                .take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "B" },
            color = if (background == Color.White) Color.DarkGray else Color.White,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold
        )
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "${name} avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
    }
}
