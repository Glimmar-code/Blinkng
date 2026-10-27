package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Private channel-media uploader. The backend's storage RLS MUST be deployed
 * before enabling this flow; paths are bound to the channel and current admin.
 * Uses Android's permission-scoped system picker, not gallery-wide access.
 */
object BlinkChannelMediaService {
    private const val BUCKET = "blink-channel-media"
    private const val MAX_BYTES = 25L * 1024L * 1024L
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS).build()
    private val allowed = mapOf(
        "image/jpeg" to ("image" to "jpg"),
        "image/png" to ("image" to "png"),
        "image/webp" to ("image" to "webp"),
        "video/mp4" to ("video" to "mp4"),
        "audio/mp4" to ("audio" to "m4a"),
        "audio/mpeg" to ("audio" to "mp3"),
        "application/pdf" to ("document" to "pdf")
    )

    data class Uploaded(val kind: String, val objectPath: String)

    private fun encoded(path: String): String = path.split('/').joinToString("/") {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    private fun authenticated(path: String): Request.Builder {
        val bearer = SupabaseService.accessToken() ?: error("Sign in to upload channel media.")
        return Request.Builder().url(SupabaseConfig.url.trimEnd('/') + path)
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $bearer")
    }

    suspend fun upload(context: Context, channelId: String, contentUri: Uri): Uploaded =
        withContext(Dispatchers.IO) {
            val owner = SupabaseService().getCurrentUserId() ?: error("Sign in again.")
            val kindAndExtension = allowed[context.contentResolver.getType(contentUri)?.lowercase()]
                ?: throw IllegalArgumentException("Use a JPG, PNG, WebP, MP4, MP3, M4A or PDF.")
            val (kind, extension) = kindAndExtension
            val mime = allowed.entries.first { it.value == kindAndExtension }.key
            val local = File.createTempFile("blink_channel_", ".$extension", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(contentUri)
                    ?: error("The selected file is not accessible.")
                input.use { source ->
                    local.outputStream().use { target ->
                        val bytes = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = source.read(bytes)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "The maximum channel upload is 25 MB." }
                            target.write(bytes, 0, count)
                        }
                        require(total > 0) { "Cannot upload an empty file." }
                    }
                }
                val id = UUID.fromString(channelId).toString()
                val path = "$id/$owner/${UUID.randomUUID()}.$extension"
                http.newCall(
                    authenticated("/storage/v1/object/$BUCKET/${encoded(path)}")
                        .header("Content-Type", mime)
                        .header("x-upsert", "false")
                        .post(local.asRequestBody(mime.toMediaType()))
                        .build()
                ).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Channel upload failed: HTTP ${response.code}.")
                    }
                }
                Uploaded(kind, path)
            } finally {
                local.delete()
            }
        }

    /** Explicit forwarding copies readable media to the destination owner's private folder.
     * An original private object URL is never exposed to the destination's followers.
     * Both the original read and destination write remain protected by Storage RLS.
     */
    suspend fun forwardCopy(
        context: Context, sourcePath: String, destinationChannelId: String
    ): Uploaded = withContext(Dispatchers.IO) {
        val signed = signedUrl(sourcePath) ?: error("Source media is not accessible.")
        val base = SupabaseConfig.url.trimEnd('/')
        require(signed.startsWith("$base/storage/v1/")) { "Unexpected source media host." }
        val ext = sourcePath.substringAfterLast('.').lowercase()
        val mime = allowed.entries.firstOrNull { it.value.second == ext }?.key
            ?: error("Unsupported Channel media type.")
        val kind = allowed.getValue(mime).first
        val tmp = File.createTempFile("blink_forward_", ".$ext", context.cacheDir)
        try {
            http.newCall(Request.Builder().url(signed).get().build()).execute().use { response ->
                check(response.isSuccessful) { "Couldn't read the original Channel media." }
                val stream = response.body?.byteStream() ?: error("Original media is empty.")
                stream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val length = input.read(buf)
                            if (length < 0) break
                            total += length
                            require(total <= MAX_BYTES) { "Channel attachment exceeds 25 MB." }
                            output.write(buf, 0, length)
                        }
                        require(total > 0L) { "Original media is empty." }
                    }
                }
            }
            val uid = SupabaseService().getCurrentUserId() ?: error("Sign in again.")
            val id = UUID.fromString(destinationChannelId).toString()
            val path = "$id/$uid/${UUID.randomUUID()}.$ext"
            http.newCall(
                authenticated("/storage/v1/object/$BUCKET/${encoded(path)}")
                    .header("Content-Type", mime)
                    .header("x-upsert", "false")
                    .post(tmp.asRequestBody(mime.toMediaType()))
                    .build()
            ).execute().use { response ->
                check(response.isSuccessful) { "Channel media forwarding failed (HTTP ${response.code})." }
            }
            Uploaded(kind, path)
        } finally { tmp.delete() }
    }

    /** Forward a readable attachment as a real encrypted-transport BLINK chat
     * attachment (private message-media Storage), not just an external link.
     * Chat creation and writing are checked by the existing message ACL.
     */
    suspend fun forwardToChat(
        context: Context,
        sourcePath: String,
        recipientUsername: String,
        channelId: String,
        channelName: String,
        updateId: String,
        previewText: String
    ) = withContext(Dispatchers.IO) {
        val ext = sourcePath.substringAfterLast('.').lowercase()
        val mime = allowed.entries.firstOrNull { it.value.second == ext }?.key
            ?: error("Unsupported Channel media type.")
        val kind = when (allowed.getValue(mime).first) {
            "image" -> com.blinkng.shared.BlinkChatAttachmentKind.IMAGE
            "video" -> com.blinkng.shared.BlinkChatAttachmentKind.VIDEO
            "audio" -> com.blinkng.shared.BlinkChatAttachmentKind.VOICE
            "document" -> com.blinkng.shared.BlinkChatAttachmentKind.DOCUMENT
            else -> error("Unsupported Chat attachment.")
        }
        val url = signedUrl(sourcePath) ?: error("Original Channel media is not accessible.")
        val host = SupabaseConfig.url.trimEnd('/')
        require(url.startsWith("$host/storage/v1/")) { "Invalid signed Channel URL." }
        val file = File.createTempFile("blink_channel_chat_", ".$ext", context.cacheDir)
        try {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                check(response.isSuccessful) { "Could not read original Channel media." }
                val input = response.body?.byteStream() ?: error("Missing Channel attachment.")
                input.use { source ->
                    file.outputStream().use { target ->
                        val buffer = ByteArray(8192)
                        var size = 0L
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            size += n
                            require(size <= minOf(MAX_BYTES, kind.maxBytes)) {
                                "Attachment is too large for private Chat."
                            }
                            target.write(buffer, 0, n)
                        }
                        require(size > 0) { "The Channel attachment is empty." }
                    }
                }
            }
            val link = "https://blink.com.ng/channel/$channelId/update/$updateId"
            val attribution = "Forwarded from $channelName: ${previewText.take(160)}\n$link"
            com.example.data.supabase.MessageMediaService.sendAttachment(
                context, recipientUsername, Uri.fromFile(file), kind,
                caption = if (kind == com.blinkng.shared.BlinkChatAttachmentKind.IMAGE)
                    attribution.take(1024) else ""
            ).getOrThrow()
            if (kind != com.blinkng.shared.BlinkChatAttachmentKind.IMAGE) {
                SupabaseService().sendMessage(recipientUsername, attribution).getOrThrow()
            }
        } finally { file.delete() }
    }

    suspend fun signedUrl(objectPath: String): String? = withContext(Dispatchers.IO) {
        if (objectPath.isBlank()) return@withContext null
        val request = authenticated("/storage/v1/object/sign/$BUCKET/${encoded(objectPath)}")
            .post(JSONObject().put("expiresIn", 1200).toString()
                .toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val signed = json.optString("signedURL").ifBlank { json.optString("signedUrl") }
            val host = SupabaseConfig.url.trimEnd('/')
            when {
                signed.startsWith("$host/storage/v1/") -> signed
                signed.startsWith("/storage/v1/") -> host + signed
                signed.startsWith("/object/") -> "$host/storage/v1$signed"
                else -> null
            }
        }
    }
}
