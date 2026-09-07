package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BlinkAiReply(
    val text: String,
    val interactionId: String? = null,
    val model: String? = null
)

class BlinkAiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    suspend fun ask(
        message: String,
        previousInteractionId: String? = null
    ): Result<BlinkAiReply> = runCatching {
        require(message.isNotBlank()) { "Please type a message for Blink AI." }
        require(message.length <= 4_000) { "Keep your message under 4,000 characters." }

        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) {
                throw IllegalStateException("Your Blink session has expired. Please sign in again.")
            }

            fun buildRequest(): Request {
                val token = SupabaseService.accessToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("A signed-in Blink account is required.")

                val json = JSONObject()
                    .put("message", message.trim())
                    .apply {
                        previousInteractionId
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("previous_interaction_id", it) }
                    }
                    .toString()
                    .toRequestBody(jsonMediaType)

                return Request.Builder()
                    .url("$baseUrl/functions/v1/blink-ai")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .post(json)
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

                val payload = JSONObject(raw)
                val text = payload.optString("text").trim()
                if (text.isBlank()) {
                    throw IllegalStateException("Blink AI returned an empty response.")
                }

                BlinkAiReply(
                    text = text,
                    interactionId = payload.optString("interaction_id")
                        .takeIf { value -> value.isNotBlank() && value != "null" },
                    model = payload.optString("model")
                        .takeIf { value -> value.isNotBlank() && value != "null" }
                )
            }
        }
    }

    private fun parseError(body: String, status: Int): String = runCatching {
        val json = JSONObject(body)
        val message = json.optString("error").takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }

        when (status) {
            429 -> "Blink AI is busy right now. Please try again shortly."
            503 -> message ?: "Blink AI is being configured. Please try again later."
            else -> message ?: "Blink AI couldn't answer that request."
        }
    }.getOrElse {
        when (status) {
            429 -> "Blink AI is busy right now. Please try again shortly."
            else -> "Blink AI couldn't answer that request."
        }
    }
}
