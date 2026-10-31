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
                    val msg = contestResult { JSONObject(raw).optString("message") }
                        .getOrNull().orEmpty().ifBlank { "Contest request failed (" + it.code + ")." }
                    if (msg.contains("blink_contest_entry_media_required"))
                        error("A photo or video is required to apply for a contest.")
                    if (msg.contains("PRIVATE_CONTEST_MEDIA_REQUIRED"))
                        error("Choose and upload your own photo or video before applying.")
                    error(msg.replace('_', ' '))
                }
                raw
            }
        }
    /** Reveal pending entry media only after the Edge Function verifies the admin JWT. */
    suspend fun adminMediaPreview(mediaUrl: String): Result<String> = contestResult {
        if (!mediaUrl.contains("/functions/v1/blink-contest-private-media/")) {
            mediaUrl
        } else withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) error("Sign in to view private contest media.")
            val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                ?: error("Sign in to view private contest media.")
            val url = mediaUrl + if ('?' in mediaUrl) "&format=json" else "?format=json"
            http.newCall(Request.Builder().url(url)
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer " + token).get().build()).execute().use { response ->
                if (!response.isSuccessful) error("Private contest media is not available to this account.")
                JSONObject(response.body?.string().orEmpty()).getString("url")
            }
        }
    }

    suspend fun contests(): Result<JSONArray> = contestResult { JSONArray(call("list_blink_contests")) }
    suspend fun detail(id: String): Result<JSONObject> = contestResult {
        JSONObject(call("get_blink_contest", JSONObject().put("p_contest_id", id)))
    }
    /** Bounded server-side search over approved contestants, including ranks below #20. */
    suspend fun searchCandidates(
        contestId: String, query: String, offset: Int, limit: Int = 20
    ): Result<JSONObject> = contestResult {
        JSONObject(call("search_blink_contest_candidates", JSONObject()
            .put("p_contest_id", contestId)
            .put("p_query", query.trim().take(60))
            .put("p_limit", limit.coerceIn(1, 30))
            .put("p_offset", offset.coerceIn(0, 10000))))
    }

    suspend fun candidate(contestId: String, entryId: String): Result<JSONObject> = contestResult {
        JSONObject(call("get_blink_contest_candidate", JSONObject()
            .put("p_contest_id", contestId).put("p_entry_id", entryId)))
    }
    suspend fun apply(id: String, statement: String, mediaUrl: String): Result<JSONObject> = contestResult {
        JSONObject(call("apply_blink_contest", JSONObject().put("p_contest_id", id)
            .put("p_statement", statement.trim())
            .put("p_media_url", mediaUrl.trim().takeIf { it.isNotEmpty() } ?: JSONObject.NULL)))
    }
    suspend fun vote(id: String, entryId: String): Result<JSONObject> = contestResult {
        JSONObject(call("vote_blink_contest", JSONObject().put("p_contest_id", id)
            .put("p_entry_id", entryId)))
    }
    suspend fun adminContests(): Result<JSONArray> = contestResult {
        JSONArray(call("admin_list_blink_contests"))
    }
    suspend fun adminEntries(id: String): Result<JSONArray> = contestResult {
        JSONArray(call("admin_list_blink_contest_entries", JSONObject().put("p_contest_id", id)))
    }
    suspend fun adminSave(data: JSONObject, id: String? = null): Result<JSONObject> = contestResult {
        JSONObject(call("admin_save_blink_contest", JSONObject()
            .put("p_id", id ?: JSONObject.NULL).put("p_data", data)))
    }
    suspend fun adminTransition(id: String, action: String): Result<JSONObject> = contestResult {
        JSONObject(call("admin_transition_blink_contest", JSONObject()
            .put("p_contest_id", id).put("p_action", action)))
    }
    suspend fun recordShare(contestId: String, entryId: String, channel: String): Result<JSONObject> = contestResult {
        JSONObject(call("record_blink_contest_share", JSONObject()
            .put("p_contest_id", contestId).put("p_entry_id", entryId)
            .put("p_channel", channel)))
    }
    suspend fun adminInsights(contestId: String): Result<JSONObject> = contestResult {
        JSONObject(call("admin_blink_contest_insights", JSONObject().put("p_contest_id", contestId)))
    }
    suspend fun adminVoteReview(id: String, action: String, reason: String): Result<JSONObject> = contestResult {
        JSONObject(call("admin_review_blink_contest_vote", JSONObject().put("p_vote_id", id)
            .put("p_action", action).put("p_reason", reason)))
    }
    suspend fun adminFinalize(contestId: String): Result<JSONObject> = contestResult {
        JSONObject(call("admin_finalize_blink_contest", JSONObject().put("p_contest_id", contestId)))
    }
    suspend fun adminPrize(id: String, status: String, receipt: String, note: String): Result<JSONObject> = contestResult {
        JSONObject(call("admin_record_blink_contest_prize", JSONObject().put("p_prize_id", id)
            .put("p_status", status)
            .put("p_reference", if (receipt.isBlank()) JSONObject.NULL else receipt.trim())
            .put("p_note", note.trim())))
    }
    suspend fun adminReview(entryId: String, action: String, reason: String): Result<JSONObject> = contestResult {
        JSONObject(call("admin_review_blink_contest_entry", JSONObject()
            .put("p_entry_id", entryId).put("p_action", action)
            .put("p_reason", reason.trim().take(300))))
    }
}

private suspend inline fun <T> contestResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}
