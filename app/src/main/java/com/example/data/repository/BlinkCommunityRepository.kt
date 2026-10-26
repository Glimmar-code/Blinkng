package com.example.data.repository

import com.blinkng.shared.BlinkCommunityPolicy
import com.blinkng.shared.BlinkChannelPolicy
import com.blinkng.shared.BlinkChannelUpdate
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
import java.util.UUID
import java.util.concurrent.TimeUnit

data class BlinkCommunity(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String,
    val university: String,
    val category: String,
    val visibility: String,
    val coverUrl: String?,
    val rules: List<String> = emptyList()
)

data class BlinkCommunityMember(
    val id: String, val username: String, val displayName: String,
    val role: String, val status: String
)

/** No fake membership, count, owner, or cover data; all reads are RLS-backed. */
class BlinkCommunityRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun request(
        path: String,
        method: String = "GET",
        payload: JSONObject? = null,
        represent: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val token = SupabaseService.accessToken()
            ?: throw IllegalStateException("Please sign in to use Channels.")
        val builder = Request.Builder()
            .url(SupabaseConfig.url.trimEnd('/') + "/rest/v1/" + path)
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
        if (represent) builder.header("Prefer", "return=representation")
        if (method == "POST") {
            builder.post((payload ?: JSONObject()).toString().toRequestBody(mediaType))
        }
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optString("message") }
                    .getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "Channel request failed (HTTP " + response.code + ")." })
            }
            raw
        }
    }

    private fun parse(row: JSONObject): BlinkCommunity {
        val rulesJson = row.optJSONArray("rules") ?: JSONArray()
        return BlinkCommunity(
            id = row.optString("id"),
            ownerId = row.optString("owner_id"),
            name = row.optString("name"),
            description = row.optString("description"),
            university = row.optString("university"),
            category = row.optString("category"),
            visibility = row.optString("visibility"),
            coverUrl = row.optString("cover_url").takeIf { it.isNotBlank() && it != "null" },
            rules = (0 until rulesJson.length()).mapNotNull {
                rulesJson.optString(it).takeIf(String::isNotBlank)
            }
        )
    }

    suspend fun discover(limit: Int = 50): List<BlinkCommunity> {
        val raw = request(
            "blink_communities?select=id,owner_id,name,description,university,category,visibility,cover_url,rules" +
                "&order=created_at.desc&limit=" + limit.coerceIn(1, 100)
        )
        val arr = JSONArray(raw.ifBlank { "[]" })
        return (0 until arr.length()).mapNotNull { index -> arr.optJSONObject(index)?.let(::parse) }
    }

    suspend fun create(
        name: String, description: String, university: String, category: String,
        visibility: BlinkCommunityPolicy.Visibility, rules: List<String>,
        coverUrl: String? = null
    ): BlinkCommunity {
        val owner = SupabaseService().getCurrentUserId()
            ?: error("Sign in before creating a channel.")
        val body = JSONObject()
            .put("owner_id", owner)
            .put("name", BlinkCommunityPolicy.validatedName(name))
            .put("description", BlinkCommunityPolicy.validatedDescription(description))
            .put("university", BlinkCommunityPolicy.validatedField(university, "University"))
            .put("category", BlinkCommunityPolicy.validatedField(category, "Category", 80))
            .put("visibility", visibility.wire)
            .put("rules", JSONArray(BlinkCommunityPolicy.validatedRules(rules)))
        if (!coverUrl.isNullOrBlank()) body.put("cover_url", coverUrl)
        val raw = request("blink_communities?select=*", "POST", body, represent = true)
        val row = JSONArray(raw).optJSONObject(0)
            ?: error("Channel was not confirmed by Supabase.")
        return parse(row)
    }

    private suspend fun rpc(function: String, body: JSONObject): String =
        request("rpc/" + function, method = "POST", payload = body).trim().trim('"')

    suspend fun join(communityId: String): String {
        require(runCatching { UUID.fromString(communityId) }.isSuccess) { "Invalid community." }
        return rpc("blink_community_join", JSONObject().put("p_community_id", communityId))
    }

    suspend fun requestJoin(communityId: String): String =
        rpc("blink_community_request_join", JSONObject().put("p_community_id", communityId))

    suspend fun invite(communityId: String, memberId: String): String =
        rpc("blink_community_invite",
            JSONObject().put("p_community_id", communityId).put("p_user_id", memberId))

    suspend fun memberRoster(communityId: String): List<BlinkCommunityMember> {
        val raw = request(
            "rpc/blink_community_members_page",
            "POST",
            JSONObject().put("p_community_id", communityId).put("p_limit", 100)
        )
        val rows = JSONArray(raw.ifBlank { "[]" })
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { person ->
                BlinkCommunityMember(
                    id = person.optString("user_id"),
                    username = person.optString("username"),
                    displayName = person.optString("full_name").ifBlank {
                        person.optString("username")
                    },
                    role = person.optString("role"),
                    status = person.optString("status")
                )
            }
        }
    }

    suspend fun moderate(communityId: String, memberId: String, action: String): Boolean {
        require(action in setOf("approve", "ban", "remove", "promote", "demote"))
        return rpc("blink_community_moderate",
            JSONObject().put("p_community_id", communityId)
                .put("p_target", memberId).put("p_action", action)) == "true"
    }


    /** The membership query is scoped to the current user by the backend RLS. */
    suspend fun myChannelRole(channelId: String): String =
        rpc("blink_channel_my_role", JSONObject().put("p_channel_id", channelId))

    suspend fun channelFollowerCount(channelId: String): Int =
        rpc("blink_channel_follower_count", JSONObject().put("p_channel_id", channelId))
            .toIntOrNull() ?: 0

    suspend fun channelUpdates(channelId: String): List<BlinkChannelUpdate> {
        val raw = request(
            "rpc/blink_channel_updates_page", "POST",
            JSONObject().put("p_channel_id", channelId).put("p_limit", 100)
        )
        val rows = JSONArray(raw.ifBlank { "[]" })
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val reactions = row.optJSONObject("reactions") ?: JSONObject()
            val counts = mutableMapOf<String, Int>()
            reactions.keys().forEach { key -> counts[key] = reactions.optInt(key) }
            BlinkChannelUpdate(
                id = row.optString("id"),
                channelId = row.optString("channel_id"),
                authorId = row.optString("author_id"),
                body = row.optString("body"),
                createdAt = row.optString("created_at"),
                reactions = counts,
                myReaction = row.optString("my_reaction").takeIf { it.isNotBlank() && it != "null" },
                mediaKind = row.optString("media_kind").ifBlank { "text" },
                mediaPath = row.optString("media_path").takeIf { it.isNotBlank() && it != "null" },
                pollQuestion = row.optString("poll_question").takeIf { it.isNotBlank() && it != "null" },
                pollOptions = row.optJSONArray("poll_options")?.let { p ->
                    (0 until p.length()).map { index -> p.optString(index) }
                }.orEmpty(),
                pollCounts = row.optJSONObject("poll_counts")?.let { obj ->
                    buildMap<Int, Int> {
                        obj.keys().forEach { key -> key.toIntOrNull()?.let { put(it, obj.optInt(key)) } }
                    }
                }.orEmpty(),
                myVote = if (row.isNull("my_vote")) null else row.optInt("my_vote"),
                pinned = row.optBoolean("pinned", false),
                viewCount = row.optLong("view_count", 0)
            )
        }.reversed()
    }

    suspend fun publishChannelUpdate(channelId: String, body: String): String =
        rpc("blink_channel_publish",
            JSONObject().put("p_channel_id", channelId)
                .put("p_body", BlinkChannelPolicy.validatedUpdate(body)))

    suspend fun reactToChannelUpdate(updateId: String, emoji: String): String =
        rpc("blink_channel_react",
            JSONObject().put("p_update_id", updateId)
                .put("p_emoji", BlinkChannelPolicy.validatedReaction(emoji)))


    suspend fun publishRichUpdate(
        channelId: String,
        body: String,
        kind: String = "text",
        mediaPath: String? = null,
        pollQuestion: String? = null,
        options: List<String> = emptyList(),
        scheduledAt: String? = null,
        forwardedFrom: String? = null
    ): String {
        BlinkChannelPolicy.validatedMediaKind(kind)
        val poll = if (kind == "poll")
            BlinkChannelPolicy.validatedPoll(pollQuestion.orEmpty(), options) else null
        val json = JSONObject()
            .put("p_channel_id", channelId)
            .put("p_body", body.trim().take(BlinkChannelPolicy.MAX_UPDATE_LENGTH))
            .put("p_media_kind", kind)
            .put("p_media_path", mediaPath ?: JSONObject.NULL)
            .put("p_poll_question", poll?.first ?: JSONObject.NULL)
            .put("p_poll_options", poll?.second?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("p_scheduled_at", scheduledAt ?: JSONObject.NULL)
            .put("p_forwarded_from", forwardedFrom ?: JSONObject.NULL)
        return rpc("blink_channel_publish_rich", json)
    }

    suspend fun voteChannelPoll(updateId: String, option: Int): Boolean =
        rpc("blink_channel_poll_vote", JSONObject()
            .put("p_update_id", updateId).put("p_option", option)) == "true"

    suspend fun recordChannelView(updateId: String): Boolean =
        rpc("blink_channel_view", JSONObject().put("p_update_id", updateId)) == "true"

    suspend fun reportChannelUpdate(updateId: String, reason: String): Boolean =
        rpc("blink_channel_report", JSONObject().put("p_update_id", updateId)
            .put("p_reason", reason.trim().take(280))) == "true"

    suspend fun moderateChannelUpdate(updateId: String, action: String): Boolean {
        require(action in setOf("pin", "unpin", "remove"))
        return rpc("blink_channel_manage_update", JSONObject()
            .put("p_update_id", updateId).put("p_action", action)) == "true"
    }

    suspend fun channelStats(channelId: String): JSONObject {
        val raw = request("rpc/blink_channel_statistics", "POST",
            JSONObject().put("p_channel_id", channelId))
        return JSONObject(raw.ifBlank { "{}" })
    }

    suspend fun muteChannel(channelId: String, muted: Boolean): Boolean =
        rpc("blink_channel_mute", JSONObject()
            .put("p_channel_id", channelId).put("p_muted", muted)) == "true"

    suspend fun isChannelMuted(channelId: String): Boolean =
        rpc("blink_channel_is_muted", JSONObject()
            .put("p_channel_id", channelId)) == "true"

    suspend fun leave(communityId: String): Boolean =
        rpc("blink_community_leave", JSONObject().put("p_community_id", communityId)) == "true"
}
