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

data class AdminCapability(
    val isAdmin: Boolean = false,
    val isOwner: Boolean = false,
    val role: String = "none",
    val expiresAt: String? = null
)

data class AdminDashboardStats(
    val users: Int = 0,
    val verified: Int = 0,
    val activeAdmins: Int = 0,
    val posts: Int = 0,
    val ownerPosts: Int = 0
)

data class AdminUserSummary(
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String?,
    val university: String,
    val verificationBadge: String,
    val verificationExpiresAt: String?,
    val coins: Long,
    val adminRole: String,
    val adminExpiresAt: String?,
    val email: String = "",
    val faculty: String = "",
    val department: String = "",
    val createdAt: String? = null,
    val lastSeen: String? = null
)

data class AdminFeature(
    val featureId: Int,
    val title: String,
    val category: String,
    val ownerOnly: Boolean,
    val enabled: Boolean
)

data class AdminSection(
    val key: String,
    val title: String,
    val description: String,
    val sortOrder: Int,
    val showInSidebar: Boolean,
    val isTopAction: Boolean,
    val enabled: Boolean,
    val featureCount: Int
)

data class AdminFeatureV3(
    val featureId: Int,
    val title: String,
    val category: String,
    val module: String,
    val sectionKey: String,
    val routeKey: String,
    val targetType: String,
    val inputKind: String,
    val ownerOnly: Boolean,
    val enabled: Boolean,
    val reversible: Boolean,
    val description: String,
    val permissionKey: String,
    val riskLevel: String,
    val confirmationKind: String
)

data class AdminPostSummary(
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
    val likeCount: Long,
    val commentCount: Long,
    val shareCount: Long,
    val viewCount: Long,
    val createdAt: String?
)

data class AdminHistoryItem(
    val id: String,
    val actorId: String,
    val actorUsername: String,
    val action: String,
    val targetUserId: String?,
    val targetUsername: String?,
    val targetPostId: String?,
    val details: String,
    val createdAt: String?,
    val reversed: Boolean,
    val reversedAt: String?,
    val reversalNote: String?,
    val canRevert: Boolean
)

class AdminSupabaseService {
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

