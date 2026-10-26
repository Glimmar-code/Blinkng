package com.blinkng.desktop.ui

import com.blinkng.shared.BlinkDesignTokens

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.runtime.DisposableEffect
import coil3.compose.AsyncImage
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.blinkng.desktop.data.DesktopChatImageOutbox
import com.blinkng.desktop.data.DesktopChatPhotoWorker
import com.blinkng.desktop.data.DesktopStickerLibrary
import com.blinkng.shared.BlinkChatImageQuality
import com.blinkng.shared.BlinkChatPhotoAlbum
import javax.swing.JFileChooser
import java.util.UUID
import java.io.File
import org.jetbrains.skia.Image as SkiaImage
import com.blinkng.shared.BlinkChatInboxPolicy
import com.blinkng.shared.BlinkConversationSearchPolicy
import com.blinkng.shared.BlinkMessageScrollAction
import com.blinkng.shared.BlinkMessageDatePolicy
import com.blinkng.shared.BlinkMessageScrollPolicy
import com.blinkng.shared.BlinkMessageInbox
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant

private data class DesktopSelectedPhoto(val file: File, val caption: String = "")

@Composable
fun MessagesScreen(state: DesktopAppState) {
    val myUserId = state.session?.userId.orEmpty()
    // Fail closed for network previews until the real account preference loads.
    var linkPreviewsAllowed by remember(myUserId) { mutableStateOf(false) }
    var linkPreviewSaving by remember(myUserId) { mutableStateOf(false) }
    LaunchedEffect(myUserId) {
        if (myUserId.isNotBlank()) {
            linkPreviewsAllowed = runCatching { state.client.fetchChatLinkPreviewsEnabled() }
                .getOrDefault(false)
        }
    }

    var conversations by remember(myUserId) { mutableStateOf<List<DesktopConversation>>(emptyList()) }
    var selected by remember(myUserId) { mutableStateOf<DesktopConversation?>(null) }
    var messages by remember(myUserId) { mutableStateOf<List<DesktopMessage>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var inboxShowSearch by remember { mutableStateOf(false) }
    var inboxShowAll by remember { mutableStateOf(false) }
    var chatQuery by remember { mutableStateOf("") }
    var chatSearchVisible by remember { mutableStateOf(false) }
    var showContactCard by remember { mutableStateOf(false) }
    var showGroupMembers by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var olderLoading by remember { mutableStateOf(false) }
    var hasMoreOlder by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewGroup by remember { mutableStateOf(false) }
    var actionMessageId by remember { mutableStateOf<String?>(null) }
    var inbox by remember { mutableStateOf(BlinkMessageInbox.PRIMARY) }
    var controlsBusy by remember { mutableStateOf(false) }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }
    var forwardUsername by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<DesktopMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deleteMessage by remember { mutableStateOf<DesktopMessage?>(null) }
    var deleteForEveryone by remember { mutableStateOf(false) }
    var reportMessage by remember { mutableStateOf<DesktopMessage?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<DesktopMessage?>(null) }
    var privatePicture by remember(myUserId) { mutableStateOf<SkiaImage?>(null) }
    var viewedChatPhoto by remember(myUserId) { mutableStateOf<DesktopMessage?>(null) }
    var aiImageFile by remember(myUserId) { mutableStateOf<File?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var showStickerTray by remember(myUserId) { mutableStateOf(false) }
    var stickerRevision by remember(myUserId) { mutableStateOf(0) }
    var stickerToDelete by remember(myUserId) { mutableStateOf<File?>(null) }
    var photoReviewItems by remember(myUserId) {
        mutableStateOf<List<DesktopSelectedPhoto>>(emptyList())
    }
    var desktopEditingPhoto by remember(myUserId) { mutableStateOf<DesktopSelectedPhoto?>(null) }
    var photoReviewQuality by remember(myUserId) {
        mutableStateOf(BlinkChatImageQuality.STANDARD)
    }
    var photoReviewDestination by remember(myUserId) { mutableStateOf<String?>(null) }
    var outboxPending by remember { mutableStateOf(0) }
    var outboxPercent by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var showGroupInvitations by remember(myUserId) { mutableStateOf(false) }
    val viewedOncePhotos = remember(myUserId) { mutableStateMapOf<String, Boolean>() }
    var latestInboxReceipts by remember(myUserId) { mutableStateOf<Map<String, DesktopMessage>>(emptyMap()) }
    var receiptRefreshEpoch by remember(myUserId) { mutableStateOf(0) }
    // Drafts must never cross conversations or accounts, including after switching chats.
    val draftsByConversation = remember(myUserId) { mutableStateMapOf<String, String>() }
    val sendingConversations = remember(myUserId) { mutableStateMapOf<String, Boolean>() }

    suspend fun loadConversations() {
        val owner = state.session?.userId.orEmpty()
        if (owner.isBlank()) return
        loading = true
        runCatching { state.client.fetchConversations() }
            .onSuccess {
                if (state.session?.userId == owner) {
                    conversations = it
                    receiptRefreshEpoch++
                    selected?.id?.let { id -> selected = it.firstOrNull { conversation -> conversation.id == id } }
                    error = null
                    // Recipient acknowledged device delivery, not chat read.
                    scope.launch { runCatching { state.client.acknowledgePendingMessageDeliveries() } }
                }
            }
            .onFailure { if (state.session?.userId == owner) error = it.message }
        if (state.session?.userId == owner) loading = false
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
        val owner = state.session?.userId.orEmpty()
        if (owner.isBlank()) return
        runCatching { state.client.fetchMessages(target.id) }
            .onSuccess {
                // A slow response from chat A must never repaint chat B (or another account).
                if (state.session?.userId == owner && selected?.id == target.id) {
                    messages = it
                    hasMoreOlder = it.size >= 100
                    error = null
                    if (!target.isGroup && state.settings?.readReceipts == true &&
                        !target.partnerUsername.isNullOrBlank()) {
                        scope.launch {
                            runCatching {
                                state.client.markVisibleConversationRead(target.partnerUsername!!)
                            }
                        }
                    }
                }
            }
            .onFailure {
                if (state.session?.userId == owner && selected?.id == target.id) error = it.message
            }
    }

    fun chooseChatPhoto(viewOnce: Boolean) {
        val account = state.session?.userId ?: return
        val target = selected ?: return
        if (target.isGroup || target.requestStatus == "pending") {
            error = "Photos are available in accepted direct chats."
            return
        }
        val chooser = JFileChooser().apply {
            dialogTitle = if (viewOnce) "Choose a view-once picture" else "Choose a chat picture"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = !viewOnce
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val picture = chooser.selectedFile?.takeIf { it.isFile } ?: return
        if (photoBusy) return
        if (viewOnce) {
            photoBusy = true
            scope.launch {
                runCatching {
                    state.client.sendViewOnceImage(target.id, picture, "")
                }.onSuccess { sent ->
                    if (state.session?.userId == account && selected?.id == target.id) {
                        messages = messages.filterNot { it.id == sent.id } + sent
                    }
                }.onFailure { failure ->
                    if (state.session?.userId == account) error = failure.message
                }
                photoBusy = false
            }
        } else {
            val pictures = chooser.selectedFiles.toList()
                .ifEmpty { listOfNotNull(chooser.selectedFile) }.filter { it.isFile }
            if (pictures.isEmpty() || pictures.size > 10) {
                error = "Choose between 1 and 10 pictures."
                return
            }
            photoReviewDestination = target.id
            photoReviewQuality = BlinkChatImageQuality.STANDARD
            photoReviewItems = pictures.map { DesktopSelectedPhoto(it) }
        }
    }

    LaunchedEffect(myUserId) {
        if (myUserId.isNotBlank()) {
            val staged = DesktopChatImageOutbox.pending(myUserId)
            if (staged.isNotEmpty()) DesktopChatPhotoWorker.start(myUserId)
            while (state.session?.userId == myUserId && myUserId.isNotBlank()) {
                val pending = DesktopChatImageOutbox.pending(myUserId).size
                if (pending != outboxPending) {
                    outboxPending = pending
                    if (pending == 0 && selected != null) loadMessages(selected)
                }
                outboxPercent = DesktopChatImageOutbox.pendingProgress(myUserId)
                kotlinx.coroutines.delay(2500)
            }
        }
    }

    fun loadOlderMessages() {
        if (olderLoading || !hasMoreOlder || messages.isEmpty() || chatQuery.isNotBlank()) return
        val target = selected ?: return
        val owner = state.session?.userId ?: return
        val earliest = messages.first()
        olderLoading = true
        scope.launch {
            runCatching {
                state.client.fetchMessages(target.id, earliest.createdAt, earliest.id)
            }.onSuccess { older ->
                if (state.session?.userId == owner && selected?.id == target.id) {
                    // Keep server ordering and deduplicate rows replayed at a cursor boundary.
                    val knownIds = messages.map { it.id }.toHashSet()
                    messages = older.filterNot { it.id in knownIds } + messages
                    hasMoreOlder = older.size >= 100
                }
            }.onFailure {
                if (state.session?.userId == owner && selected?.id == target.id) error = it.message
            }
            olderLoading = false
        }
    }

    fun changeMessageControl(conversationId: String, action: suspend () -> Boolean) {
        if (controlsBusy) return
        controlsBusy = true
        val owner = state.session?.userId
        scope.launch {
            runCatching {
                check(action()) { "The message action could not be saved." }
                if (state.session?.userId == owner && selected?.id == conversationId) {
                    loadMessages(selected)
                }
            }.onFailure {
                if (state.session?.userId == owner && selected?.id == conversationId) {
                    error = it.message
                }
            }
            controlsBusy = false
        }
    }

    LaunchedEffect(myUserId) {
        // A reused desktop window must never show the previous account's chats.
        selected = null
        messages = emptyList()
        conversations = emptyList()
        latestInboxReceipts = emptyMap()
        editingMessage = null
        deleteMessage = null
        reportMessage = null
        replyingTo = null
        olderLoading = false
        hasMoreOlder = false
        error = null
        if (myUserId.isNotBlank()) loadConversations()
    }
    LaunchedEffect(selected?.id) {
        showContactCard = false
        chatSearchVisible = false
        chatQuery = ""
        editingMessage = null
        deleteMessage = null
        reportMessage = null
        replyingTo = null
        olderLoading = false
        hasMoreOlder = false
        // Never display the previous contact's private history while the next chat loads.
        messages = emptyList()
        loadMessages(selected)
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
        DesktopInboxPanel(
            state = state,
            conversations = conversations,
            query = query,
            inbox = inbox,
            inboxShowSearch = inboxShowSearch,
            inboxShowAll = inboxShowAll,
            loading = loading,
            error = error,
            myUserId = myUserId,
            receiptRefreshEpoch = receiptRefreshEpoch,
            latestInboxReceipts = latestInboxReceipts,
            onReceiptsChange = { latestInboxReceipts = it },
            onSelect = { conversation -> error = null; selected = conversation },
            onQueryChange = { query = it },
            onSearchVisibleChange = { inboxShowSearch = it },
            onShowAllChange = { inboxShowAll = it },
            onInboxChange = { inbox = it },
            onRefresh = { scope.launch { loadConversations() } },
            onNewGroup = { showNewGroup = true },
            onGroupInvitations = { showGroupInvitations = true },
        )
        return@AnimatedContent
    }

    LaunchedEffect(active.id, myUserId) {
        linkPreviewsAllowed = runCatching { state.client.fetchChatLinkPreviewsEnabled() }
            .getOrDefault(false)
    }

    val draft = draftsByConversation[active.id].orEmpty()
    val replyLookup = remember(messages) { messages.associateBy { it.id } }
    val visibleMessages = remember(messages, chatQuery) {
        if (chatQuery.isBlank()) messages
        else messages.filter { it.content.contains(chatQuery, ignoreCase = true) }
    }
    val messageDates = remember(visibleMessages) {
        BlinkMessageDatePolicy.labels(visibleMessages.map {
            BlinkMessageDatePolicy.Entry(it.id, it.createdAt)
        })
    }
    val chatListState = remember(active.id) { LazyListState() }
    var userHasScrolled by remember(active.id) { mutableStateOf(false) }
    var previousLatestId by remember(active.id) { mutableStateOf<String?>(null) }
    var hasNewMessages by remember(active.id) { mutableStateOf(false) }
    val nearLatest by remember(active.id) {
        derivedStateOf {
            val layout = chatListState.layoutInfo
            layout.totalItemsCount == 0 ||
                (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 2
        }
    }
    LaunchedEffect(chatListState.isScrollInProgress) {
        if (chatListState.isScrollInProgress) userHasScrolled = true
    }
    val latestId = messages.lastOrNull()?.id
    LaunchedEffect(active.id, latestId, chatQuery) {
        val action = BlinkMessageScrollPolicy.decide(
            hasMessages = visibleMessages.isNotEmpty(),
            filtering = chatQuery.isNotBlank(),
            hasSeenLatest = previousLatestId != null,
            userHasScrolled = userHasScrolled,
            nearLatest = nearLatest,
            latestChanged = previousLatestId != null && previousLatestId != latestId
        )
        when (action) {
            BlinkMessageScrollAction.JUMP_TO_LATEST -> {
                chatListState.scrollToItem(visibleMessages.lastIndex)
                hasNewMessages = false
            }
            BlinkMessageScrollAction.ANIMATE_TO_LATEST -> {
                chatListState.animateScrollToItem(visibleMessages.lastIndex)
                hasNewMessages = false
            }
            BlinkMessageScrollAction.SHOW_NEW_MESSAGE -> hasNewMessages = true
            BlinkMessageScrollAction.KEEP -> Unit
        }
        if (visibleMessages.isNotEmpty() && chatQuery.isBlank()) previousLatestId = latestId
    }
    LaunchedEffect(nearLatest) {
        if (nearLatest) hasNewMessages = false
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
                        color = if (!active.isGroup && active.isOnline) Color(BlinkDesignTokens.Semantic.Success)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !linkPreviewSaving,
                        onClick = {
                            linkPreviewSaving = true
                            val account = myUserId
                            scope.launch {
                                val desired = !linkPreviewsAllowed
                                runCatching { state.client.setChatLinkPreviewsEnabled(desired) }
                                    .onSuccess { success ->
                                        if (state.session?.userId == account) {
                                            if (success) linkPreviewsAllowed = desired
                                            else error = "Could not save link preview settings."
                                        }
                                    }.onFailure { if (state.session?.userId == account) error = it.message }
                                linkPreviewSaving = false
                            }
                        }
                    ) {
                        Text(if (linkPreviewsAllowed) "Link previews on" else "Link previews off",
                            fontSize = 11.sp)
                    }

                    Box(modifier = Modifier.clickable {
                        if (active.isGroup) showGroupMembers = true else showContactCard = true
                    }) {
                        PresenceAvatar(
                            name = active.title,
                            isOnline = if (active.isGroup) null else active.isOnline,
                            size = 40.dp
                        )
                    }
                    IconButton(onClick = {
                        if (active.isGroup) showGroupMembers = true else showContactCard = true
                    }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription =
                            if (active.isGroup) "Group members and options" else "Chat profile and options")
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

            if (hasMoreOlder && chatQuery.isBlank() && chatListState.firstVisibleItemIndex <= 2) {
                TextButton(
                    onClick = { loadOlderMessages() },
                    enabled = !olderLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (olderLoading) "Loading earlier messages…" else "Load earlier messages")
                }
            }
            LazyColumn(
                state = chatListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleMessages, key = { it.id }) { message ->
                    val mine = message.senderId == myUserId
                    Column {
                        messageDates[message.id]?.let { date ->
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        date,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
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
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp).widthIn(max = 360.dp)) {
                                    message.replyToMessageId?.let { reference ->
                                        val quoted = replyLookup[reference]?.content?.take(100) ?: "Earlier message"
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        ) {
                                            Text(
                                                "↳ $quoted",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    if (message.imageViewOnce) {
                                        Text(
                                            if (mine) "🔒 Private photo sent"
                                            else if (viewedOncePhotos[message.id] == true)
                                                "Private photo already opened"
                                            else "🔒 View-once photo",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (!mine && viewedOncePhotos[message.id] != true) {
                                            TextButton(onClick = {
                                                val owner = myUserId
                                                val selectedId = selected?.id
                                                scope.launch {
                                                    runCatching {
                                                        val bytes = state.client.openViewOnceImage(message.id)
                                                        try {
                                                            SkiaImage.makeFromEncoded(bytes)
                                                        } finally {
                                                            bytes.fill(0)
                                                        }
                                                    }.onSuccess { picture ->
                                                        if (state.session?.userId == owner &&
                                                            selected?.id == selectedId) {
                                                            viewedOncePhotos[message.id] = true
                                                            privatePicture = picture
                                                        } else picture.close()
                                                    }.onFailure { failure ->
                                                        if (failure.message?.contains("already", true) == true) {
                                                            viewedOncePhotos[message.id] = true
                                                        }
                                                        error = failure.message
                                                    }
                                                }
                                            }) { Text("Open once") }
                                        }
                                    } else {
                                        if (message.messageType == "image" &&
                                            !message.mediaUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = message.mediaUrl,
                                                contentDescription = "Shared private picture",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.widthIn(max = 330.dp)
                                                    .heightIn(max = 270.dp)
                                                    .clickable { viewedChatPhoto = message }
                                            )
                                        }
                                        if (message.content.isNotBlank() &&
                                            !(message.messageType == "image" &&
                                                message.content in setOf("Image", "Photo"))) {
                                            Text(message.content)
                                            if (linkPreviewsAllowed) {
                                                DesktopRichLinkPreview(message.content, compact = true)
                                            }
                                        }
                                    }
                                    if (message.reactionCounts.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            message.reactionCounts.forEach { (emoji, count) ->
                                                Text(
                                                    "$emoji $count",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (emoji in message.myReactions) FontWeight.Bold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (mine) {
                                            val receipt = when {
                                                message.readAt != null || message.isRead -> "READ"
                                                message.deliveredAt != null -> "DELIVERED"
                                                else -> "SENT"
                                            }
                                            Text(
                                                com.blinkng.shared.BlinkDeliveryReceipt.ticks(receipt),
                                                fontSize = 11.sp,
                                                color = if (receipt == "READ") Color(0xFF3B82F6)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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
                                            modifier = Modifier.clickable(enabled = !controlsBusy) {
                                                actionMessageId = null
                                                changeMessageControl(active.id) {
                                                    state.client.setChatMessageReaction(
                                                        message.id, emoji, emoji !in message.myReactions
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Reply") },
                                    onClick = {
                                        replyingTo = message
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
                                    enabled = !message.imageViewOnce,
                                    onClick = {
                                        forwardMessageId = message.id
                                        forwardUsername = ""
                                        actionMessageId = null
                                    }
                                )
                                if (mine && message.messageType == "text") {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        enabled = !controlsBusy,
                                        onClick = {
                                            editingMessage = message
                                            editDraft = message.content
                                            actionMessageId = null
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete for me") },
                                    enabled = !controlsBusy,
                                    onClick = {
                                        deleteForEveryone = false
                                        deleteMessage = message
                                        actionMessageId = null
                                    }
                                )
                                if (mine) {
                                    DropdownMenuItem(
                                        text = { Text("Unsend for everyone") },
                                        enabled = !controlsBusy,
                                        onClick = {
                                            deleteForEveryone = true
                                            deleteMessage = message
                                            actionMessageId = null
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Report message") },
                                        enabled = !controlsBusy,
                                        onClick = {
                                            reportMessage = message
                                            reportReason = ""
                                            actionMessageId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }

            if (hasNewMessages && visibleMessages.isNotEmpty()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            chatListState.animateScrollToItem(visibleMessages.lastIndex)
                            hasNewMessages = false
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("↓ New messages") }
            }
            HorizontalDivider()
            replyingTo?.let { target ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Replying to: " + target.content.take(100),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    TextButton(onClick = { replyingTo = null }) { Text("Cancel") }
                }
            }
            if (outboxPending > 0) {
                Text(
                    "Uploading ${outboxPending} photo(s)" +
                        (outboxPercent?.let { " · $it%" } ?: ""),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    enabled = !photoBusy && myUserId.isNotBlank(),
                    onClick = { chooseChatPhoto(false) }
                ) { Text("Photo") }
                TextButton(
                    enabled = myUserId.isNotBlank() && !photoBusy,
                    onClick = { showStickerTray = true }
                ) { Text("Stickers") }
                TextButton(
                    enabled = !photoBusy && myUserId.isNotBlank(),
                    onClick = { chooseChatPhoto(true) }
                ) { Text("View once") }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draftsByConversation[active.id] = it },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                            val conversationId = active.id
                            val accountId = myUserId
                            val text = draft.trim()
                            if (accountId.isNotBlank() && text.isNotBlank() && sendingConversations[conversationId] != true) {
                                val replyId = replyingTo?.takeIf { it.conversationId == conversationId }?.id
                                sendingConversations[conversationId] = true
                                draftsByConversation[conversationId] = ""
                                replyingTo = null
                                scope.launch {
                                    try {
                                        val result = runCatching {
                                            state.client.sendMessage(
                                                conversationId = conversationId,
                                                content = text,
                                                partnerUsername = active.partnerUsername,
                                                isGroup = active.isGroup
                                            )
                                        }
                                        result.onSuccess { sent ->
                                            // The authorized server may resolve an older duplicate direct
                                            // thread to a different conversation: do not append across chats.
                                            if (state.session?.userId == accountId && selected?.id == conversationId) {
                                                if (sent.conversationId == conversationId) {
                                                    messages = messages.filterNot { it.id == sent.id } + sent
                                                } else {
                                                    error = "Sent to your existing chat. Refresh Messages to view it."
                                                    scope.launch { loadConversations() }
                                                }
                                            }
                                            if (replyId != null) {
                                                val linked = runCatching {
                                                    state.client.setChatMessageReply(sent.id, replyId)
                                                }.getOrDefault(false)
                                                if (state.session?.userId == accountId && selected?.id == conversationId) {
                                                    if (linked) {
                                                        messages = messages.map {
                                                            if (it.id == sent.id) it.copy(replyToMessageId = replyId) else it
                                                        }
                                                    } else {
                                                        error = "Message sent, but its reply link could not be saved."
                                                    }
                                                }
                                            }
                                        }.onFailure { failure ->
                                            if (state.session?.userId == accountId) {
                                                if (selected?.id == conversationId) {
                                                    error = failure.message
                                                    if (replyId != null && replyingTo == null) {
                                                        replyingTo = messages.firstOrNull { it.id == replyId }
                                                    }
                                                }
                                                if (draftsByConversation[conversationId].isNullOrEmpty()) {
                                                    draftsByConversation[conversationId] = text
                                                }
                                            }
                                        }
                                    } finally {
                                        sendingConversations[conversationId] = false
                                    }
                                }
                            }
                        },
                        enabled = draft.isNotBlank() && sendingConversations[active.id] != true && myUserId.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        if (showGroupMembers && active.isGroup) {
            DesktopGroupManagementDialog(
                state = state,
                conversation = active,
                onDismiss = { showGroupMembers = false },
                onLeft = {
                    showGroupMembers = false
                    selected = null
                    messages = emptyList()
                    scope.launch { loadConversations() }
                }
            )
        }
        if (showContactCard && !active.isGroup) {
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

    if (showStickerTray) {
        val stickers=remember(myUserId,stickerRevision) {
            DesktopStickerLibrary.saved(myUserId)
        }
        AlertDialog(
            onDismissRequest={showStickerTray=false},
            title={Text("My Stickers · ${stickers.size}/${DesktopStickerLibrary.MAX_SAVED}")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Private stickers saved on this computer; no automatic upload.",
                        style=MaterialTheme.typography.labelSmall)
                    TextButton(
                        enabled=stickers.size<DesktopStickerLibrary.MAX_SAVED,
                        onClick={
                            val chooser=JFileChooser().apply {
                                dialogTitle="Create a reusable sticker from a picture"
                            }
                            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                                val input=chooser.selectedFile
                                scope.launch {
                                    runCatching {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            DesktopStickerLibrary.create(myUserId,input)
                                        }
                                    }.onSuccess {stickerRevision++}
                                        .onFailure {error=it.message}
                                }
                            }
                        }
                    ) {Text("＋ Create sticker")}
                    if (stickers.isEmpty()) Text("No saved stickers yet.")
                    LazyRow(
                        modifier=Modifier.heightIn(max=180.dp),
                        horizontalArrangement=Arrangement.spacedBy(7.dp)
                    ) {
                        items(stickers,key={it.name}) { file ->
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model=file,
                                    contentDescription="Use saved sticker",
                                    contentScale=ContentScale.Fit,
                                    modifier=Modifier.size(85.dp).clickable {
                                        val target=selected
                                        if(target==null || target.isGroup) {
                                            error="Stickers can be sent in direct chats."
                                        } else {
                                            photoReviewDestination=target.id
                                            photoReviewQuality=BlinkChatImageQuality.STANDARD
                                            photoReviewItems=listOf(DesktopSelectedPhoto(file))
                                            showStickerTray=false
                                        }
                                    }
                                )
                                TextButton(onClick={stickerToDelete=file}) {Text("Remove")}
                            }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={showStickerTray=false}){Text("Close")}}
        )
    }
    stickerToDelete?.let { file ->
        AlertDialog(
            onDismissRequest={stickerToDelete=null},
            title={Text("Remove saved sticker?")},
            text={Text("This removes only your local copy.")},
            confirmButton={TextButton(onClick={
                DesktopStickerLibrary.remove(myUserId,file)
                stickerRevision++
                stickerToDelete=null
            }) {Text("Remove")}},
            dismissButton={TextButton(onClick={stickerToDelete=null}){Text("Cancel")}}
        )
    }
    desktopEditingPhoto?.let { item ->
        DesktopUniversalPhotoEditor(
            sourceFile = item.file,
            onEdited = { result ->
                photoReviewItems = photoReviewItems.map {
                    if (it.file == item.file) it.copy(file = result) else it
                }
                desktopEditingPhoto = null
            },
            onDismiss = { desktopEditingPhoto = null }
        )
    }
    if (photoReviewItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!photoBusy) photoReviewItems = emptyList() },
            title = { Text("Review ${photoReviewItems.size} pictures") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !photoBusy,
                            onClick = { photoReviewQuality = BlinkChatImageQuality.STANDARD }
                        ) {
                            Text(if (photoReviewQuality == BlinkChatImageQuality.STANDARD)
                                "✓ Standard" else "Standard")
                        }
                        TextButton(
                            enabled = !photoBusy,
                            onClick = { photoReviewQuality = BlinkChatImageQuality.ORIGINAL }
                        ) {
                            Text(if (photoReviewQuality == BlinkChatImageQuality.ORIGINAL)
                                "✓ Original / HD" else "Original / HD")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(photoReviewItems.size) { index ->
                            val item = photoReviewItems[index]
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = item.file,
                                        contentDescription = "Picture ${index + 1} preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(70.dp)
                                    )
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(
                                            item.file.name, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row {
                                            TextButton(
                                                enabled = !photoBusy && index > 0,
                                                onClick = {
                                                    photoReviewItems = photoReviewItems.toMutableList()
                                                        .apply { add(index - 1, removeAt(index)) }
                                                }
                                            ) { Text("↑") }
                                            TextButton(
                                                enabled = !photoBusy &&
                                                    index < photoReviewItems.lastIndex,
                                                onClick = {
                                                    photoReviewItems = photoReviewItems.toMutableList()
                                                        .apply { add(index + 1, removeAt(index)) }
                                                }
                                            ) { Text("↓") }
                                            TextButton(
                                                enabled = !photoBusy,
                                                onClick = { desktopEditingPhoto = item }
                                            ) { Text("Edit") }
                                            TextButton(
                                                enabled = !photoBusy,
                                                onClick = {
                                                    photoReviewItems =
                                                        photoReviewItems.filterIndexed { i, _ ->
                                                            i != index
                                                        }
                                                }
                                            ) { Text("Remove") }
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = item.caption,
                                    onValueChange = { value ->
                                        photoReviewItems = photoReviewItems.toMutableList().apply {
                                            this[index] = item.copy(caption = value.take(1024))
                                        }
                                    },
                                    label = { Text("Caption for picture ${index + 1}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                    enabled = !photoBusy
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !photoBusy && photoReviewItems.isNotEmpty() &&
                        photoReviewDestination == selected?.id &&
                        state.session?.userId == myUserId,
                    onClick = {
                        val owner = myUserId
                        val targetId = photoReviewDestination ?: return@Button
                        val chosen = photoReviewItems.toList()
                        val quality = photoReviewQuality
                        photoReviewItems = emptyList()
                        photoBusy = true
                        scope.launch {
                            // Disk staging must not block the Compose UI. The queue
                            // remains per-account and private if a network send fails.
                            val staged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val records = mutableListOf<DesktopChatImageOutbox.Record>()
                                runCatching {
                                    val albumId = if (chosen.size > 1)
                                        UUID.randomUUID().toString() else null
                                    chosen.forEachIndexed { index, item ->
                                        records += DesktopChatImageOutbox.enqueue(
                                            owner, targetId, UUID.randomUUID().toString(),
                                            item.file, item.caption, quality,
                                            if (albumId == null) BlinkChatPhotoAlbum.single()
                                            else BlinkChatPhotoAlbum.of(
                                                albumId, index, chosen.size
                                            )
                                        )
                                    }
                                    records.toList()
                                }.onFailure {
                                    records.forEach(DesktopChatImageOutbox::complete)
                                }
                            }
                            staged.onSuccess { records ->
                                if (state.session?.userId != owner) {
                                    records.forEach(DesktopChatImageOutbox::complete)
                                    return@onSuccess
                                }
                                outboxPending = DesktopChatImageOutbox.pending(owner).size
                                if (!DesktopChatPhotoWorker.start(owner)) {
                                    // On a developer machine, foreground fallback preserves
                                    // the durable outbox if an upload fails.
                                    for (record in records) {
                                        if (state.session?.userId != owner) break
                                        runCatching {
                                            state.client.sendChatImage(
                                                record.conversationId, record.file,
                                                record.caption, record.quality,
                                                clientMessageId = record.messageId,
                                                album = record.album,
                                                onProgress = { outboxPercent = it }
                                            )
                                        }.onSuccess { sent ->
                                            DesktopChatImageOutbox.complete(record)
                                            if (selected?.id == targetId &&
                                                state.session?.userId == owner) {
                                                messages = messages.filterNot {
                                                    it.id == sent.id
                                                } + sent
                                            }
                                        }.onFailure { failure ->
                                            if (state.session?.userId == owner) {
                                                error = failure.message
                                            }
                                        }
                                    }
                                }
                            }.onFailure { failure ->
                                if (state.session?.userId == owner) {
                                    error = failure.message
                                }
                            }
                            if (state.session?.userId == owner) {
                                outboxPending = DesktopChatImageOutbox.pending(owner).size
                            }
                            photoBusy = false
                        }
                    }
                ) { Text("Send ${photoReviewItems.size}") }
            },
            dismissButton = {
                TextButton(
                    enabled = !photoBusy,
                    onClick = { photoReviewItems = emptyList() }
                ) { Text("Cancel") }
            }
        )
    }

    viewedChatPhoto?.let { requested ->
        val photo = messages.firstOrNull { it.id == requested.id } ?: requested
        val normalPhotos = remember(messages) {
            messages.filter { it.messageType == "image" && !it.imageViewOnce &&
                !it.mediaUrl.isNullOrBlank() }
        }
        val index = normalPhotos.indexOfFirst { it.id == photo.id }
        DesktopChatPictureViewer(
            message = photo,
            senderLabel = if (photo.senderId == myUserId) "You" else (selected?.title ?: "Student"),
            hasPrevious = index > 0,
            hasNext = index >= 0 && index < normalPhotos.lastIndex,
            onPrevious = { if (index > 0) viewedChatPhoto = normalPhotos[index - 1] },
            onNext = {
                if (index in 0 until normalPhotos.lastIndex)
                    viewedChatPhoto = normalPhotos[index + 1]
            },
            onReply = {
                replyingTo = photo
                viewedChatPhoto = null
            },
            onForward = {
                forwardMessageId = photo.id
                forwardUsername = ""
                viewedChatPhoto = null
            },
            onStar = {
                viewedChatPhoto = null
                changeMessageControl(photo.conversationId) {
                    state.client.setChatMessageStarred(photo.id, !photo.isStarred)
                }
            },
            onDeleteForMe = {
                deleteForEveryone = false
                deleteMessage = photo
                viewedChatPhoto = null
            },
            authorizedDownload = { destination ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val owner = myUserId
                    check(state.session?.userId == owner) { "Sign in again to download." }
                    check(!destination.exists()) { "Choose an unused filename." }
                    val part = File.createTempFile(".blink-picture-", ".partial", destination.parentFile)
                    try {
                        val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                        part.outputStream().use { destinationStream ->
                            val guarded = object : java.io.FilterOutputStream(destinationStream) {
                                override fun write(b: ByteArray, off: Int, len: Int) {
                                    job?.ensureActive()
                                    out.write(b, off, len)
                                }
                                override fun write(b: Int) {
                                    job?.ensureActive()
                                    out.write(b)
                                }
                            }
                            state.client.copyChatImageTo(
                                photo.id, photo.conversationId, guarded
                            )
                        }
                        check(state.session?.userId == owner) { "Account changed during download." }
                        java.nio.file.Files.move(part.toPath(), destination.toPath())
                        destination
                    } catch (failure: Throwable) {
                        part.delete()
                        throw failure
                    }
                }
            },
            groupPhotoEnabled = selected?.isGroup == true,
            onSaveSticker = { file ->
                scope.launch {
                    runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            DesktopStickerLibrary.create(myUserId,file)
                        }
                    }.onSuccess { stickerRevision++; error = "Sticker saved on this computer." }
                        .onFailure {error = it.message}
                }
            },
            onAskBlinkAi = { file ->
                aiImageFile = file
                viewedChatPhoto = null
            },
            onSetGroupPicture = { file ->
                val groupId = selected?.id
                val accountId = myUserId
                if (selected?.isGroup != true || groupId.isNullOrBlank()) {
                    error = "Only group conversations support group pictures."
                } else scope.launch {
                    runCatching {
                        check(state.session?.userId == accountId) { "Sign in again." }
                        check(state.client.setGroupAvatarFromPicture(groupId,file)) {
                            "Only group admins may change this picture."
                        }
                    }.onSuccess {
                        error = "Group picture updated."
                        loadConversations()
                    }.onFailure {error = it.message}
                }
            },
            onClose = { viewedChatPhoto = null }
        )
    }

    aiImageFile?.let { file ->
        DesktopAiPhotoQuestionDialog(
            file=file,
            client=state.client,
            onDismiss={aiImageFile=null}
        )
    }
    privatePicture?.let { picture ->
        DisposableEffect(picture) {
            onDispose { picture.close() }
        }
        AlertDialog(
            onDismissRequest = { privatePicture = null },
            title = { Text("View-once private photo") },
            text = {
                ComposeImage(
                    bitmap = picture.toComposeImageBitmap(),
                    contentDescription = "View-once private photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 660.dp).heightIn(max = 540.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { privatePicture = null }) { Text("Close") }
            }
        )
    }
    editingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!controlsBusy) editingMessage = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    label = { Text("Message") },
                    maxLines = 7
                )
            },
            confirmButton = {
                Button(
                    enabled = !controlsBusy && editDraft.trim().isNotEmpty(),
                    onClick = {
                        val updatedText = editDraft
                        changeMessageControl(target.conversationId) {
                            state.client.editChatMessage(target.id, updatedText)
                        }
                        editingMessage = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(enabled = !controlsBusy, onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!controlsBusy) deleteMessage = null },
            title = { Text(if (deleteForEveryone) "Unsend message?" else "Delete message for you?") },
            text = {
                Text(
                    if (deleteForEveryone) "This removes the message for all conversation participants."
                    else "This hides the message from your own account."
                )
            },
            confirmButton = {
                Button(enabled = !controlsBusy, onClick = {
                    val unsend = deleteForEveryone
                    changeMessageControl(target.conversationId) {
                        if (unsend) state.client.deleteChatMessageForEveryone(target.id)
                        else state.client.hideChatMessageForMe(target.id)
                    }
                    deleteMessage = null
                }) { Text(if (deleteForEveryone) "Unsend" else "Delete") }
            },
            dismissButton = {
                TextButton(enabled = !controlsBusy, onClick = { deleteMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    reportMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!controlsBusy) reportMessage = null },
            title = { Text("Report message") },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("Reason") },
                    maxLines = 4
                )
            },
            confirmButton = {
                Button(
                    enabled = !controlsBusy && reportReason.trim().isNotEmpty(),
                    onClick = {
                        val reason = reportReason
                        changeMessageControl(target.conversationId) {
                            state.client.reportChatMessage(target.id, reason)
                        }
                        reportMessage = null
                    }
                ) { Text("Submit report") }
            },
            dismissButton = {
                TextButton(enabled = !controlsBusy, onClick = { reportMessage = null }) {
                    Text("Cancel")
                }
            }
        )
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
    // Dialogs must be OUTSIDE AnimatedContent: the empty-inbox branch
    // returns early, so children inside its lambda cannot display.
    if (showGroupInvitations) {
        DesktopGroupInvitationsDialog(
            state = state,
            onDismiss = { showGroupInvitations = false },
            onMembershipChanged = { scope.launch { loadConversations() } }
        )
    }
    if (showNewGroup) {
        DesktopNewGroupDialog(
            state = state,
            onDismiss = { showNewGroup = false },
            onCreated = { id ->
                showNewGroup = false
                scope.launch {
                    loadConversations()
                    selected = conversations.firstOrNull { it.id == id }
                }
            }
        )
    }
}

/**
 * Separate the inbox from the full chat surface to prevent the Windows Kotlin
 * compiler from transforming one enormous Compose lambda. State and network
 * ownership remain with MessagesScreen and are passed via callbacks.
 */
@Composable
private fun DesktopInboxPanel(
    state: DesktopAppState,
    conversations: List<DesktopConversation>,
    query: String,
    inbox: BlinkMessageInbox,
    inboxShowSearch: Boolean,
    inboxShowAll: Boolean,
    loading: Boolean,
    error: String?,
    myUserId: String,
    receiptRefreshEpoch: Int,
    latestInboxReceipts: Map<String, DesktopMessage>,
    onReceiptsChange: (Map<String, DesktopMessage>) -> Unit,
    onSelect: (DesktopConversation) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchVisibleChange: (Boolean) -> Unit,
    onShowAllChange: (Boolean) -> Unit,
    onInboxChange: (BlinkMessageInbox) -> Unit,
    onRefresh: () -> Unit,
    onNewGroup: () -> Unit,
    onGroupInvitations: () -> Unit,
) {
    val filtered = remember(conversations, query, inbox) {
        conversations.filter {
            BlinkChatInboxPolicy.matches(inbox, it.isArchived, if (it.requestStatus == "pending") "requests" else "primary", it.unreadCount > 0 || it.markedUnread) &&
                BlinkConversationSearchPolicy.matches(query, it.title, it.partnerUsername, it.lastMessage)
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
                "B L I N K",
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                TextButton(onClick = onGroupInvitations) { Text("Invites") }
                TextButton(onClick = onNewGroup) { Text("New Group") }
                IconButton(onClick = { onSearchVisibleChange(!inboxShowSearch); if (inboxShowSearch) onQueryChange("") }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search messages")
                }
                IconButton(onClick = { onRefresh() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh messages")
                }
            }
        }

        if (inboxShowSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search messages") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Messages", modifier = Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onGroupInvitations) { Text("Groups & invitations") }
            TextButton(onClick = {
                onShowAllChange(!inboxShowAll)
                onInboxChange(if (!inboxShowAll) BlinkMessageInbox.ALL else BlinkMessageInbox.PRIMARY)
            }) {
                Text(if (inboxShowAll) "Close" else "See all")
            }
        }

        if (important.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(important, key = { "important_${it.id}" }) { conversation ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(76.dp).clickable {
                            onSelect(conversation)
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

        HorizontalDivider()
        if (inboxShowAll) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlinkMessageInbox.entries.forEach { tab ->
                    FilterChip(selected = inbox == tab, onClick = { onInboxChange(tab) },
                        label = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
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
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
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
                LaunchedEffect(conversation.id, conversation.lastMessageAt, myUserId, receiptRefreshEpoch) {
                    val owner = myUserId
                    if (owner.isNotBlank()) {
                        val last = runCatching { state.client.fetchLatestMessage(conversation.id) }
                            .getOrNull()
                        if (state.session?.userId == owner) {
                            onReceiptsChange(
                                if (last == null) latestInboxReceipts - conversation.id
                                else latestInboxReceipts + (conversation.id to last)
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onSelect(conversation)
                    },
                    shape = RoundedCornerShape(0.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val latest = latestInboxReceipts[conversation.id]
                                if (!conversation.isGroup && latest != null && latest.senderId == myUserId) {
                                    val receipt = when {
                                        latest.readAt != null || latest.isRead -> "READ"
                                        latest.deliveredAt != null -> "DELIVERED"
                                        else -> "SENT"
                                    }
                                    Text(
                                        com.blinkng.shared.BlinkDeliveryReceipt.ticks(receipt),
                                        fontSize = 12.sp,
                                        color = if (receipt == "READ") Color(0xFF3B82F6)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 5.dp)
                                    )
                                }
                                Text(
                                    conversation.lastMessage.ifBlank {
                                        if (conversation.isGroup) "Group conversation"
                                        else desktopPresenceStatus(conversation.isOnline, conversation.lastSeenAt)
                                    },
                                    fontSize = 13.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (conversation.unreadCount > 0 || conversation.markedUnread) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onBackground
                            ) {
                                Text(
                                    if (conversation.unreadCount > 0) conversation.unreadCount.coerceAtMost(99).toString() else "•",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 77.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
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
