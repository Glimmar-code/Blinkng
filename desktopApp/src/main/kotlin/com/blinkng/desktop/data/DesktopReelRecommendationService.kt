package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Windows-side route for the same Reel retention telemetry used by Android.
 *
 * The current desktop Reels screen does not yet host a native video player, so
 * this service deliberately does not invent watch-time samples from card
 * exposure. A desktop player can call this route with real playback metrics as
 * soon as playback is available, while ranking remains shared in Supabase.
 */
class DesktopReelRecommendationService {
    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val baseUrl = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_URL") ?: System.getenv("VITE_SUPABASE_URL"),
        BlinkBackendDefaults.SUPABASE_URL,
    ).trimEnd('/')

    private val anonKey = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_ANON_KEY") ?: System.getenv("VITE_SUPABASE_ANON_KEY"),
        BlinkBackendDefaults.SUPABASE_ANON_KEY,
    )

    suspend fun record(
        session: DesktopSession,
        postId: String,
        watchedMs: Long,
        durationMs: Long,
        completed: Boolean,
        rewatched: Boolean,
        playbackSessionId: UUID = UUID.randomUUID(),
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanPostId = postId.trim()
        if (cleanPostId.isBlank() || watchedMs < 250L || durationMs < 1_000L) {
            return@withContext false
        }

        val body = JSONObject()
            .put("p_post_id", cleanPostId)
            .put("p_watched_ms", watchedMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            .put("p_duration_ms", durationMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
            .put("p_session_id", playbackSessionId.toString())
            .put("p_completed", completed)
            .put("p_rewatched", rewatched)
            .toString()

        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/record_reel_engagement")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        runCatching {
            http.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

/** Convenient route from the existing desktop client once real player metrics exist. */
suspend fun DesktopSupabaseClient.recordReelEngagement(
    postId: String,
    watchedMs: Long,
    durationMs: Long,
    completed: Boolean,
    rewatched: Boolean,
): Boolean {
    val active = session ?: return false
    return DesktopReelRecommendationService().record(
        session = active,
        postId = postId,
        watchedMs = watchedMs,
        durationMs = durationMs,
        completed = completed,
        rewatched = rewatched,
    )
}

/** Windows consumes the same server-authoritative featured Reel cohort as Android. */
data class DesktopFeaturedReel(
    val id: String,
    val author: String,
    val username: String,
    val avatarUrl: String,
    val coverUrl: String,
    val caption: String,
    val videoUrl: String,
    val viewsInWindow: Long,
)

suspend fun DesktopSupabaseClient.featuredReels12h(): List<DesktopFeaturedReel> =
    withContext(Dispatchers.IO) {
        val token = session?.accessToken ?: return@withContext emptyList()
        val baseUrl = BlinkBackendDefaults.resolve(
            System.getenv("SUPABASE_URL") ?: System.getenv("VITE_SUPABASE_URL"),
            BlinkBackendDefaults.SUPABASE_URL
        ).trimEnd('/')
        val apiKey = BlinkBackendDefaults.resolve(
            System.getenv("SUPABASE_ANON_KEY") ?: System.getenv("VITE_SUPABASE_ANON_KEY"),
            BlinkBackendDefaults.SUPABASE_ANON_KEY
        )
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/blink_featured_reels_12h")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get().build()
        runCatching {
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<DesktopFeaturedReel>()
                val rows = JSONArray(response.body?.string().orEmpty())
                (0 until minOf(5, rows.length())).mapNotNull { index ->
                    val row = rows.optJSONObject(index) ?: return@mapNotNull null
                    val id = row.optString("reel_id")
                    if (id.isBlank()) return@mapNotNull null
                    DesktopFeaturedReel(
                        id = id,
                        author = row.optString("display_name"),
                        username = row.optString("username"),
                        avatarUrl = row.optString("avatar_url"),
                        coverUrl = row.optString("cover_url"),
                        caption = row.optString("reel_caption"),
                        videoUrl = row.optString("video_url"),
                        viewsInWindow = row.optLong("window_views"),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
