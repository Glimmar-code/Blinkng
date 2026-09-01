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
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

object MessageMediaService {
    private const val TAG = "MessageMediaService"
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private fun uid(): String? = SupabaseService.accessToken()?.let { token -> runCatching { val part = token.split('.').getOrNull(1) ?: return@runCatching null; JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), StandardCharsets.UTF_8)).optString("sub").takeIf { it.isNotBlank() } }.getOrNull() }
    private fun builder(path: String): Request.Builder { val token = SupabaseService.accessToken() ?: throw IllegalStateException("Please sign in again."); return Request.Builder().url("${SupabaseConfig.url.trimEnd('/')}$path").addHeader("apikey", SupabaseConfig.anonKey).addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/json") }
    private fun error(body: String, fallback: String): String = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("hint") } }.getOrNull().orEmpty().ifBlank { fallback }
    suspend fun sendVideoMessage(context: Context, receiverUsername: String, uri: Uri): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val senderId = uid() ?: return@withContext Result.failure(Exception("Please sign in again.")); val receiver = receiverUsername.trim(); if (receiver.isBlank()) return@withContext Result.failure(Exception("Recipient is required."))
            val target = client.newCall(builder("/rest/v1/profiles?username=eq.${java.net.URLEncoder.encode(receiver, "UTF-8")}&select=id,username&limit=1").get().build()).execute().use { r -> val body = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(body, "Recipient lookup failed.")); JSONArray(body.ifBlank { "[]" }).optJSONObject(0) } ?: return@withContext Result.failure(Exception("Recipient not found."))
            val targetId = target.optString("id"); if (targetId == senderId) return@withContext Result.failure(Exception("Cannot message yourself."))
            val mine = client.newCall(builder("/rest/v1/conversation_participants?user_id=eq.${java.net.URLEncoder.encode(senderId, "UTF-8")}&select=conversation_id").get().build()).execute().use { r -> JSONArray(r.body?.string().orEmpty().ifBlank { "[]" }) }
            var conversationId = ""
            for (i in 0 until mine.length()) { val cid = mine.optJSONObject(i)?.optString("conversation_id").orEmpty(); if (cid.isBlank()) continue; val exists = client.newCall(builder("/rest/v1/conversation_participants?conversation_id=eq.${java.net.URLEncoder.encode(cid, "UTF-8")}&user_id=eq.${java.net.URLEncoder.encode(targetId, "UTF-8")}&select=user_id&limit=1").get().build()).execute().use { r -> r.isSuccessful && r.body?.string().orEmpty() != "[]" }; if (exists) { conversationId = cid; break } }
            if (conversationId.isBlank()) { val cBody = JSONObject().put("created_by", senderId).put("is_group", false); val cRaw = client.newCall(builder("/rest/v1/conversations").addHeader("Prefer", "return=representation").post(cBody.toString().toRequestBody(jsonType)).build()).execute().use { r -> val body = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(body, "Conversation creation failed.")); body }; conversationId = JSONArray(cRaw).getJSONObject(0).optString("id"); listOf(senderId, targetId).forEach { member -> val body = JSONObject().put("conversation_id", conversationId).put("user_id", member); client.newCall(builder("/rest/v1/conversation_participants").post(body.toString().toRequestBody(jsonType)).build()).execute().use { r -> if (!r.isSuccessful) throw IllegalStateException(error(r.body?.string().orEmpty(), "Conversation membership failed.")) } } }
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"; val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext Result.failure(Exception("Could not read video.")); if (bytes.isEmpty()) return@withContext Result.failure(Exception("Video is empty.")); val ext = when { mime.contains("webm", true) -> "webm"; mime.contains("quicktime", true) -> "mov"; else -> "mp4" }; val path = "users/$senderId/messages/videos/${UUID.randomUUID()}.$ext"
            client.newCall(builder("/storage/v1/object/post-media/$path").addHeader("Content-Type", mime).addHeader("x-upsert", "true").post(bytes.toRequestBody(mime.toMediaType())).build()).execute().use { r -> val body = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(body, "Video upload failed.")) }
            val publicUrl = "${SupabaseConfig.url.trimEnd('/')}/storage/v1/object/public/post-media/$path"; val body = JSONObject().put("conversation_id", conversationId).put("sender_id", senderId).put("content", "Video").put("media_url", publicUrl).put("is_read", false).put("message_type", "video"); val raw = client.newCall(builder("/rest/v1/messages").addHeader("Prefer", "return=representation").post(body.toString().toRequestBody(jsonType)).build()).execute().use { r -> val text = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(text, "Video message failed.")); text }; val o = JSONArray(raw).getJSONObject(0)
            Result.success(ChatMessage(id = o.optString("id"), senderId = senderId, text = "Video", timestamp = "Just now", isFromMe = true, conversationId = conversationId, rawTimestamp = o.optString("created_at"), isRead = false, status = MessageStatus.SENT, attachedVideoUrl = publicUrl))
        } catch (e: Exception) { Log.e(TAG, "sendVideoMessage failed", e); Result.failure(e) }
    }
    suspend fun hydrateVideos(conversations: List<ChatConversation>): List<ChatConversation> = withContext(Dispatchers.IO) {
        if (conversations.isEmpty() || SupabaseService.accessToken().isNullOrBlank()) return@withContext conversations
        try { val raw = client.newCall(builder("/rest/v1/messages?select=id,media_url,message_type&message_type=eq.video&limit=300").get().build()).execute().use { r -> if (!r.isSuccessful) return@withContext conversations else r.body?.string().orEmpty() }; val arr = JSONArray(raw.ifBlank { "[]" }); val mediaById = mutableMapOf<String, String>(); for (i in 0 until arr.length()) { val o = arr.optJSONObject(i) ?: continue; val id = o.optString("id"); val url = o.optString("media_url"); if (id.isNotBlank() && url.isNotBlank()) mediaById[id] = url }; conversations.map { convo -> convo.copy(messages = convo.messages.map { msg -> msg.copy(attachedVideoUrl = mediaById[msg.id]) }.toMutableList()) }
        } catch (e: Exception) { Log.w(TAG, "hydrateVideos failed", e); conversations }
    }
}
