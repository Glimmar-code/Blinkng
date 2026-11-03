package com.example.data.supabase

import com.example.auth.SupabaseSessionRefresher
import com.example.data.models.FeedPost
import com.example.data.models.VerificationBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Data returned by the server-authoritative completed twelve-hour discovery window. */
data class BlinkFeaturedReel(
    val reel: FeedPost,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val coverUrl: String,
    val caption: String,
    val windowViews: Long,
    val nextRefresh: String,
)

class BlinkFeaturedReelsService {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): List<BlinkFeaturedReel> = withContext(Dispatchers.IO) {
        var token = SupabaseService.accessToken()?.takeIf(String::isNotBlank)
            ?: return@withContext emptyList()
        var response = request(token)
        if (response.first == 401) {
            val refreshed = SupabaseSessionRefresher.refresh(
                SupabaseService.refreshToken().orEmpty()
            ).getOrNull()
            if (refreshed != null) {
                SupabaseService.saveSession(refreshed.accessToken, refreshed.refreshToken)
                token = refreshed.accessToken
                response = request(token)
            }
        }
        if (response.first !in 200..299) return@withContext emptyList()
        val rows = runCatching { JSONArray(response.second) }.getOrNull()
            ?: return@withContext emptyList()
        buildList {
            for (index in 0 until minOf(rows.length(), 5)) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("reel_id")
                val video = row.optString("video_url")
                if (id.isBlank() || video.isBlank()) continue
                val username = row.optString("username")
                val badge = runCatching {
                    VerificationBadge.valueOf(row.optString("verification_badge").uppercase())
                }.getOrDefault(VerificationBadge.NONE)
                val caption = row.optString("reel_caption")
                val avatar = row.optString("avatar_url")
                val name = row.optString("display_name").ifBlank { username }
                add(
                    BlinkFeaturedReel(
                        reel = FeedPost(
                            id = id,
                            author = name,
                            authorAvatar = avatar,
                            timeAgo = "Trending",
                            text = caption,
                            likes = row.optInt("likes", 0),
                            commentsCount = row.optInt("comments", 0),
                            sharesCount = row.optInt("shares", 0),
                            viewsCount = row.optInt("total_views", 0),
                            isVerified = row.optBoolean("verified", false),
                            verificationBadge = badge,
                            isReel = true,
                            videoUrl = video,
                            images = row.optString("cover_url").takeIf(String::isNotBlank)
                                ?.let(::listOf).orEmpty(),
                            authorUsername = username,
                            authorProfileId = row.optString("author_id"),
                            createdAt = row.optString("created_at"),
                        ),
                        username = username,
                        displayName = name,
                        avatarUrl = avatar,
                        coverUrl = row.optString("cover_url"),
                        caption = caption,
                        windowViews = row.optLong("window_views"),
                        nextRefresh = row.optString("next_refresh"),
                    )
                )
            }
        }
    }

    private fun request(token: String): Pair<Int, String> =
        runCatching {
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/blink_featured_reels_12h")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get().build()
            http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        }.getOrDefault(-1 to "")
}
