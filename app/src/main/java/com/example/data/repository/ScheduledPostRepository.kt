package com.example.data.repository

import com.example.data.models.FeedPost
import com.example.data.models.PollOption
import com.example.data.models.PostPoll
import com.example.data.models.ScheduledPost
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ScheduledPostRepository {
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun schedule(post: FeedPost, scheduledTimeMillis: Long): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("faculty", post.facultyTag)
            put("text", post.text)
            put("video_url", post.videoUrl ?: JSONObject.NULL)
            put("images", JSONArray(post.images))
            put("tags", JSONArray(post.tags))
            put("is_reel", post.isReel || !post.videoUrl.isNullOrBlank())
            put("audience", post.audience)
            put("category", post.category)
            put("location", post.location ?: JSONObject.NULL)
            put("link_url", post.linkUrl ?: JSONObject.NULL)
            put("allow_comments", post.allowComments)
            put("hide_likes", post.hideLikes)
            put("is_pinned", post.isPinned)
            put("is_disappearing", post.isDisappearing)
            put("audio_title", post.audioTitle ?: JSONObject.NULL)
            put("alt_text", post.altText ?: JSONObject.NULL)
            post.poll?.let { poll ->
                put(
                    "poll",
                    JSONObject()
                        .put("question", poll.question)
                        .put(
                            "options",
                            JSONArray().apply {
                                poll.options.forEach { option ->
                                    put(JSONObject().put("text", option.text))
                                }
                            }
                        )
                )
            }
        }
        rpc(
            "schedule_feed_post",
            JSONObject()
                .put("p_payload", payload)
                .put("p_scheduled_for", Instant.ofEpochMilli(scheduledTimeMillis).toString())
        ).trim().removeSurrounding("\"")
    }

    suspend fun fetchMine(): List<ScheduledPost> = withContext(Dispatchers.IO) {
        val token = SupabaseService.accessToken() ?: return@withContext emptyList()
        val request = Request.Builder()
            .url(
                "${SupabaseConfig.url.trimEnd('/')}/rest/v1/scheduled_feed_posts" +
                    "?select=id,payload,scheduled_for,status&status=in.(pending,failed)&order=scheduled_for.asc&limit=100"
            )
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful || raw.isBlank()) return@withContext emptyList()
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    val scheduledIso = row.optString("scheduled_for")
                    val millis = runCatching { Instant.parse(scheduledIso).toEpochMilli() }.getOrDefault(0L)
                    add(
                        ScheduledPost(
                            id = row.optString("id"),
                            post = parsePayload(row.optJSONObject("payload") ?: JSONObject()),
                            scheduledTimeMillis = millis,
                            scheduledTimeFormatted = formatTime(millis)
                        )
                    )
                }
            }
        }
    }

    suspend fun cancel(id: String): Boolean =
        rpc("cancel_scheduled_feed_post", JSONObject().put("p_schedule_id", id)).isNotBlank()

    suspend fun publishNow(id: String): Boolean =
        rpc("publish_scheduled_feed_post_now", JSONObject().put("p_schedule_id", id)).isNotBlank()

    private fun parsePayload(payload: JSONObject): FeedPost {
        val images = payload.optJSONArray("images").toStrings()
        val tags = payload.optJSONArray("tags").toStrings()
        val pollObject = payload.optJSONObject("poll")
        val poll = pollObject?.let { p ->
            PostPoll(
                question = p.optString("question"),
                options = p.optJSONArray("options").toObjects().mapIndexed { index, o ->
                    PollOption(id = "scheduled_$index", text = o.optString("text"))
                }
            )
        }
        return FeedPost(
            id = "scheduled_preview",
            author = "you",
            authorAvatar = "",
            facultyTag = payload.optString("faculty"),
            timeAgo = "Scheduled",
            text = payload.optString("text"),
            images = images,
            likes = 0,
            commentsCount = 0,
            sharesCount = 0,
            isReel = payload.optBoolean("is_reel", false),
            videoUrl = payload.optString("video_url").takeIf { it.isNotBlank() && it != "null" },
            tags = tags,
            poll = poll,
            audience = payload.optString("audience", "Everyone"),
            category = payload.optString("category", "Campus Life"),
            location = payload.optString("location").takeIf { it.isNotBlank() && it != "null" },
            linkUrl = payload.optString("link_url").takeIf { it.isNotBlank() && it != "null" },
            allowComments = payload.optBoolean("allow_comments", true),
            hideLikes = payload.optBoolean("hide_likes", false),
            isPinned = payload.optBoolean("is_pinned", false),
            isDisappearing = payload.optBoolean("is_disappearing", false),
            audioTitle = payload.optString("audio_title").takeIf { it.isNotBlank() && it != "null" },
            altText = payload.optString("alt_text").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private suspend fun rpc(name: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val token = SupabaseService.accessToken() ?: throw IllegalStateException("Please sign in again.")
        val request = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$name")
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "$name failed (${response.code})." })
            }
            raw
        }
    }

    private fun formatTime(millis: Long): String = if (millis <= 0L) "Scheduled" else {
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a"))
    }

    private fun JSONArray?.toStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun JSONArray?.toObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) optJSONObject(i)?.let(::add)
        }
    }
}
