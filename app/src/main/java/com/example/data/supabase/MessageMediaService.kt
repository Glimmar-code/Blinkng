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
                if (!response.isSuccessful) throw IllegalStateException(error(raw, "Could not open private media."))
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
    ): Result<ChatMessage> = sendAttachmentMessage(
        context = context,
        receiverUsername = receiverUsername,
        uri = uri,
        kind = "video"
    )

    /**
     * Upload a verified private file without loading the entire source into RAM.
     * All direct conversation membership is established by the existing backend
     * RPC (never by client-side insertion of another user's membership).
     * Retrying with the same message UUID cannot create another chat message.
     */
    suspend fun sendAttachmentMessage(
        context: Context,
        receiverUsername: String,
        uri: Uri,
        kind: String,
        displayName: String? = null,
        clientMessageId: String = UUID.randomUUID().toString()
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val normalized = kind.trim().lowercase()
            val policy = BlinkChatAttachmentKind.fromWireType(normalized)
                ?: if (normalized == "audio") BlinkChatAttachmentKind.VOICE else
                    throw IllegalArgumentException("Unsupported attachment type.")
            val senderId = uid() ?: throw IllegalStateException("Please sign in again.")
            val receiver = receiverUsername.trim().removePrefix("@")
            require(receiver.isNotBlank()) { "Recipient is required." }
            val stableMessageId = UUID.fromString(clientMessageId).toString()

            val conversationText = client.newCall(
                builder("/rest/v1/rpc/get_or_create_direct_conversation")
                    .post(JSONObject().put("p_receiver_username", receiver)
                        .toString().toRequestBody(jsonType)).build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful)
                    throw IllegalStateException(error(raw, "Cannot open conversation."))
                raw.trim().trim('"')
            }
            val conversationId = UUID.fromString(conversationText).toString()

            // Resolve an ambiguous/timeout retry BEFORE uploading anything.
            val previous = client.newCall(
                builder("/rest/v1/messages?id=eq.$stableMessageId&select=id,conversation_id," +
                    "sender_id,content,created_at,media_url,message_type&limit=1")
                    .get().build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(
                    error(raw, "Could not verify previous send.")
                )
                JSONArray(raw.ifBlank { "[]" }).optJSONObject(0)
            }
            if (previous != null) {
                check(previous.optString("sender_id") == senderId &&
                    previous.optString("conversation_id") == conversationId) {
                    "Attachment message ID belongs to a different conversation."
                }
                val stored = previous.optString("media_url")
                val url = if (stored.startsWith("$PRIVATE_BUCKET:conversations/$conversationId/users/")) {
                    signedUrl(stored.removePrefix("$PRIVATE_BUCKET:"))
                } else null
                return@withContext Result.success(
                    ChatMessage(
                        id = stableMessageId, conversationId = conversationId,
                        senderId = senderId, text = previous.optString("content"),
                        rawTimestamp = previous.optString("created_at"),
                        timestamp = "Just now", isFromMe = true, isRead = false,
                        status = MessageStatus.SENT, messageType = normalized,
                        isVoiceNote = normalized == "voice",
                        attachedImageUrl = url.takeIf { normalized == "image" },
                        attachedVideoUrl = url.takeIf { normalized == "video" },
                        attachedAudioUrl = url.takeIf { normalized == "voice" || normalized == "audio" },
                        attachedDocumentUrl = url.takeIf { normalized == "document" },
                        attachmentName = previous.optString("content")
                    )
                )
            }

            BoundedMessageAttachment.open(context, uri, policy).use { staged ->
                val extension = staged.extension
                // Deterministic owner-conversation key also prevents duplicate
                // stored files on retries when a send result was lost.
                val objectPath =
                    "conversations/$conversationId/users/$senderId/${normalized}s/$stableMessageId.$extension"
                client.newCall(
                    builder("/storage/v1/object/$PRIVATE_BUCKET/${encodedPath(objectPath)}")
                        .addHeader("x-upsert", "false")
                        .post(staged.requestBody()).build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful &&
                        response.code != 409 &&
                        !(response.code == 400 && raw.contains("already exists", true))) {
                        throw IllegalStateException(error(raw, "Attachment upload failed."))
                    }
                }

                val name = safeChatFilename(displayName ?: staged.name)
                val label = when (normalized) {
                    "image" -> "Photo"
                    "video" -> "Video"
                    "voice" -> "Voice message"
                    "audio" -> "Audio"
                    else -> name
                }
                val data = JSONObject()
                    .put("id", stableMessageId)
                    .put("conversation_id", conversationId)
                    .put("sender_id", senderId)
                    .put("content", label)
                    .put("media_url", "$PRIVATE_BUCKET:$objectPath")
                    .put("is_read", false)
                    .put("message_type", normalized)

                val saved = client.newCall(
                    builder("/rest/v1/messages")
                        .addHeader("Prefer", "return=representation")
                        .post(data.toString().toRequestBody(jsonType)).build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException(
                        error(raw, "Attachment message could not be saved.")
                    )
                    JSONArray(raw).getJSONObject(0)
                }
                val url = signedUrl(objectPath)
                Result.success(
                    ChatMessage(
                        id = saved.optString("id"),
                        conversationId = conversationId,
                        senderId = senderId,
                        text = label,
                        timestamp = "Just now",
                        rawTimestamp = saved.optString("created_at"),
                        isFromMe = true, isRead = false,
                        status = MessageStatus.SENT, messageType = normalized,
                        isVoiceNote = normalized == "voice",
                        attachedImageUrl = url.takeIf { normalized == "image" },
                        attachedVideoUrl = url.takeIf { normalized == "video" },
                        attachedAudioUrl = url.takeIf { normalized == "audio" || normalized == "voice" },
                        attachedDocumentUrl = url.takeIf { normalized == "document" },
                        attachmentName = name,
                        attachmentMimeType = staged.mime
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendAttachmentMessage failed", e)
            Result.failure(e)
        }
    }

    suspend fun hydrateVideos(
        conversations: List<ChatConversation>
    ): List<ChatConversation> = hydrateMedia(conversations)

    suspend fun hydrateMessagePage(
        messages: List<ChatMessage>
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (messages.isEmpty() || SupabaseService.accessToken().isNullOrBlank()) {
            return@withContext messages
        }
        val signedCache = mutableMapOf<String, String?>()
        fun resolve(reference: String?): String? {
            if (reference.isNullOrBlank()) return reference
            return when {
                reference.startsWith("$PRIVATE_BUCKET:") -> {
                    val objectPath = reference.removePrefix("$PRIVATE_BUCKET:")
                    signedCache.getOrPut(objectPath) { signedUrl(objectPath) }
                }
                reference.startsWith("http") -> reference
                else -> reference
            }
        }
        messages.map { message ->
            message.copy(
                attachedImageUrl = resolve(message.attachedImageUrl),
                attachedVideoUrl = resolve(message.attachedVideoUrl),
                attachedAudioUrl = resolve(message.attachedAudioUrl),
                attachedDocumentUrl = resolve(message.attachedDocumentUrl)
            )
        }
    }

    suspend fun hydrateMedia(
        conversations: List<ChatConversation>
    ): List<ChatConversation> = withContext(Dispatchers.IO) {
        if (conversations.isEmpty() || SupabaseService.accessToken().isNullOrBlank()) {
            return@withContext conversations
        }
        try {
            val raw = client.newCall(
                builder("/rest/v1/messages?select=id,media_url,message_type,content&media_url=not.is.null&limit=1000")
                    .get().build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@withContext conversations
                response.body?.string().orEmpty()
            }

            data class MediaRef(val reference: String, val kind: String, val name: String)
            val rows = JSONArray(raw.ifBlank { "[]" })
            val references = mutableMapOf<String, MediaRef>()
            val signedCache = mutableMapOf<String, String?>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val reference = row.optString("media_url")
                val kind = row.optString("message_type").lowercase()
                if (id.isNotBlank() && reference.isNotBlank()) {
                    references[id] = MediaRef(reference, kind, row.optString("content"))
                }
            }

            conversations.map { conversation ->
                conversation.copy(
                    messages = conversation.messages.map { message ->
                        val media = references[message.id] ?: return@map message
                        val playable = when {
                            media.reference.startsWith("$PRIVATE_BUCKET:") -> {
                                val objectPath = media.reference.removePrefix("$PRIVATE_BUCKET:")
                                signedCache.getOrPut(objectPath) { signedUrl(objectPath) }
                            }
                            media.reference.startsWith("http") -> media.reference
                            else -> null
                        }
                        message.copy(
                            messageType = media.kind.ifBlank { message.messageType },
                            isVoiceNote = media.kind == "voice" || message.isVoiceNote,
                            attachedImageUrl = playable.takeIf { media.kind == "image" } ?: message.attachedImageUrl,
                            attachedVideoUrl = playable.takeIf { media.kind == "video" } ?: message.attachedVideoUrl,
                            attachedAudioUrl = playable.takeIf { media.kind == "audio" || media.kind == "voice" } ?: message.attachedAudioUrl,
                            attachedDocumentUrl = playable.takeIf { media.kind == "document" } ?: message.attachedDocumentUrl,
                            attachmentName = message.attachmentName ?: media.name.takeIf { media.kind == "document" }
                        )
                    }.toMutableList()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "hydrateMedia failed", e)
            conversations
        }
    }

}
