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
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchConversations(): List<ChatConversation> = withContext(Dispatchers.IO) {
        val token = SupabaseService.accessToken() ?: return@withContext emptyList()
        val body = JSONObject().put("p_limit", 80)
        client.newCall(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/get_conversation_summaries")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
        ).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext emptyList()
            val array = org.json.JSONArray(if (raw.isBlank()) "[]" else raw)
            buildList {
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
                            lastMessage = o.optString("last_message"),
                            lastMessageTime = o.optString("last_message_at").takeIf { it.isNotBlank() }?.let { formatMessageTime(it) }.orEmpty(),
                            lastMessageRawTime = o.optString("last_message_at"),
                            unreadCount = o.optInt("unread_count", 0),
                            messages = mutableListOf()
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchMessagePage(
        conversationId: String,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
        limit: Int = 40
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (conversationId.isBlank() || conversationId.startsWith("local_")) return@withContext emptyList()
        val token = SupabaseService.accessToken() ?: return@withContext emptyList()
        val uid = supabaseService.getCurrentUserId().orEmpty()
        val body = JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_limit", limit.coerceIn(1, 100))
            put("p_before", beforeCreatedAt ?: JSONObject.NULL)
            put("p_before_id", beforeId ?: JSONObject.NULL)
        }
        client.newCall(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/get_conversation_messages_page")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
        ).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext emptyList()
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
    }

    private fun formatMessageTime(value: String): String {
        if (value.isBlank()) return "Just now"
        return runCatching {
            java.time.OffsetDateTime.parse(value)
                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrDefault("Recently")
    }

    /** Sends through auth.uid() on the server; retries once after refreshing an expired JWT. */
    suspend fun sendMessage(receiverUsername: String, text: String): Result<ChatMessage> = withContext(Dispatchers.IO) {
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
                        status = MessageStatus.SENT
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to send message.", e))
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
