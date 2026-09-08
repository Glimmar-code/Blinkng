package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BlinkAiSource(
    val title: String,
    val url: String
)

data class BlinkAiAction(
    val type: String,
    val title: String,
    val description: String,
    val field: String? = null,
    val value: String? = null,
    val requiresConfirmation: Boolean = true
)

data class BlinkAiReply(
    val text: String,
    val interactionId: String? = null,
    val conversationId: String? = null,
    val model: String? = null,
    val mode: String? = null,
    val searchedWeb: Boolean = false,
    val sources: List<BlinkAiSource> = emptyList(),
    val latencyMs: Long? = null,
    val historySaved: Boolean = false,
    val action: BlinkAiAction? = null,
    val actionCompleted: Boolean = false
)

data class BlinkAiConversation(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String
)

data class BlinkAiHistoryMessage(
    val id: String,
    val role: String,
    val content: String,
    val providerInteractionId: String?,
    val createdAt: String
)

class BlinkAiService {
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
        val rawBytes: Int
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey
    @Volatile private var activeCall: Call? = null

    fun cancelActiveRequest() {
        activeCall?.cancel()
        activeCall = null
    }

    suspend fun ask(
        context: Context,
        message: String,
        previousInteractionId: String? = null,
        imageUris: List<Uri> = emptyList(),
        audioUri: Uri? = null,
        usePersonalContext: Boolean = true,
        useWebSearch: Boolean = true,
        mode: String = "fast",
        tone: String = "balanced",
        responseLength: String = "medium",
        temporaryChat: Boolean = false,
        customInstructions: String = ""
    ): Result<BlinkAiReply> = runCatching {
        val cleanMessage = message.trim()
        require(cleanMessage.length <= MAX_MESSAGE_LENGTH) {
            "Keep your message under 8,000 characters."
        }
        require(cleanMessage.isNotBlank() || imageUris.isNotEmpty() || audioUri != null) {
            "Type a message or attach an image or voice note."
        }
        require(imageUris.size <= MAX_IMAGES) { "Attach up to 6 images at a time." }

        withContext(Dispatchers.IO) {
            val attachments = encodeAttachments(context, imageUris, audioUri)
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
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put("previous_interaction_id", it) }
                }
            executeAi(payload)
        }
    }

    suspend fun executeAction(
        context: Context,
        action: BlinkAiAction,
        imageUri: Uri? = null
    ): Result<BlinkAiReply> = runCatching {
        withContext(Dispatchers.IO) {
            val attachments = encodeAttachments(context, listOfNotNull(imageUri), null)
            val actionJson = JSONObject()
                .put("type", action.type)
                .apply {
                    action.field?.let { put("field", it) }
                    action.value?.let { put("value", it) }
                }
            executeAi(
                JSONObject()
                    .put("confirm_action", actionJson)
                    .put("attachments", attachmentsToJson(attachments))
            )
        }
    }

    suspend fun loadConversations(limit: Int = 50): Result<List<BlinkAiConversation>> = runCatching {
        withContext(Dispatchers.IO) {
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
                        BlinkAiConversation(
                            id = id,
                            title = row.optString("title", "New conversation").ifBlank { "New conversation" },
                            createdAt = row.optString("created_at"),
                            updatedAt = row.optString("updated_at")
                        )
                    )
                }
            }
        }
    }

    suspend fun loadConversationMessages(conversationId: String): Result<List<BlinkAiHistoryMessage>> = runCatching {
        require(conversationId.isNotBlank()) { "Conversation id is required." }
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(conversationId, Charsets.UTF_8.name())
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
                        BlinkAiHistoryMessage(
                            id = id,
                            role = row.optString("role", "assistant"),
                            content = content,
                            providerInteractionId = row.optString("provider_interaction_id")
                                .takeIf { it.isNotBlank() && it != "null" },
                            createdAt = row.optString("created_at")
                        )
                    )
                }
            }
        }
    }

    suspend fun renameConversation(conversationId: String, title: String): Result<Unit> = runCatching {
        val cleanTitle = title.trim().take(160)
        require(cleanTitle.isNotBlank()) { "Conversation title cannot be empty." }
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(conversationId, Charsets.UTF_8.name())
            executeRest(
                path = "/rest/v1/blink_ai_conversations?id=eq.$encoded",
                method = "PATCH",
                body = JSONObject().put("title", cleanTitle)
            )
            Unit
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(conversationId, Charsets.UTF_8.name())
            executeRest(
                path = "/rest/v1/blink_ai_conversations?id=eq.$encoded",
                method = "DELETE"
            )
            Unit
        }
    }

    private fun encodeAttachments(
        context: Context,
        imageUris: List<Uri>,
        audioUri: Uri?
    ): List<EncodedAttachment> {
        val result = mutableListOf<EncodedAttachment>()
        imageUris.take(MAX_IMAGES).forEach { uri ->
            result += encodeAttachment(context, uri, "image", MAX_IMAGE_BYTES)
        }
        audioUri?.let { result += encodeAttachment(context, it, "audio", MAX_AUDIO_BYTES) }
        val total = result.sumOf { it.rawBytes }
        require(total <= MAX_COMBINED_MEDIA_BYTES) {
            "The attached media is too large. Keep the total under 24 MB."
        }
        return result
    }

    private fun encodeAttachment(
        context: Context,
        uri: Uri,
        type: String,
        maxBytes: Int
    ): EncodedAttachment {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri).orEmpty().lowercase().trim()
        val mime = normalizeMime(type, rawMime)
        require(isSupported(type, mime)) {
            if (type == "image") "That image format isn't supported." else "That voice-note format isn't supported."
        }

        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= maxBytes) {
                    if (type == "image") "Keep each image under 6 MB." else "Keep voice notes under 8 MB."
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("Couldn't read the attached ${if (type == "image") "image" else "voice note"}.")

        require(bytes.isNotEmpty()) { "The attached media is empty." }
        return EncodedAttachment(
            type = type,
            mimeType = mime,
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            rawBytes = bytes.size
        )
    }

    private fun normalizeMime(type: String, raw: String): String = when {
        type == "image" && raw == "image/jpg" -> "image/jpeg"
        type == "audio" && (raw == "audio/mp4" || raw == "audio/x-m4a") -> "audio/m4a"
        type == "audio" && raw == "audio/x-wav" -> "audio/wav"
        else -> raw
    }

    private fun isSupported(type: String, mime: String): Boolean {
        val images = setOf(
            "image/png", "image/jpeg", "image/webp", "image/heic", "image/heif",
            "image/gif", "image/bmp", "image/tiff"
        )
        val audio = setOf(
            "audio/wav", "audio/mp3", "audio/aiff", "audio/aac", "audio/ogg",
            "audio/flac", "audio/mpeg", "audio/m4a", "audio/opus", "audio/webm"
        )
        return if (type == "image") mime in images else mime in audio
    }

    private fun attachmentsToJson(attachments: List<EncodedAttachment>): JSONArray = JSONArray().apply {
        attachments.forEach { attachment ->
            put(
                JSONObject()
                    .put("type", attachment.type)
                    .put("mime_type", attachment.mimeType)
                    .put("data", attachment.base64)
            )
        }
    }

    private suspend fun executeAi(payload: JSONObject): BlinkAiReply {
        val session = ensureSession()
        val endpoints = listOf("blink-ai-v2", "blink-ai")
        var lastFailure: IllegalStateException? = null

        for ((index, endpoint) in endpoints.withIndex()) {
            val response = executeAuthenticatedRequest(
                session = session,
                requestFactory = { token ->
                    Request.Builder()
                        .url("$baseUrl/functions/v1/$endpoint")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/json")
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()
                }
            )

            response.use {
                val raw = it.body?.string().orEmpty().trim()
                if (it.isSuccessful) return parseReply(raw)

                val failure = IllegalStateException(parseError(raw, it.code))
                lastFailure = failure
                val fallbackAllowed = index == 0 && it.code in setOf(404, 500, 502, 503)
                if (!fallbackAllowed) throw failure
            }
        }

        throw lastFailure ?: IllegalStateException("Blink AI couldn't answer that request.")
    }

    private suspend fun executeRest(
        path: String,
        method: String = "GET",
        body: JSONObject? = null
    ): String {
        val session = ensureSession()
        val response = executeAuthenticatedRequest(
            session = session,
            requestFactory = { token ->
                Request.Builder()
                    .url("$baseUrl$path")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .apply {
                        when (method) {
                            "PATCH" -> patch((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
                            "DELETE" -> delete()
                            else -> get()
                        }
                    }
                    .build()
            }
        )
        response.use {
            val raw = it.body?.string().orEmpty().trim()
            if (!it.isSuccessful) throw IllegalStateException(parseError(raw, it.code))
            return raw
        }
    }

    private suspend fun ensureSession(): SupabaseService {
        val session = SupabaseService()
        if (!session.restoreSession()) {
            throw IllegalStateException("Your Blink session has expired. Please sign in again.")
        }
        return session
    }

    private suspend fun executeAuthenticatedRequest(
        session: SupabaseService,
        requestFactory: (String) -> Request
    ): okhttp3.Response {
        fun token(): String = SupabaseService.accessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("A signed-in Blink account is required.")

        var request = requestFactory(token())
        var call = client.newCall(request)
        activeCall = call
        var response = withContext(Dispatchers.IO) { call.execute() }

        if (response.code == 401) {
            response.close()
            if (!session.refreshSession()) {
                activeCall = null
                throw IllegalStateException("Your Blink session has expired. Please sign in again.")
            }
            request = requestFactory(token())
            call = client.newCall(request)
            activeCall = call
            response = withContext(Dispatchers.IO) { call.execute() }
        }
        activeCall = null
        return response
    }

    private fun parseReply(raw: String): BlinkAiReply {
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
                    BlinkAiSource(
                        title = row.optString("title", "Source").ifBlank { "Source" },
                        url = url
                    )
                )
            }
        }

        return BlinkAiReply(
            text = text,
            interactionId = json.optString("interaction_id")
                .takeIf { it.isNotBlank() && it != "null" },
            conversationId = json.optString("conversation_id")
                .takeIf { it.isNotBlank() && it != "null" },
            model = json.optString("model")
                .takeIf { it.isNotBlank() && it != "null" },
            mode = json.optString("mode")
                .takeIf { it.isNotBlank() && it != "null" },
            searchedWeb = json.optBoolean("searched_web", false),
            sources = sources,
            latencyMs = json.optLong("latency_ms", -1L).takeIf { it >= 0L },
            historySaved = json.optBoolean("history_saved", false),
            action = parseAction(json.optJSONObject("action")),
            actionCompleted = json.optBoolean("action_completed", false)
        )
    }

    private fun parseAction(json: JSONObject?): BlinkAiAction? {
        if (json == null) return null
        val type = json.optString("type").trim()
        if (type.isBlank()) return null
        return BlinkAiAction(
            type = type,
            title = json.optString("title", "Confirm Blink AI action").ifBlank { "Confirm Blink AI action" },
            description = json.optString("description").ifBlank { "Blink AI wants to make this change." },
            field = json.optString("field").takeIf { it.isNotBlank() && it != "null" },
            value = json.optString("value").takeIf { it.isNotBlank() && it != "null" },
            requiresConfirmation = json.optBoolean("requires_confirmation", true)
        )
    }

    private fun parseError(body: String, status: Int): String = runCatching {
        val json = JSONObject(body)
        val message = json.optString("error").takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
        when (status) {
            401 -> message ?: "Your Blink session has expired. Please sign in again."
            413 -> message ?: "The request is too large."
            429 -> message ?: "Blink AI is busy right now. Please try again shortly."
            503 -> message ?: "Blink AI is being configured. Please try again later."
            else -> message ?: "Blink AI couldn't answer that request."
        }
    }.getOrElse {
        if (status == 429) "Blink AI is busy right now. Please try again shortly."
        else "Blink AI couldn't answer that request."
    }
}
