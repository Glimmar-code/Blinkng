package com.blinkng.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopConversation
import com.blinkng.desktop.data.DesktopMessage
import com.blinkng.shared.BlinkDeliveryReceipt
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun MessagesScreen(state: DesktopAppState) {
    var conversations by remember { mutableStateOf<List<DesktopConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<DesktopConversation?>(null) }
    var messages by remember { mutableStateOf<List<DesktopMessage>>(emptyList()) }
    var lastInboxReceipts by remember { mutableStateOf<Map<String, DesktopMessage>>(emptyMap()) }
    var inboxRefreshEpoch by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var inboxSearch by remember { mutableStateOf("") }
    var showInboxSearch by remember { mutableStateOf(false) }
    var showAllInbox by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMessageId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val myUserId = state.session?.userId.orEmpty()

    suspend fun loadConversations() {
        loading = true
        runCatching { state.client.fetchConversations() }
            .onSuccess {
                conversations = it
                inboxRefreshEpoch++
                error = null
                // Opening the desktop app/inbox confirms transport delivery, not reading.
                scope.launch { runCatching { state.client.acknowledgePendingMessages() } }
            }
            .onFailure { error = it.message }
        loading = false
    }

    suspend fun loadMessages(conversation: DesktopConversation?) {
        val target = conversation ?: run {
            messages = emptyList()
            return
        }
        runCatching { state.client.fetchMessages(target.id) }
            .onSuccess {
                messages = it
                error = null
                // A read receipt is sent only after the selected conversation is visible.
                if (!target.isGroup && target.partnerUsername.isNotBlank() &&
                    selected?.id == target.id) {
                    scope.launch {
                        runCatching { state.client.markConversationReadReceipt(target.partnerUsername) }
                    }
                }
            }
            .onFailure { error = it.message }
    }

    LaunchedEffect(myUserId) {
        selected = null
        messages = emptyList()
        lastInboxReceipts = emptyMap()
        loadConversations()
    }
    LaunchedEffect(selected?.id) { loadMessages(selected) }

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(420, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(390, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(220)))
        },
        label = "desktop_chat_slide"
    ) { active ->
    if (active == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("B L I N K", fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { showInboxSearch = !showInboxSearch; if (!showInboxSearch) inboxSearch = "" }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search messages")
                }
                IconButton(onClick = { scope.launch { loadConversations() } }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh messages")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Messages", fontWeight = FontWeight.Bold, fontSize = 27.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAllInbox = !showAllInbox }) { Text(if (showAllInbox) "Close" else "See all") }
            }
            if (showInboxSearch) {
                OutlinedTextField(
                    value = inboxSearch,
                    onValueChange = { inboxSearch = it },
                    singleLine = true,
                    placeholder = { Text("Search conversations") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                )
            }
            if (!showAllInbox) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(conversations.filter { it.isOnline }.take(12), key = { "recent_${it.id}" }) { peer ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(68.dp).clickable { error = null; selected = peer }
                        ) {
                            PresenceAvatar(name = peer.title, isOnline = if (peer.isGroup) null else peer.isOnline, size = 52.dp)
                            Text(peer.title.substringBefore(" "), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                        }
                    }
                }
            }
            HorizontalDivider()
            error?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (loading) {
                    item {
                        Text(
                            "Loading conversations…",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!loading && conversations.none { inboxSearch.isBlank() || it.title.contains(inboxSearch, ignoreCase = true) }) {
                    item {
                        Text(
                            "No conversations yet.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(conversations.filter { inboxSearch.isBlank() || it.title.contains(inboxSearch, ignoreCase = true) }, key = { it.id }) { conversation ->
                    LaunchedEffect(conversation.id, conversation.lastMessageAt, myUserId, inboxRefreshEpoch) {
                        if (myUserId.isNotBlank()) {
                            val newest = runCatching { state.client.fetchLatestMessage(conversation.id) }.getOrNull()
                            if (state.session?.userId == myUserId) {
                                lastInboxReceipts = if (newest != null) {
                                    lastInboxReceipts + (conversation.id to newest)
                                } else {
                                    lastInboxReceipts - conversation.id
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            error = null
                            selected = conversation
                        },
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PresenceAvatar(
                                name = conversation.title,
                                isOnline = if (conversation.isGroup) null else conversation.isOnline,
                                size = 42.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conversation.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                val latest = lastInboxReceipts[conversation.id]
                                if (latest != null && latest.senderId == myUserId) {
                                    val status = when {
                                        latest.readAt != null || latest.isRead -> "READ"
                                        latest.deliveredAt != null -> "DELIVERED"
                                        else -> "SENT"
                                    }
                                    val statusText = BlinkDeliveryReceipt.ticks(status) + " " +
                                        status.lowercase().replaceFirstChar { it.uppercase() }
                                    Text(
                                        statusText,
                                        fontSize = 11.sp,
                                        color = if (latest.readAt != null || latest.isRead)
                                            Color(0xFF3B82F6)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    if (conversation.isGroup) "Group conversation" else desktopPresenceStatus(conversation.isOnline, conversation.lastSeenAt),
                                    fontSize = 11.sp,
                                    color = if (!conversation.isGroup && conversation.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        return@AnimatedContent
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    selected = null
                    messages = emptyList()
                    error = null
                },
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Messages")
            }
            Spacer(Modifier.width(4.dp))
            PresenceAvatar(
                name = active.title,
                isOnline = if (active.isGroup) null else active.isOnline,
                size = 40.dp,
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(active.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (active.isGroup) "Group chat" else desktopPresenceStatus(active.isOnline, active.lastSeenAt),
                    fontSize = 11.sp,
                    color = if (!active.isGroup && active.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { error = "Desktop voice-call media adapter is not active yet." }) {
                Icon(Icons.Rounded.Call, contentDescription = "Voice call")
            }
            IconButton(onClick = { error = "Desktop video-call media adapter is not active yet." }) {
                Icon(Icons.Rounded.Videocam, contentDescription = "Video call")
            }
        }
        HorizontalDivider()
        error?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == myUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    Box {
                        Surface(
                            modifier = Modifier.pointerInput(message.id) {
                                detectTapGestures(
                                    onLongPress = { actionMessageId = message.id },
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp).width(360.dp)) {
                                Text(message.content)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        when {
                                            message.readAt != null -> "Read"
                                            message.deliveredAt != null -> "Delivered"
                                            else -> "Sent"
                                        },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (message.messageType != "text") Text(message.messageType, fontSize = 10.sp)
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = actionMessageId == message.id,
                            onDismissRequest = { actionMessageId = null },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                    Text(
                                        emoji,
                                        fontSize = 18.sp,
                                        modifier = Modifier.clickable {
                                            actionMessageId = null
                                            error = "Reaction " + emoji + " selected on desktop. Sync support is not available yet."
                                        },
                                    )
                                }
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Reply") },
                                onClick = {
                                    draft = if (message.content.isBlank()) "" else "> " + message.content.take(80) + "\n"
                                    actionMessageId = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                enabled = message.content.isNotBlank(),
                                onClick = {
                                    runCatching {
                                        Toolkit.getDefaultToolkit().systemClipboard
                                            .setContents(StringSelection(message.content), null)
                                    }.onFailure { error = "Unable to copy this message." }
                                    actionMessageId = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Forward") },
                                onClick = {
                                    draft = message.content
                                    actionMessageId = null
                                    error = "Message copied into the composer. Choose another chat to forward it."
                                },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message ${active.title}") },
                maxLines = 5,
            )
            Button(
                onClick = {
                    val text = draft
                    draft = ""
                    scope.launch {
                        runCatching { state.client.sendMessage(active.id, text) }
                            .onSuccess { messages = messages + it }
                            .onFailure { error = it.message; draft = text }
                    }
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Send")
            }
        }
    }
    }
}
