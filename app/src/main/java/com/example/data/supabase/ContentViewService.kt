package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ContentViewResult(
    val accepted: Boolean,
    val duplicate: Boolean,
    val capReached: Boolean,
    val viewCount: Int,
    val userViewCount: Int,
    val contentType: String,
    val eventId: String
)

/**
 * Thin client for the idempotent Supabase record_content_view RPC.
 * The server derives post-vs-reel, verification weight, and contribution caps; the Android
 * client never sends an aggregate total or a verification multiplier.
 */
object ContentViewService {
    private const val TAG = "ContentViewService"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val authoritativeCountClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun record(postId: String, eventId: String): ContentViewResult? =
        withContext(Dispatchers.IO) {
            if (postId.isBlank() || eventId.isBlank()) return@withContext null

            for (attempt in 0..1) {
                val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val payload = JSONObject().apply {
                    put("p_post_id", postId)
                    put("p_event_id", eventId)
                }
                val request = Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/record_content_view")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = runCatching { client.newCall(request).execute() }.getOrNull()
                    ?: return@withContext null
                val status = response.code
                val raw = response.body?.string().orEmpty().trim()
                response.close()

                if (status == 401 && attempt == 0) {
                    val restored = runCatching { SupabaseService().restoreSession() }.getOrDefault(false)
                    if (restored) continue
                }
                if (status !in 200..299) {
                    Log.w(TAG, "record_content_view failed status=$status body=${raw.take(300)}")
                    return@withContext null
                }

                val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrNull()
                    ?: return@withContext null
                return@withContext ContentViewResult(
                    accepted = json.optBoolean("accepted", false),
                    duplicate = json.optBoolean("duplicate", false),
                    capReached = json.optBoolean("cap_reached", false),
                    viewCount = json.optInt("view_count", 0).coerceAtLeast(0),
                    userViewCount = json.optInt("user_view_count", 0).coerceIn(0, 100),
                    contentType = json.optString("content_type", "post"),
                    eventId = json.optString("event_id", eventId).ifBlank { eventId }
                )
            }
            null
        }

    /**
     * Presentation-only authoritative refetch used once when a 30-second display window closes.
     * Durable view writes happen through [record] immediately; this request never delays them.
     */
    suspend fun fetchAuthoritativeCount(postId: String): Int? = withContext(Dispatchers.IO) {
        if (postId.isBlank()) return@withContext null

        for (attempt in 0..1) {
            val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val request = Request.Builder()
                .url(
                    "${SupabaseConfig.url.trimEnd('/')}/rest/v1/feed_posts" +
                        "?select=view_count&id=eq.$postId&limit=1"
                )
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .get()
                .build()

            val response = runCatching {
                authoritativeCountClient.newCall(request).execute()
            }.getOrNull() ?: return@withContext null
            val status = response.code
            val raw = response.body?.string().orEmpty().trim()
            response.close()

            if (status == 401 && attempt == 0) {
                val restored = runCatching { SupabaseService().restoreSession() }.getOrDefault(false)
                if (restored) continue
            }
            if (status !in 200..299) {
                Log.w(TAG, "authoritative view refetch failed status=$status body=${raw.take(300)}")
                return@withContext null
            }

            val rows = runCatching { JSONArray(raw.ifBlank { "[]" }) }.getOrNull()
                ?: return@withContext null
            if (rows.length() == 0) return@withContext null
            return@withContext rows.optJSONObject(0)
                ?.optInt("view_count", 0)
                ?.coerceAtLeast(0)
        }
        null
    }
}
