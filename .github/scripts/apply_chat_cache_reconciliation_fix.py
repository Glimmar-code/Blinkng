from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHED = ROOT / "app/src/main/java/com/example/data/local/CachedContent.kt"
STORE = ROOT / "app/src/main/java/com/example/data/local/OfflineContentStore.kt"
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
MARKER = "CHAT_CACHE_RECONCILIATION_V1"


def require_once(text: str, old: str, label: str) -> None:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")


def replace_function(text: str, marker: str, replacement: str) -> str:
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
                    return text[:start] + replacement.rstrip() + text[i + 1:]
        i += 1
    raise RuntimeError(f"Closing brace not found: {marker}")


def patch_cached_content() -> None:
    text = CACHED.read_text()
    if MARKER in text:
        return

    anchor = '''    @Query("DELETE FROM cached_feed_content WHERE id = :postId")
    suspend fun deletePost(postId: String)

'''
    require_once(text, anchor, "CachedContent deletePost anchor")
    additions = '''    @Query("DELETE FROM cached_feed_content WHERE id = :postId")
    suspend fun deletePost(postId: String)

    // CHAT_CACHE_RECONCILIATION_V1: remove only obsolete local aliases/temp rows.
    // Real server-backed history is never pruned by these queries.
    @Query("DELETE FROM cached_messages WHERE owner_username = :ownerUsername AND conversation_id IN (SELECT id FROM cached_conversations WHERE owner_username = :ownerUsername AND substr(id, 1, 6) = 'local_' AND partner_username IN (:partnerUsernames))")
    suspend fun deleteMessagesForLocalConversationAliases(ownerUsername: String, partnerUsernames: List<String>)

    @Query("DELETE FROM cached_conversations WHERE owner_username = :ownerUsername AND substr(id, 1, 6) = 'local_' AND partner_username IN (:partnerUsernames)")
    suspend fun deleteLocalConversationAliases(ownerUsername: String, partnerUsernames: List<String>)

    @Query("DELETE FROM cached_messages WHERE owner_username = :ownerUsername AND conversation_id IN (:conversationIds) AND substr(id, 1, 5) = 'temp_'")
    suspend fun deleteTempMessagesForConversations(ownerUsername: String, conversationIds: List<String>)

'''
    text = text.replace(anchor, additions, 1)

    old = '''    @Transaction
    suspend fun replaceConversations(
        conversations: List<CachedConversationEntity>,
        messages: List<CachedMessageEntity>
    ) {
        // Never blank chat history while a smaller/partial Supabase page refreshes.
        // Existing rows remain until the long-term prune policy removes old content.
        if (conversations.isNotEmpty()) insertConversations(conversations)
        if (messages.isNotEmpty()) insertMessages(messages)
    }'''
    require_once(text, old, "CachedContent replaceConversations")
    new = '''    @Transaction
    suspend fun replaceConversations(
        conversations: List<CachedConversationEntity>,
        messages: List<CachedMessageEntity>
    ) {
        // Never blank real server history while a smaller/partial page refreshes.
        // We only clear stale local aliases/temp rows that are replaced by this snapshot.
        conversations.groupBy { it.ownerUsername }.forEach { (owner, rows) ->
            val serverPartners = rows
                .filterNot { it.id.startsWith("local_") }
                .map { it.partnerUsername }
                .filter { it.isNotBlank() }
                .distinct()
            if (serverPartners.isNotEmpty()) {
                deleteMessagesForLocalConversationAliases(owner, serverPartners)
                deleteLocalConversationAliases(owner, serverPartners)
            }

            val conversationIds = rows.map { it.id }.filter { it.isNotBlank() }.distinct()
            if (conversationIds.isNotEmpty()) {
                // Active temp messages are inserted again below from the current snapshot;
                // obsolete temp rows from older snapshots stay gone.
                deleteTempMessagesForConversations(owner, conversationIds)
            }
        }

        if (conversations.isNotEmpty()) insertConversations(conversations)
        if (messages.isNotEmpty()) insertMessages(messages)
    }'''
    text = text.replace(old, new, 1)
    CACHED.write_text(text)