    suspend fun fetchSectionsV3(): Result<List<AdminSection>> = runCatching {
        val array = JSONArray(rpc("admin_list_sections_v3"))
        buildList {
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                add(
                    AdminSection(
                        key = json.optString("section_key"),
                        title = json.optString("title"),
                        description = json.optString("description"),
                        sortOrder = json.optInt("sort_order"),
                        showInSidebar = json.optBoolean("show_in_sidebar", true),
                        isTopAction = json.optBoolean("is_top_action", false),
                        enabled = json.optBoolean("enabled", true),
                        featureCount = json.optInt("feature_count", 0)
                    )
                )
            }
        }
    }

    suspend fun fetchFeaturesV3(
        sectionKey: String? = null,
        query: String = ""
    ): Result<List<AdminFeatureV3>> = runCatching {
        val payload = JSONObject()
            .put("p_section_key", sectionKey?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_query", query.trim())
        val array = JSONArray(rpc("admin_list_features_v3", payload))
        buildList {
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                add(
                    AdminFeatureV3(
                        featureId = json.optInt("feature_id"),
                        title = json.optString("title"),
                        category = json.optString("category"),
                        module = json.optString("module"),
                        sectionKey = json.optString("section_key"),
                        routeKey = json.optString("route_key"),
                        targetType = json.optString("target_type", "none"),
                        inputKind = json.optString("input_kind", "none"),
                        ownerOnly = json.optBoolean("owner_only", false),
                        enabled = json.optBoolean("enabled", true),
                        reversible = json.optBoolean("reversible", false),
                        description = json.optString("description"),
                        permissionKey = json.optString("permission_key"),
                        riskLevel = json.optString("risk_level", "low"),
                        confirmationKind = json.optString("confirmation_kind", "none")
                    )
                )
            }
        }
    }

    suspend fun executeFeatureV3(
        featureId: Int,
        entityRef: String? = null,
        text: String? = null,
        amount: Long? = null,
        durationHours: Int? = null,
        options: JSONObject = JSONObject()
    ): Result<JSONObject> = runCatching {
        require(featureId in 1..700) { "Feature ID must be between 1 and 700." }
        val payload = JSONObject()
            .put("p_feature_id", featureId)
            .put("p_entity_ref", entityRef?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_text", text?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_amount", amount ?: JSONObject.NULL)
            .put("p_duration_hours", durationHours ?: JSONObject.NULL)
            .put("p_options", options)
        JSONObject(rpc("admin_execute_feature_v3", payload))
    }

    suspend fun searchUsersV2(query: String, limit: Int = 40): Result<List<AdminUserSummary>> = runCatching {
        val payload = JSONObject()
            .put("p_query", query.trim())
            .put("p_limit", limit.coerceIn(1, 100))
        parseUsers(JSONArray(rpc("admin_search_users_v2", payload)))
    }

    suspend fun searchPostsV2(query: String, limit: Int = 40): Result<List<AdminPostSummary>> = runCatching {
        val payload = JSONObject()
            .put("p_query", query.trim())
            .put("p_limit", limit.coerceIn(1, 100))
        val array = JSONArray(rpc("admin_search_posts_v2", payload))
        buildList {
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                add(
                    AdminPostSummary(
                        id = json.optString("id"),
                        userId = json.optString("user_id"),
                        username = json.optString("username"),
                        fullName = json.optString("full_name"),
                        university = json.optString("university"),
                        caption = json.optString("caption"),
                        text = json.optString("text"),
                        isReel = json.optBoolean("is_reel", false),
                        isActive = json.optBoolean("is_active", true),
                        isFlagged = json.optBoolean("is_flagged", false),
                        isPinned = json.optBoolean("is_pinned", false),
                        isSponsored = json.optBoolean("is_sponsored", false),
                        likeCount = json.optLong("like_count", 0L),
                        commentCount = json.optLong("comment_count", 0L),
                        shareCount = json.optLong("share_count", 0L),
                        viewCount = json.optLong("view_count", 0L),
                        createdAt = json.nullableString("created_at")
                    )
                )
            }
        }
    }

    suspend fun searchUniversitiesV3(query: String = "", limit: Int = 100): Result<List<String>> = runCatching {
        val payload = JSONObject()
            .put("p_query", query.trim())
            .put("p_limit", limit.coerceIn(1, 300))
        parseStringArray(JSONArray(rpc("admin_search_universities_v3", payload)))
    }

    suspend fun fetchHistoryV2(limit: Int = 100, offset: Int = 0): Result<List<AdminHistoryItem>> = runCatching {
        val payload = JSONObject()
            .put("p_limit", limit.coerceIn(1, 250))
            .put("p_offset", offset.coerceAtLeast(0))
        val array = JSONArray(rpc("admin_history_v2", payload))
        buildList {
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                add(
                    AdminHistoryItem(
                        id = json.optString("id"),
                        actorId = json.optString("actor_id"),
                        actorUsername = json.optString("actor_username", "Admin"),
                        action = json.optString("action"),
                        targetUserId = json.nullableString("target_user_id"),
                        targetUsername = json.nullableString("target_username"),
                        targetPostId = json.nullableString("target_post_id"),
                        details = when (val value = json.opt("details")) {
                            is JSONObject -> value.toString()
                            JSONObject.NULL, null -> ""
                            else -> value.toString()
                        },
                        createdAt = json.nullableString("created_at"),
                        reversed = json.optBoolean("reversed", false),
                        reversedAt = json.nullableString("reversed_at"),
                        reversalNote = json.nullableString("reversal_note"),
                        canRevert = json.optBoolean("can_revert", false)
                    )
                )
            }
        }
    }

    suspend fun revertActionV2(actionId: String, note: String? = null): Result<JSONObject> = runCatching {
        val payload = JSONObject()
            .put("p_action_id", actionId)
            .put("p_note", note?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        JSONObject(rpc("admin_revert_action_v2", payload))
    }

    suspend fun globalSearchV2(query: String): Result<JSONObject> = runCatching {
        JSONObject(rpc("admin_global_search_v2", JSONObject().put("p_query", query.trim())))
    }

    // Legacy API kept for older callers while the V3 UI migrates completely.
    suspend fun fetchFeatures(): Result<List<AdminFeature>> = runCatching {
        val array = JSONArray(rpc("admin_list_features"))
        buildList {
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                add(
                    AdminFeature(
                        featureId = json.optInt("feature_id"),
                        title = json.optString("title"),
                        category = json.optString("category"),
                        ownerOnly = json.optBoolean("owner_only", false),
                        enabled = json.optBoolean("enabled", true)
                    )
                )
            }
        }
    }

    suspend fun executeFeature(
        featureId: Int,
        targetId: String? = null,
        text: String? = null,
        amount: Long? = null,
        durationHours: Int? = null,
        extraJson: String = "{}"
    ): Result<String> = runCatching {
        require(featureId in 1..200) { "Feature ID must be between 1 and 200." }
        val extra = try {
            JSONObject(extraJson.ifBlank { "{}" })
        } catch (_: Exception) {
            throw IllegalArgumentException("Extra JSON must be a valid JSON object.")
        }
        val payload = JSONObject()
            .put("p_feature_id", featureId)
            .put("p_target_id", targetId?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_text", text?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_amount", amount ?: JSONObject.NULL)
            .put("p_duration_hours", durationHours ?: JSONObject.NULL)
            .put("p_extra", extra)
        val raw = rpc("admin_execute_feature", payload)
        runCatching { JSONObject(raw).toString(2) }.getOrElse { raw }
    }

    suspend fun searchUsers(query: String, limit: Int = 40): Result<List<AdminUserSummary>> = runCatching {
        val payload = JSONObject()
            .put("p_query", query.trim())
            .put("p_limit", limit.coerceIn(1, 100))
        parseUsers(JSONArray(rpc("admin_search_users", payload)))
    }

    suspend fun fetchUniversities(): Result<List<String>> = runCatching {
        parseStringArray(JSONArray(rpc("admin_get_universities")))
    }

    suspend fun grantCoins(userId: String, amount: Long, reason: String = "Admin grant"): Result<Long> = runCatching {
        require(amount in 1..1_000_000) { "Coin amount must be between 1 and 1,000,000." }
        val json = JSONObject(
            rpc(
                "admin_grant_coins",
                JSONObject()
                    .put("p_user_id", userId)
                    .put("p_amount", amount)
                    .put("p_reason", reason)
            )
        )
        json.optLong("balance", 0L)
    }

    suspend fun setVerification(userId: String, badge: String, durationHours: Int): Result<String?> = runCatching {
        val json = JSONObject(
            rpc(
                "admin_set_verification",
                JSONObject()
                    .put("p_user_id", userId)
                    .put("p_badge", badge.uppercase())
                    .put("p_duration_hours", durationHours)
            )
        )
        json.nullableString("expires_at")
    }

    suspend fun grantAdmin(userId: String, durationHours: Int): Result<String?> = runCatching {
        val json = JSONObject(
            rpc(
                "admin_grant_role",
                JSONObject()
                    .put("p_user_id", userId)
                    .put("p_duration_hours", durationHours)
            )
        )
        json.nullableString("expires_at")
    }

    suspend fun revokeAdmin(userId: String): Result<Unit> = runCatching {
        rpc("admin_revoke_role", JSONObject().put("p_user_id", userId))
        Unit
    }

    suspend fun sendAnnouncement(
        message: String,
        targetUserId: String? = null,
        targetUniversity: String? = null,
        verificationFilter: String = "all"
    ): Result<Int> = runCatching {
        val payload = JSONObject()
            .put("p_message", message.trim())
            .put("p_target_user_id", targetUserId ?: JSONObject.NULL)
            .put("p_target_university", targetUniversity ?: JSONObject.NULL)
            .put("p_verification_filter", verificationFilter.lowercase())
        JSONObject(rpc("admin_send_announcement", payload)).optInt("delivered", 0)
    }

    suspend fun postAction(postId: String, action: String, weight: Double = 12.0): Result<Unit> = runCatching {
        rpc(
            "admin_post_action",
            JSONObject()
                .put("p_post_id", postId.trim())
                .put("p_action", action.lowercase())
                .put("p_weight", weight.coerceAtLeast(1.0))
        )
        Unit
    }

    private fun parseUsers(array: JSONArray): List<AdminUserSummary> = buildList {
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            add(
                AdminUserSummary(
                    id = json.optString("id"),
                    username = json.optString("username"),
                    fullName = json.optString("full_name"),
                    avatarUrl = json.nullableString("avatar_url"),
                    university = json.optString("university"),
                    verificationBadge = json.optString("verification_badge", "NONE"),
                    verificationExpiresAt = json.nullableString("verification_expires_at"),
                    coins = json.optLong("coins", 0L),
                    adminRole = json.optString("admin_role", "none"),
                    adminExpiresAt = json.nullableString("admin_expires_at"),
                    email = json.optString("email"),
                    faculty = json.optString("faculty"),
                    department = json.optString("department"),
                    createdAt = json.nullableString("created_at"),
                    lastSeen = json.nullableString("last_seen")
                )
            )
        }
    }

    private fun parseStringArray(array: JSONArray): List<String> = buildList {
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun parseError(body: String, fallback: String): String {
        return runCatching {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error_description").takeIf { it.isNotBlank() }
                ?: json.optString("hint").takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback)
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}
