from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHAT = ROOT / "app/src/main/java/com/example/data/repository/ChatRepository.kt"
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
OFFLINE = ROOT / "app/src/main/java/com/example/data/local/OfflineContentStore.kt"

# 1) Make the server the durable source of truth for the complete conversation list.
chat = CHAT.read_text(encoding="utf-8")
start_marker = "    suspend fun fetchConversations(): List<ChatConversation> = withContext(Dispatchers.IO) {"
end_marker = "    private fun formatMessageTime(value: String): String {"
start = chat.find(start_marker)
end = chat.find(end_marker, start + len(start_marker))
if start < 0 or end < 0:
    raise SystemExit("Could not locate ChatRepository conversation/message fetch block")

replacement = r'''    private data class ConversationPage(
        val items: List<ChatConversation>,
        val nextBeforeAt: String?,
        val nextBeforeId: String?,
        val hasMore: Boolean
    )

    suspend fun fetchConversations(): List<ChatConversation> = withContext(Dispatchers.IO) {
        // Conversation summaries are account data, not cache data. Walk the full server
        // history so clearing local storage or returning years later can rebuild the list.
        val merged = LinkedHashMap<String, ChatConversation>()
        var beforeAt: String? = null
        var beforeId: String? = null
        var pageNumber = 0

        while (pageNumber < MAX_CONVERSATION_PAGES) {
            val page = fetchConversationPage(beforeAt, beforeId)
            page.items.forEach { conversation ->
                if (conversation.id.isNotBlank()) merged[conversation.id] = conversation
            }
            pageNumber += 1

            val nextAt = page.nextBeforeAt
            val nextId = page.nextBeforeId
            if (!page.hasMore || nextAt.isNullOrBlank() || nextId.isNullOrBlank()) break
            if (nextAt == beforeAt && nextId == beforeId) break
            beforeAt = nextAt
            beforeId = nextId
        }

        merged.values.toList()
    }

    private suspend fun fetchConversationPage(beforeAt: String?, beforeId: String?): ConversationPage {
        val body = JSONObject().apply {
            put("p_limit", CONVERSATION_PAGE_SIZE)
            put("p_before", beforeAt ?: JSONObject.NULL)
            put("p_before_id", beforeId ?: JSONObject.NULL)
        }
        val raw = postAuthenticatedRpc("get_conversation_summaries_page", body)
        val array = org.json.JSONArray(if (raw.isBlank()) "[]" else raw)
        val items = buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    ChatConversation(
                        id = o.optString("conversation_id"),
                        partnerUsername = o.optString("partner_username"),
                        partnerId = o.optString("partner_id"),
                        partnerName = o.optString("partner_name").ifBlank { o.optString("partner_username") },
                        partnerAvatar = o.optString("partner_avatar"),
                        isOnline = o.optBoolean("partner_online", false),
                        lastSeen = o.optString("partner_last_seen")
                            .takeIf { it.isNotBlank() && !it.equals("null", true) }
                            ?.let(TimeFormatters::relativeOrDate)
                            ?: "recently",
                        lastMessage = o.optString("last_message").takeUnless { it.equals("null", true) }.orEmpty(),
                        lastMessageTime = o.optString("last_message_at")
                            .takeIf { it.isNotBlank() && !it.equals("null", true) }
                            ?.let(::formatMessageTime)
                            .orEmpty(),
                        lastMessageRawTime = o.optString("last_message_at")
                            .takeUnless { it.equals("null", true) }
                            .orEmpty(),
                        unreadCount = o.optInt("unread_count", 0),
                        messages = mutableListOf()
                    )
                )
            }
        }
        val last = if (array.length() > 0) array.getJSONObject(array.length() - 1) else null
        return ConversationPage(
            items = items,
            nextBeforeAt = last?.optString("cursor_at")?.takeIf { it.isNotBlank() && !it.equals("null", true) },
            nextBeforeId = last?.optString("conversation_id")?.takeIf { it.isNotBlank() },
            hasMore = array.length() >= CONVERSATION_PAGE_SIZE
        )
    }

    suspend fun fetchMessagePage(
        conversationId: String,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
        limit: Int = 100
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (conversationId.isBlank() || conversationId.startsWith("local_")) return@withContext emptyList()
        val uid = supabaseService.getCurrentUserId().orEmpty()
        val body = JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_limit", limit.coerceIn(1, 100))
            put("p_before", beforeCreatedAt ?: JSONObject.NULL)
            put("p_before_id", beforeId ?: JSONObject.NULL)
        }
        val raw = postAuthenticatedRpc("get_conversation_messages_page", body)
        val array = org.json.JSONArray(if (raw.isBlank()) "[]" else raw)
        buildList {
            for (i in array.length() - 1 downTo 0) {
                val o = array.getJSONObject(i)
                val mediaType = o.optString("message_type")
                val mediaUrl = o.optString("media_url").takeIf { it.isNotBlank() && it != "null" }
                add(
                    ChatMessage(
                        id = o.optString("id"),
                        conversationId = o.optString("conversation_id"),
                        senderId = o.optString("sender_id"),
                        text = o.optString("content"),
                        rawTimestamp = o.optString("created_at"),
                        timestamp = formatMessageTime(o.optString("created_at")),
                        isFromMe = o.optString("sender_id") == uid,
                        isRead = o.optBoolean("is_read", false),
                        status = when {
                            o.optBoolean("is_read", false) ||
                                o.optString("read_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.READ
                            o.optString("delivered_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.DELIVERED
                            else -> MessageStatus.SENT
                        },
                        isVoiceNote = mediaType.equals("voice", true) || mediaType.equals("audio", true),
                        attachedImageUrl = mediaUrl.takeIf { mediaType.equals("image", true) },
                        attachedVideoUrl = mediaUrl.takeIf { mediaType.equals("video", true) }
                    )
                )
            }
        }
    }

    /**
     * Executes a Supabase RPC with the current account session and refreshes an expired
     * access token once. Fetch failures throw instead of pretending the server returned an
     * empty history, so callers can keep their existing Room cache intact.
     */
    private suspend fun postAuthenticatedRpc(name: String, body: JSONObject): String {
        var token = SupabaseService.accessToken()
            ?: throw IllegalStateException("No authenticated Supabase session is available.")

        fun request(currentToken: String): okhttp3.Response = client.newCall(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$name")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $currentToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
        ).execute()

        var response = request(token)
        if (response.code == 401) {
            response.close()
            if (!refreshSession()) throw IllegalStateException("Supabase session refresh failed.")
            token = SupabaseService.accessToken()
                ?: throw IllegalStateException("Supabase session refresh returned no access token.")
            response = request(token)
        }

        response.use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val detail = runCatching { JSONObject(raw).optString("message") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "$name failed (${res.code})." })
            }
            return raw
        }
    }

'''
chat = chat[:start] + replacement + chat[end:]

