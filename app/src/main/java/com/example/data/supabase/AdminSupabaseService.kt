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
    val adminExpiresAt: String?
)

data class AdminFeature(
    val featureId: Int,
    val title: String,
    val category: String,
    val ownerOnly: Boolean,
    val enabled: Boolean
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
        val array = JSONArray(rpc("admin_search_users", payload))
        buildList {
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
                        adminExpiresAt = json.nullableString("admin_expires_at")
                    )
                )
            }
        }
    }

    suspend fun fetchUniversities(): Result<List<String>> = runCatching {
        val array = JSONArray(rpc("admin_get_universities"))
        buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
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

        val json = JSONObject(rpc("admin_send_announcement", payload))
        json.optInt("delivered", 0)
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

    private fun parseError(body: String, fallback: String): String {
        return runCatching {
            val json = JSONObject(body)
            json.optString("message")
                .takeIf { it.isNotBlank() }
                ?: json.optString("error_description")
                    .takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback)
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}
