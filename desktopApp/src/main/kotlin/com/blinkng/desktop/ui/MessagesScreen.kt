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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import com.blinkng.shared.BlinkChatImageQuality
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File
import java.io.FileOutputStream
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
    var forwardPhoto by remember { mutableStateOf<DesktopMessage?>(null) }
    var selectedPictures by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedPictureIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pictureCaptions by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var pictureCaptionIndex by remember { mutableStateOf(0) }
    var pictureQuality by remember { mutableStateOf(BlinkChatImageQuality.STANDARD) }
    var pictureUploading by remember { mutableStateOf(false) }
    var pictureProgress by remember { mutableStateOf(0) }
    var expandedPicture by remember { mutableStateOf<String?>(null) }
    var showMediaGallery by remember { mutableStateOf(false) }
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
            TextButton(onClick = { showMediaGallery = true }) { Text("Photos") }
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
                                if (message.messageType == "image" && !message.mediaUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = message.mediaUrl,
                                        contentDescription = "Shared picture. Click to enlarge.",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(280.dp).height(175.dp)
                                            .clickable { expandedPicture = message.mediaUrl }
                                    )
                                    if (message.content.isNotBlank() &&
                                        !message.content.equals("Image", true)) {
                                        Text(message.content)
                                    }
                                } else Text(message.content)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (mine) {
                                        val receipt = when {
                                            message.readAt != null || message.isRead -> "READ"
                                            message.deliveredAt != null -> "DELIVERED"
                                            else -> "SENT"
                                        }
                                        Text(
                                            BlinkDeliveryReceipt.ticks(receipt),
                                            fontSize = 11.sp,
                                            color = if (BlinkDeliveryReceipt.isRead(receipt))
                                                Color(0xFF3B82F6)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
                            if (message.messageType == "image" && !message.mediaUrl.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Save picture as…") },
                                    onClick = {
                                        actionMessageId = null
                                        val chooser = JFileChooser().apply {
                                            dialogTitle = "Save BLINK picture"
                                            selectedFile = File("BLINK-picture")
                                        }
                                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                            val target = chooser.selectedFile
                                            if (target.exists()) {
                                                error = "Choose a new file name to avoid overwriting a picture."
                                            } else {
                                                scope.launch {
                                                    runCatching {
                                                        FileOutputStream(target).use { output ->
                                                            state.client.copyChatImageTo(
                                                                message.id, message.conversationId, output
                                                            )
                                                        }
                                                    }.onSuccess {
                                                        error = "Picture saved to ${target.absolutePath}"
                                                    }.onFailure { ex ->
                                                        target.delete()
                                                        error = ex.message ?: "Could not save picture."
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Forward") },
                                onClick = {
                                    if (message.messageType == "image" && !message.mediaUrl.isNullOrBlank()) {
                                        forwardPhoto = message
                                    } else if (message.messageType != "text") {
                                        error = "Forwarding this media type is not available yet."
                                    } else {
                                        draft = message.content
                                        error = "Message copied into the composer. Choose another chat to forward it."
                                    }
                                    actionMessageId = null
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
            TextButton(onClick = {
                val chooser = JFileChooser().apply {
                    isMultiSelectionEnabled = true
                    fileFilter = FileNameExtensionFilter("Pictures (JPEG, PNG, GIF, WebP)",
                        "jpg", "jpeg", "png", "gif", "webp")
                    dialogTitle = "Choose up to 10 pictures"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val files = chooser.selectedFiles.toList()
                    if (files.size > 10) error = "Choose no more than 10 pictures."
                    else {
                        selectedPictures = files
                        selectedPictureIds = files.map { java.util.UUID.randomUUID().toString() }
                        pictureCaptions = emptyMap()
                        pictureCaptionIndex = 0
                    }
                }
            }) { Text("📷 Picture") }
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
        if (selectedPictures.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { if (!pictureUploading) selectedPictures = emptyList() },
                title = { Text("Send ${selectedPictures.size} picture(s)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selectedPictures.joinToString(", ") { it.name }.take(250))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectedPictures.size) { index ->
                                TextButton(onClick = { pictureCaptionIndex = index }) {
                                    Text(if (pictureCaptionIndex == index) "✓ ${index + 1}" else "${index + 1}")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = pictureCaptions[pictureCaptionIndex].orEmpty(),
                            onValueChange = { value ->
                                pictureCaptions = pictureCaptions + (pictureCaptionIndex to value.take(1024))
                            },
                            label = { Text("Caption for picture ${pictureCaptionIndex + 1}") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = {
                                pictureQuality = BlinkChatImageQuality.STANDARD
                            }) { Text(if (pictureQuality == BlinkChatImageQuality.STANDARD) "✓ Standard" else "Standard") }
                            TextButton(onClick = {
                                pictureQuality = BlinkChatImageQuality.ORIGINAL
                            }) { Text(if (pictureQuality == BlinkChatImageQuality.ORIGINAL) "✓ Original / HD" else "Original / HD") }
                        }
                        if (pictureUploading) Text("Uploading $pictureProgress%")
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !pictureUploading,
                        onClick = {
                            val photos = selectedPictures
                            val imageIds = selectedPictureIds
                            val quality = pictureQuality
                            val captions = pictureCaptions
                            val conversationId = active.id
                            pictureUploading = true
                            scope.launch {
                                var count = 0
                                try {
                                    photos.forEachIndexed { index, file ->
                                        val result = state.client.sendChatImage(
                                            conversationId, file, captions[index].orEmpty(), quality,
                                            { percent ->
                                                pictureProgress = ((index * 100 + percent) / photos.size)
                                            }, clientMessageId = imageIds[index]
                                        )
                                        if (selected?.id == conversationId) messages = messages + result
                                        count++
                                    }
                                    selectedPictures = emptyList()
                                    selectedPictureIds = emptyList()
                                    pictureProgress = 0
                                } catch (ex: Exception) {
                                    error = "Sent $count of ${photos.size} pictures. " +
                                        (ex.message ?: "Upload failed; retry remaining photos.")
                                    selectedPictures = photos.drop(count)
                                    selectedPictureIds = imageIds.drop(count)
                                    if (count > 0) {
                                        pictureCaptions = pictureCaptions
                                            .filterKeys { it >= count }
                                            .mapKeys { it.key - count }
                                        pictureCaptionIndex = 0
                                    }
                                } finally { pictureUploading = false }
                            }
                        }
                    ) { Text("Send") }
                },
                dismissButton = {
                    TextButton(enabled = !pictureUploading, onClick = { selectedPictures = emptyList(); selectedPictureIds = emptyList() }) {
                        Text("Cancel")
                    }
                }
            )
        }
        forwardPhoto?.let { sourcePicture ->
            AlertDialog(
                onDismissRequest = { forwardPhoto = null },
                title = { Text("Forward picture to…") },
                text = {
                    val eligible = conversations.filter { it.id != sourcePicture.conversationId }
                    if (eligible.isEmpty()) Text("No other conversation available.")
                    else LazyColumn(modifier = Modifier.heightIn(max = 310.dp)) {
                        items(eligible) { target ->
                            TextButton(onClick = {
                                forwardPhoto = null
                                scope.launch {
                                    var temporary: File? = null
                                    try {
                                        val staging = File.createTempFile("blink-desktop-forward-", ".staged")
                                        temporary = staging
                                        val mime = FileOutputStream(staging).use {
                                            state.client.copyChatImageTo(
                                                sourcePicture.id, sourcePicture.conversationId, it
                                            ).second
                                        }
                                        val extension = when (mime) {
                                            "image/jpeg" -> "jpg"
                                            "image/png" -> "png"
                                            "image/gif" -> "gif"
                                            "image/webp" -> "webp"
                                            else -> throw IllegalArgumentException(
                                                "This picture format cannot be forwarded on Windows."
                                            )
                                        }
                                        val proper = File(staging.parentFile,
                                            "blink-desktop-forward-${java.util.UUID.randomUUID()}.$extension")
                                        check(staging.renameTo(proper)) { "Could not stage forwarded picture." }
                                        temporary = proper
                                        state.client.sendChatImage(
                                            target.id, proper,
                                            sourcePicture.content.takeUnless {
                                                it.equals("Image", ignoreCase = true)
                                            }.orEmpty(),
                                            BlinkChatImageQuality.ORIGINAL
                                        )
                                        error = "Picture forwarded to ${target.title}."
                                    } catch (ex: Exception) {
                                        error = ex.message ?: "Could not forward picture."
                                    } finally {
                                        temporary?.delete()
                                    }
                                }
                            }) { Text(target.title, maxLines = 1) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { forwardPhoto = null }) { Text("Cancel") }
                }
            )
        }
        if (showMediaGallery) {
            AlertDialog(
                onDismissRequest = { showMediaGallery = false },
                title = { Text("Shared pictures") },
                text = {
                    val photos = messages.filter {
                        it.messageType == "image" && !it.mediaUrl.isNullOrBlank()
                    }
                    if (photos.isEmpty()) Text("No pictures in the loaded chat history.")
                    else LazyColumn(modifier = Modifier.heightIn(max = 390.dp)) {
                        items(photos) { photo ->
                            AsyncImage(
                                model = photo.mediaUrl,
                                contentDescription = "Open shared photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(230.dp).height(120.dp)
                                    .clickable { expandedPicture = photo.mediaUrl }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMediaGallery = false }) { Text("Close") }
                }
            )
        }
        expandedPicture?.let { imageUrl ->
            Dialog(onDismissRequest = { expandedPicture = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().clickable { expandedPicture = null },
                    contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Full-size shared picture",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    TextButton(
                        onClick = { expandedPicture = null },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) { Text("Close") }
                }
            }
        }

    }
    }
}
