from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PREMIUM = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
REPO = ROOT / "app/src/main/java/com/example/data/repository/ChatRepository.kt"


def add_import(text: str, anchor: str, new_import: str) -> str:
    if new_import in text:
        return text
    if anchor not in text:
        raise RuntimeError(f"Import anchor not found: {anchor}")
    return text.replace(anchor, anchor + "\n" + new_import, 1)


def function_range(text: str, marker: str) -> tuple[int, int]:
    start = text.find(marker)
    if start < 0:
        raise RuntimeError(f"Function marker not found: {marker}")
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"Opening brace not found: {marker}")
    depth = 0
    in_string = False
    escaped = False
    i = brace
    while i < len(text):
        ch = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return start, i + 1
        i += 1
    raise RuntimeError(f"Closing brace not found: {marker}")


def patch_premium() -> None:
    text = PREMIUM.read_text()
    if "PREMIUM_CHAT_DIRECT_RETURN_V2" in text:
        return

    text = add_import(
        text,
        "import androidx.compose.animation.core.FastOutSlowInEasing",
        "import androidx.compose.animation.core.Animatable",
    )
    text = add_import(
        text,
        "import androidx.compose.runtime.rememberUpdatedState",
        "import androidx.compose.runtime.rememberCoroutineScope",
    )
    text = add_import(
        text,
        "import kotlinx.coroutines.delay",
        "import kotlinx.coroutines.launch",
    )

    # Android back should leave the conversation directly. Full-screen is a visual mode,
    # not an intermediate navigation destination.
    old_back = '''    BackHandler(enabled = activeCall == null && activeConversation != null) {
        if (isConversationFullScreen) onConversationFullScreenChange(false) else onCloseConversation()
    }'''
    new_back = '''    BackHandler(enabled = activeCall == null && activeConversation != null) {
        onCloseConversation()
    }'''
    if old_back not in text:
        raise RuntimeError("Premium conversation BackHandler changed")
    text = text.replace(old_back, new_back, 1)

    # Keep the real Messages home mounted under the chat. This removes the old visual
    # collapse where the inbox vanished into an avatar strip and gives swipe-to-Messages
    # an actual blurred destination that is already rendered.
    branch_start = text.find("            } else if (activeConversation != null) {")
    branch_end_marker = "            } else {\n                PremiumMessagesHome("
    branch_end = text.find(branch_end_marker, branch_start)
    if branch_start < 0 or branch_end < 0:
        raise RuntimeError("Active premium conversation branch changed")

    replacement_branch = '''            } else if (activeConversation != null) {
                // PREMIUM_CHAT_DIRECT_RETURN_V2
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(if (isConversationFullScreen) 5.dp else 10.dp)
                            .graphicsLayer {
                                alpha = if (isConversationFullScreen) .84f else .74f
                            }
                    ) {
                        PremiumMessagesHome(
                            conversations = conversations,
                            stories = stories,
                            myAvatar = myAvatar,
                            myName = myName,
                            palette = palette,
                            onOpenConversation = onOpenConversation,
                            onProfileClick = onProfileClick,
                            onStoryClick = onStoryClick,
                            onAddStoryClick = onAddStoryClick,
                            onOpenAppearance = { showAppearanceSheet = true },
                            isConnected = isConnected
                        )
                    }

                    PremiumMessagesMasterDetail(
                        conversations = conversations,
                        activeConversation = activeConversation,
                        palette = palette,
                        isFullScreen = isConversationFullScreen,
                        onFullScreenChange = onConversationFullScreenChange,
                        onOpenConversation = onOpenConversation,
                        onCloseConversation = onCloseConversation,
                        onSendMessage = onSendMessage,
                        onSendVideo = onSendVideo,
                        onRetryMessage = onRetryMessage,
                        interactionActions = interactionActions,
                        onProfileClick = onProfileClick,
                        onStartCall = { conversation, kind ->
                            activeCall = MessageCallState(conversation, kind)
                            launchSecureCall(context, conversation, kind)
                        },
                        isConnected = isConnected
                    )
                }
'''
    text = text[:branch_start] + replacement_branch + text[branch_end:]

    # Make the master-detail layer transparent outside the chat panel so the blurred
    # Messages home remains visible under the avatar rail.
    master_start, master_end = function_range(text, "private fun PremiumMessagesMasterDetail(")
    master = text[master_start:master_end]
    if "MessageBackground(palette) {" not in master:
        raise RuntimeError("PremiumMessagesMasterDetail background wrapper changed")
    master = master.replace(
        "    MessageBackground(palette) {",
        "    Box(modifier = Modifier.fillMaxSize()) {",
        1,
    )

    # Gesture state: drag the visible chat itself. A right swipe in either compact or
    # full-screen mode exits directly to Messages; it never unfolds into another state.
    threshold = '''    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
'''
    threshold_new = '''    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val gestureScope = rememberCoroutineScope()
'''
    if threshold not in master:
        raise RuntimeError("Premium chat swipe threshold changed")
    master = master.replace(threshold, threshold_new, 1)

    box_marker = '''        ) {
            val railWidth = (maxWidth * .20f).coerceIn(68.dp, 92.dp)
'''
    box_new = '''        ) {
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val dragOffset = remember(activeConversation.partnerUsername, isFullScreen, widthPx) {
                Animatable(0f)
            }
            val railWidth = (maxWidth * .20f).coerceIn(68.dp, 92.dp)
'''
    if box_marker not in master:
        raise RuntimeError("Premium chat BoxWithConstraints body changed")
    master = master.replace(box_marker, box_new, 1)

    old_pointer = '''                    .fillMaxHeight()
                    .pointerInput(isFullScreen, activeConversation.partnerUsername) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragCancel = { totalDrag = 0f },
                            onDragEnd = {
                                when {
                                    isFullScreen && totalDrag > swipeThresholdPx -> onFullScreenChange(false)
                                    !isFullScreen && totalDrag < -swipeThresholdPx -> onFullScreenChange(true)
                                }
                                totalDrag = 0f
                            }
                        )
                    },'''
    new_pointer = '''                    .fillMaxHeight()
                    .graphicsLayer { translationX = dragOffset.value }
                    .pointerInput(isFullScreen, activeConversation.partnerUsername, widthPx) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                if (dragAmount > 0f || dragOffset.value > 0f) {
                                    change.consume()
                                    val next = (dragOffset.value + dragAmount).coerceIn(0f, widthPx)
                                    gestureScope.launch { dragOffset.snapTo(next) }
                                }
                            },
                            onDragCancel = {
                                totalDrag = 0f
                                gestureScope.launch {
                                    dragOffset.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDragEnd = {
                                when {
                                    dragOffset.value >= swipeThresholdPx -> gestureScope.launch {
                                        dragOffset.animateTo(
                                            widthPx,
                                            tween(190, easing = FastOutSlowInEasing)
                                        )
                                        onCloseConversation()
                                    }
                                    !isFullScreen && totalDrag < -swipeThresholdPx -> {
                                        gestureScope.launch { dragOffset.snapTo(0f) }
                                        onFullScreenChange(true)
                                    }
                                    else -> gestureScope.launch {
                                        dragOffset.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                                totalDrag = 0f
                            }
                        )
                    },'''
    if old_pointer not in master:
        raise RuntimeError("Premium chat pointer gesture block changed")
    master = master.replace(old_pointer, new_pointer, 1)

    old_detail_back = '''                        onBack = {
                            if (isFullScreen) onFullScreenChange(false) else onCloseConversation()
                        },'''
    if old_detail_back not in master:
        raise RuntimeError("PremiumChatDetail back callback changed")
    master = master.replace(
        old_detail_back,
        '''                        onBack = { onCloseConversation() },''',
        1,
    )
    text = text[:master_start] + master + text[master_end:]

    # Avatar rail: keep the active person visible and crisp; soften other chats. Tapping
    # the glass/blurred rail background returns to Messages, while tapping an avatar
    # switches conversations without leaving the chat surface.
    rail_start, rail_end = function_range(text, "private fun ConversationSwitchRail(")
    rail = text[rail_start:rail_end]
    old_remaining = '''    val remaining = remember(conversations, selectedPartner) {
        conversations.filterNot {
            it.partnerUsername.equals(selectedPartner, ignoreCase = true)
        }
    }
'''
    new_remaining = '''    val railConversations = remember(conversations, selectedPartner) {
        val selected = conversations.firstOrNull {
            it.partnerUsername.equals(selectedPartner, ignoreCase = true)
        }
        buildList {
            selected?.let(::add)
            addAll(
                conversations.filterNot {
                    it.partnerUsername.equals(selectedPartner, ignoreCase = true)
                }
            )
        }
    }
'''
    if old_remaining not in rail:
        raise RuntimeError("Conversation switch rail list changed")
    rail = rail.replace(old_remaining, new_remaining, 1)
    rail = rail.replace(
        "        modifier = Modifier.fillMaxSize(),",
        '''        modifier = Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onCloseConversation),''',
        1,
    )
    back_button = '''            GlassIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back to chats",
                palette = palette,
                size = 40.dp,
                onClick = onCloseConversation
            )
'''
    if back_button not in rail:
        raise RuntimeError("Rail back button changed")
    rail = rail.replace(back_button, "", 1)
    rail = rail.replace(
        '                items(remaining, key = { "switch_${it.id}" }) { conversation ->',
        '                items(railConversations, key = { "switch_${it.id}" }) { conversation ->',
        1,
    )
    ring = '''                        RingAvatar(
                            url = conversation.partnerAvatar,
                            name = conversation.partnerName,
                            palette = palette,
                            size = 48.dp,
                            online = conversation.isOnline,
                            emphasizeRing = conversation.unreadCount > 0
                        )'''
    ring_new = '''                        val selected = conversation.partnerUsername.equals(
                            selectedPartner,
                            ignoreCase = true
                        )
                        RingAvatar(
                            url = conversation.partnerAvatar,
                            name = conversation.partnerName,
                            palette = palette,
                            size = 48.dp,
                            online = conversation.isOnline,
                            emphasizeRing = selected || conversation.unreadCount > 0,
                            modifier = if (selected) Modifier else Modifier.blur(1.8.dp)
                        )'''
    if ring not in rail:
        raise RuntimeError("Rail RingAvatar block changed")
    rail = rail.replace(ring, ring_new, 1)
    text = text[:rail_start] + rail + text[rail_end:]

    # Remove the artificial date divider. Also scroll by latest message identity rather
    # than message count so background history prepends do not yank the user to the bottom.
    old_scroll = '''    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.size)
    }'''
    new_scroll = '''    val latestVisibleMessageId = visibleMessages.lastOrNull()?.id
    LaunchedEffect(conversation.partnerUsername, latestVisibleMessageId) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }'''
    if old_scroll not in text:
        raise RuntimeError("Premium chat auto-scroll block changed")
    text = text.replace(old_scroll, new_scroll, 1)
    today = '                    item(key = "today_divider") { DayDivider("Today", palette) }\n'
    if today not in text:
        raise RuntimeError("Today divider changed or already removed")
    text = text.replace(today, "", 1)

    PREMIUM.write_text(text)


