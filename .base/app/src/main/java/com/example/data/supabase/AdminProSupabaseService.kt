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

data class ProAdminFeature(
    val featureId: Int,
    val title: String,
    val category: String,
    val module: String,
    val routeKey: String,
    val targetType: String,
    val inputKind: String,
    val ownerOnly: Boolean,
    val enabled: Boolean,
    val reversible: Boolean,
    val description: String
)

data class ProAdminUser(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val avatarUrl: String?,
    val university: String,
    val faculty: String,
    val department: String,
    val verificationBadge: String,
    val coins: Long,
    val adminRole: String,
    val createdAt: String?,
    val lastSeen: String?
)

data class ProAdminPost(
    val id: String,
    val userId: String,
    val username: String,
    val fullName: String,
    val university: String,
    val caption: String,
    val text: String,
    val isReel: Boolean,
    val isActive: Boolean,
    val isFlagged: Boolean,
    val isPinned: Boolean,
    val isSponsored: Boolean,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val views: Long,
    val createdAt: String?
)

data class ProAdminHistoryItem(
    val id: String,
    val actorUsername: String,
    val action: String,
    val targetUsername: String?,
    val targetPostId: String?,
    val details: String,
    val createdAt: String,
    val reversed: Boolean,
    val reversedAt: String?,
    val canRevert: Boolean
)

data class ProAdminSearchBundle(
    val users: List<ProAdminUser>,
    val posts: List<ProAdminPost>,
    val universities: List<String>
)

