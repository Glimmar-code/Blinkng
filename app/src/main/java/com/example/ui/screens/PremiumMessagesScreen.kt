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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ActivityItem
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.data.models.Story
import com.example.data.models.VerificationBadge

/**
 * The compact, visual-first messaging surface used by Blink.
 *
 * It deliberately consumes the same [Story] list as the feed, so the status rail on the
 * Messages tab never becomes a second source of truth. The existing conversation/realtime
 * pipeline remains untouched; this file is only the presentation layer.
 */
@Composable
fun PremiumMessagesScreen(
    conversations: List<ChatConversation>,
    stories: List<Story>,
    activities: List<ActivityItem>,
    myAvatar: String,
    myName: String,
    activePartner: String?,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onRetryMessage: ((String, ChatMessage) -> Unit)? = null,
    onProfileClick: (String) -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenActivity: () -> Unit,
    isConnected: Boolean = true,
    isDark: Boolean = false
) {
    val activeConversation = activePartner?.let { partner ->
        conversations.firstOrNull { it.partnerUsername.equals(partner, ignoreCase = true) }
            ?: ChatConversation(
                id = "local_$partner",
                partnerUsername = partner,
                partnerName = partner.replace("_", " ").replace(".", " "),
                partnerAvatar = ""
            )
    }

    if (activeConversation != null) {
        PremiumChatDetail(
            conversation = activeConversation,
            onBack = onCloseConversation,
            onSend = { onSendMessage(activeConversation.partnerUsername, it) },
            onRetry = { message -> onRetryMessage?.invoke(activeConversation.partnerUsername, message) },
            onProfileClick = { onProfileClick(activeConversation.partnerUsername) },
            isConnected = isConnected
        )
    } else {
        PremiumMessagesHome(
            conversations = conversations,
            stories = stories,
            activities = activities,
            myAvatar = myAvatar,
            myName = myName,
            onOpenConversation = onOpenConversation,
            onProfileClick = onProfileClick,
            onStoryClick = onStoryClick,
            onAddStoryClick = onAddStoryClick,
            onOpenActivity = onOpenActivity,
            isConnected = isConnected
        )
    }
}

