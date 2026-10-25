package com.example.data.repository

import com.blinkng.shared.BlinkCommunityPolicy
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
            ?: throw IllegalStateException("Please sign in to use Communities.")
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
                throw IllegalStateException(message.ifBlank { "Community request failed (HTTP " + response.code + ")." })
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
            ?: error("Sign in before creating a community.")
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
            ?: error("Community was not confirmed by Supabase.")
        return parse(row)
    }

    private suspend fun rpc(function: String, body: JSONObject): String =
        request("rpc/" + function, method = "POST", payload = body).trim().trim('"')

    suspend fun join(communityId: String): String {
        require(runCatching { UUID.fromString(communityId) }.isSuccess) { "Invalid community." }
        return rpc("blink_community_join", JSONObject().put("p_community_id", communityId))
    }

    suspend fun invite(communityId: String, memberId: String): String =
        rpc("blink_community_invite",
            JSONObject().put("p_community_id", communityId).put("p_user_id", memberId))

    suspend fun moderate(communityId: String, memberId: String, action: String): Boolean {
        require(action in setOf("approve", "ban", "remove", "promote", "demote"))
        return rpc("blink_community_moderate",
            JSONObject().put("p_community_id", communityId)
                .put("p_target", memberId).put("p_action", action)) == "true"
    }

    suspend fun leave(communityId: String): Boolean =
        rpc("blink_community_leave", JSONObject().put("p_community_id", communityId)) == "true"
}
