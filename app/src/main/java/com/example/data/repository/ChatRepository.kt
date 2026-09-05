package com.example.data.repository

import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import com.example.util.TimeFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

class ChatRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    private companion object {
        const val CONVERSATION_PAGE_SIZE = 100
        const val MAX_CONVERSATION_PAGES = 1000
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private data class ConversationPage(
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

        applyConversationState(merged.values.toList())
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
        val baseMessages = buildList {
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
                        attachedVideoUrl = mediaUrl.takeIf { mediaType.equals("video", true) },
                        replyToMessageId = o.optString("reply_to_message_id")
                            .takeIf { it.isNotBlank() && !it.equals("null", true) },
                        editedAt = o.optString("edited_at")
                            .takeIf { it.isNotBlank() && !it.equals("null", true) },
                        deletedForEveryone = o.optBoolean("deleted_for_everyone", false)
                    )
                )
            }
        }
        val visibleMessages = filterClearedMessages(conversationId, baseMessages)
        enrichMessageActions(visibleMessages)
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

    private fun formatMessageTime(value: String): String {
        if (value.isBlank()) return "Just now"
        return runCatching {
            java.time.OffsetDateTime.parse(value)
                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrDefault("Recently")
    }

    /** Sends through auth.uid() on the server; retries once after refreshing an expired JWT. */
    suspend fun sendMessage(
        receiverUsername: String,
        text: String,
        replyToMessageId: String? = null
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val receiver = receiverUsername.trim()
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
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/send_message")
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
                val refreshed = refreshSession()
                if (!refreshed) {
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
                val messageId = raw.trim().removeSurrounding("\"")
                if (messageId.isBlank() || messageId == "null") {
                    return@withContext Result.failure(Exception("Message was not created."))
                }
                val validReplyId = replyToMessageId
                    ?.takeIf { id -> runCatching { java.util.UUID.fromString(id) }.isSuccess }
                if (validReplyId != null) {
                    runCatching { setMessageReply(messageId, validReplyId) }
                }
                SupabaseService.accessToken()?.takeIf { it.isNotBlank() }?.let { currentToken ->
                    runCatching { triggerMessagePush(messageId, currentToken) }
                }
                Result.success(
                    ChatMessage(
                        id = messageId,
                        senderId = uid,
                        text = cleanText,
                        rawTimestamp = Instant.now().toString(),
                        timestamp = "Just now",
                        isFromMe = true,
                        isRead = false,
                        status = MessageStatus.SENT,
                        replyToMessageId = replyToMessageId
                            ?.takeIf { id -> runCatching { java.util.UUID.fromString(id) }.isSuccess }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to send message.", e))
        }
    }


    private data class MessageActionState(
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

    private fun triggerMessagePush(messageId: String, accessToken: String) {
        if (messageId.isBlank() || accessToken.isBlank()) return
        val body = JSONObject()
            .put("message_id", messageId)
            .toString()
            .toRequestBody(jsonMediaType)
        client.newCall(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/send-push-notification")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
        ).execute().use { /* Message delivery succeeds even if push is unavailable. */ }
    }

    private suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = SupabaseService.refreshToken() ?: return@withContext false
        try {
            val body = "grant_type=refresh_token&refresh_token=${java.net.URLEncoder.encode(refreshToken, "UTF-8")}".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            client.newCall(
                Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .post(body)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val json = JSONObject(response.body?.string().orEmpty())
                val access = json.optString("access_token")
                val refresh = json.optString("refresh_token").ifBlank { refreshToken }
                if (access.isBlank()) return@withContext false
                SupabaseService.saveSession(access, refresh)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun markMessageDelivered(messageId: String): Boolean {
        val cleanId = messageId.trim()
        if (cleanId.isBlank() || cleanId.startsWith("temp_")) return false
        return callReceiptRpc(
            name = "ack_message_delivered",
            body = JSONObject().put("p_message_id", cleanId)
        )
    }

    suspend fun markConversationRead(partnerUsername: String): Boolean {
        val clean = partnerUsername.trim().removePrefix("@")
        if (clean.isBlank()) return false
        return callReceiptRpc(
            name = "mark_conversation_read",
            body = JSONObject().put("p_partner_username", clean)
        )
    }

    private suspend fun callReceiptRpc(name: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            var token = SupabaseService.accessToken() ?: return@withContext false

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
                if (!refreshSession()) return@withContext false
                token = SupabaseService.accessToken() ?: return@withContext false
                response = request(token)
            }
            response.use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
