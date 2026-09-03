package com.example.data.repository

import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
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
        supabaseService.fetchMessages()
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

    private suspend fun refreshSession(): Boolean =
        supabaseService.refreshSession()

    suspend fun markConversationRead(partnerUsername: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.markMessagesRead(partnerUsername)
    }
}
