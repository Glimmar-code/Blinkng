package com.example.data.supabase

import android.content.Context
import android.net.Uri
import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.safeChatFilename
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
                when {
                    signed.isBlank() -> null
                    signed.startsWith("https://") &&
                        signed.startsWith(SupabaseConfig.url.trimEnd('/')) -> signed
                    signed.startsWith("/storage/v1/") ->
                        SupabaseConfig.url.trimEnd('/') + signed
                    signed.startsWith("/object/") ->
                        SupabaseConfig.url.trimEnd('/') + "/storage/v1" + signed
                    else -> null
                }
            }
        }.getOrNull()
    }

    suspend fun sendVideoMessage(
        context: Context,
        receiverUsername: String,
        uri: Uri
    ): Result<ChatMessage> = sendAttachment(context, receiverUsername, uri, BlinkChatAttachmentKind.VIDEO)

    suspend fun sendAttachment(
        context: Context,
        receiverUsername: String,
        uri: Uri,
        kind: BlinkChatAttachmentKind,
        durationMs: Long = 0L
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val senderId = uid() ?: return@withContext Result.failure(Exception("Please sign in again."))
            val receiver = receiverUsername.trim().removePrefix("@")
            if (receiver.isBlank() || receiver.equals("null", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Recipient is required."))
            }

            val target = client.newCall(
                builder(
                    "/rest/v1/profiles?username=eq.${
                        URLEncoder.encode(receiver, StandardCharsets.UTF_8.name())
                    }&select=id,username&limit=1"
                ).get().build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(error(raw, "Recipient lookup failed."))
                JSONArray(raw.ifBlank { "[]" }).optJSONObject(0)
            } ?: return@withContext Result.failure(Exception("Recipient not found."))

            val targetId = target.optString("id")
            if (targetId == senderId) return@withContext Result.failure(Exception("Cannot message yourself."))

            val mine = client.newCall(
                builder(
                    "/rest/v1/conversation_participants?user_id=eq.${
                        URLEncoder.encode(senderId, StandardCharsets.UTF_8.name())
                    }&select=conversation_id"
                ).get().build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(error(raw, "Conversation lookup failed."))
                JSONArray(raw.ifBlank { "[]" })
            }

            var conversationId = ""
            for (i in 0 until mine.length()) {
                val cid = mine.optJSONObject(i)?.optString("conversation_id").orEmpty()
                if (cid.isBlank()) continue
                val exists = client.newCall(
                    builder(
                        "/rest/v1/conversation_participants?conversation_id=eq.${
                            URLEncoder.encode(cid, StandardCharsets.UTF_8.name())
                        }&user_id=eq.${
                            URLEncoder.encode(targetId, StandardCharsets.UTF_8.name())
                        }&select=user_id&limit=1"
                    ).get().build()
                ).execute().use { response ->
                    response.isSuccessful && response.body?.string().orEmpty() != "[]"
                }
                if (exists) {
                    conversationId = cid
                    break
                }
            }

            if (conversationId.isBlank()) {
                val conversationBody = JSONObject()
                    .put("created_by", senderId)
                    .put("is_group", false)
                val created = client.newCall(
                    builder("/rest/v1/conversations")
                        .addHeader("Prefer", "return=representation")
                        .post(conversationBody.toString().toRequestBody(jsonType))
                        .build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(error(raw, "Conversation creation failed."))
                    raw
                }
                conversationId = JSONArray(created).getJSONObject(0).optString("id")
                for (member in listOf(senderId, targetId)) {
                    val memberBody = JSONObject()
                        .put("conversation_id", conversationId)
                        .put("user_id", member)
                    client.newCall(
                        builder("/rest/v1/conversation_participants")
                            .post(memberBody.toString().toRequestBody(jsonType))
                            .build()
                    ).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IllegalStateException(error(raw, "Conversation membership failed."))
                        }
                    }
                }
            }

            BoundedMessageAttachment.open(context, uri, kind).use { staged ->
                val objectPath =
                    "conversations/$conversationId/users/$senderId/${kind.wireType}/${UUID.randomUUID()}.${staged.extension}"
                client.newCall(
                    builder("/storage/v1/object/$PRIVATE_BUCKET/${encodedPath(objectPath)}")
                        .addHeader("x-upsert", "false")
                        .post(staged.requestBody()).build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(error(raw, "Upload failed."))
                }
                val label = when (kind) {
                    BlinkChatAttachmentKind.DOCUMENT -> "Document: ${staged.name}"
                    BlinkChatAttachmentKind.VOICE -> "Voice note: ${durationMs.coerceAtLeast(0) / 1000}s"
                    else -> kind.title
                }
                val messageBody = JSONObject()
                    .put("conversation_id", conversationId)
                    .put("sender_id", senderId)
                    .put("content", label)
                    .put("media_url", "$PRIVATE_BUCKET:$objectPath")
                    .put("is_read", false)
                    .put("message_type", kind.wireType)
                val raw = client.newCall(
                    builder("/rest/v1/messages")
                        .addHeader("Prefer", "return=representation")
                        .post(messageBody.toString().toRequestBody(jsonType)).build()
                ).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(
                        error(body, "Could not send ${kind.title.lowercase()} message.")
                    )
                    body
                }
                val row = JSONArray(raw).getJSONObject(0)
                val messageId = row.optString("id")
                if (messageId.isBlank()) throw IllegalStateException("Message not confirmed by server.")
                // Metadata is best-effort and never creates a duplicate chat message.
                runCatching {
                    val data = JSONObject()
                        .put("message_id", messageId)
                        .put("conversation_id", conversationId)
                        .put("uploader_id", senderId)
                        .put("kind", kind.wireType)
                        .put("storage_bucket", PRIVATE_BUCKET)
                        .put("storage_path", objectPath)
                        .put("display_name", staged.name)
                        .put("mime_type", staged.mime)
                        .put("byte_size", staged.size)
                    if (durationMs > 0) data.put("duration_ms", durationMs)
                    client.newCall(
                        builder("/rest/v1/message_attachments")
                            .post(data.toString().toRequestBody(jsonType)).build()
                    ).execute().close()
                }
                val url = signedUrl(objectPath)
                Result.success(
                    ChatMessage(
                        id = messageId,
                        senderId = senderId,
                        text = label,
                        timestamp = "Just now",
                        isFromMe = true,
                        conversationId = conversationId,
                        rawTimestamp = row.optString("created_at"),
                        isRead = false,
                        status = MessageStatus.SENT,
                        isVoiceNote = kind == BlinkChatAttachmentKind.VOICE,
                        voiceDuration = if (durationMs > 0) "${durationMs / 1000}s" else "",
                        voiceAudioUrl = url.takeIf { kind == BlinkChatAttachmentKind.VOICE },
                        attachedImageUrl = url.takeIf { kind == BlinkChatAttachmentKind.IMAGE },
                        attachedVideoUrl = url.takeIf { kind == BlinkChatAttachmentKind.VIDEO },
                        attachedDocumentUrl = url.takeIf { kind == BlinkChatAttachmentKind.DOCUMENT },
                        attachedDocumentName = staged.name.takeIf { kind == BlinkChatAttachmentKind.DOCUMENT }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendVideoMessage failed", e)
            Result.failure(e)
        }
    }

    /** Convert a page's private references to short-lived signed URLs only
     * after the server has returned messages in the current conversation. */
    suspend fun hydrateMediaPage(messages: List<ChatMessage>, conversationId: String):
        List<ChatMessage> = withContext(Dispatchers.IO) {
        if (messages.isEmpty() || SupabaseService.accessToken().isNullOrBlank()) {
            return@withContext messages
        }
        val signedCache = mutableMapOf<String, String?>()
        messages.map { message ->
            val kind = when {
                message.attachedImageUrl != null -> BlinkChatAttachmentKind.IMAGE
                message.attachedVideoUrl != null -> BlinkChatAttachmentKind.VIDEO
                message.isVoiceNote -> BlinkChatAttachmentKind.VOICE
                message.attachedDocumentUrl != null -> BlinkChatAttachmentKind.DOCUMENT
                else -> null
            }
            val reference = when (kind) {
                BlinkChatAttachmentKind.IMAGE -> message.attachedImageUrl
                BlinkChatAttachmentKind.VIDEO -> message.attachedVideoUrl
                BlinkChatAttachmentKind.VOICE -> message.voiceAudioUrl
                BlinkChatAttachmentKind.DOCUMENT -> message.attachedDocumentUrl
                else -> null
            }
            // Only sign storage references belonging to the opened conversation.
            val prefix = "$PRIVATE_BUCKET:conversations/$conversationId/users/"
            val url = when {
                reference?.startsWith(prefix) == true -> {
                    val objectPath = reference.removePrefix("$PRIVATE_BUCKET:")
                    signedCache.getOrPut(objectPath) { signedUrl(objectPath) }
                }
                reference?.startsWith("https://") == true -> reference
                else -> null
            }
            when (kind) {
                BlinkChatAttachmentKind.IMAGE -> message.copy(attachedImageUrl = url)
                BlinkChatAttachmentKind.VIDEO -> message.copy(attachedVideoUrl = url)
                BlinkChatAttachmentKind.VOICE -> message.copy(voiceAudioUrl = url)
                BlinkChatAttachmentKind.DOCUMENT -> message.copy(attachedDocumentUrl = url)
                null -> message
            }
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
