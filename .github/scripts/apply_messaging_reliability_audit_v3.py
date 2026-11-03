from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPO = ROOT / "app/src/main/java/com/example/data/repository/ChatRepository.kt"
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
REALTIME = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseRealtimeManager.kt"
PREMIUM = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MARKER = "MESSAGING_RELIABILITY_AUDIT_V3"


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


def replace_function(text: str, marker: str, replacement: str) -> str:
    start, end = function_range(text, marker)
    return text[:start] + replacement.rstrip() + text[end:]


def add_import(text: str, anchor: str, value: str) -> str:
    if value in text:
        return text
    if anchor not in text:
        raise RuntimeError(f"Import anchor missing: {anchor}")
    return text.replace(anchor, anchor + "\n" + value, 1)


def patch_repository() -> None:
    text = REPO.read_text()
    if MARKER in text:
        return

    text = text.replace(
        "        limit: Int = 100\n    ): List<ChatMessage>",
        "        limit: Int = 50\n    ): List<ChatMessage>",
        1,
    )

    send = r'''    // MESSAGING_RELIABILITY_AUDIT_V3: server-confirmed sends always return both IDs.
    suspend fun sendMessage(
        receiverUsername: String,
        text: String,
        replyToMessageId: String? = null
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val receiver = receiverUsername.trim().removePrefix("@")
        val cleanText = text.trim()
        if (receiver.isBlank() || cleanText.isBlank()) {
            return@withContext Result.failure(Exception("Recipient and message are required."))
        }
        val uid = supabaseService.getCurrentUserId()
            ?: return@withContext Result.failure(Exception("Please sign in again."))

        suspend fun request(): okhttp3.Response {
            val token = SupabaseService.accessToken()
                ?: throw IllegalStateException("Your session has expired. Please sign in again.")
            val body = JSONObject().apply {
                put("p_receiver_username", receiver)
                put("p_content", cleanText)
            }
            return client.newCall(
                Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/send_message_v2")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute()
        }

        try {
            var response = request()
            if (response.code == 401) {
                response.close()
                if (!refreshSession()) {
                    return@withContext Result.failure(Exception("Your session expired. Please sign in again."))
                }
                response = request()
            }
            response.use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val message = runCatching { JSONObject(raw).optString("message") }.getOrNull()
                        .orEmpty().ifBlank { "Unable to send message (${res.code})." }
                    return@withContext Result.failure(Exception(message))
                }

                val rows = org.json.JSONArray(if (raw.isBlank()) "[]" else raw)
                if (rows.length() == 0) {
                    return@withContext Result.failure(Exception("Message was created but no server identity was returned."))
                }
                val row = rows.getJSONObject(0)
                val messageId = row.optString("message_id")
                val conversationId = row.optString("conversation_id")
                val createdAt = row.optString("created_at").takeIf {
                    it.isNotBlank() && !it.equals("null", true)
                } ?: Instant.now().toString()
                if (messageId.isBlank() || conversationId.isBlank()) {
                    return@withContext Result.failure(Exception("Message confirmation was incomplete."))
                }

                val validReplyId = replyToMessageId
                    ?.takeIf { id -> runCatching { java.util.UUID.fromString(id) }.isSuccess }
                if (validReplyId != null) {
                    runCatching { setMessageReply(messageId, validReplyId) }
                }

                Result.success(
                    ChatMessage(
                        id = messageId,
                        conversationId = conversationId,
                        senderId = uid,
                        receiverUsername = receiver,
                        text = cleanText,
                        rawTimestamp = createdAt,
                        timestamp = formatMessageTime(createdAt),
                        isFromMe = true,
                        isRead = false,
                        status = MessageStatus.SENT,
                        replyToMessageId = validReplyId
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to send message.", e))
        }
    }'''
    text = replace_function(text, "    suspend fun sendMessage(", send)

    anchor = "    suspend fun markMessageDelivered(messageId: String): Boolean {"
    idx = text.find(anchor)
    if idx < 0:
        raise RuntimeError("markMessageDelivered not found")
    ack = r'''    suspend fun ackPendingDeliveries(): Int = withContext(Dispatchers.IO) {
        runCatching {
            postAuthenticatedRpc("ack_pending_message_deliveries", JSONObject())
                .trim().trim('"').toIntOrNull() ?: 0
        }.getOrDefault(0)
    }

'''
    text = text[:idx] + ack + text[idx:]
    REPO.write_text(text)


