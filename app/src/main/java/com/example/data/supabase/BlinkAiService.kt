package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
    val model: String? = null,
    val action: BlinkAiAction? = null,
    val actionCompleted: Boolean = false
)

class BlinkAiService {
    companion object {
        private const val MAX_MESSAGE_LENGTH = 4_000
        private const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
        private const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
        private const val MAX_COMBINED_MEDIA_BYTES = 12 * 1024 * 1024
    }

    private data class EncodedAttachment(
        val type: String,
        val mimeType: String,
        val base64: String,
        val rawBytes: Int
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    suspend fun ask(
        context: Context,
        message: String,
        previousInteractionId: String? = null,
        imageUri: Uri? = null,
        audioUri: Uri? = null,
        usePersonalContext: Boolean = true
    ): Result<BlinkAiReply> = runCatching {
        val cleanMessage = message.trim()
        require(cleanMessage.length <= MAX_MESSAGE_LENGTH) {
            "Keep your message under 4,000 characters."
        }
        require(cleanMessage.isNotBlank() || imageUri != null || audioUri != null) {
            "Type a message or attach an image or voice note."
        }

        withContext(Dispatchers.IO) {
            val attachments = encodeAttachments(context, imageUri, audioUri)
            val payload = JSONObject()
                .put("message", cleanMessage)
                .put("use_personal_context", usePersonalContext)
                .put("attachments", attachmentsToJson(attachments))
                .apply {
                    previousInteractionId
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put("previous_interaction_id", it) }
                }
            execute(payload)
        }
    }

    suspend fun executeAction(
        context: Context,
        action: BlinkAiAction,
        imageUri: Uri? = null
    ): Result<BlinkAiReply> = runCatching {
        withContext(Dispatchers.IO) {
            val attachments = encodeAttachments(context, imageUri, null)
            val actionJson = JSONObject()
                .put("type", action.type)
                .apply {
                    action.field?.let { put("field", it) }
                    action.value?.let { put("value", it) }
                }
            val payload = JSONObject()
                .put("confirm_action", actionJson)
                .put("attachments", attachmentsToJson(attachments))
            execute(payload)
        }
    }

    private fun encodeAttachments(
        context: Context,
        imageUri: Uri?,
        audioUri: Uri?
    ): List<EncodedAttachment> {
        val result = mutableListOf<EncodedAttachment>()
        imageUri?.let { result += encodeAttachment(context, it, "image", MAX_IMAGE_BYTES) }
        audioUri?.let { result += encodeAttachment(context, it, "audio", MAX_AUDIO_BYTES) }
        val total = result.sumOf { it.rawBytes }
        require(total <= MAX_COMBINED_MEDIA_BYTES) {
            "The attached media is too large. Keep the total under 12 MB."
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
                    if (type == "image") "Keep images under 6 MB." else "Keep voice notes under 8 MB."
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

    private fun execute(payload: JSONObject): BlinkAiReply {
        val session = SupabaseService()
        if (!session.restoreSession()) {
            throw IllegalStateException("Your Blink session has expired. Please sign in again.")
        }

        fun buildRequest(): Request {
            val token = SupabaseService.accessToken()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("A signed-in Blink account is required.")
            return Request.Builder()
                .url("$baseUrl/functions/v1/blink-ai")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }

        var response = client.newCall(buildRequest()).execute()
        if (response.code == 401) {
            response.close()
            if (!session.refreshSession()) {
                throw IllegalStateException("Your Blink session has expired. Please sign in again.")
            }
            response = client.newCall(buildRequest()).execute()
        }

        response.use {
            val raw = it.body?.string().orEmpty().trim()
            if (!it.isSuccessful) {
                throw IllegalStateException(parseError(raw, it.code))
            }

            val json = JSONObject(raw.ifBlank { "{}" })
            val text = json.optString("text").trim()
            if (text.isBlank()) throw IllegalStateException("Blink AI returned an empty response.")

            return BlinkAiReply(
                text = text,
                interactionId = json.optString("interaction_id")
                    .takeIf { value -> value.isNotBlank() && value != "null" },
                model = json.optString("model")
                    .takeIf { value -> value.isNotBlank() && value != "null" },
                action = parseAction(json.optJSONObject("action")),
                actionCompleted = json.optBoolean("action_completed", false)
            )
        }
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
            413 -> message ?: "The request is too large."
            429 -> "Blink AI is busy right now. Please try again shortly."
            503 -> message ?: "Blink AI is being configured. Please try again later."
            else -> message ?: "Blink AI couldn't answer that request."
        }
    }.getOrElse {
        if (status == 429) "Blink AI is busy right now. Please try again shortly."
        else "Blink AI couldn't answer that request."
    }
}
