package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class InboxFilter(val label: String) { All("All"), Unread("Unread"), Pinned("Pinned") }

@Composable
fun BlinkReferenceInbox(
    conversations: List<BlinkConversation>,
    actions: BlinkMessagesActions,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(InboxFilter.All) }

    val visible = remember(conversations, query, filter) {
        conversations
            .filter { c ->
                (query.isBlank() ||
                    c.name.contains(query, ignoreCase = true) ||
                    c.lastMessage.contains(query, ignoreCase = true)) &&
                    when (filter) {
                        InboxFilter.All -> true
                        InboxFilter.Unread -> c.unread > 0
                        InboxFilter.Pinned -> c.pinned
                    }
            }
            .sortedByDescending { it.pinned }
    }

    Box(modifier.fillMaxSize().background(BlinkChatColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Messages", color = Color.White, fontSize = 30.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                    Icon(
                        if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                        "Search", tint = Color.White,
                    )
                }
            }

            AnimatedVisibility(
                visible = searchOpen,
                enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(24.dp)).background(BlinkChatColors.Panel)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = BlinkChatColors.Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search chats", color = BlinkChatColors.Muted, fontSize = 16.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(BlinkChatColors.Accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InboxFilter.entries.forEach { f ->
                    val selected = f == filter
                    val count = when (f) {
                        InboxFilter.Unread -> conversations.count { it.unread > 0 }
                        else -> 0
                    }
                    Box(
                        Modifier.clip(CircleShape)
                            .background(if (selected) BlinkChatColors.Accent else BlinkChatColors.Panel)
                            .combinedClickable(onClick = { filter = f })
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (count > 0) "${f.label} $count" else f.label,
                            color = Color.White, fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) "No chats match “$query”" else "No conversations yet",
                        color = BlinkChatColors.Muted, fontSize = 16.sp,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(visible, key = { _, c -> c.id }) { index, c ->
                        InboxRow(c, index, actions, onOpen)
                    }
                }
            }
        }

        // New message FAB
        val fab = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(250)
            fab.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp)
                .graphicsLayer { scaleX = fab.value; scaleY = fab.value }
                .size(60.dp).clip(CircleShape).background(BlinkChatColors.Accent)
                .combinedClickable(onClick = { actions.onNewMessage() }),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Edit, "New message", tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxRow(
    c: BlinkConversation,
    index: Int,
    actions: BlinkMessagesActions,
    onOpen: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 35L).coerceAtMost(280L))
        appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    val unread = c.unread > 0

    Box(Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 36f }) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { onOpen(c.id) }, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BlinkAvatar(c.name, c.avatarUrl, 58.dp, storyRing = c.hasStory, online = c.online)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name, color = Color.White, fontSize = 17.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        c.timeLabel, fontSize = 12.sp,
                        color = if (unread) BlinkChatColors.Accent else BlinkChatColors.Muted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    c.lastStatus?.let {
                        DeliveryTicks(it, BlinkChatColors.Muted, size = 17.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        c.lastMessage, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (unread) Color(0xFFE6E6EA) else BlinkChatColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (c.muted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.NotificationsOff, "Muted", tint = BlinkChatColors.Muted, modifier = Modifier.size(16.dp))
                    }
                    if (c.pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.PushPin, "Pinned", tint = BlinkChatColors.Muted,
                            modifier = Modifier.size(16.dp).rotate(45f),
                        )
                    }
                    AnimatedVisibility(
                        visible = unread,
                        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        Box(
                            Modifier.padding(start = 8.dp).heightIn(min = 22.dp)
                                .clip(CircleShape)
                                .background(if (c.muted) BlinkChatColors.Muted else BlinkChatColors.Accent)
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (c.unread > 99) "99+" else "${c.unread}",
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menu, onDismissRequest = { menu = false },
            modifier = Modifier.background(Color(0xFF202024)),
        ) {
            DropdownMenuItem(
                text = { Text(if (c.pinned) "Unpin" else "Pin", color = Color.White) },
                onClick = { menu = false; actions.onTogglePin(c.id) },
            )
            DropdownMenuItem(
                text = { Text(if (c.muted) "Unmute" else "Mute", color = Color.White) },
                onClick = { menu = false; actions.onToggleMute(c.id) },
            )
            if (unread) DropdownMenuItem(
                text = { Text("Mark as read", color = Color.White) },
                onClick = { menu = false; actions.onMarkRead(c.id) },
            )
        }
    }
}