def patch_realtime() -> None:
    text = REALTIME.read_text()
    if MARKER in text:
        return

    text = add_import(text, "import kotlinx.coroutines.flow.MutableSharedFlow", "import kotlinx.coroutines.flow.MutableStateFlow")
    text = add_import(text, "import kotlinx.coroutines.flow.SharedFlow", "import kotlinx.coroutines.flow.StateFlow")
    text = add_import(text, "import kotlinx.coroutines.flow.asSharedFlow", "import kotlinx.coroutines.flow.asStateFlow")

    state_anchor = "    private val isConnecting = AtomicBoolean(false)\n"
    if state_anchor not in text:
        raise RuntimeError("Realtime state anchor changed")
    text = text.replace(
        state_anchor,
        state_anchor +
        "    // MESSAGING_RELIABILITY_AUDIT_V3: socket-open is not the same as a joined messages channel.\n"
        "    private val _messagesSubscribed = MutableStateFlow(false)\n"
        "    val messagesSubscribed: StateFlow<Boolean> = _messagesSubscribed.asStateFlow()\n"
        "    private var lastAccessTokenSent: String = \"\"\n",
        1,
    )

    old_open = "        override fun onOpen(webSocket: WebSocket, response: Response) { isConnected.set(true); isConnecting.set(false); startHeartbeat(); sendAccessToken(); subscribeToTables(); setPresence(true) }"
    new_open = "        override fun onOpen(webSocket: WebSocket, response: Response) { isConnected.set(true); isConnecting.set(false); _messagesSubscribed.value = false; lastAccessTokenSent = \"\"; startHeartbeat(); sendAccessToken(); subscribeToTables(); setPresence(true) }"
    if old_open not in text:
        raise RuntimeError("Realtime onOpen changed")
    text = text.replace(old_open, new_open, 1)

    heartbeat = r'''    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected.get()) {
                delay(25_000L)
                // Refresh Realtime authorization whenever REST refreshes the Supabase JWT.
                sendAccessToken()
                webSocket?.send(
                    JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", "hb_${refCounter.getAndIncrement()}")
                    }.toString()
                )
                setPresence(true)
            }
        }
    }'''
    text = replace_function(text, "    private fun startHeartbeat()", heartbeat)

    token_fun = r'''    private fun sendAccessToken() {
        val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() } ?: return
        if (token == lastAccessTokenSent) return
        val sent = webSocket?.send(
            JSONObject().apply {
                put("topic", "realtime")
                put("event", "access_token")
                put("payload", JSONObject().put("access_token", token))
                put("ref", refCounter.getAndIncrement().toString())
            }.toString()
        ) == true
        if (sent) lastAccessTokenSent = token
    }'''
    text = replace_function(text, "    private fun sendAccessToken()", token_fun)

    incoming = r'''    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")
            val topic = json.optString("topic")
            val payload = json.optJSONObject("payload") ?: JSONObject()

            if (event == "phx_reply" && topic == "realtime:public:messages") {
                val status = payload.optString("status").ifBlank {
                    payload.optJSONObject("response")?.optString("status").orEmpty()
                }
                if (status.equals("ok", true)) _messagesSubscribed.value = true
                if (status.equals("error", true)) _messagesSubscribed.value = false
                return
            }

            if (event == "system") {
                val status = payload.optString("status")
                val extension = payload.optString("extension")
                val message = payload.optString("message")
                val channel = payload.optString("channel").ifBlank { topic }
                val isMessageSubscription = channel.contains("messages", true) || topic.contains("messages", true)
                if (isMessageSubscription && extension.equals("postgres_changes", true)) {
                    when {
                        status.equals("ok", true) && message.contains("Subscribed", true) -> _messagesSubscribed.value = true
                        status.equals("error", true) -> _messagesSubscribed.value = false
                    }
                }
                return
            }

            if ((event == "phx_error" || event == "phx_close") && topic.contains("messages", true)) {
                _messagesSubscribed.value = false
                return
            }
            if (event != "postgres_changes") return

            val data = payload.optJSONObject("data") ?: return
            val type = data.optString("type")
            val table = data.optString("table")
            val record = data.optJSONObject("record") ?: data.optJSONObject("old_record") ?: JSONObject()

            when (table) {
                "messages" -> {
                    _messagesSubscribed.value = true
                    val senderId = record.optString("sender_id")
                    val senderUsername = record.optString("sender_username")
                    val createdAt = record.optString("created_at")
                    publishEvent(
                        RealtimeEvent.MessageEvent(
                            type,
                            ChatMessage(
                                id = record.optString("id"),
                                conversationId = record.optString("conversation_id"),
                                senderId = senderId.ifBlank { senderUsername },
                                senderUsername = senderUsername,
                                receiverId = record.optString("receiver_id").ifBlank { record.optString("receiver_username") },
                                receiverUsername = record.optString("receiver_username"),
                                text = record.optString("content", record.optString("text")),
                                rawTimestamp = createdAt,
                                timestamp = formatTimestamp(createdAt),
                                isFromMe = senderId == activeUserId || senderUsername.equals(activeUsername, true),
                                isRead = record.optBoolean("is_read", false),
                                status = when {
                                    record.optBoolean("is_read", false) || record.optString("read_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.READ
                                    record.optString("delivered_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.DELIVERED
                                    else -> MessageStatus.SENT
                                }
                            )
                        )
                    )
                }
                "conversations" -> publishEvent(RealtimeEvent.ConversationEvent(type, record.optString("id"), record.optString("last_message"), record.optString("updated_at", record.optString("last_message_at"))))
                "notifications" -> publishEvent(RealtimeEvent.NotificationEvent(type, record.optString("id"), record.optString("user_id"), record.optString("username"), record.optString("type"), record.optString("title"), record.optString("content")))
                "feed_posts" -> publishEvent(RealtimeEvent.FeedPostEvent(type, record.optString("id")))
                "roommate_profiles", "roommate_applications", "mentor_profiles", "mentor_requests",
                "reading_mate_profiles", "reading_mate_requests", "housing_agent_profiles",
                "housing_requests", "housing_request_applications", "game_challenges", "study_circles", "study_circle_members" ->
                    publishEvent(RealtimeEvent.ConnectHubEvent(type, table))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing realtime message", e)
        }
    }'''
    text = replace_function(text, "    private fun handleIncomingMessage(text: String)", incoming)

    disconnect = r'''    private fun handleDisconnected() {
        isConnected.set(false)
        isConnecting.set(false)
        _messagesSubscribed.value = false
        lastAccessTokenSent = ""
        heartbeatJob?.cancel()
        setPresence(false)
    }'''
    text = replace_function(text, "    private fun handleDisconnected()", disconnect)

    old_disconnect = "    fun disconnect() { setPresence(false); activeUsername = \"\"; activeUserId = \"\"; heartbeatJob?.cancel(); reconnectJob?.cancel(); try { webSocket?.close(1000, \"User logged out\") } catch (_: Exception) {}; webSocket = null; isConnected.set(false); isConnecting.set(false) }"
    new_disconnect = "    fun disconnect() { setPresence(false); activeUsername = \"\"; activeUserId = \"\"; heartbeatJob?.cancel(); reconnectJob?.cancel(); try { webSocket?.close(1000, \"User logged out\") } catch (_: Exception) {}; webSocket = null; isConnected.set(false); isConnecting.set(false); _messagesSubscribed.value = false; lastAccessTokenSent = \"\" }"
    if old_disconnect not in text:
        raise RuntimeError("Realtime disconnect changed")
    text = text.replace(old_disconnect, new_disconnect, 1)
    REALTIME.write_text(text)


