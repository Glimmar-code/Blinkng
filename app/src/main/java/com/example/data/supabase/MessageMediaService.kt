package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

object MessageMediaService {
    private const val TAG = "MessageMediaService"
    private const val PRIVATE_BUCKET = "message-media"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun uid(): String? = SupabaseService.accessToken()?.let { token ->
        runCatching {
            val part = token.split('.').getOrNull(1) ?: return@runCatching null
            JSONObject(
                String(
                    Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                    StandardCharsets.UTF_8
                )
            ).optString("sub").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun builder(path: String): Request.Builder {
        val token = SupabaseService.accessToken() ?: throw IllegalStateException("Please sign in again.")
        return Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}$path")
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
    }

    private fun error(body: String, fallback: String): String =
        runCatching {
            val obj = JSONObject(body)
            obj.optString("message").ifBlank { obj.optString("hint") }
        }.getOrNull().orEmpty().ifBlank { fallback }

    private fun encodedPath(path: String): String =
        path.split('/').joinToString("/") {
            URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }

    private fun signedUrl(objectPath: String, expiresInSeconds: Int = 3600): String? {
        if (objectPath.isBlank()) return null
        return runCatching {
            val body = JSONObject().put("expiresIn", expiresInSeconds)
            client.newCall(
                builder("/storage/v1/object/sign/$PRIVATE_BUCKET/${encodedPath(objectPath)}")
                    .post(body.toString().toRequestBody(jsonType))
                    .build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(error(raw, "Could not open private video."))
                val obj = JSONObject(raw)
                val signed = obj.optString("signedURL").ifBlank { obj.optString("signedUrl") }
                if (signed.isBlank()) null
                else if (signed.startsWith("http")) signed
                else "${SupabaseConfig.url.trimEnd('/')}$signed"
            }
        }.getOrNull()
    }

    suspend fun sendVideoMessage(
        context: Context,
        receiverUsername: String,
        uri: Uri
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val senderId = uid() ?: return@withContext Result.failure(Exception("Please sign in again."))
            val receiver = receiverUsername.trim().removePrefix("@")
            if (receiver.isBlank() || receiver.equals("null", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Recipient is required."))
            }

            val conversationId = client.newCall(
                builder("/rest/v1/rpc/get_or_create_direct_conversation")
                    .post(
                        JSONObject()
                            .put("p_receiver_username", receiver)
                            .toString()
                            .toRequestBody(jsonType)
                    )
                    .build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        error(raw, "Could not open this conversation.")
                    )
                }
                raw.trim().removeSurrounding("\"")
            }

            if (conversationId.isBlank() || conversationId == "null") {
                return@withContext Result.failure(Exception("Could not create conversation."))
            }

            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Could not read video."))
            if (bytes.isEmpty()) return@withContext Result.failure(Exception("Video is empty."))

            val extension = when {
                mime.contains("webm", true) -> "webm"
                mime.contains("quicktime", true) -> "mov"
                else -> "mp4"
            }
            val objectPath =
                "conversations/$conversationId/users/$senderId/videos/${UUID.randomUUID()}.$extension"

            client.newCall(
                builder("/storage/v1/object/$PRIVATE_BUCKET/${encodedPath(objectPath)}")
                    .addHeader("Content-Type", mime)
                    .addHeader("x-upsert", "false")
                    .post(bytes.toRequestBody(mime.toMediaType()))
                    .build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(error(raw, "Video upload failed."))
            }

            val storedReference = "$PRIVATE_BUCKET:$objectPath"
            val messageBody = JSONObject()
                .put("conversation_id", conversationId)
                .put("sender_id", senderId)
                .put("content", "Video")
                .put("media_url", storedReference)
                .put("is_read", false)
                .put("message_type", "video")

            val raw = client.newCall(
                builder("/rest/v1/messages")
                    .addHeader("Prefer", "return=representation")
                    .post(messageBody.toString().toRequestBody(jsonType))
                    .build()
            ).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(error(text, "Video message failed."))
                text
            }
            val row = JSONArray(raw).getJSONObject(0)
            val playableUrl = signedUrl(objectPath)

            Result.success(
                ChatMessage(
                    id = row.optString("id"),
                    senderId = senderId,
                    text = "Video",
                    timestamp = "Just now",
                    isFromMe = true,
                    conversationId = conversationId,
                    rawTimestamp = row.optString("created_at"),
                    isRead = false,
                    status = MessageStatus.SENT,
                    attachedVideoUrl = playableUrl
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendVideoMessage failed", e)
            Result.failure(e)
        }
    }

    suspend fun hydrateVideos(
        conversations: List<ChatConversation>
    ): List<ChatConversation> = withContext(Dispatchers.IO) {
        if (conversations.isEmpty() || SupabaseService.accessToken().isNullOrBlank()) {
            return@withContext conversations
        }
        try {
            val raw = client.newCall(
                builder("/rest/v1/messages?select=id,media_url,message_type&message_type=eq.video&limit=300")
                    .get().build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@withContext conversations
                response.body?.string().orEmpty()
            }

            val rows = JSONArray(raw.ifBlank { "[]" })
            val references = mutableMapOf<String, String>()
            val signedCache = mutableMapOf<String, String?>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val reference = row.optString("media_url")
                if (id.isNotBlank() && reference.isNotBlank()) references[id] = reference
            }

            conversations.map { conversation ->
                conversation.copy(
                    messages = conversation.messages.map { message ->
                        val reference = references[message.id]
                        val playable = when {
                            reference.isNullOrBlank() -> null
                            reference.startsWith("$PRIVATE_BUCKET:") -> {
                                val objectPath = reference.removePrefix("$PRIVATE_BUCKET:")
                                signedCache.getOrPut(objectPath) { signedUrl(objectPath) }
                            }
                            reference.startsWith("http") -> reference
                            else -> null
                        }
                        message.copy(attachedVideoUrl = playable)
                    }.toMutableList()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "hydrateVideos failed", e)
            conversations
        }
    }
}
