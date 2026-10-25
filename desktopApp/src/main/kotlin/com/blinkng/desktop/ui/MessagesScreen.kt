package com.blinkng.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import com.blinkng.shared.BlinkChatImageQuality
import com.blinkng.shared.BlinkChatPhotoAlbum
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File
import java.io.FileOutputStream
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skiko.toComposeImageBitmap
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
import androidx.compose.runtime.DisposableEffect
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
import com.blinkng.desktop.data.DesktopChatImageOutbox
import com.blinkng.desktop.data.DesktopChatPhotoWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedAlbumSize by remember { mutableStateOf(1) }
    var selectedAlbumPositions by remember { mutableStateOf<List<Int>>(emptyList()) }
    var pictureCaptions by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var pictureCaptionIndex by remember { mutableStateOf(0) }
    var pictureQuality by remember { mutableStateOf(BlinkChatImageQuality.STANDARD) }
    var selectedPictureViewOnce by remember { mutableStateOf(false) }
    var privatePicture by remember { mutableStateOf<SkiaImage?>(null) }
    var consumedPrivatePictures by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pictureUploading by remember { mutableStateOf(false) }
    var pictureProgress by remember { mutableStateOf(0) }
    var queuedPhotoCount by remember { mutableStateOf(0) }
    var outboxUploading by remember { mutableStateOf(false) }
    var expandedPicture by remember { mutableStateOf<String?>(null) }
    var showMediaGallery by remember { mutableStateOf(false) }
    var fullGallery by remember { mutableStateOf<List<DesktopMessage>>(emptyList()) }
    var fullGalleryOffset by remember { mutableStateOf(0) }
    var fullGalleryMore by remember { mutableStateOf(true) }
    var fullGalleryBusy by remember { mutableStateOf(false) }
    var expandedGalleryIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    fun loadMoreChatPictures() {
        val id = selected?.id ?: return
        if (fullGalleryBusy || !fullGalleryMore) return
        fullGalleryBusy = true
        scope.launch {
            runCatching { state.client.fetchChatImagePage(id, fullGalleryOffset) }
                .onSuccess { batch ->
                    if (selected?.id == id) {
                        fullGallery = (fullGallery + batch).distinctBy { it.id }
                        fullGalleryOffset += batch.size
                        fullGalleryMore = batch.size >= 36
                    }
                }
                .onFailure { error = it.message ?: "Cannot load chat photos." }
            fullGalleryBusy = false
        }
    }
    LaunchedEffect(showMediaGallery, selected?.id) {
        if (showMediaGallery) {
            fullGallery = emptyList()
            fullGalleryOffset = 0
            fullGalleryMore = true
            loadMoreChatPictures()
        }
    }

    val myUserId = state.session?.userId.orEmpty()
    fun attemptQueuedPhotoUploads() {
        val account = myUserId
        if (account.isBlank() || outboxUploading) return
        // Prefer a detached user-scoped worker so uploads survive closing the window.
        // The previous in-process queue remains the fallback for developer builds
        // whose platform launcher cannot start another BLINK process.
        if (DesktopChatPhotoWorker.start(account)) return
        outboxUploading = true
        scope.launch {
            try {
                val queued = withContext(Dispatchers.IO) {
                    DesktopChatImageOutbox.pending(account)
                }
                queuedPhotoCount = queued.size
                for (pending in queued) {
                    if (state.session?.userId != account) break
                    try {
                        val result = state.client.sendChatImage(
                            pending.conversationId, pending.file, pending.caption, pending.quality,
                            onProgress = { progress ->
                                scope.launch { pictureProgress = progress }
                            },
                            clientMessageId = pending.messageId,
                            album = pending.album
                        )
                        withContext(Dispatchers.IO) {
                            DesktopChatImageOutbox.complete(pending)
                        }
                        if (selected?.id == pending.conversationId &&
                            messages.none { it.id == result.id }) {
                            messages = messages + result
                        }
                        queuedPhotoCount--
                    } catch (ex: Exception) {
                        error = ex.message ?: "Picture upload paused. Tap Retry photos."
                        break
                    }
                }
            } finally {
                queuedPhotoCount = runCatching {
                    withContext(Dispatchers.IO) {
                        DesktopChatImageOutbox.pending(account).size
                    }
                }.getOrDefault(queuedPhotoCount)
                pictureProgress = 0
                outboxUploading = false
            }
        }
    }
    LaunchedEffect(myUserId) {
        if (myUserId.isNotBlank()) {
            queuedPhotoCount = runCatching {
                withContext(Dispatchers.IO) {
                    DesktopChatImageOutbox.pending(myUserId).size
                }
            }.getOrDefault(0)
            attemptQueuedPhotoUploads()
        }
    }


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

    LaunchedEffect(myUserId) {
        while (myUserId.isNotBlank() && state.session?.userId == myUserId) {
            val pending = runCatching {
                withContext(Dispatchers.IO) {
                    DesktopChatImageOutbox.pending(myUserId).size to
                        DesktopChatImageOutbox.pendingProgress(myUserId)
                }
            }.getOrNull()
            if (pending != null) {
                val previousCount = queuedPhotoCount
                queuedPhotoCount = pending.first
                pictureProgress = pending.second ?: 0
                if (previousCount > queuedPhotoCount) {
                    loadMessages(selected)
                    loadConversations()
                }
            }
            delay(2_000L)
        }
    }

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
            if (queuedPhotoCount > 0) {
                TextButton(enabled = !outboxUploading,
                    onClick = { attemptQueuedPhotoUploads() }
                ) {
                    Text(if (outboxUploading) "Uploading $pictureProgress%"
                         else "Retry $queuedPhotoCount photos")
                }
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
        val photoGroups = messages.filter {
            it.messageType == "image" &&
                it.imageAlbumId != null && it.imageAlbumSize in 2..10 &&
                !it.mediaUrl.isNullOrBlank()
        }.groupBy { it.imageAlbumId }
        val renderedMessages = messages.filter { message ->
            val group = message.imageAlbumId?.let(photoGroups::get).orEmpty()
            group.size < 2 || group.first().id == message.id
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(renderedMessages, key = { it.id }) { message ->
                val albumPhotos = message.imageAlbumId?.let(photoGroups::get).orEmpty()
                    .sortedBy { it.imageAlbumPosition }
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
                                if (albumPhotos.size > 1) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        albumPhotos.take(4).chunked(2).forEachIndexed { row, pair ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                pair.forEachIndexed { col, photo ->
                                                    val photoIndex = row * 2 + col
                                                    Box(modifier = Modifier.width(145.dp).height(110.dp)) {
                                                        AsyncImage(
                                                            model = photo.imagePreviewUrl ?: photo.mediaUrl,
                                                            contentDescription = "Album picture ${photoIndex + 1}",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                                .clickable {
                                                                    fullGallery = albumPhotos
                                                                    expandedGalleryIndex = photoIndex
                                                                    scope.launch {
                                                                        expandedPicture = state.client.freshChatImageUrl(
                                                                            photo.id, photo.conversationId
                                                                        ) ?: photo.mediaUrl
                                                                    }
                                                                }
                                                        )
                                                        if (photoIndex == 3 && albumPhotos.size > 4) {
                                                            Text(
                                                                "+${albumPhotos.size - 4}",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.align(Alignment.BottomEnd)
                                                                    .padding(8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Text("${albumPhotos.size} pictures", fontSize = 11.sp)
                                    }
                                } else if (message.imageViewOnce) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(if (mine) "🔒 View-once picture sent" else
                                            if (message.id in consumedPrivatePictures)
                                                "View-once picture opened" else "🔒 View-once picture")
                                        if (!mine && message.id !in consumedPrivatePictures) {
                                            TextButton(onClick = {
                                                consumedPrivatePictures = consumedPrivatePictures + message.id
                                                scope.launch {
                                                    runCatching {
                                                        val bytes = state.client.openViewOnceImage(message.id)
                                                        try { SkiaImage.makeFromEncoded(bytes) }
                                                        finally { bytes.fill(0) }
                                                    }.onSuccess { privatePicture = it }
                                                        .onFailure { error = it.message ?: "Private picture unavailable." }
                                                }
                                            }) { Text("Open once") }
                                        }
                                    }
                                } else if (message.messageType == "image" && !message.mediaUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = message.imagePreviewUrl ?: message.mediaUrl,
                                        contentDescription = "Shared picture. Click to enlarge.",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(280.dp).height(175.dp)
                                            .clickable {
                                                scope.launch {
                                                    expandedPicture = state.client.freshChatImageUrl(
                                                        message.id, message.conversationId
                                                    ) ?: message.mediaUrl
                                                }
                                            }
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
                    fileFilter = FileNameExtensionFilter("Pictures (JPEG, PNG, GIF, WebP, HEIC/HEIF)",
                        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif")
                    dialogTitle = "Choose up to 10 pictures"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val files = chooser.selectedFiles.toList()
                    if (files.size > 10) error = "Choose no more than 10 pictures."
                    else {
                        selectedPictures = files
                        selectedPictureIds = files.map { java.util.UUID.randomUUID().toString() }
                        selectedAlbumId = java.util.UUID.randomUUID().toString().takeIf {
                            files.size > 1
                        }
                        selectedAlbumSize = files.size
                        selectedAlbumPositions = files.indices.toList()
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
                        if (selectedPictures.size == 1) {
                            TextButton(onClick = {
                                selectedPictureViewOnce = !selectedPictureViewOnce
                            }) { Text(if (selectedPictureViewOnce) "✓ View once" else "View once") }
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
                            val batchId = selectedAlbumId
                            val batchSize = selectedAlbumSize
                            val batchPositions = selectedAlbumPositions
                            val quality = pictureQuality
                            val captions = pictureCaptions
                            val conversationId = active.id
                            pictureUploading = true
                            scope.launch {
                                var staged = 0
                                try {
                                    if (selectedPictureViewOnce && photos.size == 1) {
                                        val privateMessage = state.client.sendViewOnceImage(
                                            conversationId, photos[0], captions[0].orEmpty(), imageIds[0]
                                        )
                                        if (selected?.id == conversationId) {
                                            messages = messages + privateMessage
                                        }
                                        selectedPictureViewOnce = false
                                        selectedPictures = emptyList()
                                        selectedPictureIds = emptyList()
                                        selectedAlbumId = null
                                        selectedAlbumPositions = emptyList()
                                    } else {
                                    withContext(Dispatchers.IO) {
                                        photos.forEachIndexed { index, file ->
                                            DesktopChatImageOutbox.enqueue(
                                                myUserId, conversationId, imageIds[index], file,
                                                captions[index].orEmpty(), quality,
                                                if (batchId != null && batchSize > 1)
                                                    BlinkChatPhotoAlbum.of(
                                                        batchId, batchPositions[index], batchSize
                                                    )
                                                else BlinkChatPhotoAlbum.single()
                                            )
                                            staged++
                                        }
                                    }
                                    selectedPictures = emptyList()
                                    selectedPictureIds = emptyList()
                                    selectedAlbumId = null
                                    selectedAlbumPositions = emptyList()
                                    queuedPhotoCount += staged
                                    attemptQueuedPhotoUploads()
                                    }
                                } catch (ex: Exception) {
                                    error = ex.message ?: "Picture could not be sent."
                                    // A view-once failure never falls back to a regular upload.
                                    selectedPictures = photos.drop(staged)
                                    selectedPictureIds = imageIds.drop(staged)
                                    selectedAlbumPositions = batchPositions.drop(staged)
                                    queuedPhotoCount += staged
                                    if (staged > 0) attemptQueuedPhotoUploads()
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
                    if (fullGallery.isEmpty() && !fullGalleryBusy) {
                        Text("No pictures found or media could not be loaded.")
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 390.dp)) {
                        items(fullGallery) { photo ->
                            AsyncImage(
                                model = photo.mediaUrl,
                                contentDescription = "Open shared photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(230.dp).height(120.dp)
                                    .clickable {
                                        expandedGalleryIndex = fullGallery.indexOfFirst {
                                            it.id == photo.id
                                        }.coerceAtLeast(0)
                                        scope.launch {
                                            expandedPicture = state.client.freshChatImageUrl(
                                                photo.id, photo.conversationId
                                            ) ?: photo.mediaUrl
                                        }
                                    }
                            )
                        }
                        if (fullGalleryMore || fullGalleryBusy) {
                            item {
                                TextButton(enabled = !fullGalleryBusy, onClick = {
                                    loadMoreChatPictures()
                                }) {
                                    Text(if (fullGalleryBusy) "Loading…" else "Older pictures")
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMediaGallery = false }) { Text("Close") }
                }
            )
        }
        privatePicture?.let { privateImage ->
                DisposableEffect(privateImage) {
                    onDispose { privateImage.close() }
                }
                Dialog(
                    onDismissRequest = { privatePicture = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ComposeImage(
                            bitmap = privateImage.toComposeImageBitmap(),
                            contentDescription = "One-time private picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        TextButton(
                            onClick = { privatePicture = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        ) { Text("Close", color = Color.White) }
                    }
                }
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
                    expandedGalleryIndex?.let { index ->
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(enabled = index > 0, onClick = {
                                val previous = fullGallery[index - 1]
                                expandedGalleryIndex = index - 1
                                scope.launch {
                                    expandedPicture = state.client.freshChatImageUrl(
                                        previous.id, previous.conversationId
                                    ) ?: previous.mediaUrl
                                }
                            }) { Text("← Previous") }
                            Text("${index + 1} / ${fullGallery.size}")
                            TextButton(enabled = index < fullGallery.lastIndex, onClick = {
                                val next = fullGallery[index + 1]
                                expandedGalleryIndex = index + 1
                                scope.launch {
                                    expandedPicture = state.client.freshChatImageUrl(
                                        next.id, next.conversationId
                                    ) ?: next.mediaUrl
                                }
                            }) { Text("Next →") }
                        }
                    }
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