def patch_viewmodel() -> None:
    text = VM.read_text()
    if MARKER in text:
        return

    text = text.replace(
        "    val isLiveSupabaseConnected: Boolean = false,\n",
        "    val isLiveSupabaseConnected: Boolean = false,\n    val isMessagingRealtimeConnected: Boolean = false,\n",
        1,
    )
    text = text.replace(
        "    val loadingOlderConversationId: String? = null\n)",
        "    val loadingOlderConversationId: String? = null,\n    val loadingInitialConversationId: String? = null\n)",
        1,
    )

    event_anchor = "        viewModelScope.launch { realtimeManager.events.collect { handleRealtimeEvent(it) } }\n"
    if event_anchor not in text:
        raise RuntimeError("Realtime collector anchor changed")
    text = text.replace(
        event_anchor,
        event_anchor + r'''        // MESSAGING_RELIABILITY_AUDIT_V3: expose real messages-channel readiness, not feed REST health.
        viewModelScope.launch {
            realtimeManager.messagesSubscribed.collectLatest { subscribed ->
                _uiState.value = _uiState.value.copy(isMessagingRealtimeConnected = subscribed)
                if (subscribed) {
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { chatRepository.ackPendingDeliveries() }
                    }
                }
            }
        }
''',
        1,
    )

    text = text.replace(
        "            viewModelScope.launch {\n                chatRepository.markConversationRead(clean)\n                if (_uiState.value.isOnline && existing.id.isNotBlank() && !existing.id.startsWith(\"local_\")) {",
        "            viewModelScope.launch {\n                if (_uiState.value.isOnline && existing.id.isNotBlank() && !existing.id.startsWith(\"local_\")) {",
        1,
    )

    history = r'''    private suspend fun loadConversationHistory(
        conversationId: String,
        partnerUsername: String,
        older: Boolean
    ) {
        if (conversationId.isBlank()) return

        var resolvedConversationId = conversationId
        if (resolvedConversationId.startsWith("local_") && _uiState.value.isOnline) {
            val server = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
                .firstOrNull { it.partnerUsername.equals(partnerUsername, true) }
            if (server != null && server.id.isNotBlank() && !server.id.startsWith("local_")) {
                withContext(Dispatchers.Main) {
                    val latest = _uiState.value
                    val conversations = latest.conversations.toMutableList()
                    val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
                    if (index >= 0) {
                        val local = conversations[index]
                        conversations[index] = server.copy(
                            messages = local.messages.toMutableList(),
                            isMuted = local.isMuted
                        )
                        val historyMap = latest.messageHistoryHasMore.toMutableMap().apply {
                            remove(local.id)
                            put(server.id, get(server.id) ?: true)
                        }
                        _uiState.value = latest.copy(
                            conversations = conversations,
                            messageHistoryHasMore = historyMap
                        )
                    }
                }
                persistConversationsNow()
                resolvedConversationId = server.id
            } else {
                return
            }
        }
        if (resolvedConversationId.startsWith("local_")) return

        val state = _uiState.value
        val current = state.conversations.firstOrNull {
            it.id == resolvedConversationId || it.partnerUsername.equals(partnerUsername, true)
        } ?: return
        if (older && state.loadingOlderConversationId == resolvedConversationId) return
        if (!older && state.loadingInitialConversationId == resolvedConversationId) return

        val oldest = current.messages
            .filter { it.rawTimestamp.isNotBlank() && !it.id.startsWith("temp_") }
            .minWithOrNull(compareBy<ChatMessage> { it.rawTimestamp }.thenBy { it.id })
        val beforeAt = if (older) oldest?.rawTimestamp else null
        val beforeId = if (older) oldest?.id else null

        _uiState.value = if (older) {
            state.copy(loadingOlderConversationId = resolvedConversationId)
        } else {
            state.copy(loadingInitialConversationId = resolvedConversationId)
        }

        try {
            val page = chatRepository.fetchMessagePage(
                conversationId = resolvedConversationId,
                beforeCreatedAt = beforeAt,
                beforeId = beforeId,
                limit = 50
            )

            withContext(Dispatchers.Main) {
                val latest = _uiState.value
                val conversations = latest.conversations.toMutableList()
                val index = conversations.indexOfFirst {
                    it.id == resolvedConversationId || it.partnerUsername.equals(partnerUsername, true)
                }
                if (index >= 0) {
                    val old = conversations[index]
                    val merged = LinkedHashMap<String, ChatMessage>()
                    old.messages.forEach { message ->
                        val key = message.id.ifBlank { "local:${message.rawTimestamp}:${message.text}" }
                        merged[key] = message
                    }
                    // Server rows win for matching IDs so delivery/read status is refreshed.
                    page.forEach { message ->
                        val key = message.id.ifBlank { "server:${message.rawTimestamp}:${message.text}" }
                        merged[key] = message.copy(conversationId = resolvedConversationId)
                    }
                    val messages = merged.values
                        .sortedWith(compareBy<ChatMessage> { it.rawTimestamp.ifBlank { "9999" } }.thenBy { it.id })
                        .toMutableList()
                    val newest = messages.lastOrNull()
                    conversations[index] = old.copy(
                        id = resolvedConversationId,
                        messages = messages,
                        lastMessage = newest?.text ?: old.lastMessage,
                        lastMessageTime = newest?.timestamp ?: old.lastMessageTime,
                        lastMessageRawTime = newest?.rawTimestamp ?: old.lastMessageRawTime
                    )
                    _uiState.value = latest.copy(
                        conversations = conversations,
                        messageHistoryHasMore = latest.messageHistoryHasMore + (resolvedConversationId to (page.size >= 50))
                    )
                }
            }

            // Make the merged page durable before acknowledging it to the server.
            persistConversationsNow()
            runCatching { chatRepository.ackPendingDeliveries() }
            if (!older) runCatching { chatRepository.markConversationRead(partnerUsername) }
        } catch (e: Exception) {
            Log.w(TAG, "Message history hydration failed for @$partnerUsername", e)
        } finally {
            val latest = _uiState.value
            _uiState.value = if (older) {
                latest.copy(loadingOlderConversationId = null)
            } else {
                latest.copy(loadingInitialConversationId = null)
            }
        }
    }'''
    text = replace_function(text, "    private suspend fun loadConversationHistory(", history)

    send = r'''    fun sendMessage(
        partnerUsername: String,
        text: String,
        isFromMe: Boolean = true,
        replyToMessageId: String? = null
    ) {
        val cleanText = text.trim()
        val cleanPartner = partnerUsername.trim().removePrefix("@")
        if (cleanText.isBlank() || cleanPartner.isBlank()) return

        val uid = supabaseService.getCurrentUserId() ?: "local_user"
        val currentUsername = supabaseService.getCurrentUsername()
            ?: _uiState.value.myProfile.username.ifBlank { "you" }
        val tempId = "temp_${UUID.randomUUID()}"
        val currentConversationId = _uiState.value.conversations
            .firstOrNull { it.partnerUsername.equals(cleanPartner, true) }
            ?.id
            ?.takeUnless { it.startsWith("local_") }
        val optimistic = ChatMessage(
            id = tempId,
            conversationId = currentConversationId,
            senderId = uid,
            senderUsername = currentUsername,
            receiverUsername = cleanPartner,
            text = cleanText,
            rawTimestamp = java.time.Instant.now().toString(),
            timestamp = "Sending...",
            isFromMe = isFromMe,
            isRead = false,
            status = MessageStatus.SENDING,
            replyToMessageId = replyToMessageId
        )
        if (!replyToMessageId.isNullOrBlank()) pendingReplyTargets[tempId] = replyToMessageId
        appendMessageToState(cleanPartner, optimistic)

        viewModelScope.launch(Dispatchers.IO) {
            offlineContentStore.enqueueMessage(tempId, cleanPartner, cleanText)
            if (!activeOutboxIds.add(tempId)) return@launch
            try {
                chatRepository.sendMessage(cleanPartner, cleanText, replyToMessageId).fold(
                    onSuccess = { serverMsg ->
                        withContext(Dispatchers.Main) {
                            replaceMessageInState(
                                cleanPartner,
                                tempId,
                                serverMsg.copy(receiverUsername = cleanPartner, status = MessageStatus.SENT)
                            )
                        }
                        // Confirmed server identity must reach Room before the outbox row is removed.
                        persistConversationsNow()
                        offlineContentStore.deleteOutbox(tempId)
                        pendingReplyTargets.remove(tempId)
                        chatRepository.triggerMessagePushBestEffort(serverMsg.id)
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)
                            showToast(error.message ?: "Message couldn't be sent. It will retry when you're online.")
                        }
                    }
                )
            } finally {
                activeOutboxIds.remove(tempId)
            }
        }
    }'''
    text = replace_function(text, "    fun sendMessage(", send)

    drain = r'''    private suspend fun drainMessageOutbox() {
        if (supabaseService.getCurrentUserId().isNullOrBlank() || !_uiState.value.isOnline) return
        val pending = messageOutboxMutex.withLock { offlineContentStore.pendingOutbox(100) }
        if (pending.isEmpty()) return

        for (item in pending) {
            if (!activeOutboxIds.add(item.localId)) continue
            try {
                chatRepository.sendMessage(
                    item.receiverUsername,
                    item.content,
                    pendingReplyTargets[item.localId]
                ).fold(
                    onSuccess = { serverMsg ->
                        withContext(Dispatchers.Main) {
                            replaceMessageInState(
                                item.receiverUsername,
                                item.localId,
                                serverMsg.copy(receiverUsername = item.receiverUsername, status = MessageStatus.SENT)
                            )
                        }
                        persistConversationsNow()
                        offlineContentStore.deleteOutbox(item.localId)
                        pendingReplyTargets.remove(item.localId)
                        chatRepository.triggerMessagePushBestEffort(serverMsg.id)
                    },
                    onFailure = { error ->
                        offlineContentStore.markOutboxFailure(item, error.message ?: "Send failed")
                        withContext(Dispatchers.Main) {
                            updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                        }
                    }
                )
            } finally {
                activeOutboxIds.remove(item.localId)
            }
        }
    }'''
    text = replace_function(text, "    private suspend fun drainMessageOutbox()", drain)

    replacement = r'''    private fun replaceMessageInState(partnerUsername: String, oldId: String, newMsg: ChatMessage) {
        val state = _uiState.value
        val conversations = state.conversations.toMutableList()
        var index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
        val serverConversationId = newMsg.conversationId
            ?.takeIf { it.isNotBlank() && !it.startsWith("local_") }

        if (index < 0) {
            val id = serverConversationId ?: "local_${UUID.randomUUID()}"
            conversations.add(
                0,
                ChatConversation(
                    id = id,
                    partnerUsername = partnerUsername,
                    partnerName = partnerUsername.replace(".", " ").replace("_", " ").capitalizeWords(),
                    partnerAvatar = "",
                    lastMessage = newMsg.text,
                    lastMessageTime = newMsg.timestamp,
                    lastMessageRawTime = newMsg.rawTimestamp,
                    messages = mutableListOf(newMsg.copy(conversationId = serverConversationId))
                )
            )
            _uiState.value = state.copy(conversations = conversations)
            persistConversations()
            return
        }

        val old = conversations[index]
        val resolvedConversationId = if (old.id.startsWith("local_") && serverConversationId != null) {
            serverConversationId
        } else old.id
        val normalized = newMsg.copy(conversationId = serverConversationId ?: newMsg.conversationId)
        val messages = old.messages.toMutableList()
        val messageIndex = messages.indexOfFirst { it.id == oldId || (normalized.id.isNotBlank() && it.id == normalized.id) }
        if (messageIndex >= 0) messages[messageIndex] = normalized
        else if (messages.none { it.id == normalized.id }) messages.add(normalized)

        conversations[index] = old.copy(
            id = resolvedConversationId,
            lastMessage = normalized.text,
            lastMessageTime = normalized.timestamp,
            lastMessageRawTime = normalized.rawTimestamp,
            messages = messages.distinctBy { it.id.ifBlank { "${it.rawTimestamp}:${it.text}" } }
                .sortedBy { it.rawTimestamp.ifBlank { "9999" } }
                .toMutableList()
        )

        // If a server summary arrived while this local conversation was sending, merge it.
        val duplicateIndex = conversations.indexOfFirst { candidate ->
            candidate.id == resolvedConversationId && candidate.partnerUsername.equals(partnerUsername, true)
        }
        if (duplicateIndex >= 0 && duplicateIndex != index) {
            val primary = conversations[index]
            val duplicate = conversations[duplicateIndex]
            val mergedMessages = (duplicate.messages + primary.messages)
                .distinctBy { it.id.ifBlank { "${it.rawTimestamp}:${it.text}" } }
                .sortedBy { it.rawTimestamp.ifBlank { "9999" } }
                .toMutableList()
            conversations[index] = primary.copy(messages = mergedMessages)
            conversations.removeAt(duplicateIndex)
            if (duplicateIndex < index) index -= 1
        }

        var history = state.messageHistoryHasMore
        if (resolvedConversationId != old.id) {
            history = history - old.id + (resolvedConversationId to (history[old.id] ?: history[resolvedConversationId] ?: true))
        }
        _uiState.value = state.copy(
            conversations = conversations,
            messageHistoryHasMore = history,
            loadingOlderConversationId = if (state.loadingOlderConversationId == old.id) resolvedConversationId else state.loadingOlderConversationId,
            loadingInitialConversationId = if (state.loadingInitialConversationId == old.id) resolvedConversationId else state.loadingInitialConversationId
        )
        persistConversations()
    }'''
    text = replace_function(text, "    private fun replaceMessageInState(", replacement)

    persist = r'''    private suspend fun persistConversationsNow(
        snapshot: List<ChatConversation> = _uiState.value.conversations
    ) {
        val owner = _uiState.value.myProfile.username
        cacheWriteMutex.withLock {
            offlineContentStore.replaceConversations(snapshot, owner)
        }
    }

    private fun persistConversations() {
        val snapshot = _uiState.value.conversations
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { persistConversationsNow(snapshot) }
                .onFailure { Log.w(TAG, "Unable to persist conversations", it) }
        }
    }'''
    text = replace_function(text, "    private fun persistConversations()", persist)

    sync_anchor = "        syncJob = viewModelScope.launch {\n"
    if sync_anchor not in text:
        raise RuntimeError("fetchSupabaseData launch anchor changed")
    text = text.replace(
        sync_anchor,
        sync_anchor + "            if (_uiState.value.isOnline) runCatching { chatRepository.ackPendingDeliveries() }\n",
        1,
    )

    VM.write_text(text)