def patch_store() -> None:
    text = STORE.read_text()
    if MARKER in text:
        return

    marker = '''    suspend fun replaceConversations(conversations: List<ChatConversation>, ownerUsername: String = "") {'''
    start = text.find(marker)
    if start < 0:
        raise RuntimeError("OfflineContentStore replaceConversations missing")

    replacement = '''    // CHAT_CACHE_RECONCILIATION_V1: collapse a temporary local alias into the
    // server conversation for the same partner while preserving every cached message.
    private fun normalizeConversationsForCache(
        conversations: List<ChatConversation>
    ): List<ChatConversation> {
        if (conversations.size < 2) return conversations

        val grouped = LinkedHashMap<String, MutableList<ChatConversation>>()
        conversations.forEach { conversation ->
            val partnerKey = conversation.partnerUsername
                .trim().removePrefix("@").lowercase()
                .ifBlank { "id:${conversation.id}" }
            grouped.getOrPut(partnerKey) { mutableListOf() }.add(conversation)
        }

        return grouped.values.map { group ->
            val base = group.firstOrNull { !it.id.startsWith("local_") } ?: group.first()
            val mergedMessages = group
                .flatMap { it.messages }
                .distinctBy { message ->
                    message.id.ifBlank {
                        "${message.rawTimestamp}:${message.senderId}:${message.text}"
                    }
                }
                .sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }
                .toMutableList()
            val newest = mergedMessages.lastOrNull()
            base.copy(
                lastMessage = newest?.text ?: base.lastMessage,
                lastMessageTime = newest?.timestamp ?: base.lastMessageTime,
                lastMessageRawTime = newest?.rawTimestamp ?: base.lastMessageRawTime,
                messages = mergedMessages
            )
        }
    }

    suspend fun replaceConversations(conversations: List<ChatConversation>, ownerUsername: String = "") {
        val owner = ownerUsername.trim().removePrefix("@").lowercase().ifBlank { cachedOwnerUsername() }
        if (owner.isBlank()) return
        rememberOwner(owner)
        val normalizedConversations = normalizeConversationsForCache(conversations)
        val cachedAt = System.currentTimeMillis()
        val conversationRows = normalizedConversations.distinctBy { it.id }.mapIndexedNotNull { index, conversation ->
            codec.encodeConversation(conversation.copy(messages = mutableListOf()))?.let { json ->
                CachedConversationEntity(
                    id = conversation.id,
                    ownerUsername = owner,
                    partnerUsername = conversation.partnerUsername.lowercase(),
                    displayOrder = index,
                    payloadJson = json,
                    cachedAt = cachedAt
                )
            }
        }
        val messageRows = normalizedConversations.flatMap { conversation ->
            conversation.messages.distinctBy { it.id }.mapIndexedNotNull { index, message ->
                val stableId = message.id.ifBlank { "${conversation.id}_${index}_${message.rawTimestamp}" }
                codec.encodeMessage(message)?.let { json ->
                    CachedMessageEntity(
                        id = stableId,
                        ownerUsername = owner,
                        conversationId = conversation.id,
                        displayOrder = index,
                        rawTimestamp = message.rawTimestamp,
                        payloadJson = json,
                        cachedAt = cachedAt
                    )
                }
            }
        }
        dao.replaceConversations(conversationRows, messageRows)
    }'''
    text = replace_function(text, marker, replacement)
    STORE.write_text(text)


def patch_viewmodel() -> None:
    text = VM.read_text()
    if MARKER in text:
        return

    mutex_anchor = '''    private val cacheWriteMutex = Mutex()
'''
    require_once(text, mutex_anchor, "ViewModel cache mutex")
    text = text.replace(
        mutex_anchor,
        mutex_anchor + '''    // CHAT_CACHE_RECONCILIATION_V1: stale async snapshots must never write after newer ones.
    private val conversationCacheRevision = java.util.concurrent.atomic.AtomicLong(0L)
''',
        1,
    )

    persist_now = '''    private suspend fun persistConversationsNow(
        snapshot: List<ChatConversation> = _uiState.value.conversations
    ) {
        val owner = _uiState.value.myProfile.username
        val revision = conversationCacheRevision.incrementAndGet()
        cacheWriteMutex.withLock {
            // A newer chat-state write may have been scheduled while this critical write waited.
            // Persist the newest in-memory state in that case, never an older snapshot.
            val currentSnapshot = if (revision == conversationCacheRevision.get()) {
                snapshot
            } else {
                _uiState.value.conversations
            }
            offlineContentStore.replaceConversations(currentSnapshot, _uiState.value.myProfile.username.ifBlank { owner })
        }
    }'''
    text = replace_function(text, "    private suspend fun persistConversationsNow(", persist_now)

    persist_async = '''    private fun persistConversations() {
        val snapshot = _uiState.value.conversations
        val owner = _uiState.value.myProfile.username
        val revision = conversationCacheRevision.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cacheWriteMutex.withLock {
                    // Skip a snapshot that became stale before it reached disk.
                    if (revision != conversationCacheRevision.get()) return@withLock
                    offlineContentStore.replaceConversations(snapshot, owner)
                }
            }.onFailure { Log.w(TAG, "Unable to persist conversations", it) }
        }
    }'''
    text = replace_function(text, "    private fun persistConversations()", persist_async)

    video = '''    fun sendVideoMessage(partnerUsername: String, uri: Uri) {
        val cleanPartner = partnerUsername.trim().removePrefix("@")
        if (cleanPartner.isBlank()) return
        val tempId = "temp_video_${UUID.randomUUID()}"
        val uid = supabaseService.getCurrentUserId() ?: "local_user"
        val existingConversationId = _uiState.value.conversations
            .firstOrNull { it.partnerUsername.equals(cleanPartner, true) }
            ?.id
            ?.takeUnless { it.startsWith("local_") }
        appendMessageToState(
            cleanPartner,
            ChatMessage(
                id = tempId,
                conversationId = existingConversationId,
                senderId = uid,
                receiverUsername = cleanPartner,
                text = "Video",
                rawTimestamp = java.time.Instant.now().toString(),
                timestamp = "Sending...",
                isFromMe = true,
                isRead = false,
                status = MessageStatus.SENDING
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            MessageMediaService.sendVideoMessage(appContext, cleanPartner, uri).fold(
                onSuccess = { serverMsg ->
                    withContext(Dispatchers.Main) {
                        replaceMessageInState(cleanPartner, tempId, serverMsg.copy(status = MessageStatus.SENT))
                    }
                    // Confirmed media messages receive the same durable Room ordering as text.
                    persistConversationsNow()
                    withContext(Dispatchers.Main) { fetchSupabaseData() }
                },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)
                        showToast("Failed to send video. Tap the message to retry.")
                    }
                }
            )
        }
    }'''
    text = replace_function(text, "    fun sendVideoMessage(", video)

    VM.write_text(text)


def main() -> None:
    patch_cached_content()
    patch_store()
    patch_viewmodel()
    print("Chat cache reconciliation fix applied.")


if __name__ == "__main__":
    main()
