package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopConversation
import com.blinkng.desktop.data.DesktopMessage
import com.blinkng.shared.BlinkChatInboxPolicy
import com.blinkng.shared.BlinkMessageInbox
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant

@Composable
fun MessagesScreen(state: DesktopAppState) {
    var conversations by remember { mutableStateOf<List<DesktopConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<DesktopConversation?>(null) }
    var messages by remember { mutableStateOf<List<DesktopMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var chatQuery by remember { mutableStateOf("") }
    var chatSearchVisible by remember { mutableStateOf(false) }
    var showContactCard by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMessageId by remember { mutableStateOf<String?>(null) }
    var inbox by remember { mutableStateOf(BlinkMessageInbox.PRIMARY) }
    var controlsBusy by remember { mutableStateOf(false) }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }
    var forwardUsername by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val myUserId = state.session?.userId.orEmpty()

    suspend fun loadConversations() {
        loading = true
        runCatching { state.client.fetchConversations() }
            .onSuccess {
                conversations = it
                selected?.id?.let { id -> selected = it.firstOrNull { conversation -> conversation.id == id } }
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    fun changeControl(action: suspend () -> Boolean) {
        if (controlsBusy) return
        controlsBusy = true
        scope.launch {
            runCatching { check(action()) { "The conversation action was not applied." }; loadConversations() }
                .onFailure { error = it.message }
            controlsBusy = false
        }
    }

    suspend fun loadMessages(conversation: DesktopConversation?) {
        val target = conversation ?: run {
            messages = emptyList()
            return
        }
        runCatching { state.client.fetchMessages(target.id) }
            .onSuccess { messages = it; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { loadConversations() }
    LaunchedEffect(selected?.id) {
        showContactCard = false
        chatSearchVisible = false
        chatQuery = ""
        loadMessages(selected)
    }

    val active = selected
    if (active == null) {
        val filtered = remember(conversations, query, inbox) {
            conversations.filter {
                BlinkChatInboxPolicy.matches(inbox, it.isArchived, if (it.requestStatus == "pending") "requests" else "primary") &&
                    (query.isBlank() || it.title.contains(query, ignoreCase = true))
            }.sortedWith(compareByDescending<DesktopConversation> { it.isPinned }.thenByDescending { it.lastMessageAt.orEmpty() })
        }
        val important = remember(conversations) {
            conversations.filter { !it.isArchived && it.requestStatus != "pending" }.sortedWith(
                compareByDescending<DesktopConversation> { it.isOnline }
                    .thenByDescending { it.lastMessageAt.orEmpty() }
            ).take(6)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    "Messages",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = { scope.launch { loadConversations() } },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh messages")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search by name or conversation") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            Text(
                "Important",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp)
            )

            if (important.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(important, key = { "important_${it.id}" }) { conversation ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(76.dp).clickable {
                                error = null
                                selected = conversation
                            }
                        ) {
                            PresenceAvatar(
                                name = conversation.title,
                                isOnline = if (conversation.isGroup) null else conversation.isOnline,
                                size = 48.dp
                            )
                            Text(
                                conversation.title,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                }
            }

            Text(
                "Inbox",
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp)
            )
            HorizontalDivider()

            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlinkMessageInbox.entries.forEach { tab ->
                    FilterChip(selected = inbox == tab, onClick = { inbox = tab },
                        label = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }

            error?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (loading) {
                    item {
                        Text(
                            "Loading conversations…",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!loading && filtered.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "No conversations yet." else "No conversations found.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(filtered, key = { it.id }) { conversation ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            error = null
                            selected = conversation
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PresenceAvatar(
                                name = conversation.title,
                                isOnline = if (conversation.isGroup) null else conversation.isOnline,
                                size = 46.dp
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text((if (conversation.isPinned) "Pinned · " else "") + conversation.title,
                                    fontWeight = if (conversation.markedUnread || conversation.unreadCount > 0) FontWeight.Black else FontWeight.SemiBold,
                                    maxLines = 1)
                                Text(
                                    if (conversation.isGroup) "Group conversation"
                                    else desktopPresenceStatus(conversation.isOnline, conversation.lastSeenAt),
                                    fontSize = 11.sp,
                                    color = if (!conversation.isGroup && conversation.isOnline) Color(0xFF22C55E)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val visibleMessages = remember(messages, chatQuery) {
        if (chatQuery.isBlank()) messages
        else messages.filter { it.content.contains(chatQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = {
                        selected = null
                        messages = emptyList()
                        error = null
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Messages")
                }

                Column(
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 320.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(active.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                    Text(
                        if (active.isGroup) "Group chat" else desktopPresenceStatus(active.isOnline, active.lastSeenAt),
                        fontSize = 11.sp,
                        color = if (!active.isGroup && active.isOnline) Color(0xFF22C55E)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.clickable { showContactCard = true }) {
                        PresenceAvatar(
                            name = active.title,
                            isOnline = if (active.isGroup) null else active.isOnline,
                            size = 40.dp
                        )
                    }
                    IconButton(onClick = { showContactCard = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Chat profile and options")
                    }
                }
            }
            HorizontalDivider()

            if (chatSearchVisible) {
                OutlinedTextField(
                    value = chatQuery,
                    onValueChange = { chatQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Search this chat") },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp)
                )
            }

            error?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleMessages, key = { it.id }) { message ->
                    val mine = message.senderId == myUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Box {
                            Surface(
                                modifier = Modifier.pointerInput(message.id) {
                                    detectTapGestures(onLongPress = { actionMessageId = message.id })
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (message.messageType != "text") Text(message.messageType, fontSize = 10.sp)
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = actionMessageId == message.id,
                                onDismissRequest = { actionMessageId = null }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                        Text(
                                            emoji,
                                            fontSize = 18.sp,
                                            modifier = Modifier.clickable {
                                                actionMessageId = null
                                                error = "Reaction ${emoji} selected. Desktop reaction sync is pending."
                                            }
                                        )
                                    }
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Reply") },
                                    onClick = {
                                        draft = if (message.content.isBlank()) "" else "> " + message.content.take(80) + "\n"
                                        actionMessageId = null
                                    }
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
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Forward") },
                                    onClick = {
                                        forwardMessageId = message.id
                                        forwardUsername = ""
                                        actionMessageId = null
                                    }
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 5,
                    shape = RoundedCornerShape(26.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(46.dp)
                ) {
                    IconButton(
                        onClick = {
                            val text = draft
                            draft = ""
                            scope.launch {
                                runCatching { state.client.sendMessage(active.id, text) }
                                    .onSuccess { messages = messages + it }
                                    .onFailure { error = it.message; draft = text }
                            }
                        },
                        enabled = draft.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        if (showContactCard) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(370.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showContactCard = false }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to chat")
                        }
                        Text(
                            "Chat profile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.size(28.dp))
                    Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        PresenceAvatar(
                            name = active.title,
                            isOnline = if (active.isGroup) null else active.isOnline,
                            size = 84.dp
                        )
                    }
                    Text(
                        active.title,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (active.isGroup) "Group conversation" else desktopPresenceStatus(active.isOnline, active.lastSeenAt),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 3.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.size(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ContactActionButton(
                            label = "Audio",
                            icon = Icons.Rounded.Call,
                            modifier = Modifier.weight(1f)
                        ) {
                            error = "Desktop voice-call media adapter is not active yet."
                            showContactCard = false
                        }
                        ContactActionButton(
                            label = "Video",
                            icon = Icons.Rounded.Videocam,
                            modifier = Modifier.weight(1f)
                        ) {
                            error = "Desktop video-call media adapter is not active yet."
                            showContactCard = false
                        }
                        ContactActionButton(
                            label = "Mute",
                            icon = Icons.Rounded.VolumeOff,
                            modifier = Modifier.weight(1f)
                        ) {
                            changeControl { state.client.setChatNotifications(active.id,
                                if (active.notificationMode == "none") "all" else "none") }
                            showContactCard = false
                        }
                        ContactActionButton(
                            label = "Search",
                            icon = Icons.Rounded.Search,
                            modifier = Modifier.weight(1f)
                        ) {
                            chatSearchVisible = true
                            showContactCard = false
                        }
                    }

                    Spacer(Modifier.size(20.dp))
                    HorizontalDivider()
                    if (active.controlsAvailable) {
                        TextButton(enabled = !controlsBusy, onClick = {
                            changeControl { state.client.setChatInboxState(active.id, pinned = !active.isPinned) }
                        }) { Text(if (active.isPinned) "Unpin conversation" else "Pin conversation") }
                        TextButton(enabled = !controlsBusy, onClick = {
                            changeControl { state.client.setChatInboxState(active.id, archived = !active.isArchived) }
                        }) { Text(if (active.isArchived) "Move to Primary" else "Archive conversation") }
                        TextButton(enabled = !controlsBusy, onClick = {
                            changeControl { state.client.setChatInboxState(active.id, unread = !active.markedUnread) }
                        }) { Text(if (active.markedUnread) "Clear unread marker" else "Mark unread") }
                        TextButton(enabled = !controlsBusy, onClick = {
                            changeControl { state.client.setChatNotifications(active.id, "none", Instant.now().plusSeconds(3600).toString()) }
                        }) { Text("Mute for one hour") }
                        TextButton(enabled = !controlsBusy, onClick = {
                            changeControl { state.client.setChatNotifications(active.id, "mentions") }
                        }) { Text("Notify for mentions only") }
                        if (active.requestStatus == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !controlsBusy, onClick = {
                                    changeControl { state.client.respondMessageRequest(active.id, true) }
                                }) { Text("Accept request") }
                                TextButton(enabled = !controlsBusy, onClick = {
                                    changeControl { state.client.respondMessageRequest(active.id, false) }
                                }) { Text("Decline") }
                            }
                        }
                    }
                }
            }
        }
    }
    forwardMessageId?.let { messageId ->
        AlertDialog(
            onDismissRequest = { if (!controlsBusy) forwardMessageId = null },
            title = { Text("Forward message") },
            text = { OutlinedTextField(value = forwardUsername, onValueChange = { forwardUsername = it }, label = { Text("Recipient username") }, singleLine = true) },
            confirmButton = { Button(enabled = !controlsBusy && forwardUsername.isNotBlank(), onClick = {
                if (!controlsBusy) {
                    controlsBusy = true
                    val username = forwardUsername
                    scope.launch {
                        runCatching { state.client.forwardChatMessage(messageId, username) }
                            .onSuccess { forwardMessageId = null; loadConversations() }
                            .onFailure { error = it.message }
                        controlsBusy = false
                    }
                }
            }) { Text("Forward") } },
            dismissButton = { TextButton(enabled = !controlsBusy, onClick = { forwardMessageId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ContactActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}
