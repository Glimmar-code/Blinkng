package com.example.data.repository

import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
        supabaseService.fetchMessages()
    }

    /**
     * Uses the server-side send_message RPC so conversation creation,
     * membership and message insertion happen atomically under auth.uid().
     */
    suspend fun sendMessage(receiverUsername: String, text: String): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val uid = supabaseService.getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Not authenticated."))
            val cleanReceiver = receiverUsername.trim()
            val cleanText = text.trim()
            if (cleanReceiver.isBlank() || cleanText.isBlank()) {
                return@withContext Result.failure(Exception("Recipient and message are required."))
            }

            val body = JSONObject().apply {
                put("p_sender_id", uid)
                put("p_receiver_username", cleanReceiver)
                put("p_content", cleanText)
            }
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/send_message")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseService.accessToken().orEmpty()}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(raw).optString("message") }
                        .getOrNull()
                        .orEmpty()
                        .ifBlank { "Message send failed (${response.code})." }
                    return@withContext Result.failure(Exception(message))
                }
                val messageId = raw.trim().removeSurrounding("\"")
                val now = java.time.Instant.now().toString()
                Result.success(
                    ChatMessage(
                        id = messageId,
                        senderId = uid,
                        text = cleanText,
                        rawTimestamp = now,
                        timestamp = "Just now",
                        isFromMe = true,
                        isRead = false,
                        status = com.example.data.models.MessageStatus.SENT
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markConversationRead(partnerUsername: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.markMessagesRead(partnerUsername)
    }
}
