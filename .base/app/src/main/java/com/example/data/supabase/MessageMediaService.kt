package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.blinkng.shared.BlinkChatImageQuality
import com.blinkng.shared.BlinkChatPhotoAlbum
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MultipartBody
import java.io.ByteArrayOutputStream
import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.BlinkChatUploadProgress
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

    suspend fun sendViewOncePicture(
        context: Context,
        conversationId: String,
        uri: Uri,
        caption: String,
        imageQuality: BlinkChatImageQuality = BlinkChatImageQuality.STANDARD,
        messageId: String = UUID.randomUUID().toString()
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val conversation = UUID.fromString(conversationId).toString()
            val id = UUID.fromString(messageId).toString()
            val owner = uid() ?: throw SecurityException("Sign in to send a view-once picture.")
            require(SupabaseService().restoreSession()) { "Please sign in again." }
            BlinkChatImagePreparer.open(context, uri, imageQuality).use { photo ->
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("conversation_id", conversation)
                    .addFormDataPart("message_id", id)
                    .addFormDataPart("caption", caption.trim().take(1024))
                    .addFormDataPart("photo", photo.name,
                        photo.file.asRequestBody(photo.mime.toMediaType()))
                    .build()
                val response = client.newCall(
                    builder("/functions/v1/blink-view-once-image").post(form).build()
                ).execute().use { http ->
                    val raw = http.body?.string().orEmpty()
                    check(http.isSuccessful) {
                        error(raw, "View-once pictures aren't available yet.")
                    }
                    JSONObject(raw)
                }
                require(response.optString("messageId") == id) {
                    "Server didn't acknowledge your private picture."
                }
                ChatMessage(
                    id = id, senderId = owner, conversationId = conversation,
                    text = caption.trim().take(1024).ifBlank { "View once photo" },
                    timestamp = "Just now", isFromMe = true, isRead = false,
                    status = MessageStatus.SENT, imageViewOnce = true
                )
            }
        }
    }

    /** One atomic server claim. No signed URL, WebView, disk cache or external
     * viewer is involved. The memory bitmap must be released with the viewer. */
    suspend fun openViewOncePicture(messageId: String): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = UUID.fromString(messageId).toString()
                require(SupabaseService().restoreSession()) { "Please sign in again." }
                val url = "/functions/v1/blink-view-once-image?message_id=$id"
                val body = client.newCall(builder(url).get().build()).execute().use { response ->
                    check(response.isSuccessful) {
                        if (response.code == 410) "This picture has already been opened."
                        else "Private picture could not be opened."
                    }
                    val mime = response.header("Content-Type").orEmpty()
                        .substringBefore(';').lowercase()
                    require(BlinkChatAttachmentKind.IMAGE.accepts(mime)) {
                        "Server sent an unexpected image type."
                    }
                    val data = response.body ?: throw IllegalStateException("Empty private picture.")
                    require(data.contentLength() <= 15L * 1024L * 1024L) {
                        "Picture is too large."
                    }
                    ByteArrayOutputStream().use { target ->
                        val chunk = ByteArray(64 * 1024)
                        var length = 0
                        data.byteStream().use { input ->
                            while (true) {
                                val count = input.read(chunk)
                                if (count == -1) break
                                length += count
                                require(length <= 15 * 1024 * 1024) {
                                    "Picture exceeds the maximum safe size."
                                }
                                target.write(chunk, 0, count)
                            }
                        }
                        target.toByteArray()
                    }
                }
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(body, 0, body.size, bounds)
                    require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000) {
                        "Picture dimensions are not supported."
                    }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    BitmapFactory.decodeByteArray(body, 0, body.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: throw IllegalStateException("Cannot decode this picture.")
                } finally {
                    // View-once bytes must not remain in a reusable heap buffer.
                    body.fill(0)
                }
            }
        }

    /** Query only rows already readable through messages RLS. Older schemas
     * gracefully fall back to normal image rendering. */
    suspend fun hydrateViewOnceFlags(
        messages: List<ChatMessage>, conversationId: String
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext messages
        val convo = runCatching { UUID.fromString(conversationId).toString() }.getOrNull()
            ?: return@withContext messages
        val ids = messages.mapNotNull {
            runCatching { UUID.fromString(it.id).toString() }.getOrNull()
        }
        if (ids.isEmpty()) return@withContext messages
        val flags = runCatching {
            val request = builder("/rest/v1/messages?conversation_id=eq.$convo" +
                "&id=in.(${ids.joinToString(",")})&select=id,image_view_once&limit=100").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptySet<String>()
                val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                buildSet {
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        if (row.optBoolean("image_view_once")) add(row.optString("id"))
                    }
                }
            }
        }.getOrDefault(emptySet())
        if (flags.isEmpty()) messages
        else messages.map { if (it.id in flags)
            it.copy(imageViewOnce = true, attachedImageUrl = null, imagePreviewUrl = null)
            else it
        }
    }

    /**
     * The deployed history RPC predates photo albums. Read the new metadata
     * through the same conversation's RLS-protected messages endpoint, without
     * replacing any message or exposing a private Storage object key in the UI.
     * Old backend deployments still return the original individual photo rows.
     */
    suspend fun hydratePhotoAlbumMetadata(
        messages: List<ChatMessage>, conversationId: String
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (messages.none { it.messageType.equals("image", ignoreCase = true) || it.messageType.equals("video", ignoreCase = true) } ||
            !photoMetadataSupported()) return@withContext messages
        val owner = uid() ?: return@withContext messages
        val convo = runCatching { UUID.fromString(conversationId).toString() }.getOrNull()
            ?: return@withContext messages
        val ids = messages.filter { it.messageType.equals("image", ignoreCase = true) || it.messageType.equals("video", ignoreCase = true) }
            .mapNotNull { runCatching { UUID.fromString(it.id).toString() }.getOrNull() }
        if (ids.isEmpty()) return@withContext messages

        val metadata = runCatching {
            val path = "/rest/v1/messages?conversation_id=eq.$convo" +
                "&id=in.(${ids.joinToString(",")})" +
                "&select=id,sender_id,image_album_id,image_album_position," +
                "image_album_size,image_preview_url&limit=100"
            client.newCall(builder(path).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap<String, JSONObject>()
                val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                buildMap<String, JSONObject> {
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        val mid = row.optString("id")
                        if (mid in ids) put(mid, row)
                    }
                }
            }
        }.getOrDefault(emptyMap())
        if (uid() != owner || metadata.isEmpty()) return@withContext messages

        messages.map { message ->
            val row = metadata[message.id] ?: return@map message
            if (!(message.messageType.equals("image", ignoreCase = true) ||
                    message.messageType.equals("video", ignoreCase = true)) ||
                message.imageViewOnce || row.optString("sender_id") != message.senderId) {
                return@map message
            }
            val groupId = row.optString("image_album_id").takeIf { value ->
                runCatching { UUID.fromString(value) }.isSuccess
            }
            val count = row.optInt("image_album_size", 1)
            val position = row.optInt("image_album_position", 0)
            val safeGroup = groupId != null && count in 2..10 && position in 0 until count
            val preview = row.optString("image_preview_url").takeIf { path ->
                path.startsWith(
                    "$PRIVATE_BUCKET:conversations/$convo/users/${message.senderId}/preview/"
                )
            }?.removePrefix("$PRIVATE_BUCKET:")?.let(::signedUrl)
            message.copy(
                imageAlbumId = groupId.takeIf { safeGroup },
                imageAlbumPosition = if (safeGroup) position else 0,
                imageAlbumSize = if (safeGroup) count else 1,
                imagePreviewUrl = preview ?: message.imagePreviewUrl
            )
        }
    }

    suspend fun sendVideoMessage(
        context: Context,
        receiverUsername: String,
        uri: Uri
    ): Result<ChatMessage> = sendAttachment(context, receiverUsername, uri, BlinkChatAttachmentKind.VIDEO)

    /**
     * Upload a verified private file without loading the entire source into RAM.
     * All direct conversation membership is established by the existing backend
     * RPC (never by client-side insertion of another user's membership).
     * Retrying with the same message UUID cannot create another chat message.
     */
    @Volatile private var serverHasVideoAlbums: Boolean? = null

    private fun videoAlbumSupported(): Boolean {
        serverHasVideoAlbums?.let { return it }
        val supported = runCatching {
            client.newCall(
                builder("/rest/v1/rpc/blink_chat_video_album_supported")
                    .post("{}".toRequestBody(jsonType)).build()
            ).execute().use { response ->
                response.isSuccessful && response.body?.string()?.trim() == "true"
            }
        }.getOrDefault(false)
        if (supported) serverHasVideoAlbums = true
        return supported
    }

    @Volatile private var serverHasPhotoMetadata: Boolean? = null
    private fun photoMetadataSupported(): Boolean {
        serverHasPhotoMetadata?.let { return it }
        val supported = runCatching {
            client.newCall(builder(
                "/rest/v1/messages?select=image_album_id,image_album_position," +
                    "image_album_size,image_preview_url&limit=0"
            ).get().build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        if (supported) serverHasPhotoMetadata = true
        return supported
    }

    suspend fun sendAttachment(
        context: Context,
        receiverUsername: String,
        uri: Uri,
        kind: BlinkChatAttachmentKind,
        durationMs: Long = 0L,
        clientMessageId: String = UUID.randomUUID().toString(),
        onUploadProgress: (Int) -> Unit = {},
        caption: String = "",
        imageQuality: BlinkChatImageQuality = BlinkChatImageQuality.STANDARD,
        album: BlinkChatPhotoAlbum = BlinkChatPhotoAlbum.single(),
        shouldCancel: () -> Boolean = { false }
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            if (shouldCancel()) throw java.io.InterruptedIOException("Upload cancelled.")
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
                        "content,created_at,media_url,message_type&limit=1"
                ).get().build()
            ).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(
                    error(raw, "Cannot check existing attachment message.")
                )
                JSONArray(raw.ifBlank { "[]" }).optJSONObject(0)
            }
            if (existing != null) {
                require(existing.optString("sender_id") == senderId &&
                    existing.optString("conversation_id") == conversationId &&
                    existing.optString("message_type") == kind.wireType) {
                    "This message ID belongs to a different attachment."
                }
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

            val effectiveAlbum = if (kind == BlinkChatAttachmentKind.VIDEO &&
                album.grouped && !videoAlbumSupported()) {
                // Older databases enforce image-only album metadata. Preserve
                // successful standalone delivery until the verified migration runs.
                BlinkChatPhotoAlbum.single()
            } else album
            val selected = if (kind == BlinkChatAttachmentKind.IMAGE)
                BlinkChatImagePreparer.open(context, uri, imageQuality)
            else BoundedMessageAttachment.open(context, uri, kind)
            selected.use { staged ->
                if (shouldCancel()) throw java.io.InterruptedIOException("Upload cancelled.")
                val expandedMetadata = (kind == BlinkChatAttachmentKind.IMAGE ||
                    kind == BlinkChatAttachmentKind.VIDEO) && photoMetadataSupported()
                val objectPath =
                    "conversations/$conversationId/users/$senderId/${kind.wireType}/$safeMessageId.${staged.extension}"
                client.newCall(
                    builder("/storage/v1/object/$PRIVATE_BUCKET/${encodedPath(objectPath)}")
                        .addHeader("x-upsert", "false")
                        .post(staged.requestBody { sent, total ->
                            if (shouldCancel()) throw java.io.InterruptedIOException("Upload cancelled.")
                            onUploadProgress(BlinkChatUploadProgress.percent(sent, total))
                        }).build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful && response.code != 409) {
                        throw IllegalStateException(error(raw, "Upload failed."))
                    }
                }
                var privatePreviewPath: String? = null
                if (expandedMetadata && kind == BlinkChatAttachmentKind.IMAGE) {
                    val thumb = BlinkChatImagePreparer.thumbnail(context, staged.file)
                    if (thumb != null) {
                        try {
                            val proposed = "conversations/$conversationId/users/$senderId/preview/" +
                                "$safeMessageId.jpg"
                            val result = client.newCall(
                                builder("/storage/v1/object/$PRIVATE_BUCKET/${encodedPath(proposed)}")
                                    .addHeader("x-upsert", "false")
                                    .post(thumb.asRequestBody("image/jpeg".toMediaType()))
                                    .build()
                            ).execute().use { it.isSuccessful || it.code == 409 }
                            if (result) privatePreviewPath = proposed
                        } finally {
                            thumb.delete()
                        }
                    }
                }
                if (shouldCancel()) throw java.io.InterruptedIOException("Upload cancelled.")
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
                if (expandedMetadata) {
                    if (effectiveAlbum.grouped) {
                        messageBody.put("image_album_id", effectiveAlbum.id)
                            .put("image_album_position", effectiveAlbum.position)
                            .put("image_album_size", effectiveAlbum.size)
                    }
                    privatePreviewPath?.let {
                        messageBody.put("image_preview_url", "$PRIVATE_BUCKET:$it")
                    }
                }
                if (shouldCancel()) throw java.io.InterruptedIOException("Upload cancelled.")
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
                        imageAlbumId = effectiveAlbum.id.takeIf { expandedMetadata && effectiveAlbum.grouped },
                        messageType = kind.wireType,
                        imageAlbumPosition = if (expandedMetadata) effectiveAlbum.position else 0,
                        imageAlbumSize = if (expandedMetadata) effectiveAlbum.size else 1,
                        imagePreviewUrl = privatePreviewPath?.let(::signedUrl),
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

    /**
     * Server-paginated gallery for the entire accessible conversation history.
     * The underlying messages SELECT is always subject to conversation RLS.
     * No public bucket listing or unbounded local history load is required.
     */

    suspend fun fetchConversationPhotoPage(
        conversationId: String, offset: Int = 0, limit: Int = 36
    ): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.fromString(conversationId).toString()
            require(offset in 0..100000) { "Invalid gallery page." }
            val pageSize = limit.coerceIn(1, 48)
            val metadataFields = if (photoMetadataSupported())
                ",image_album_id,image_album_position,image_album_size,image_preview_url" else ""
            val url = "/rest/v1/messages?select=id,conversation_id,sender_id,content,media_url," +
                "created_at,delivered_at,read_at,deleted_for_everyone" + metadataFields +
                "&conversation_id=eq.$id&message_type=eq.image" +
                "&deleted_for_everyone=eq.false&order=created_at.desc,id.desc" +
                "&limit=$pageSize&offset=$offset"
            val rows = client.newCall(builder(url).get().build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                check(response.isSuccessful) { error(raw, "Could not load shared pictures.") }
                JSONArray(raw.ifBlank { "[]" })
            }
            val prefix = "$PRIVATE_BUCKET:conversations/$id/users/"
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val reference = row.optString("media_url")
                    if (!reference.startsWith(prefix)) continue
                    val url = signedUrl(reference.removePrefix("$PRIVATE_BUCKET:")) ?: continue
                    add(ChatMessage(
                        id = row.optString("id"),
                        conversationId = id,
                        senderId = row.optString("sender_id"),
                        text = row.optString("content"),
                        rawTimestamp = row.optString("created_at"),
                        timestamp = row.optString("created_at").take(16).replace('T', ' '),
                        status = when {
                            row.optString("read_at").isNotBlank() -> MessageStatus.READ
                            row.optString("delivered_at").isNotBlank() -> MessageStatus.DELIVERED
                            else -> MessageStatus.SENT
                        },
                        attachedImageUrl = url,
                        imageAlbumId = row.optString("image_album_id")
                            .takeIf { runCatching { UUID.fromString(it) }.isSuccess },
                        imageAlbumPosition = row.optInt("image_album_position", 0),
                        imageAlbumSize = row.optInt("image_album_size", 1),
                        imagePreviewUrl = row.optString("image_preview_url").takeIf {
                            it.startsWith(prefix)
                        }?.removePrefix("$PRIVATE_BUCKET:")?.let(::signedUrl)
                    ))
                }
            }
        }
    }

    /** Convert a page's private references to short-lived signed URLs only
     * after the server has returned messages in the current conversation. */

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