@Composable
private fun PremiumMessagesHome(
    conversations: List<ChatConversation>,
    stories: List<Story>,
    activities: List<ActivityItem>,
    myAvatar: String,
    myName: String,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenActivity: () -> Unit,
    isConnected: Boolean
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredConversations = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter {
            it.partnerName.contains(query, true) ||
                it.partnerUsername.contains(query, true) ||
                it.lastMessage.contains(query, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            RoundHeaderButton(icon = Icons.Default.FilterList, contentDescription = "Filter chats") { }
            Spacer(Modifier.width(8.dp))
            RoundHeaderButton(icon = Icons.Default.Search, contentDescription = "Search chats") {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            }
        }

        if (searchOpen) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search messages") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        if (!isConnected) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            ) {
                Text(
                    "Offline — new messages will send automatically when your connection returns.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        StatusRail(
            stories = stories,
            myAvatar = myAvatar,
            myName = myName,
            onStoryClick = onStoryClick,
            onAddStoryClick = onAddStoryClick
        )

        if (conversations.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Best Matches", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            BestMatches(
                conversations = conversations.take(8),
                onOpenConversation = onOpenConversation
            )
        }

        SegmentedChatActivity(
            selected = selectedTab,
            onSelected = {
                selectedTab = it
                if (it == 1 && activities.isEmpty()) onOpenActivity()
            }
        )

        if (selectedTab == 0) {
            ConversationList(
                conversations = filteredConversations,
                onOpenConversation = onOpenConversation,
                onProfileClick = onProfileClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            ActivityPreviewList(
                activities = activities,
                onOpenActivity = onOpenActivity,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusRail(
    stories: List<Story>,
    myAvatar: String,
    myName: String,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit
) {
    val statusStories = stories.filterNot { it.isUser }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
                Box {
                    AvatarImage(url = myAvatar, name = myName, modifier = Modifier.size(50.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .size(19.dp)
                            .align(Alignment.BottomEnd)
                            .clickable(onClick = onAddStoryClick)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add status",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("You", fontSize = 10.sp, maxLines = 1)
            }
        }
        items(statusStories, key = { it.id }) { story ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(58.dp)
                    .clickable { onStoryClick(story) }
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .border(
                            width = if (story.hasUnseen) 2.dp else 1.dp,
                            color = if (story.hasUnseen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .padding(3.dp)
                ) {
                    AvatarImage(story.avatar, story.username, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    story.username.removePrefix("@").substringBefore(" ").take(10),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BestMatches(
    conversations: List<ChatConversation>,
    onOpenConversation: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(conversations, key = { it.id }) { convo ->
            Card(
                modifier = Modifier
                    .width(145.dp)
                    .height(170.dp)
                    .clickable { onOpenConversation(convo.partnerUsername) },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (convo.partnerAvatar.isNotBlank()) {
                        AsyncImage(
                            model = convo.partnerAvatar,
                            contentDescription = convo.partnerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                initials(convo.partnerName),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .72f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                convo.partnerName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            VerificationDot(convo.verificationBadge)
                        }
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            TinyPill(if (convo.isOnline) "Active" else "Match")
                            TinyPill(if (convo.faculty.isBlank()) "Chat" else convo.faculty.take(8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedChatActivity(selected: Int, onSelected: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(Modifier.padding(3.dp)) {
            listOf("Chat", "Activity").forEachIndexed { index, label ->
                Surface(
                    color = if (selected == index) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = if (selected == index) 1.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(index) }
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<ChatConversation>,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (conversations.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "No conversations yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(30.dp)
            )
        }
        return
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        items(conversations, key = { it.id }) { convo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenConversation(convo.partnerUsername) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onProfileClick(convo.partnerUsername) }) {
                    AvatarImage(convo.partnerAvatar, convo.partnerName, Modifier.size(48.dp))
                    if (convo.isOnline) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color(0xFF20C979))
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            convo.partnerName,
                            fontWeight = if (convo.unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        VerificationDot(convo.verificationBadge)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        convo.lastMessage.ifBlank { "Start the conversation" },
                        color = if (convo.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (convo.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        convo.lastMessageTime,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (convo.unreadCount > 0) {
                        Spacer(Modifier.height(5.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(convo.unreadCount.coerceAtMost(99).toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityPreviewList(
    activities: List<ActivityItem>,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        if (activities.isEmpty()) {
            item {
                Text(
                    "Open Activity to see your latest notifications.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenActivity)
                        .padding(24.dp)
                )
            }
        } else {
            items(activities.take(40), key = { it.id }) { activity ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenActivity)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(activity.avatar, activity.user, Modifier.size(43.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            activity.user,
                            fontSize = 12.sp,
                            fontWeight = if (activity.isUnread) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                        Text(
                            activity.action,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(activity.time, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PremiumChatDetail(
    conversation: ChatConversation,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onProfileClick: () -> Unit,
    isConnected: Boolean
) {
    var text by rememberSaveable(conversation.partnerUsername) { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .045f))
            .statusBarsPadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onProfileClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(conversation.partnerAvatar, conversation.partnerName, Modifier.size(38.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                conversation.partnerName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VerificationDot(conversation.verificationBadge)
                        }
                        Text(
                            if (conversation.isOnline) "Online" else conversation.lastSeen,
                            fontSize = 9.sp,
                            color = if (conversation.isOnline) Color(0xFF20A968) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { }) { Icon(Icons.Default.Call, contentDescription = "Voice call") }
                IconButton(onClick = { }) { Icon(Icons.Default.Videocam, contentDescription = "Video call") }
                IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
            }
        }

        if (!isConnected) {
            Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .86f)) {
                Text(
                    "Offline — your message will stay queued and retry automatically.",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        if (conversation.messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AvatarImage(conversation.partnerAvatar, conversation.partnerName, Modifier.size(76.dp))
                Spacer(Modifier.height(10.dp))
                Text(conversation.partnerName, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Start the conversation with a message 👋",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(conversation.messages, key = { it.id }) { message ->
                    MessageBubble(message = message, onRetry = { onRetry(message) })
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
            shadowElevation = 4.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Message…") },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        val clean = text.trim()
                        if (clean.isNotEmpty()) {
                            onSend(clean)
                            text = ""
                        }
                    }),
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .64f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .64f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp)
                )
                Spacer(Modifier.width(7.dp))
                Surface(
                    shape = CircleShape,
                    color = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(enabled = text.isNotBlank()) {
                            val clean = text.trim()
                            if (clean.isNotEmpty()) {
                                onSend(clean)
                                text = ""
                            }
                        }
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (message.isFromMe) 18.dp else 5.dp,
                    topEnd = if (message.isFromMe) 5.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                color = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                tonalElevation = if (message.isFromMe) 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clickable(enabled = message.status == MessageStatus.FAILED, onClick = onRetry)
            ) {
                Text(
                    message.text,
                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            val meta = when (message.status) {
                MessageStatus.SENDING -> "Sending…"
                MessageStatus.FAILED -> "Failed • tap to retry"
                MessageStatus.SENT -> message.timestamp
            }
            Text(
                meta,
                fontSize = 8.sp,
                color = if (message.status == MessageStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoundHeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.padding(9.dp))
    }
}

@Composable
private fun AvatarImage(url: String, name: String, modifier: Modifier) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials(name),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerificationDot(badge: VerificationBadge) {
    if (badge == VerificationBadge.NONE) return
    Text(
        "✓",
        color = if (badge == VerificationBadge.GOLD) Color(0xFFFFB000) else Color(0xFF2F80ED),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun TinyPill(text: String) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color.White.copy(alpha = .20f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .28f))
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}
