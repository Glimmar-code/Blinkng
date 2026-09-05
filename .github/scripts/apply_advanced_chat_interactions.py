from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "app/src/main/java/com/example/data/models/PostModel.kt"
REPO = ROOT / "app/src/main/java/com/example/data/repository/ChatRepository.kt"
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
PREMIUM = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
INTERACTIONS = ROOT / "app/src/main/java/com/example/ui/screens/ChatInteractionSheets.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        print(f"{label}: already applied")
        return text
    if old not in text:
        raise SystemExit(f"{label}: expected source block not found")
    print(f"{label}: applied")
    return text.replace(old, new, 1)


def replace_region(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        if replacement.strip() in text:
            print(f"{label}: already applied")
            return text
        raise SystemExit(f"{label}: start marker not found")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker not found")
    print(f"{label}: applied")
    return text[:start] + replacement.rstrip() + "\n\n" + text[end:]


# -----------------------------------------------------------------------------
# Models
# -----------------------------------------------------------------------------
model = MODEL.read_text()
model = replace_once(
    model,
    '''    val attachedImageUrl: String? = null,\n    val attachedVideoUrl: String? = null\n)''',
    '''    val attachedImageUrl: String? = null,\n    val attachedVideoUrl: String? = null,\n    val replyToMessageId: String? = null,\n    val editedAt: String? = null,\n    val deletedForEveryone: Boolean = false,\n    val reactionCounts: Map<String, Int> = emptyMap(),\n    val myReactions: Set<String> = emptySet(),\n    val isStarred: Boolean = false,\n    val isPinned: Boolean = false\n)''',
    "chat message interaction fields",
)
model = replace_once(
    model,
    '''    val faculty: String = "SIMME",\n    val lastSeen: String = "Last seen recently",\n    val messages: MutableList<ChatMessage> = mutableListOf()''',
    '''    val faculty: String = "SIMME",\n    val lastSeen: String = "Last seen recently",\n    val isMuted: Boolean = false,\n    val messages: MutableList<ChatMessage> = mutableListOf()''',
    "conversation muted state",
)
MODEL.write_text(model)


# -----------------------------------------------------------------------------
# Chat repository: reply/edit/delete/reactions/star/pin/report + state hydration.
# -----------------------------------------------------------------------------
repo = REPO.read_text()
repo = replace_once(
    repo,
    "        merged.values.toList()",
    "        applyConversationState(merged.values.toList())",
    "conversation clear/mute hydration",
)

fetch_start = repo.index("    suspend fun fetchMessagePage(")
fetch_end = repo.index("\n    /**\n     * Executes a Supabase RPC", fetch_start)
fetch = repo[fetch_start:fetch_end]
if "val baseMessages = buildList" not in fetch:
    fetch = fetch.replace("        buildList {", "        val baseMessages = buildList {", 1)
fetch = fetch.replace(
    '''                        attachedImageUrl = mediaUrl.takeIf { mediaType.equals("image", true) },\n                        attachedVideoUrl = mediaUrl.takeIf { mediaType.equals("video", true) }''',
    '''                        attachedImageUrl = mediaUrl.takeIf { mediaType.equals("image", true) },\n                        attachedVideoUrl = mediaUrl.takeIf { mediaType.equals("video", true) },\n                        replyToMessageId = o.optString("reply_to_message_id")\n                            .takeIf { it.isNotBlank() && !it.equals("null", true) },\n                        editedAt = o.optString("edited_at")\n                            .takeIf { it.isNotBlank() && !it.equals("null", true) },\n                        deletedForEveryone = o.optBoolean("deleted_for_everyone", false)''',
    1,
)
if "enrichMessageActions(visibleMessages)" not in fetch:
    closing = "        }\n    }"
    pos = fetch.rfind(closing)
    if pos < 0:
        raise SystemExit("fetch message page closing block not found")
    fetch = fetch[:pos] + '''        }\n        val visibleMessages = filterClearedMessages(conversationId, baseMessages)\n        enrichMessageActions(visibleMessages)\n    }''' + fetch[pos + len(closing):]
repo = repo[:fetch_start] + fetch + repo[fetch_end:]

repo = replace_once(
    repo,
    '''    suspend fun sendMessage(receiverUsername: String, text: String): Result<ChatMessage> = withContext(Dispatchers.IO) {''',
    '''    suspend fun sendMessage(\n        receiverUsername: String,\n        text: String,\n        replyToMessageId: String? = null\n    ): Result<ChatMessage> = withContext(Dispatchers.IO) {''',
    "reply-aware send signature",
)
repo = replace_once(
    repo,
    '''                if (messageId.isBlank() || messageId == "null") {\n                    return@withContext Result.failure(Exception("Message was not created."))\n                }\n                SupabaseService.accessToken()?.takeIf { it.isNotBlank() }?.let { currentToken ->''',
    '''                if (messageId.isBlank() || messageId == "null") {\n                    return@withContext Result.failure(Exception("Message was not created."))\n                }\n                val validReplyId = replyToMessageId\n                    ?.takeIf { id -> runCatching { java.util.UUID.fromString(id) }.isSuccess }\n                if (validReplyId != null) {\n                    runCatching { setMessageReply(messageId, validReplyId) }\n                }\n                SupabaseService.accessToken()?.takeIf { it.isNotBlank() }?.let { currentToken ->''',
    "link reply after send",
)
repo = replace_once(
    repo,
    '''                        isFromMe = true,\n                        isRead = false,\n                        status = MessageStatus.SENT''',
    '''                        isFromMe = true,\n                        isRead = false,\n                        status = MessageStatus.SENT,\n                        replyToMessageId = replyToMessageId\n                            ?.takeIf { id -> runCatching { java.util.UUID.fromString(id) }.isSuccess }''',
    "returned reply metadata",
)

repo_helpers = r'''    private data class MessageActionState(
        val reactions: Map<String, Int> = emptyMap(),
        val myReactions: Set<String> = emptySet(),
        val isStarred: Boolean = false,
        val isHidden: Boolean = false,
        val isPinned: Boolean = false
    )

    private data class ConversationUserState(
        val clearedAt: java.time.Instant? = null,
        val isMuted: Boolean = false
    )

    private fun isServerUuid(value: String?): Boolean =
        !value.isNullOrBlank() && runCatching { java.util.UUID.fromString(value) }.isSuccess

    private suspend fun booleanRpc(name: String, body: JSONObject): Boolean =
        runCatching {
            postAuthenticatedRpc(name, body).trim().trim('"').equals("true", ignoreCase = true)
        }.getOrDefault(false)

    suspend fun setMessageReply(messageId: String, replyToMessageId: String?): Boolean {
        if (!isServerUuid(messageId)) return false
        if (replyToMessageId != null && !isServerUuid(replyToMessageId)) return false
        return booleanRpc(
            "set_message_reply",
            JSONObject().apply {
                put("p_message_id", messageId)
                put("p_reply_to_message_id", replyToMessageId ?: JSONObject.NULL)
            }
        )
    }

    suspend fun editMessage(messageId: String, content: String): Boolean {
        if (!isServerUuid(messageId) || content.isBlank()) return false
        return booleanRpc(
            "edit_message",
            JSONObject().put("p_message_id", messageId).put("p_content", content.trim())
        )
    }

    suspend fun deleteMessageForEveryone(messageId: String): Boolean {
        if (!isServerUuid(messageId)) return false
        return booleanRpc("delete_message_for_everyone", JSONObject().put("p_message_id", messageId))
    }

    suspend fun setMessageReaction(messageId: String, emoji: String, active: Boolean): Boolean {
        if (!isServerUuid(messageId) || emoji.isBlank()) return false
        return booleanRpc(
            "set_message_reaction",
            JSONObject().put("p_message_id", messageId).put("p_emoji", emoji).put("p_active", active)
        )
    }

    suspend fun setMessageStarred(messageId: String, starred: Boolean): Boolean {
        if (!isServerUuid(messageId)) return false
        return booleanRpc(
            "set_message_starred",
            JSONObject().put("p_message_id", messageId).put("p_starred", starred)
        )
    }

    suspend fun hideMessageForMe(messageId: String): Boolean {
        if (!isServerUuid(messageId)) return false
        return booleanRpc("hide_message_for_me", JSONObject().put("p_message_id", messageId))
    }

    suspend fun setMessagePinned(messageId: String, pinned: Boolean): Boolean {
        if (!isServerUuid(messageId)) return false
        return booleanRpc(
            "set_message_pinned",
            JSONObject().put("p_message_id", messageId).put("p_pinned", pinned)
        )
    }

    suspend fun reportMessage(messageId: String, reason: String): Boolean {
        if (!isServerUuid(messageId) || reason.isBlank()) return false
        return booleanRpc(
            "report_message",
            JSONObject().put("p_message_id", messageId).put("p_reason", reason.take(500))
        )
    }

    suspend fun clearConversationForMe(conversationId: String): Boolean {
        if (!isServerUuid(conversationId)) return false
        return booleanRpc(
            "clear_conversation_for_me",
            JSONObject().put("p_conversation_id", conversationId)
        )
    }

    suspend fun setConversationMuted(conversationId: String, muted: Boolean): Boolean {
        if (!isServerUuid(conversationId)) return false
        return booleanRpc(
            "set_conversation_muted",
            JSONObject().put("p_conversation_id", conversationId).put("p_muted", muted)
        )
    }

    suspend fun reportConversation(conversationId: String, reason: String): Boolean {
        if (!isServerUuid(conversationId) || reason.isBlank()) return false
        return booleanRpc(
            "report_conversation",
            JSONObject().put("p_conversation_id", conversationId).put("p_reason", reason.take(500))
        )
    }

    private suspend fun enrichMessageActions(messages: List<ChatMessage>): List<ChatMessage> {
        val serverIds = messages.map { it.id }.filter(::isServerUuid)
        if (serverIds.isEmpty()) return messages
        val raw = runCatching {
            postAuthenticatedRpc(
                "get_message_action_state",
                JSONObject().put("p_message_ids", org.json.JSONArray(serverIds))
            )
        }.getOrNull() ?: return messages
        val array = runCatching { org.json.JSONArray(if (raw.isBlank()) "[]" else raw) }.getOrNull()
            ?: return messages
        val states = mutableMapOf<String, MessageActionState>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val messageId = o.optString("message_id")
            if (messageId.isBlank()) continue
            val reactionObject = o.optJSONObject("reactions") ?: JSONObject()
            val reactionCounts = buildMap<String, Int> {
                val keys = reactionObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, reactionObject.optInt(key, 0))
                }
            }.filterValues { it > 0 }
            val mineArray = o.optJSONArray("my_reactions")
            val mine = buildSet {
                if (mineArray != null) {
                    for (j in 0 until mineArray.length()) {
                        mineArray.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            states[messageId] = MessageActionState(
                reactions = reactionCounts,
                myReactions = mine,
                isStarred = o.optBoolean("is_starred", false),
                isHidden = o.optBoolean("is_hidden", false),
                isPinned = o.optBoolean("is_pinned", false)
            )
        }
        return messages.mapNotNull { message ->
            val state = states[message.id] ?: return@mapNotNull message
            if (state.isHidden) null else message.copy(
                reactionCounts = state.reactions,
                myReactions = state.myReactions,
                isStarred = state.isStarred,
                isPinned = state.isPinned
            )
        }
    }

    private suspend fun fetchConversationStates(conversationIds: List<String>): Map<String, ConversationUserState> {
        val ids = conversationIds.filter(::isServerUuid)
        if (ids.isEmpty()) return emptyMap()
        val raw = runCatching {
            postAuthenticatedRpc(
                "get_my_conversation_state",
                JSONObject().put("p_conversation_ids", org.json.JSONArray(ids))
            )
        }.getOrNull() ?: return emptyMap()
        val array = runCatching { org.json.JSONArray(if (raw.isBlank()) "[]" else raw) }.getOrNull()
            ?: return emptyMap()
        return buildMap {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("conversation_id")
                if (id.isBlank()) continue
                val cleared = o.optString("cleared_at")
                    .takeIf { it.isNotBlank() && !it.equals("null", true) }
                    ?.let { value ->
                        runCatching { java.time.OffsetDateTime.parse(value).toInstant() }.getOrNull()
                    }
                put(id, ConversationUserState(clearedAt = cleared, isMuted = o.optBoolean("is_muted", false)))
            }
        }
    }

    private suspend fun applyConversationState(conversations: List<ChatConversation>): List<ChatConversation> {
        val states = fetchConversationStates(conversations.map { it.id })
        if (states.isEmpty()) return conversations
        return conversations.mapNotNull { conversation ->
            val state = states[conversation.id] ?: return@mapNotNull conversation
            val cleared = state.clearedAt
            val lastAt = conversation.lastMessageRawTime.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            }
            if (cleared != null && (lastAt == null || !lastAt.isAfter(cleared))) null
            else conversation.copy(isMuted = state.isMuted)
        }
    }

    private suspend fun filterClearedMessages(conversationId: String, messages: List<ChatMessage>): List<ChatMessage> {
        val cleared = fetchConversationStates(listOf(conversationId))[conversationId]?.clearedAt ?: return messages
        return messages.filter { message ->
            val created = message.rawTimestamp.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            }
            created == null || created.isAfter(cleared)
        }
    }

'''
repo = replace_once(
    repo,
    "    private fun triggerMessagePush(messageId: String, accessToken: String) {",
    repo_helpers + "    private fun triggerMessagePush(messageId: String, accessToken: String) {",
    "message action repository API",
)
REPO.write_text(repo)


# -----------------------------------------------------------------------------
# ViewModel: true concurrent sends + optimistic interaction actions.
# -----------------------------------------------------------------------------
vm = VM.read_text()
vm = replace_once(
    vm,
    '''    private val messageOutboxMutex = Mutex()\n    private var syncJob: Job? = null''',
    '''    private val messageOutboxMutex = Mutex()\n    private val activeOutboxIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()\n    private val pendingReplyTargets = java.util.concurrent.ConcurrentHashMap<String, String>()\n    private var syncJob: Job? = null''',
    "parallel outbox tracking",
)
vm = replace_once(
    vm,
    '''    fun sendMessage(partnerUsername: String, text: String, isFromMe: Boolean = true) {''',
    '''    fun sendMessage(\n        partnerUsername: String,\n        text: String,\n        isFromMe: Boolean = true,\n        replyToMessageId: String? = null\n    ) {''',
    "reply-aware ViewModel send",
)
vm = replace_once(
    vm,
    '''            isFromMe = true,\n            isRead = false,\n            status = MessageStatus.SENDING\n        )\n        appendMessageToState(cleanPartner, optimistic)''',
    '''            isFromMe = true,\n            isRead = false,\n            status = MessageStatus.SENDING,\n            replyToMessageId = replyToMessageId\n        )\n        if (!replyToMessageId.isNullOrBlank()) pendingReplyTargets[tempId] = replyToMessageId\n        appendMessageToState(cleanPartner, optimistic)''',
    "optimistic reply metadata",
)
vm = replace_once(
    vm,
    '''            if (pending == null) {\n                offlineContentStore.enqueueMessage(failedMessage.id, partnerUsername.trim(), failedMessage.text.trim())\n            } else {''',
    '''            failedMessage.replyToMessageId?.takeIf { it.isNotBlank() }?.let { pendingReplyTargets[failedMessage.id] = it }\n            if (pending == null) {\n                offlineContentStore.enqueueMessage(failedMessage.id, partnerUsername.trim(), failedMessage.text.trim())\n            } else {''',
    "retry reply metadata",
)

new_drain = r'''    private suspend fun drainMessageOutbox() {
        if (supabaseService.getCurrentUserId().isNullOrBlank()) return
        // Only protect the Room snapshot. Network requests run independently so a slow
        // message can never block a rapid second/third send.
        val pending = messageOutboxMutex.withLock { offlineContentStore.pendingOutbox(100) }
        if (pending.isEmpty()) return

        kotlinx.coroutines.coroutineScope {
            pending.map { item ->
                async(Dispatchers.IO) {
                    if (!activeOutboxIds.add(item.localId)) return@async
                    try {
                        chatRepository.sendMessage(
                            item.receiverUsername,
                            item.content,
                            pendingReplyTargets[item.localId]
                        ).fold(
                            onSuccess = { serverMsg ->
                                offlineContentStore.deleteOutbox(item.localId)
                                pendingReplyTargets.remove(item.localId)
                                withContext(Dispatchers.Main) {
                                    replaceMessageInState(
                                        item.receiverUsername,
                                        item.localId,
                                        serverMsg.copy(
                                            receiverUsername = item.receiverUsername,
                                            status = MessageStatus.SENT
                                        )
                                    )
                                    persistConversations()
                                }
                                runCatching {
                                    supabaseService.recordActivity(
                                        item.receiverUsername,
                                        "sent you a direct message",
                                        NotificationFilter.ALL,
                                        targetUsername = supabaseService.getCurrentUsername().orEmpty(),
                                        previewText = item.content,
                                        targetType = "CHAT"
                                    )
                                }
                                reconcileConversationSummary(item.receiverUsername)
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
                                    offlineContentStore.resetOutbox(item.localId)
                                    withContext(Dispatchers.Main) {
                                        updateMessageStatusInState(
                                            item.receiverUsername,
                                            item.localId,
                                            MessageStatus.SENDING,
                                            pendingLabel = "Queued"
                                        )
                                        persistConversations()
                                    }
                                } else {
                                    offlineContentStore.markOutboxFailure(item, detail.ifBlank { "Message send failed" })
                                    withContext(Dispatchers.Main) {
                                        updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                                        persistConversations()
                                        showToast(detail.ifBlank { "Message failed. Please try again." })
                                    }
                                }
                            }
                        )
                    } finally {
                        activeOutboxIds.remove(item.localId)
                    }
                }
            }.forEach { it.await() }
        }
    }
'''
vm = replace_region(
    vm,
    "    private suspend fun drainMessageOutbox()",
    "    private suspend fun reconcileConversationSummary",
    new_drain,
    "parallel outbox drain",
)

vm_actions = r'''    private fun mutateChatMessage(
        partnerUsername: String,
        messageId: String,
        transform: (ChatMessage) -> ChatMessage
    ) {
        val state = _uiState.value
        val conversations = state.conversations.map { conversation ->
            if (!conversation.partnerUsername.equals(partnerUsername, true)) conversation
            else conversation.copy(
                messages = conversation.messages.map { message ->
                    if (message.id == messageId) transform(message) else message
                }.toMutableList()
            )
        }
        _uiState.value = state.copy(conversations = conversations)
        persistConversations()
    }

    fun toggleMessageReaction(partnerUsername: String, message: ChatMessage, emoji: String) {
        if (emoji.isBlank() || message.id.startsWith("temp_")) return
        val wasMine = emoji in message.myReactions
        val active = !wasMine
        val before = message
        mutateChatMessage(partnerUsername, message.id) { current ->
            val counts = current.reactionCounts.toMutableMap()
            val next = (counts[emoji] ?: 0) + if (active) 1 else -1
            if (next <= 0) counts.remove(emoji) else counts[emoji] = next
            current.copy(
                reactionCounts = counts,
                myReactions = if (active) current.myReactions + emoji else current.myReactions - emoji
            )
        }
        viewModelScope.launch {
            if (!chatRepository.setMessageReaction(message.id, emoji, active)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't update reaction.")
            }
        }
    }

    fun editChatMessage(partnerUsername: String, message: ChatMessage, newText: String) {
        val clean = newText.trim()
        if (!message.isFromMe || message.id.startsWith("temp_") || clean.isBlank()) return
        val before = message
        mutateChatMessage(partnerUsername, message.id) {
            it.copy(text = clean, editedAt = java.time.Instant.now().toString())
        }
        viewModelScope.launch {
            if (!chatRepository.editMessage(message.id, clean)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't edit message.")
            }
        }
    }

    fun deleteChatMessageForMe(partnerUsername: String, message: ChatMessage) {
        val state = _uiState.value
        val before = state.conversations
        val updated = before.map { conversation ->
            if (!conversation.partnerUsername.equals(partnerUsername, true)) conversation
            else conversation.copy(messages = conversation.messages.filterNot { it.id == message.id }.toMutableList())
        }
        _uiState.value = state.copy(conversations = updated)
        persistConversations()
        viewModelScope.launch(Dispatchers.IO) {
            if (message.id.startsWith("temp_")) {
                offlineContentStore.deleteOutbox(message.id)
                pendingReplyTargets.remove(message.id)
                return@launch
            }
            if (!chatRepository.hideMessageForMe(message.id)) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(conversations = before)
                    persistConversations()
                    showToast("Couldn't delete message for you.")
                }
            }
        }
    }

    fun deleteChatMessageForEveryone(partnerUsername: String, message: ChatMessage) {
        if (!message.isFromMe || message.id.startsWith("temp_")) return
        val before = message
        mutateChatMessage(partnerUsername, message.id) {
            it.copy(
                text = "",
                attachedImageUrl = null,
                attachedVideoUrl = null,
                deletedForEveryone = true
            )
        }
        viewModelScope.launch {
            if (!chatRepository.deleteMessageForEveryone(message.id)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't delete message for everyone.")
            }
        }
    }

    fun toggleChatMessageStar(partnerUsername: String, message: ChatMessage) {
        if (message.id.startsWith("temp_")) return
        val before = message
        val next = !message.isStarred
        mutateChatMessage(partnerUsername, message.id) { it.copy(isStarred = next) }
        viewModelScope.launch {
            if (!chatRepository.setMessageStarred(message.id, next)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't update starred message.")
            }
        }
    }

    fun toggleChatMessagePin(partnerUsername: String, message: ChatMessage) {
        if (message.id.startsWith("temp_")) return
        val before = message
        val next = !message.isPinned
        mutateChatMessage(partnerUsername, message.id) { it.copy(isPinned = next) }
        viewModelScope.launch {
            if (!chatRepository.setMessagePinned(message.id, next)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't update pinned message.")
            }
        }
    }

    fun reportChatMessage(message: ChatMessage, reason: String) {
        if (message.id.startsWith("temp_")) return
        viewModelScope.launch {
            if (chatRepository.reportMessage(message.id, reason)) showToast("Message reported.")
            else showToast("Couldn't report message.")
        }
    }

    fun clearConversationForMe(conversation: ChatConversation) {
        val state = _uiState.value
        val before = state.conversations
        _uiState.value = state.copy(
            conversations = before.filterNot { it.id == conversation.id },
            activeConversationPartner = if (state.activeConversationPartner.equals(conversation.partnerUsername, true)) null else state.activeConversationPartner,
            isConversationFullScreen = false
        )
        persistConversations()
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (!chatRepository.clearConversationForMe(conversation.id)) {
                _uiState.value = _uiState.value.copy(conversations = before)
                persistConversations()
                showToast("Couldn't delete chat.")
            }
        }
    }

    fun setConversationMuted(conversation: ChatConversation, muted: Boolean) {
        val before = conversation.isMuted
        val state = _uiState.value
        _uiState.value = state.copy(conversations = state.conversations.map {
            if (it.id == conversation.id) it.copy(isMuted = muted) else it
        })
        persistConversations()
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (!chatRepository.setConversationMuted(conversation.id, muted)) {
                val latest = _uiState.value
                _uiState.value = latest.copy(conversations = latest.conversations.map {
                    if (it.id == conversation.id) it.copy(isMuted = before) else it
                })
                persistConversations()
                showToast("Couldn't update chat notifications.")
            }
        }
    }

    fun reportConversation(conversation: ChatConversation, reason: String) {
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (chatRepository.reportConversation(conversation.id, reason)) showToast("Conversation reported.")
            else showToast("Couldn't report conversation.")
        }
    }

'''
vm = replace_once(
    vm,
    "    private suspend fun reconcileConversationSummary(partnerUsername: String) {",
    vm_actions + "    private suspend fun reconcileConversationSummary(partnerUsername: String) {",
    "message interaction ViewModel actions",
)
VM.write_text(vm)


# -----------------------------------------------------------------------------
# MainActivity wiring.
# -----------------------------------------------------------------------------
main = MAIN.read_text()
main = replace_once(
    main,
    '''                        onSendMessage = { partner, text -> viewModel.sendMessage(partner, text) },\n                        onSendVideo = { partner, uri -> viewModel.sendVideoMessage(partner, uri) },''',
    '''                        onSendMessage = { partner, text, replyTo ->\n                            viewModel.sendMessage(partner, text, replyToMessageId = replyTo)\n                        },\n                        interactionActions = ChatInteractionActions(\n                            onReact = { partner, message, emoji -> viewModel.toggleMessageReaction(partner, message, emoji) },\n                            onEdit = { partner, message, content -> viewModel.editChatMessage(partner, message, content) },\n                            onDeleteForMe = { partner, message -> viewModel.deleteChatMessageForMe(partner, message) },\n                            onDeleteForEveryone = { partner, message -> viewModel.deleteChatMessageForEveryone(partner, message) },\n                            onToggleStar = { partner, message -> viewModel.toggleChatMessageStar(partner, message) },\n                            onTogglePin = { partner, message -> viewModel.toggleChatMessagePin(partner, message) },\n                            onReportMessage = { message, reason -> viewModel.reportChatMessage(message, reason) },\n                            onClearConversation = { conversation -> viewModel.clearConversationForMe(conversation) },\n                            onMuteConversation = { conversation, muted -> viewModel.setConversationMuted(conversation, muted) },\n                            onReportConversation = { conversation, reason -> viewModel.reportConversation(conversation, reason) }\n                        ),\n                        onSendVideo = { partner, uri -> viewModel.sendVideoMessage(partner, uri) },''',
    "wire chat interaction actions",
)
MAIN.write_text(main)


# -----------------------------------------------------------------------------
# Interaction sheet components (new source file).
# -----------------------------------------------------------------------------
INTERACTIONS.write_text(r'''package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.ui.theme.MessagePalette


data class ChatInteractionActions(
    val onReact: (String, ChatMessage, String) -> Unit = { _, _, _ -> },
    val onEdit: (String, ChatMessage, String) -> Unit = { _, _, _ -> },
    val onDeleteForMe: (String, ChatMessage) -> Unit = { _, _ -> },
    val onDeleteForEveryone: (String, ChatMessage) -> Unit = { _, _ -> },
    val onToggleStar: (String, ChatMessage) -> Unit = { _, _ -> },
    val onTogglePin: (String, ChatMessage) -> Unit = { _, _ -> },
    val onReportMessage: (ChatMessage, String) -> Unit = { _, _ -> },
    val onClearConversation: (ChatConversation) -> Unit = {},
    val onMuteConversation: (ChatConversation, Boolean) -> Unit = { _, _ -> },
    val onReportConversation: (ChatConversation, String) -> Unit = { _, _ -> }
)

private val CHAT_REACTIONS = listOf(
    "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇",
    "🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚",
    "😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩",
    "🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣",
    "😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬",
    "🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗",
    "🤔","🫣","🤭","🫢","🫡","🤫","🫠","🤥","😶","😐",
    "😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴",
    "🤤","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕",
    "👍","👎","👏","🙌","🤝","🙏","💪","🔥","❤️","💜"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsSheet(
    message: ChatMessage,
    palette: MessagePalette,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onToggleStar: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Text(
            "React",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(CHAT_REACTIONS) { emoji ->
                val selected = emoji in message.myReactions
                Surface(
                    shape = CircleShape,
                    color = if (selected) palette.accent.copy(alpha = .24f) else palette.glassElevated,
                    border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
                    modifier = Modifier.size(42.dp).clickable { onReaction(emoji) }
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = palette.border)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageActionChip("Reply", Icons.Default.Reply, palette, Modifier.weight(1f), onReply)
                MessageActionChip("Forward", Icons.Default.Forward, palette, Modifier.weight(1f), onForward)
                if (message.isFromMe && !message.deletedForEveryone) {
                    MessageActionChip("Edit", Icons.Default.Edit, palette, Modifier.weight(1f), onEdit)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageActionChip(if (message.isStarred) "Unstar" else "Star", Icons.Default.Star, palette, Modifier.weight(1f), onToggleStar)
                MessageActionChip("Share", Icons.Default.Share, palette, Modifier.weight(1f), onShare)
                MessageActionChip(if (message.isPinned) "Unpin" else "Pin", Icons.Default.Place, palette, Modifier.weight(1f), onTogglePin)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageActionChip("Delete for me", Icons.Default.Delete, palette, Modifier.weight(1f), onDeleteForMe, danger = true)
                if (message.isFromMe) {
                    MessageActionChip("Delete everyone", Icons.Default.ClearAll, palette, Modifier.weight(1f), onDeleteForEveryone, danger = true)
                }
                MessageActionChip("Report", Icons.Default.Report, palette, Modifier.weight(1f), onReport, danger = true)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MessageActionChip(
    label: String,
    icon: ImageVector,
    palette: MessagePalette,
    modifier: Modifier,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Surface(
        color = if (danger) palette.danger.copy(alpha = .10f) else palette.glassElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (danger) palette.danger.copy(alpha = .35f) else palette.border),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) palette.danger else palette.textPrimary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = if (danger) palette.danger else palette.textSecondary, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForwardMessageSheet(
    sourcePartner: String,
    conversations: List<ChatConversation>,
    palette: MessagePalette,
    onForward: (List<ChatConversation>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    val candidates = conversations.filterNot { it.partnerUsername.equals(sourcePartner, true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Forward message", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Choose up to 10 chats • ${selected.size}/10", color = palette.textSecondary, fontSize = 10.sp)
            }
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onForward(candidates.filter { it.id in selected }) }
            ) { Text("Send", color = palette.accent, fontWeight = FontWeight.Bold) }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(candidates, key = { it.id }) { conversation ->
                val checked = conversation.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) selected.remove(conversation.id)
                            else if (selected.size < 10) selected.add(conversation.id)
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (checked) palette.accent else palette.glassElevated,
                        border = BorderStroke(1.dp, if (checked) palette.accent else palette.border),
                        modifier = Modifier.size(28.dp)
                    ) { Box(contentAlignment = Alignment.Center) { if (checked) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) } }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(conversation.partnerName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${conversation.partnerUsername.removePrefix("@")}", color = palette.textSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatOverflowSheet(
    conversation: ChatConversation,
    palette: MessagePalette,
    pinnedOnly: Boolean,
    starredOnly: Boolean,
    onProfile: () -> Unit,
    onSearch: () -> Unit,
    onPinned: () -> Unit,
    onStarred: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Text(conversation.partnerName, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        OverflowRow("View profile", Icons.Default.Person, palette, onProfile)
        OverflowRow("Search in chat", Icons.Default.Search, palette, onSearch)
        OverflowRow(if (pinnedOnly) "Show all messages" else "Pinned messages", Icons.Default.Place, palette, onPinned)
        OverflowRow(if (starredOnly) "Show all messages" else "Starred messages", Icons.Default.Star, palette, onStarred)
        OverflowRow(if (conversation.isMuted) "Unmute notifications" else "Mute notifications", Icons.Default.VolumeOff, palette, onMute)
        HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
        OverflowRow("Delete chat", Icons.Default.Delete, palette, onDelete, danger = true)
        OverflowRow("Report conversation", Icons.Default.Report, palette, onReport, danger = true)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OverflowRow(
    label: String,
    icon: ImageVector,
    palette: MessagePalette,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) palette.danger else palette.textSecondary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, color = if (danger) palette.danger else palette.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

internal fun shareChatMessage(context: Context, message: ChatMessage) {
    val body = message.text.takeIf { it.isNotBlank() }
        ?: message.attachedVideoUrl
        ?: message.attachedImageUrl
        ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share message"))
}
''')


# -----------------------------------------------------------------------------
# Premium screen wiring + gesture/action behavior.
# -----------------------------------------------------------------------------
premium = PREMIUM.read_text()
premium = replace_once(
    premium,
    "import androidx.compose.foundation.gestures.detectHorizontalDragGestures",
    "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\nimport androidx.compose.foundation.gestures.detectTapGestures",
    "tap gesture import",
)
premium = replace_once(
    premium,
    "import androidx.compose.ui.draw.scale",
    "import androidx.compose.ui.draw.scale\nimport androidx.compose.ui.graphics.graphicsLayer",
    "graphics layer import",
)
premium = replace_once(
    premium,
    "import androidx.compose.material.icons.filled.MoreVert",
    "import androidx.compose.material.icons.filled.MoreVert\nimport androidx.compose.material.icons.filled.Reply",
    "reply icon import",
)
premium = premium.replace("onSendMessage: (String, String) -> Unit,", "onSendMessage: (String, String, String?) -> Unit,")
premium = replace_once(
    premium,
    '''    onOpenActivity: () -> Unit,\n    isConnected: Boolean = true,''',
    '''    onOpenActivity: () -> Unit,\n    interactionActions: ChatInteractionActions = ChatInteractionActions(),\n    isConnected: Boolean = true,''',
    "interaction actions parameter",
)
premium = replace_once(
    premium,
    '''                    onRetryMessage = onRetryMessage,\n                    onProfileClick = onProfileClick,''',
    '''                    onRetryMessage = onRetryMessage,\n                    interactionActions = interactionActions,\n                    onProfileClick = onProfileClick,''',
    "pass interactions to master detail",
)
premium = replace_once(
    premium,
    '''    onRetryMessage: ((String, ChatMessage) -> Unit)?,\n    onProfileClick: (String) -> Unit,''',
    '''    onRetryMessage: ((String, ChatMessage) -> Unit)?,\n    interactionActions: ChatInteractionActions,\n    onProfileClick: (String) -> Unit,''',
    "master detail interaction parameter",
)
premium = replace_once(
    premium,
    '''                    PremiumChatDetail(\n                        conversation = displayedConversation,\n                        palette = palette,''',
    '''                    PremiumChatDetail(\n                        conversation = displayedConversation,\n                        allConversations = conversations,\n                        palette = palette,''',
    "chat detail conversation list",
)
premium = replace_once(
    premium,
    '''                        onSend = { onSendMessage(displayedConversation.partnerUsername, it) },\n                        onSendVideo = { onSendVideo(displayedConversation.partnerUsername, it) },''',
    '''                        onSend = { content, replyTo ->\n                            onSendMessage(displayedConversation.partnerUsername, content, replyTo)\n                        },\n                        onForward = { target, message ->\n                            val forwarded = message.text.takeIf { it.isNotBlank() }\n                                ?: message.attachedVideoUrl\n                                ?: message.attachedImageUrl\n                                ?: "Forwarded message"\n                            onSendMessage(target, forwarded, null)\n                        },\n                        interactionActions = interactionActions,\n                        onSendVideo = { onSendVideo(displayedConversation.partnerUsername, it) },''',
    "chat detail send and forward wiring",
)

new_detail = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumChatDetail(
    conversation: ChatConversation,
    allConversations: List<ChatConversation>,
    palette: MessagePalette,
    onBack: () -> Unit,
    onSend: (String, String?) -> Unit,
    onForward: (String, ChatMessage) -> Unit,
    interactionActions: ChatInteractionActions,
    onSendVideo: (Uri) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onProfileClick: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    isConnected: Boolean,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    val context = LocalContext.current
    var text by rememberSaveable(conversation.partnerUsername) { mutableStateOf("") }
    var showEmojiRail by rememberSaveable(conversation.partnerUsername) { mutableStateOf(false) }
    var showAttachmentSheet by rememberSaveable(conversation.partnerUsername) { mutableStateOf(false) }
    var selectedMessage by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var forwardingMessage by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var replyingTo by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var showOverflow by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var searchVisible by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(conversation.partnerUsername) { mutableStateOf("") }
    var pinnedOnly by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var starredOnly by remember(conversation.partnerUsername) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSendVideo(uri)
    }
    val startDictation = rememberSpeechInput { spoken ->
        text = listOf(text.trim(), spoken.trim()).filter { it.isNotBlank() }.joinToString(" ")
    }

    val visibleMessages = conversation.messages.filter { message ->
        val matchesSearch = searchQuery.isBlank() || message.text.contains(searchQuery, ignoreCase = true)
        val matchesPinned = !pinnedOnly || message.isPinned
        val matchesStarred = !starredOnly || message.isStarred
        matchesSearch && matchesPinned && matchesStarred
    }

    fun submitMessage(value: String = text) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val edit = editingMessage
        if (edit != null) {
            interactionActions.onEdit(conversation.partnerUsername, edit, clean)
            editingMessage = null
        } else {
            onSend(clean, replyingTo?.id)
            replyingTo = null
        }
        if (value == text) text = ""
        showEmojiRail = false
    }

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.size)
    }

    MessageBackground(palette) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()
        ) {
            ChatHeader(
                conversation = conversation,
                palette = palette,
                onBack = onBack,
                onProfileClick = onProfileClick,
                onAudioCall = onAudioCall,
                onVideoCall = onVideoCall,
                onMore = { showOverflow = true },
                isFullScreen = isFullScreen,
                onToggleFullScreen = onToggleFullScreen
            )

            if (!isConnected) {
                Surface(color = palette.danger.copy(alpha = .18f)) {
                    Text(
                        "Offline — your messages stay in the outbox and send automatically when connection returns.",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            if (searchVisible) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search this chat", color = palette.textMuted) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.textSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { searchVisible = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", tint = palette.textSecondary)
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.glass,
                        unfocusedContainerColor = palette.glass,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (pinnedOnly || starredOnly) {
                Surface(color = palette.glassElevated, border = BorderStroke(1.dp, palette.border)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (pinnedOnly) "Pinned messages" else "Starred messages",
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Show all",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { pinnedOnly = false; starredOnly = false }
                        )
                    }
                }
            }

            if (visibleMessages.isEmpty()) {
                EmptyConversation(
                    conversation = conversation,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    item(key = "today_divider") { DayDivider("Today", palette) }
                    items(visibleMessages, key = { it.id }) { message ->
                        val replyTarget = message.replyToMessageId?.let { replyId ->
                            conversation.messages.firstOrNull { it.id == replyId }
                        }
                        MessageBubble(
                            message = message,
                            replyTarget = replyTarget,
                            partnerAvatar = conversation.partnerAvatar,
                            partnerName = conversation.partnerName,
                            palette = palette,
                            onReply = {
                                replyingTo = message
                                editingMessage = null
                            },
                            onActions = { selectedMessage = message },
                            onRetry = { onRetry(message) }
                        )
                    }
                }
            }

            if (replyingTo != null || editingMessage != null) {
                val target = editingMessage ?: replyingTo
                Surface(color = palette.glassElevated, border = BorderStroke(1.dp, palette.border)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (editingMessage != null) Icons.Default.Edit else Icons.Default.Reply,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (editingMessage != null) "Editing message" else "Replying to ${if (target?.isFromMe == true) "yourself" else conversation.partnerName}",
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                target?.text?.ifBlank { "Media message" }.orEmpty(),
                                color = palette.textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyingTo = null; editingMessage = null; if (text == target?.text) text = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = palette.textSecondary)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showEmojiRail) {
                EmojiRail(palette = palette, onEmoji = { text += it })
            }

            MessageComposer(
                value = text,
                onValueChange = { text = it },
                palette = palette,
                onAttachment = { showAttachmentSheet = true },
                onDictation = startDictation,
                onEmoji = { showEmojiRail = !showEmojiRail },
                onSubmit = { submitMessage() },
                onQuickLike = { submitMessage("👍") }
            )
        }
    }

    selectedMessage?.let { message ->
        MessageActionsSheet(
            message = message,
            palette = palette,
            onReaction = { emoji ->
                interactionActions.onReact(conversation.partnerUsername, message, emoji)
                selectedMessage = null
            },
            onReply = {
                replyingTo = message
                editingMessage = null
                selectedMessage = null
            },
            onForward = {
                forwardingMessage = message
                selectedMessage = null
            },
            onEdit = {
                editingMessage = message
                replyingTo = null
                text = message.text
                selectedMessage = null
            },
            onDeleteForMe = {
                interactionActions.onDeleteForMe(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onDeleteForEveryone = {
                interactionActions.onDeleteForEveryone(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onToggleStar = {
                interactionActions.onToggleStar(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onShare = {
                shareChatMessage(context, message)
                selectedMessage = null
            },
            onTogglePin = {
                interactionActions.onTogglePin(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onReport = {
                interactionActions.onReportMessage(message, "Reported from message actions")
                selectedMessage = null
            },
            onDismiss = { selectedMessage = null }
        )
    }

    forwardingMessage?.let { message ->
        ForwardMessageSheet(
            sourcePartner = conversation.partnerUsername,
            conversations = allConversations,
            palette = palette,
            onForward = { targets ->
                targets.take(10).forEach { target -> onForward(target.partnerUsername, message) }
                forwardingMessage = null
            },
            onDismiss = { forwardingMessage = null }
        )
    }

    if (showOverflow) {
        ChatOverflowSheet(
            conversation = conversation,
            palette = palette,
            pinnedOnly = pinnedOnly,
            starredOnly = starredOnly,
            onProfile = { showOverflow = false; onProfileClick() },
            onSearch = { showOverflow = false; searchVisible = true },
            onPinned = { showOverflow = false; pinnedOnly = !pinnedOnly; starredOnly = false },
            onStarred = { showOverflow = false; starredOnly = !starredOnly; pinnedOnly = false },
            onMute = {
                showOverflow = false
                interactionActions.onMuteConversation(conversation, !conversation.isMuted)
            },
            onDelete = {
                showOverflow = false
                interactionActions.onClearConversation(conversation)
            },
            onReport = {
                showOverflow = false
                interactionActions.onReportConversation(conversation, "Reported from conversation menu")
            },
            onDismiss = { showOverflow = false }
        )
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            palette = palette,
            onVideo = {
                showAttachmentSheet = false
                videoPicker.launch("video/*")
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }
}'''
premium = replace_region(
    premium,
    "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun PremiumChatDetail(",
    "@Composable\nprivate fun ChatHeader(",
    new_detail,
    "premium chat detail interactions",
)

new_header = r'''@Composable
private fun ChatHeader(
    conversation: ChatConversation,
    palette: MessagePalette,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onMore: () -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(palette.headerBrush()).border(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.Default.ArrowBack, "Back", palette, 40.dp, onClick = onBack)
            Spacer(Modifier.width(7.dp))
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onProfileClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RingAvatar(
                    url = conversation.partnerAvatar,
                    name = conversation.partnerName,
                    palette = palette,
                    size = 40.dp,
                    online = conversation.isOnline
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            conversation.partnerName,
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        VerificationDot(conversation.verificationBadge, conversation.isVerified)
                    }
                    Text(
                        if (conversation.isOnline) "Active now" else conversation.lastSeen,
                        color = if (conversation.isOnline) palette.online else palette.textSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GlassIconButton(
                icon = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullScreen) "Restore chat drawer" else "Expand chat fullscreen",
                palette = palette,
                size = 34.dp,
                onClick = onToggleFullScreen
            )
            Spacer(Modifier.width(4.dp))
            GlassIconButton(Icons.Default.Call, "Audio call", palette, 36.dp, onClick = onAudioCall)
            Spacer(Modifier.width(4.dp))
            GlassIconButton(Icons.Default.Videocam, "Video call", palette, 36.dp, onClick = onVideoCall)
            Spacer(Modifier.width(4.dp))
            GlassIconButton(Icons.Default.MoreVert, "More chat options", palette, 36.dp, onClick = onMore)
        }
    }
}'''
premium = replace_region(
    premium,
    "@Composable\nprivate fun ChatHeader(",
    "@Composable\nprivate fun EmptyConversation(",
    new_header,
    "chat header overflow menu",
)

new_bubble = r'''@Composable
private fun MessageBubble(
    message: ChatMessage,
    replyTarget: ChatMessage?,
    partnerAvatar: String,
    partnerName: String,
    palette: MessagePalette,
    onReply: () -> Unit,
    onActions: () -> Unit,
    onRetry: () -> Unit
) {
    val isMine = message.isFromMe
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    var dragOffset by remember(message.id) { mutableStateOf(0f) }
    val displayOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = tween(70),
        label = "messageSwipeReply"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        if (displayOffset > 10f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                tint = palette.accent.copy(alpha = (displayOffset / threshold).coerceIn(.25f, 1f)),
                modifier = Modifier.align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 6.dp).size(20.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = displayOffset }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, amount ->
                            dragOffset = (dragOffset + amount).coerceIn(0f, threshold * 1.35f)
                        },
                        onDragEnd = {
                            if (dragOffset >= threshold) onReply()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f }
                    )
                }
                .pointerInput(message.id, message.status) {
                    detectTapGestures(
                        onDoubleTap = { onActions() },
                        onLongPress = { onActions() },
                        onTap = { if (message.status == MessageStatus.FAILED) onRetry() }
                    )
                },
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMine) {
                RingAvatar(url = partnerAvatar, name = partnerName, palette = palette, size = 31.dp)
                Spacer(Modifier.width(8.dp))
            }

            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                val bubbleShape = RoundedCornerShape(17.dp)
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(bubbleShape)
                        .background(
                            brush = if (isMine) palette.outgoingBrush() else Brush.linearGradient(listOf(palette.incomingBubble, palette.incomingBubble)),
                            shape = bubbleShape
                        )
                        .border(1.dp, palette.border.copy(alpha = .55f), bubbleShape)
                ) {
                    if (message.replyToMessageId != null) {
                        Surface(
                            color = palette.glass.copy(alpha = .45f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.border.copy(alpha = .55f)),
                            modifier = Modifier.fillMaxWidth().padding(start = 5.dp, end = 5.dp, top = 5.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                                Text(
                                    if (replyTarget?.isFromMe == true) "You" else partnerName,
                                    color = palette.accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    replyTarget?.text?.ifBlank { "Media message" } ?: "Original message unavailable",
                                    color = palette.textSecondary,
                                    fontSize = 9.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    MessageContent(message = message, isMine = isMine, palette = palette)
                }

                if (message.reactionCounts.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 3.dp).widthIn(max = 260.dp)
                    ) {
                        items(message.reactionCounts.entries.sortedByDescending { it.value }.take(8)) { entry ->
                            Surface(
                                color = if (entry.key in message.myReactions) palette.accent.copy(alpha = .18f) else palette.glassElevated,
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, if (entry.key in message.myReactions) palette.accent.copy(alpha = .55f) else palette.border)
                            ) {
                                Text("${entry.key} ${entry.value}", fontSize = 9.sp, color = palette.textSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))
                val metadata = when (message.status) {
                    MessageStatus.SENDING -> "Sending…"
                    MessageStatus.FAILED -> "Failed • tap to retry"
                    MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ -> message.timestamp + if (!message.editedAt.isNullOrBlank()) " • edited" else ""
                }
                val receipt = if (!isMine) "" else when (message.status) {
                    MessageStatus.SENT -> "✓"
                    MessageStatus.DELIVERED, MessageStatus.READ -> "✓✓"
                    else -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.isPinned) Text("📌 ", fontSize = 8.sp)
                    if (message.isStarred) Text("★ ", color = palette.accent, fontSize = 8.sp)
                    Text(
                        metadata,
                        color = if (message.status == MessageStatus.FAILED) palette.danger else palette.textSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (receipt.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            receipt,
                            color = if (message.status == MessageStatus.READ) Color(0xFF3B82F6) else palette.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}'''
premium = replace_region(
    premium,
    "@Composable\nprivate fun MessageBubble(",
    "@Composable\nprivate fun MessageContent(",
    new_bubble,
    "message swipe/reaction bubble",
)

new_content = r'''@Composable
private fun MessageContent(message: ChatMessage, isMine: Boolean, palette: MessagePalette) {
    val context = LocalContext.current
    val contentColor = if (isMine) palette.outgoingText else palette.textPrimary
    Column(modifier = Modifier.padding(5.dp)) {
        if (message.deletedForEveryone) {
            Text(
                "This message was deleted",
                color = contentColor.copy(alpha = .70f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
            )
        } else {
            if (!message.attachedImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = message.attachedImageUrl,
                    contentDescription = "Shared image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(220.dp).height(170.dp).clip(RoundedCornerShape(13.dp))
                )
            }
            if (!message.attachedVideoUrl.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = .20f),
                    contentColor = contentColor,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.width(220.dp).height(96.dp).clickable {
                        openExternalUri(context, Uri.parse(message.attachedVideoUrl))
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.PlayCircle, contentDescription = "Play video", modifier = Modifier.size(34.dp))
                        Text("Video message", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (message.isVoiceNote) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(104.dp).height(3.dp).clip(CircleShape).background(contentColor.copy(alpha = .55f)))
                    Spacer(Modifier.width(8.dp))
                    Text(message.voiceDuration.ifBlank { "0:00" }, color = contentColor, fontSize = 9.sp)
                }
            }
            val placeholderOnly =
                (!message.attachedVideoUrl.isNullOrBlank() && message.text.equals("Video", true)) ||
                    (!message.attachedImageUrl.isNullOrBlank() && message.text.equals("Image", true))
            if (message.text.isNotBlank() && !placeholderOnly && !message.isVoiceNote) {
                Text(
                    message.text,
                    color = contentColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}'''
premium = replace_region(
    premium,
    "@Composable\nprivate fun MessageContent(",
    "@Composable\nprivate fun EmojiRail(",
    new_content,
    "deleted message content",
)
PREMIUM.write_text(premium)

print("Advanced chat interactions patch complete.")