def patch_premium() -> None:
    text = PREMIUM.read_text()
    if MARKER in text:
        return

    top_anchor = "    onRetryMessage: ((String, ChatMessage) -> Unit)? = null,\n"
    if top_anchor not in text:
        raise RuntimeError("Premium top signature changed")
    text = text.replace(
        top_anchor,
        top_anchor +
        "    hasMoreMessages: (String) -> Boolean = { false },\n"
        "    isLoadingOlder: (String) -> Boolean = { false },\n"
        "    onLoadOlder: (String) -> Unit = {},\n"
        "    isLoadingMessages: (String) -> Boolean = { false },\n",
        1,
    )

    call_anchor = "                        onRetryMessage = onRetryMessage,\n"
    if call_anchor not in text:
        raise RuntimeError("Premium master-detail call changed")
    text = text.replace(
        call_anchor,
        call_anchor +
        "                        hasMoreMessages = hasMoreMessages,\n"
        "                        isLoadingOlder = isLoadingOlder,\n"
        "                        onLoadOlder = onLoadOlder,\n"
        "                        isLoadingMessages = isLoadingMessages,\n",
        1,
    )

    master_anchor = "    onRetryMessage: ((String, ChatMessage) -> Unit)?,\n"
    if master_anchor not in text:
        raise RuntimeError("Premium master signature changed")
    text = text.replace(
        master_anchor,
        master_anchor +
        "    hasMoreMessages: (String) -> Boolean,\n"
        "    isLoadingOlder: (String) -> Boolean,\n"
        "    onLoadOlder: (String) -> Unit,\n"
        "    isLoadingMessages: (String) -> Boolean,\n",
        1,
    )

    detail_call_anchor = "                        onSend = { content, replyTo ->\n"
    pos = text.find(detail_call_anchor)
    if pos < 0:
        raise RuntimeError("Premium detail call changed")
    # Insert pagination arguments just before onSend at the active detail call.
    text = text[:pos] + (
        "                        hasMoreMessages = hasMoreMessages(displayedConversation.id),\n"
        "                        isLoadingOlder = isLoadingOlder(displayedConversation.id),\n"
        "                        onLoadOlder = { onLoadOlder(displayedConversation.partnerUsername) },\n"
        "                        isLoadingMessages = isLoadingMessages(displayedConversation.id),\n"
    ) + text[pos:]

    detail_signature_anchor = "    onBack: () -> Unit,\n"
    detail_start = text.find("private fun PremiumChatDetail(")
    if detail_start < 0:
        raise RuntimeError("PremiumChatDetail missing")
    sig_pos = text.find(detail_signature_anchor, detail_start)
    if sig_pos < 0:
        raise RuntimeError("Premium detail signature anchor changed")
    sig_end = sig_pos + len(detail_signature_anchor)
    text = text[:sig_end] + (
        "    hasMoreMessages: Boolean,\n"
        "    isLoadingOlder: Boolean,\n"
        "    onLoadOlder: () -> Unit,\n"
        "    isLoadingMessages: Boolean,\n"
    ) + text[sig_end:]

    list_anchor = "    val listState = rememberLazyListState()\n"
    list_pos = text.find(list_anchor, detail_start)
    if list_pos < 0:
        raise RuntimeError("Premium detail listState changed")
    list_end = list_pos + len(list_anchor)
    text = text[:list_end] + r'''    // MESSAGING_RELIABILITY_AUDIT_V3: older pages load only after a real user scroll reaches the top.
    var userHasScrolled by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) userHasScrolled = true
    }
    LaunchedEffect(listState.firstVisibleItemIndex, userHasScrolled, hasMoreMessages, isLoadingOlder) {
        if (userHasScrolled && listState.firstVisibleItemIndex <= 2 && hasMoreMessages && !isLoadingOlder) {
            onLoadOlder()
        }
    }
''' + text[list_end:]

    # Put a compact progress row at the top of the message list without hiding cached rows.
    lazy_marker = "                LazyColumn(\n                    state = listState,"
    lazy_pos = text.find(lazy_marker, detail_start)
    if lazy_pos < 0:
        raise RuntimeError("Premium message LazyColumn changed")
    open_body = text.find(") {", lazy_pos)
    if open_body < 0:
        raise RuntimeError("Premium message LazyColumn body missing")
    body_end = open_body + 3
    text = text[:body_end] + r'''
                    if (isLoadingOlder || (isLoadingMessages && visibleMessages.isEmpty())) {
                        item(key = "message_history_loading") {
                            Text(
                                if (isLoadingOlder) "Loading earlier messages…" else "Loading messages…",
                                color = palette.textSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
''' + text[body_end:]

    PREMIUM.write_text(text)


