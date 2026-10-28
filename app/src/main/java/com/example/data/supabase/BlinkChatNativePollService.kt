package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Feature-detects the protected native poll RPC. A missing or unverified backend
 * MUST leave the existing honest text-poll fallback intact.
 */
class BlinkChatNativePollService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val json = "application/json; charset=utf-8".toMediaType()
    private suspend fun call(method: String, args: JSONObject): String = withContext(Dispatchers.IO) {
        val auth = SupabaseService()
        require(auth.restoreSession()) { "Sign in to use chat polls." }
        val token = SupabaseService.accessToken() ?: error("Sign in again.")
        val request = Request.Builder()
            .url(SupabaseConfig.url.trimEnd('/') + "/rest/v1/rpc/" + method)
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .post(args.toString().toRequestBody(json)).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(raw).optString("message") }
                    .getOrNull().orEmpty()
                error(detail.ifBlank { "Poll request failed: " + response.code })
            }
            raw
        }
    }
    suspend fun isSupported(): Boolean {
        return runCatching {
            // The genuine server responds with BLINK_CHAT_POLL_UNAVAILABLE.
            // Missing RPCs, network errors and denied access never enable voting.
            call("blink_chat_poll_results",
                JSONObject().put("p_message_id", UUID.randomUUID().toString()))
            true
        }.fold(
            onSuccess = { it },
            onFailure = {
                it.message.orEmpty().contains("BLINK_CHAT_POLL_UNAVAILABLE")
            }
        )
    }
    suspend fun attach(id: String, question: String, options: List<String>): Result<Unit> =
        runCatching {
            require(options.size in 2..8)
            call("blink_chat_attach_poll", JSONObject().put("p_message_id", id)
                .put("p_question", question)
                .put("p_options", JSONArray(options)))
            Unit
        }
    suspend fun results(id: String): Result<JSONObject> = runCatching {
        JSONObject(call("blink_chat_poll_results", JSONObject().put("p_message_id", id)))
    }
    suspend fun vote(id: String, option: Int): Result<JSONObject> = runCatching {
        call("blink_chat_vote_poll", JSONObject()
            .put("p_message_id", id).put("p_option", option))
        results(id).getOrThrow()
    }
}
