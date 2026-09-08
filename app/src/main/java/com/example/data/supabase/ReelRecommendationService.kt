package com.example.data.supabase

import android.util.Log
import com.example.auth.SupabaseSessionRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Sends one terminal playback sample for a full-screen Reel session.
 *
 * The database derives watch percentage and the skip threshold from the real
 * watched/duration values, while completion and rewatch are also supplied from
 * Media3 playback state so resumed Reels are measured correctly.
 */
class ReelRecommendationService {
    companion object {
        private const val TAG = "ReelRecommendation"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun recordReelEngagement(
        postId: String,
        watchedMs: Long,
        durationMs: Long,
        completed: Boolean,
        rewatched: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanPostId = postId.trim()
        if (cleanPostId.isBlank() || watchedMs < 250L || durationMs < 1_000L) {
            return@withContext false
        }

        val sessionId = UUID.randomUUID().toString()
        val body = JSONObject()
            .put("p_post_id", cleanPostId)
            .put("p_watched_ms", watchedMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            .put("p_duration_ms", durationMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
            .put("p_session_id", sessionId)
            .put("p_completed", completed)
            .put("p_rewatched", rewatched)
            .toString()

        var token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
            ?: return@withContext false

        var result = execute(token, body)
        if (result.code == 401) {
            val refreshToken = SupabaseService.refreshToken().orEmpty()
            val refreshed = SupabaseSessionRefresher.refresh(refreshToken).getOrNull()
            if (refreshed != null) {
                SupabaseService.saveSession(refreshed.accessToken, refreshed.refreshToken)
                token = refreshed.accessToken
                result = execute(token, body)
            }
        }

        if (!result.success) {
            Log.w(TAG, "REEL_ENGAGEMENT failed status=${result.code} body=${result.body}")
        }
        result.success
    }

    private fun execute(token: String, body: String): RpcResult {
        return try {
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/record_reel_engagement")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                RpcResult(
                    success = response.isSuccessful,
                    code = response.code,
                    body = response.body?.string().orEmpty().take(500)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "REEL_ENGAGEMENT exception", e)
            RpcResult(false, -1, e.message.orEmpty())
        }
    }

    private data class RpcResult(
        val success: Boolean,
        val code: Int,
        val body: String
    )
}
