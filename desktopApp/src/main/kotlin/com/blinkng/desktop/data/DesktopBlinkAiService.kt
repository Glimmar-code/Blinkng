package com.blinkng.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

data class DesktopBlinkAiSource(
    val title: String,
    val url: String,
)

data class DesktopBlinkAiReply(
    val text: String,
    val interactionId: String? = null,
    val conversationId: String? = null,
    val model: String? = null,
    val mode: String? = null,
    val searchedWeb: Boolean = false,
    val sources: List<DesktopBlinkAiSource> = emptyList(),
    val latencyMs: Long? = null,
    val historySaved: Boolean = false,
)

data class DesktopBlinkAiConversation(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
)

data class DesktopBlinkAiHistoryMessage(
    val id: String,
    val role: String,
    val content: String,
    val providerInteractionId: String?,
    val createdAt: String,
)

/** Windows counterpart to Android's Blink AI service. */
class DesktopBlinkAiService(
    private val client: DesktopSupabaseClient,
) {
    companion object {
        private const val MAX_MESSAGE_LENGTH = 8_000
        private const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
        private const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
        private const val MAX_COMBINED_MEDIA_BYTES = 24 * 1024 * 1024
        private const val MAX_IMAGES = 6
    }

    private data class EncodedAttachment(
        val type: String,
        val mimeType: String,
        val base64: String,
        val rawBytes: Int,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    @Volatile private var activeCall: Call? = null

    fun cancelActiveRequest() {
        activeCall?.cancel()
        activeCall = null
    }

    suspend fun ask(
        message: String,
        previousInteractionId: String? = null,
        imageFiles: List<File> = emptyList(),
        audioFile: File? = null,
        usePersonalContext: Boolean = true,
        useWebSearch: Boolean = true,
        mode: String = "fast",
        tone: String = "balanced",
        responseLength: String = "medium",
        temporaryChat: Boolean = false,
        customInstructions: String = "",
    ): DesktopBlinkAiReply = withContext(Dispatchers.IO) {
        val cleanMessage = message.trim()
        require(cleanMessage.isNotBlank() || imageFiles.isNotEmpty() || audioFile != null) {
            "Type a message or attach media for Blink AI."
        }
        require(cleanMessage.length <= MAX_MESSAGE_LENGTH) { "Keep your message under 8,000 characters." }
        require(imageFiles.size <= MAX_IMAGES) { "Attach up to 6 images at a time." }

        val attachments = encodeAttachments(imageFiles, audioFile)
        val payload = JSONObject()
            .put("message", cleanMessage)
            .put("use_personal_context", usePersonalContext)
            .put("use_web_search", useWebSearch)
            .put("mode", mode)
            .put("tone", tone)
            .put("response_length", responseLength)
            .put("temporary_chat", temporaryChat)
            .put("custom_instructions", customInstructions.take(1_200))
            .put("attachments", attachmentsToJson(attachments))
            .apply {
                previousInteractionId
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { put("previous_interaction_id", it) }
            }

        val endpoints = listOf("blink-ai-v2", "blink-ai")
        var lastFailure: IllegalStateException? = null
        endpoints.forEachIndexed { index, endpoint ->
            val active = client.restoreSession()
                ?: throw IllegalStateException("Your Blink session has expired. Please sign in again.")
            val request = Request.Builder()
                .url("${DesktopBackendConfig.url.trimEnd('/')}/functions/v1/$endpoint")
                .addHeader("apikey", DesktopBackendConfig.anonKey)
                .addHeader("Authorization", "Bearer ${active.accessToken}")
                .addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            val call = http.newCall(request)
            activeCall = call
            call.execute().use { response ->
                activeCall = null
                val raw = response.body?.string().orEmpty().trim()
                if (response.isSuccessful) return@withContext parseReply(raw)
                val failure = IllegalStateException(parseError(raw, response.code))
                lastFailure = failure
                val fallbackAllowed = index == 0 && response.code in setOf(404, 500, 502, 503)
                if (!fallbackAllowed) throw failure
            }
        }
        throw lastFailure ?: IllegalStateException("Blink AI couldn't answer that request.")
    }

    suspend fun loadConversations(limit: Int = 50): List<DesktopBlinkAiConversation> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        val raw = executeRest(
            "/rest/v1/blink_ai_conversations?select=id,title,created_at,updated_at&order=updated_at.desc&limit=$safeLimit"
        )
        val rows = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id").trim()
                if (id.isBlank()) continue
                add(
                    DesktopBlinkAiConversation(
                        id = id,
                        title = row.optString("title", "New conversation").ifBlank { "New conversation" },
                        createdAt = row.optString("created_at"),
                        updatedAt = row.optString("updated_at"),
                    )
                )
            }
        }
    }

    suspend fun loadConversationMessages(conversationId: String): List<DesktopBlinkAiHistoryMessage> = withContext(Dispatchers.IO) {
        require(conversationId.isNotBlank()) { "Conversation id is required." }
        val encoded = URLEncoder.encode(conversationId, StandardCharsets.UTF_8)
        val raw = executeRest(
            "/rest/v1/blink_ai_messages?conversation_id=eq.$encoded&select=id,role,content,provider_interaction_id,created_at&order=created_at.asc&limit=200"
        )
        val rows = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id").trim()
                val content = row.optString("content").trim()
                if (id.isBlank() || content.isBlank()) continue
                add(
                    DesktopBlinkAiHistoryMessage(
                        id = id,
                        role = row.optString("role", "assistant"),
                        content = content,
                        providerInteractionId = row.optString("provider_interaction_id")
                            .takeIf { it.isNotBlank() && it != "null" },
                        createdAt = row.optString("created_at"),
                    )
                )
            }
        }
    }

    suspend fun renameConversation(conversationId: String, title: String) = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().take(160)
        require(cleanTitle.isNotBlank()) { "Conversation title cannot be empty." }
        val encoded = URLEncoder.encode(conversationId, StandardCharsets.UTF_8)
        executeRest(
            path = "/rest/v1/blink_ai_conversations?id=eq.$encoded",
            method = "PATCH",
            body = JSONObject().put("title", cleanTitle),
        )
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(conversationId, StandardCharsets.UTF_8)
        executeRest(
            path = "/rest/v1/blink_ai_conversations?id=eq.$encoded",
            method = "DELETE",
        )
    }

    private fun encodeAttachments(imageFiles: List<File>, audioFile: File?): List<EncodedAttachment> {
        val result = mutableListOf<EncodedAttachment>()
        imageFiles.take(MAX_IMAGES).forEach { result += encodeFile(it, "image", MAX_IMAGE_BYTES) }
        audioFile?.let { result += encodeFile(it, "audio", MAX_AUDIO_BYTES) }
        require(result.sumOf { it.rawBytes } <= MAX_COMBINED_MEDIA_BYTES) {
            "The attached media is too large. Keep the total under 24 MB."
        }
        return result
    }

    private fun encodeFile(file: File, type: String, maxBytes: Int): EncodedAttachment {
        require(file.isFile) { "The selected media file is unavailable." }
        require(file.length() in 1..maxBytes.toLong()) {
            if (type == "image") "Keep each image under 6 MB." else "Keep voice notes under 8 MB."
        }
        val mime = mimeType(file, type)
            ?: throw IllegalArgumentException(if (type == "image") "That image format isn't supported." else "That audio format isn't supported.")
        val bytes = file.readBytes()
        return EncodedAttachment(
            type = type,
            mimeType = mime,
            base64 = Base64.getEncoder().encodeToString(bytes),
            rawBytes = bytes.size,
        )
    }

    private fun mimeType(file: File, type: String): String? {
        val ext = file.extension.lowercase()
        return if (type == "image") {
            mapOf(
                "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
                "webp" to "image/webp", "gif" to "image/gif", "bmp" to "image/bmp",
                "tif" to "image/tiff", "tiff" to "image/tiff", "heic" to "image/heic", "heif" to "image/heif",
            )[ext]
        } else {
            mapOf(
                "wav" to "audio/wav", "mp3" to "audio/mpeg", "aiff" to "audio/aiff",
                "aac" to "audio/aac", "ogg" to "audio/ogg", "flac" to "audio/flac",
                "m4a" to "audio/m4a", "opus" to "audio/opus", "webm" to "audio/webm",
            )[ext]
        }
    }

    private fun attachmentsToJson(attachments: List<EncodedAttachment>) = JSONArray().apply {
        attachments.forEach { attachment ->
            put(
                JSONObject()
                    .put("type", attachment.type)
                    .put("mime_type", attachment.mimeType)
                    .put("data", attachment.base64)
            )
        }
    }

    private suspend fun executeRest(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): String {
        val active = client.restoreSession()
            ?: throw IllegalStateException("Your Blink session has expired. Please sign in again.")
        val request = Request.Builder()
            .url("${DesktopBackendConfig.url.trimEnd('/')}$path")
            .addHeader("apikey", DesktopBackendConfig.anonKey)
            .addHeader("Authorization", "Bearer ${active.accessToken}")
            .addHeader("Accept", "application/json")
            .apply {
                when (method) {
                    "PATCH" -> patch((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
                    "DELETE" -> delete()
                    else -> get()
                }
            }
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) throw IllegalStateException(parseError(raw, response.code))
            return raw
        }
    }

    private fun parseReply(raw: String): DesktopBlinkAiReply {
        val json = JSONObject(raw.ifBlank { "{}" })
        val text = json.optString("text").trim()
        if (text.isBlank()) throw IllegalStateException("Blink AI returned an empty response.")
        val sourceRows = json.optJSONArray("sources") ?: JSONArray()
        val sources = buildList {
            for (i in 0 until sourceRows.length()) {
                val row = sourceRows.optJSONObject(i) ?: continue
                val url = row.optString("url").trim()
                if (url.isBlank()) continue
                add(
                    DesktopBlinkAiSource(
                        title = row.optString("title", "Source").ifBlank { "Source" },
                        url = url,
                    )
                )
            }
        }
        return DesktopBlinkAiReply(
            text = text,
            interactionId = json.optString("interaction_id").takeIf { it.isNotBlank() && it != "null" },
            conversationId = json.optString("conversation_id").takeIf { it.isNotBlank() && it != "null" },
            model = json.optString("model").takeIf { it.isNotBlank() && it != "null" },
            mode = json.optString("mode").takeIf { it.isNotBlank() && it != "null" },
            searchedWeb = json.optBoolean("searched_web", false),
            sources = sources,
            latencyMs = json.optLong("latency_ms", -1L).takeIf { it >= 0L },
            historySaved = json.optBoolean("history_saved", false),
        )
    }

    private fun parseError(body: String, status: Int): String = runCatching {
        val json = JSONObject(body)
        val messageText = json.optString("error").takeIf(String::isNotBlank)
            ?: json.optString("message").takeIf(String::isNotBlank)
        when (status) {
            401 -> "Your Blink session has expired. Please sign in again."
            413 -> messageText ?: "The request is too large."
            429 -> messageText ?: "Blink AI is busy right now. Please try again shortly."
            503 -> messageText ?: "Blink AI is being configured. Please try again later."
            else -> messageText ?: "Blink AI couldn't answer that request."
        }
    }.getOrElse {
        if (status == 429) "Blink AI is busy right now. Please try again shortly."
        else "Blink AI couldn't answer that request."
    }
}
