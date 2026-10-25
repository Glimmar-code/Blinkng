package com.example.data.supabase

import android.content.Context
import android.net.Uri
import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.BlinkChatUploadProgress
import com.blinkng.shared.BlinkChatImageQuality
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
import java.io.OutputStream
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

    /** Re-fetch the private object reference through messages RLS immediately before
     * the picture is viewed or saved. Never reuse an expired URL or sign arbitrary paths. */
    suspend fun freshImageUrl(messageId: String, conversationId: String): String? =
        withContext(Dispatchers.IO) {
            val id = runCatching { UUID.fromString(messageId).toString() }.getOrNull()
                ?: return@withContext null
            val convo = runCatching { UUID.fromString(conversationId).toString() }.getOrNull()
                ?: return@withContext null
            if (!SupabaseService().restoreSession()) return@withContext null
            runCatching {
                client.newCall(
                    builder("/rest/v1/messages?id=eq.$id&conversation_id=eq.$convo&" +
                        "select=id,conversation_id,message_type,media_url&limit=1")
                        .get().build()
                ).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val row = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                        .optJSONObject(0) ?: return@use null
                    if (row.optString("message_type") != "image") return@use null
                    val ref = row.optString("media_url")
                    val prefix = "$PRIVATE_BUCKET:conversations/$convo/users/"
                    if (!ref.startsWith(prefix)) return@use null
                    signedUrl(ref.removePrefix("$PRIVATE_BUCKET:"))
                }
            }.getOrNull()
        }

    /** Streams a fresh authenticated private image without ever exposing a shareable
     * signed URL outside the app. 15 MiB limit applies to received bytes as well. */
    suspend fun copyPrivateImage(
        messageId: String, conversationId: String, output: OutputStream
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val url = freshImageUrl(messageId, conversationId)
                ?: throw SecurityException("Picture access was not authorized.")
            val host = okhttp3.HttpUrl.Companion.toHttpUrlOrNull(SupabaseConfig.url)
                ?: throw SecurityException("Invalid storage configuration.")
            val signed = okhttp3.HttpUrl.Companion.toHttpUrlOrNull(url)
                ?: throw SecurityException("Invalid picture link.")
            require(signed.scheme == "https" && signed.host == host.host &&
                signed.port == host.port) { "Private picture host did not match." }
            client.newCall(Request.Builder().url(signed).get().build()).execute().use { response ->
                check(response.isSuccessful) { "Could not download picture." }
                require(response.header("Content-Type").orEmpty().startsWith("image/")) {
                    "The selected attachment is not an image."
                }
                val image = response.body ?: error("Picture was empty.")
                require(image.contentLength() <= BlinkChatAttachmentKind.IMAGE.maxBytes) {
                    "Picture exceeds the allowed size."
                }
                var written = 0L
                image.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        written += n
                        check(written <= BlinkChatAttachmentKind.IMAGE.maxBytes) {
                            "Picture exceeded the allowed size."
                        }
                        output.write(buffer, 0, n)
                    }
                }
                output.flush()
                written
            }
        }
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
        durationMs: Long = 0L,
        clientMessageId: String = UUID.randomUUID().toString(),
        onUploadProgress: (Int) -> Unit = {},
        caption: String = "",
        imageQuality: BlinkChatImageQuality = BlinkChatImageQuality.STANDARD
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val senderId = uid() ?: return@withContext Result.failure(Exception("Please sign in again."))
            val receiver = receiverUsername.trim().removePrefix("@")
            if (receiver.isBlank() || receiver.equals("null", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Recipient is required."))
            }

            // Production RLS only permits inserting your OWN membership row.
            // Use the existing server RPC: it checks blocks, self-messaging
            // and atomically creates BOTH participants without a dummy text.
            val conversationResponse = client.newCall(
                builder("/rest/v1/rpc/get_or_create_direct_conversation")
                    .post(JSONObject().put("p_receiver_username", receiver)
                        .toString().toRequestBody(jsonType)).build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(
                    error(raw, "You cannot start this conversation.")
                )
                raw.trim().trim('"')
            }
            val conversationId = runCatching {
                UUID.fromString(conversationResponse).toString()
            }.getOrElse { throw IllegalStateException("Server did not confirm the conversation.") }

            // Reuse the same client message ID on retry. If the server saved
            // the message but the success response was lost, do not send twice.
            val safeMessageId = UUID.fromString(clientMessageId).toString()
            val existing = client.newCall(
                builder(
                    "/rest/v1/messages?id=eq.$safeMessageId&select=id,sender_id,conversation_id," +
                        "content,created_at,media_url&limit=1"
                ).get().build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(
                    error(raw, "Cannot check existing attachment message.")
                )
                JSONArray(raw.ifBlank { "[]" }).optJSONObject(0)
            }
            if (existing != null && existing.optString("sender_id") == senderId &&
                existing.optString("conversation_id") == conversationId) {
                val ref = existing.optString("media_url")
                val objectPath = ref.removePrefix("$PRIVATE_BUCKET:")
                val replayUrl = if (ref.startsWith("$PRIVATE_BUCKET:conversations/$conversationId/users/"))
                    signedUrl(objectPath) else null
                return@withContext Result.success(ChatMessage(
                    id = safeMessageId,
                    senderId = senderId,
                    text = existing.optString("content"),
                    timestamp = "Just now",
                    isFromMe = true,
                    conversationId = conversationId,
                    rawTimestamp = existing.optString("created_at"),
                    isRead = false,
                    status = MessageStatus.SENT,
                    isVoiceNote = kind == BlinkChatAttachmentKind.VOICE,
                    voiceDuration = if (durationMs > 0) "${durationMs / 1000}s" else "",
                    voiceAudioUrl = replayUrl.takeIf { kind == BlinkChatAttachmentKind.VOICE },
                    attachedImageUrl = replayUrl.takeIf { kind == BlinkChatAttachmentKind.IMAGE },
                    attachedVideoUrl = replayUrl.takeIf { kind == BlinkChatAttachmentKind.VIDEO },
                    attachedDocumentUrl = replayUrl.takeIf { kind == BlinkChatAttachmentKind.DOCUMENT },
                    attachedDocumentName = existing.optString("content")
                        .removePrefix("Document: ").takeIf { kind == BlinkChatAttachmentKind.DOCUMENT }
                ))
            }

            val selected = if (kind == BlinkChatAttachmentKind.IMAGE)
                BlinkChatImagePreparer.open(context, uri, imageQuality)
            else BoundedMessageAttachment.open(context, uri, kind)
            selected.use { staged ->
                val objectPath =
                    "conversations/$conversationId/users/$senderId/${kind.wireType}/${UUID.randomUUID()}.${staged.extension}"
                client.newCall(
                    builder("/storage/v1/object/$PRIVATE_BUCKET/${encodedPath(objectPath)}")
                        .addHeader("x-upsert", "false")
                        .post(staged.requestBody { sent, total ->
                            onUploadProgress(BlinkChatUploadProgress.percent(sent, total))
                        }).build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(error(raw, "Upload failed."))
                }
                val label = when (kind) {
                    BlinkChatAttachmentKind.DOCUMENT -> "Document: ${staged.name}"
                    BlinkChatAttachmentKind.VOICE -> "Voice note: ${durationMs.coerceAtLeast(0) / 1000}s"
                    BlinkChatAttachmentKind.IMAGE -> caption.trim().take(1024).ifBlank { "Image" }
                    else -> kind.title
                }
                val messageBody = JSONObject()
                    .put("id", safeMessageId)
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
                // Keep the v1 wire contract compatible with production:
                // message_attachments is not deployed there yet. The durable
                // type, reference and filename live in the messages row.
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