companion_anchor = "class ChatRepository(\n    private val supabaseService: SupabaseService = SupabaseService()\n) {\n"
if companion_anchor not in chat:
    raise SystemExit("Could not locate ChatRepository class declaration")
if "private const val CONVERSATION_PAGE_SIZE" not in chat:
    chat = chat.replace(
        companion_anchor,
        companion_anchor + "    private companion object {\n        const val CONVERSATION_PAGE_SIZE = 100\n        const val MAX_CONVERSATION_PAGES = 1000\n    }\n\n",
        1,
    )
CHAT.write_text(chat, encoding="utf-8")

# 2) Fetch the largest safe message page and keep the paging threshold consistent.
vm = VM.read_text(encoding="utf-8")
old_limit = '''                beforeId = if (older) oldest?.id?.takeIf { it.isNotBlank() } else null,
                limit = 40
'''
new_limit = '''                beforeId = if (older) oldest?.id?.takeIf { it.isNotBlank() } else null,
                limit = 100
'''
if old_limit not in vm:
    raise SystemExit("Could not locate BlinkViewModel message page size")
vm = vm.replace(old_limit, new_limit, 1)
if "page.size >= 40" not in vm:
    raise SystemExit("Could not locate BlinkViewModel message history paging threshold")
vm = vm.replace("page.size >= 40", "page.size >= 100", 1)
VM.write_text(vm, encoding="utf-8")

# 3) Chat cache is durable local history. Never age-prune conversations/messages; the
# server still restores them after Android cache/app data is cleared.
offline = OFFLINE.read_text(encoding="utf-8")
old_prune = '''        dao.prunePosts(before)
        dao.pruneProfiles(before)
        dao.pruneConversations(before)
        dao.pruneMessages(before)
'''
new_prune = '''        dao.prunePosts(before)
        dao.pruneProfiles(before)
        // Messages and conversation summaries intentionally have no age-based local TTL.
        // Logout does not destroy history; if Android storage is cleared, Supabase rebuilds it.
'''
if old_prune not in offline:
    raise SystemExit("Could not locate OfflineContentStore prune policy")
offline = offline.replace(old_prune, new_prune, 1)
OFFLINE.write_text(offline, encoding="utf-8")

print("Applied persistent, account-backed chat history restore changes")