def patch_main() -> None:
    text = MAIN.read_text()
    if MARKER in text:
        return

    retry = '''                        onRetryMessage = { partner, message ->
                            viewModel.retrySendMessage(partner, message)
                        },
'''
    if retry not in text:
        raise RuntimeError("Premium retry call changed")
    text = text.replace(
        retry,
        retry + '''                        hasMoreMessages = { conversationId ->
                            uiState.messageHistoryHasMore[conversationId] ?: true
                        },
                        isLoadingOlder = { conversationId ->
                            uiState.loadingOlderConversationId == conversationId
                        },
                        onLoadOlder = { partner -> viewModel.loadOlderMessages(partner) },
                        isLoadingMessages = { conversationId ->
                            uiState.loadingInitialConversationId == conversationId
                        },
''',
        1,
    )

    # REST sending only requires network reachability. Realtime readiness is tracked separately
    # for receipt reconciliation and must never disable the composer by itself.
    premium_start = text.find("                    PremiumMessagesScreen(")
    premium_end = text.find("                    )", premium_start)
    region = text[premium_start:premium_end]
    if "isConnected = uiState.isLiveSupabaseConnected" in region:
        region = region.replace("isConnected = uiState.isLiveSupabaseConnected", "isConnected = uiState.isOnline", 1)
        text = text[:premium_start] + region + text[premium_end:]

    text = text.replace(
        "fun MainAppContent(\n",
        "// MESSAGING_RELIABILITY_AUDIT_V3\nfun MainAppContent(\n",
        1,
    )
    MAIN.write_text(text)


def main() -> None:
    patch_repository()
    patch_realtime()
    patch_viewmodel()
    patch_premium()
    patch_main()
    print("Messaging reliability audit v3 patch applied.")


if __name__ == "__main__":
    main()
