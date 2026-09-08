package com.blinkng.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DesktopBlinkAiReply(
    val text: String,
    val interactionId: String? = null,
    val model: String? = null,
)

/**
 * Windows client for the same authenticated Supabase `blink-ai` edge function used by Android.
 * Keep the network contract shared even though each platform renders its own native UI.
 */
class DesktopBlinkAiService(
    private val client: DesktopSupabaseClient,
) {
    companion object {
        private const val MAX_MESSAGE_LENGTH = 4_000
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun ask(
        message: String,
        previousInteractionId: String? = null,
        usePersonalContext: Boolean = true,
    ): DesktopBlinkAiReply = withContext(Dispatchers.IO) {
        val cleanMessage = message.trim()
        require(cleanMessage.isNotBlank()) { "Type a message for Blink AI." }
        require(cleanMessage.length <= MAX_MESSAGE_LENGTH) { "Keep your message under 4,000 characters." }

        val active = client.restoreSession()
            ?: throw IllegalStateException("Your Blink session has expired. Please sign in again.")

        val payload = JSONObject()
            .put("message", cleanMessage)
            .put("use_personal_context", usePersonalContext)
            .put("attachments", org.json.JSONArray())
            .apply {
                previousInteractionId
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { put("previous_interaction_id", it) }
            }

        val request = Request.Builder()
            .url("${DesktopBackendConfig.url.trimEnd('/')}/functions/v1/blink-ai")
            .addHeader("apikey", DesktopBackendConfig.anonKey)
            .addHeader("Authorization", "Bearer ${active.accessToken}")
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                val messageText = runCatching {
                    val json = JSONObject(raw.ifBlank { "{}" })
                    json.optString("error").takeIf(String::isNotBlank)
                        ?: json.optString("message").takeIf(String::isNotBlank)
                }.getOrNull()
                throw IllegalStateException(
                    when (response.code) {
                        401 -> "Your Blink session has expired. Please sign in again."
                        429 -> "Blink AI is busy right now. Please try again shortly."
                        503 -> messageText ?: "Blink AI is being configured. Please try again later."
                        else -> messageText ?: "Blink AI couldn't answer that request."
                    }
                )
            }

            val json = JSONObject(raw.ifBlank { "{}" })
            val text = json.optString("text").trim()
            if (text.isBlank()) throw IllegalStateException("Blink AI returned an empty response.")
            DesktopBlinkAiReply(
                text = text,
                interactionId = json.optString("interaction_id")
                    .takeIf { it.isNotBlank() && it != "null" },
                model = json.optString("model")
                    .takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}