def patch_repository() -> None:
    text = REPO.read_text()
    if "triggerMessagePushBestEffort" in text:
        return

    # Returning from send_message should immediately mean SENT in the UI. Push delivery
    # is a separate best-effort side effect and must not keep the sender staring at
    # "Sending..." while the notification request completes.
    blocking_push = '''                SupabaseService.accessToken()?.takeIf { it.isNotBlank() }?.let { currentToken ->
                    runCatching { triggerMessagePush(messageId, currentToken) }
                }
'''
    if blocking_push not in text:
        raise RuntimeError("Blocking message push block changed")
    text = text.replace(blocking_push, "", 1)

    insert_before = '''

    private data class MessageActionState('''
    if insert_before not in text:
        raise RuntimeError("MessageActionState marker changed")
    helper = '''

    suspend fun triggerMessagePushBestEffort(messageId: String) = withContext(Dispatchers.IO) {
        if (messageId.isBlank()) return@withContext
        val currentToken = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
            ?: return@withContext
        runCatching { triggerMessagePush(messageId, currentToken) }
    }
'''
    text = text.replace(insert_before, helper + insert_before, 1)
    REPO.write_text(text)


def patch_viewmodel() -> None:
    text = VM.read_text()
    if "FAST_MESSAGE_DELIVERY_V2" in text:
        return

    # Replace history paging with progressive full-history hydration. Every 100-message
    # page is merged into state as soon as it arrives, so the chat remains usable while
    # older years continue loading automatically in the background.
    history_start, history_end = function_range(
        text,
        "private suspend fun loadConversationHistory(conversationId: String, partnerUsername: String, older: Boolean)",
    )
    history_new = '''private suspend fun loadConversationHistory(
        conversationId: String,
        partnerUsername: String,
        older: Boolean
    ) {
        if (conversationId.isBlank() || conversationId.startsWith("local_")) return
        val state = _uiState.value
        if (state.loadingOlderConversationId == conversationId) return
        val current = state.conversations.firstOrNull { it.id == conversationId } ?: return
        val currentOldest = current.messages.minByOrNull { it.rawTimestamp.ifBlank { "9999" } }

        var beforeAt = if (older) currentOldest?.rawTimestamp?.takeIf { it.isNotBlank() } else null
        var beforeId = if (older) currentOldest?.id?.takeIf { it.isNotBlank() } else null
        var pageNumber = 0
        var hasMore = true

        _uiState.value = state.copy(loadingOlderConversationId = conversationId)
        try {
            while (hasMore && pageNumber < 1_000) {
                val page = chatRepository.fetchMessagePage(
                    conversationId = conversationId,
                    beforeCreatedAt = beforeAt,
                    beforeId = beforeId,
                    limit = 100
                )

                val latest = _uiState.value
                val updated = latest.conversations.map { conversation ->
                    if (conversation.id != conversationId) conversation
                    else {
                        // Server pages are merged with optimistic/local messages. This makes
                        // full history restoration safe even while a new message is sending.
                        val merged = page + conversation.messages
                        conversation.copy(
                            messages = merged
                                .distinctBy { it.id }
                                .sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }
                                .toMutableList()
                        )
                    }
                }

                hasMore = page.size >= 100
                _uiState.value = latest.copy(
                    conversations = updated,
                    messageHistoryHasMore = latest.messageHistoryHasMore + (conversationId to hasMore),
                    loadingOlderConversationId = if (hasMore) conversationId else null
                )
                persistConversations()

                if (!hasMore) break

                val oldestInPage = page.minByOrNull { it.rawTimestamp.ifBlank { "9999" } }
                val nextAt = oldestInPage?.rawTimestamp?.takeIf { it.isNotBlank() }
                val nextId = oldestInPage?.id?.takeIf { it.isNotBlank() }
                if (nextAt.isNullOrBlank() || nextId.isNullOrBlank()) {
                    hasMore = false
                    break
                }
                if (nextAt == beforeAt && nextId == beforeId) {
                    hasMore = false
                    break
                }

                beforeAt = nextAt
                beforeId = nextId
                pageNumber += 1
                kotlinx.coroutines.yield()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Message history hydration failed for @$partnerUsername", e)
        } finally {
            val latest = _uiState.value
            _uiState.value = latest.copy(
                loadingOlderConversationId = null,
                messageHistoryHasMore = latest.messageHistoryHasMore + (conversationId to hasMore)
            )
        }
    }'''
    text = text[:history_start] + history_new + text[history_end:]

    send_start, send_end = function_range(text, "fun sendMessage(\n        partnerUsername: String,")
    old_send = text[send_start:send_end]
    prefix_marker = '''        appendMessageToState(cleanPartner, optimistic)
        persistConversations()
'''
    if prefix_marker not in old_send:
        raise RuntimeError("Optimistic send prefix changed")
    prefix_pos = old_send.find(prefix_marker) + len(prefix_marker)
    send_prefix = old_send[:prefix_pos]
    send_tail = '''

        // FAST_MESSAGE_DELIVERY_V2
        // Persist the outbox and perform the network insert concurrently. Room durability no
        // longer sits in front of the Supabase request, so rapid sends feel immediate while
        // still surviving process death/offline transitions.
        viewModelScope.launch(Dispatchers.IO) {
            if (!activeOutboxIds.add(tempId)) return@launch
            try {
                kotlinx.coroutines.coroutineScope {
                    val persistJob = async {
                        offlineContentStore.enqueueMessage(tempId, cleanPartner, cleanText)
                    }
                    val networkJob = async {
                        chatRepository.sendMessage(cleanPartner, cleanText, replyToMessageId)
                    }

                    val result = networkJob.await()
                    persistJob.await()

                    result.fold(
                        onSuccess = { serverMsg ->
                            offlineContentStore.deleteOutbox(tempId)
                            pendingReplyTargets.remove(tempId)

                            withContext(Dispatchers.Main) {
                                replaceMessageInState(
                                    cleanPartner,
                                    tempId,
                                    serverMsg.copy(
                                        receiverUsername = cleanPartner,
                                        status = MessageStatus.SENT
                                    )
                                )
                            }

                            // Notification dispatch is deliberately after the UI has already
                            // changed to SENT; slow push infrastructure cannot delay the tick.
                            chatRepository.triggerMessagePushBestEffort(serverMsg.id)

                            runCatching {
                                supabaseService.recordActivity(
                                    cleanPartner,
                                    "sent you a direct message",
                                    NotificationFilter.ALL,
                                    targetUsername = supabaseService.getCurrentUsername().orEmpty(),
                                    previewText = cleanText,
                                    targetType = "CHAT"
                                )
                            }
                            reconcileConversationSummary(cleanPartner)
                        },
                        onFailure = { error ->
                            val detail = error.message.orEmpty()
                            val lower = detail.lowercase()
                            val retryable = lower.contains("timeout") ||
                                lower.contains("timed out") ||
                                lower.contains("network") ||
                                lower.contains("failed to connect") ||
                                lower.contains("unable to resolve host") ||
                                lower.contains("no route to host") ||
                                lower.contains("socket") ||
                                !_uiState.value.isOnline

                            if (retryable) {
                                offlineContentStore.resetOutbox(tempId)
                                withContext(Dispatchers.Main) {
                                    updateMessageStatusInState(
                                        cleanPartner,
                                        tempId,
                                        MessageStatus.SENDING,
                                        pendingLabel = "Queued"
                                    )
                                }
                            } else {
                                val pending = offlineContentStore.pendingOutbox(100)
                                    .firstOrNull { it.localId == tempId }
                                if (pending != null) {
                                    offlineContentStore.markOutboxFailure(
                                        pending,
                                        detail.ifBlank { "Message send failed" }
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    updateMessageStatusInState(
                                        cleanPartner,
                                        tempId,
                                        MessageStatus.FAILED
                                    )
                                    showToast(detail.ifBlank { "Message failed. Please try again." })
                                }
                            }
                        }
                    )
                }
            } finally {
                activeOutboxIds.remove(tempId)
            }
        }
    }'''
    text = text[:send_start] + send_prefix + send_tail + text[send_end:]

    # The outbox retry path also moves push dispatch after the SENT state transition.
    success_marker = '''                                    persistConversations()
                                }
                                runCatching {
                                    supabaseService.recordActivity('''
    success_new = '''                                    persistConversations()
                                }
                                chatRepository.triggerMessagePushBestEffort(serverMsg.id)
                                runCatching {
                                    supabaseService.recordActivity('''
    if success_marker not in text:
        raise RuntimeError("Outbox success marker changed")
    text = text.replace(success_marker, success_new, 1)

    VM.write_text(text)


if __name__ == "__main__":
    patch_premium()
    patch_repository()
    patch_viewmodel()
    print("Applied premium chat direct-return, full history and fast-send fixes.")