class AdminProSupabaseService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    private suspend fun rpc(name: String, payload: JSONObject = JSONObject()): String =
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) {
                throw IllegalStateException("Your Blink session has expired. Please sign in again.")
            }

            fun request(): Request {
                val token = SupabaseService.accessToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("A signed-in Blink account is required.")
                return Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/$name")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }

            var response = client.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!session.refreshSession()) {
                    throw IllegalStateException("Your Blink session has expired. Please sign in again.")
                }
                response = client.newCall(request()).execute()
            }

            response.use {
                val body = it.body?.string().orEmpty().trim()
                if (!it.isSuccessful) {
                    throw IllegalStateException(parseError(body, "Admin request failed (${it.code})."))
                }
                body
            }
        }

    suspend fun fetchCapability(): Result<AdminCapability> = runCatching {
        val json = JSONObject(rpc("admin_get_capability"))
        AdminCapability(
            isAdmin = json.optBoolean("is_admin", false),
            isOwner = json.optBoolean("is_owner", false),
            role = json.optString("role", "none"),
            expiresAt = json.nullableString("expires_at")
        )
    }

    suspend fun fetchStats(): Result<AdminDashboardStats> = runCatching {
        val json = JSONObject(rpc("admin_dashboard_stats"))
        AdminDashboardStats(
            users = json.optInt("users", 0),
            verified = json.optInt("verified", 0),
            activeAdmins = json.optInt("active_admins", 0),
            posts = json.optInt("posts", 0),
            ownerPosts = json.optInt("owner_posts", 0)
        )
    }

    suspend fun fetchFeatures(): Result<List<ProAdminFeature>> = runCatching {
        val array = JSONArray(rpc("admin_list_features_v2"))
        buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    ProAdminFeature(
                        featureId = j.optInt("feature_id"),
                        title = j.optString("title"),
                        category = j.optString("category"),
                        module = j.optString("module"),
                        routeKey = j.optString("route_key"),
                        targetType = j.optString("target_type", "none"),
                        inputKind = j.optString("input_kind", "none"),
                        ownerOnly = j.optBoolean("owner_only", false),
                        enabled = j.optBoolean("enabled", true),
                        reversible = j.optBoolean("reversible", false),
                        description = j.optString("description")
                    )
                )
            }
        }
    }

    suspend fun searchUsers(query: String, limit: Int = 40): Result<List<ProAdminUser>> = runCatching {
        val array = JSONArray(
            rpc(
                "admin_search_users_v2",
                JSONObject().put("p_query", query.trim()).put("p_limit", limit.coerceIn(1, 100))
            )
        )
        parseUsers(array)
    }

    suspend fun searchPosts(query: String, limit: Int = 40): Result<List<ProAdminPost>> = runCatching {
        val array = JSONArray(
            rpc(
                "admin_search_posts_v2",
                JSONObject().put("p_query", query.trim()).put("p_limit", limit.coerceIn(1, 100))
            )
        )
        parsePosts(array)
    }

    suspend fun searchUniversities(query: String = "", limit: Int = 250): Result<List<String>> = runCatching {
        val array = JSONArray(
            rpc(
                "admin_search_universities_v2",
                JSONObject().put("p_query", query.trim()).put("p_limit", limit.coerceIn(1, 250))
            )
        )
        buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    suspend fun globalSearch(query: String): Result<ProAdminSearchBundle> = runCatching {
        val json = JSONObject(rpc("admin_global_search_v2", JSONObject().put("p_query", query.trim())))
        ProAdminSearchBundle(
            users = parseUsers(json.optJSONArray("users") ?: JSONArray()),
            posts = parsePosts(json.optJSONArray("posts") ?: JSONArray()),
            universities = buildList {
                val a = json.optJSONArray("universities") ?: JSONArray()
                for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        )
    }

    suspend fun executeFeature(
        featureId: Int,
        entityRef: String? = null,
        text: String? = null,
        amount: Long? = null,
        durationHours: Int? = null,
        options: JSONObject = JSONObject()
    ): Result<String> = runCatching {
        require(featureId in 1..700) { "Admin feature ID must be between 1 and 700." }
        val payload = JSONObject()
            .put("p_feature_id", featureId)
            .put("p_entity_ref", entityRef?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_text", text?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_amount", amount ?: JSONObject.NULL)
            .put("p_duration_hours", durationHours ?: JSONObject.NULL)
            .put("p_options", options)
        val raw = rpc("admin_execute_feature_v2", payload)
        runCatching { JSONObject(raw).toString(2) }.getOrElse { raw }
    }

    suspend fun fetchHistory(limit: Int = 100, offset: Int = 0): Result<List<ProAdminHistoryItem>> = runCatching {
        val array = JSONArray(
            rpc(
                "admin_history_v2",
                JSONObject().put("p_limit", limit.coerceIn(1, 250)).put("p_offset", offset.coerceAtLeast(0))
            )
        )
        buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    ProAdminHistoryItem(
                        id = j.optString("id"),
                        actorUsername = j.optString("actor_username", "Admin"),
                        action = j.optString("action"),
                        targetUsername = j.nullableString("target_username"),
                        targetPostId = j.nullableString("target_post_id"),
                        details = when (val d = j.opt("details")) {
                            is JSONObject -> d.toString()
                            else -> d?.toString().orEmpty()
                        },
                        createdAt = j.optString("created_at"),
                        reversed = j.optBoolean("reversed", false),
                        reversedAt = j.nullableString("reversed_at"),
                        canRevert = j.optBoolean("can_revert", false)
                    )
                )
            }
        }
    }

    suspend fun revertAction(actionId: String, note: String? = null): Result<String> = runCatching {
        val raw = rpc(
            "admin_revert_action_v2",
            JSONObject()
                .put("p_action_id", actionId)
                .put("p_note", note?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        )
        runCatching { JSONObject(raw).toString(2) }.getOrElse { raw }
    }

    private fun parseUsers(array: JSONArray): List<ProAdminUser> = buildList {
        for (i in 0 until array.length()) {
            val j = array.getJSONObject(i)
            add(
                ProAdminUser(
                    id = j.optString("id"),
                    username = j.optString("username"),
                    fullName = j.optString("full_name"),
                    email = j.optString("email"),
                    avatarUrl = j.nullableString("avatar_url"),
                    university = j.optString("university"),
                    faculty = j.optString("faculty"),
                    department = j.optString("department"),
                    verificationBadge = j.optString("verification_badge", "NONE"),
                    coins = j.optLong("coins", 0L),
                    adminRole = j.optString("admin_role", "none"),
                    createdAt = j.nullableString("created_at"),
                    lastSeen = j.nullableString("last_seen")
                )
            )
        }
    }

    private fun parsePosts(array: JSONArray): List<ProAdminPost> = buildList {
        for (i in 0 until array.length()) {
            val j = array.getJSONObject(i)
            add(
                ProAdminPost(
                    id = j.optString("id"),
                    userId = j.optString("user_id"),
                    username = j.optString("username"),
                    fullName = j.optString("full_name"),
                    university = j.optString("university"),
                    caption = j.optString("caption"),
                    text = j.optString("text"),
                    isReel = j.optBoolean("is_reel", false),
                    isActive = j.optBoolean("is_active", true),
                    isFlagged = j.optBoolean("is_flagged", false),
                    isPinned = j.optBoolean("is_pinned", false),
                    isSponsored = j.optBoolean("is_sponsored", false),
                    likes = j.optLong("like_count", 0),
                    comments = j.optLong("comment_count", 0),
                    shares = j.optLong("share_count", 0),
                    views = j.optLong("view_count", 0),
                    createdAt = j.nullableString("created_at")
                )
            )
        }
    }

    private fun parseError(body: String, fallback: String): String = runCatching {
        val json = JSONObject(body)
        json.optString("message").takeIf { it.isNotBlank() }
            ?: json.optString("error_description").takeIf { it.isNotBlank() }
            ?: fallback
    }.getOrDefault(fallback)

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && !it.equals("null", true) }
    }
}