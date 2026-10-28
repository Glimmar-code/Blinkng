package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Authenticated RPC client; contest rules and vote counts live on the server. */
class BlinkContestService {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(method: String, body: JSONObject = JSONObject()): String =
        withContext(Dispatchers.IO) {
            val auth = SupabaseService()
            if (!auth.restoreSession()) error("Sign in to use BLINK Contest.")
            fun request(): Request {
                val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: error("Your session has expired. Sign in again.")
                return Request.Builder()
                    .url(SupabaseConfig.url.trimEnd('/') + "/rest/v1/rpc/" + method)
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .post(body.toString().toRequestBody(jsonType)).build()
            }
            var response = http.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!auth.refreshSession()) error("Sign in again to continue.")
                response = http.newCall(request()).execute()
            }
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val msg = runCatching { JSONObject(raw).optString("message") }
                        .getOrNull().orEmpty().ifBlank { "Contest request failed (" + it.code + ")." }
                    error(msg.replace('_', ' '))
                }
                raw
            }
        }
    suspend fun contests(): Result<JSONArray> = runCatching { JSONArray(call("list_blink_contests")) }
    suspend fun detail(id: String): Result<JSONObject> = runCatching {
        JSONObject(call("get_blink_contest", JSONObject().put("p_contest_id", id)))
    }
    suspend fun candidate(contestId: String, entryId: String): Result<JSONObject> = runCatching {
        JSONObject(call("get_blink_contest_candidate", JSONObject()
            .put("p_contest_id", contestId).put("p_entry_id", entryId)))
    }
    suspend fun apply(id: String, statement: String, mediaUrl: String): Result<JSONObject> = runCatching {
        JSONObject(call("apply_blink_contest", JSONObject().put("p_contest_id", id)
            .put("p_statement", statement.trim())
            .put("p_media_url", mediaUrl.trim().takeIf { it.isNotEmpty() } ?: JSONObject.NULL)))
    }
    suspend fun vote(id: String, entryId: String): Result<JSONObject> = runCatching {
        JSONObject(call("vote_blink_contest", JSONObject().put("p_contest_id", id)
            .put("p_entry_id", entryId)))
    }
    suspend fun adminContests(): Result<JSONArray> = runCatching {
        JSONArray(call("admin_list_blink_contests"))
    }
    suspend fun adminEntries(id: String): Result<JSONArray> = runCatching {
        JSONArray(call("admin_list_blink_contest_entries", JSONObject().put("p_contest_id", id)))
    }
    suspend fun adminSave(data: JSONObject, id: String? = null): Result<JSONObject> = runCatching {
        JSONObject(call("admin_save_blink_contest", JSONObject()
            .put("p_id", id ?: JSONObject.NULL).put("p_data", data)))
    }
    suspend fun adminTransition(id: String, action: String): Result<JSONObject> = runCatching {
        JSONObject(call("admin_transition_blink_contest", JSONObject()
            .put("p_contest_id", id).put("p_action", action)))
    }
    suspend fun adminReview(entryId: String, action: String): Result<JSONObject> = runCatching {
        JSONObject(call("admin_review_blink_contest_entry", JSONObject()
            .put("p_entry_id", entryId).put("p_action", action)))
    }
}
