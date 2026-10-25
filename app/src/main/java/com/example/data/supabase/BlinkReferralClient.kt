package com.example.data.supabase

import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralStats
import com.blinkng.shared.discovery.BlinkReferralGrowthStats
import com.blinkng.shared.discovery.BlinkReferralLeaderboardEntry
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Server-authoritative referral reads. No local credit, follower or coin mutations. */
object BlinkReferralClient {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun invoke(path: String, payload: JSONObject, authenticated: Boolean): JSONArray {
        val token = if (authenticated) SupabaseService.accessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to see your referral progress.")
        else SupabaseConfig.anonKey
        val request = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$path")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(mediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Referrals unavailable (${response.code}). Please try again.")
            }
            return JSONArray(body.ifBlank { "[]" })
        }
    }

    suspend fun findReferrers(query: String, offset: Int = 0): List<BlinkReferralSuggestion> =
        withContext(Dispatchers.IO) {
            val normalized = BlinkReferralPolicy.normalizedQuery(query)
            if (normalized.isBlank()) return@withContext emptyList()
            val rows = invoke(
                "blink_referrer_suggestions",
                JSONObject().put("p_query", normalized).put("p_limit", 10)
                    .put("p_offset", offset.coerceIn(0, 500)),
                authenticated = false,
            )
            (0 until rows.length()).map { index ->
                val item = rows.getJSONObject(index)
                BlinkReferralSuggestion(
                    id = item.getString("id"),
                    username = item.getString("username"),
                    fullName = item.optString("full_name"),
                    avatarUrl = item.optString("avatar_url").takeIf(String::isNotBlank),
                    university = item.optString("university").takeIf(String::isNotBlank),
                isVerified = item.optBoolean("is_verified"),
                verificationBadge = item.optString("verification_badge").takeIf(String::isNotBlank),
                )
            }
        }

    suspend fun dashboard(): BlinkReferralStats = withContext(Dispatchers.IO) {
        val row = invoke("blink_referral_dashboard", JSONObject(), authenticated = true)
            .optJSONObject(0) ?: return@withContext BlinkReferralStats()
        BlinkReferralStats(
            total = row.optLong("total_referrals"),
            activeLast30Days = row.optLong("active_referrals"),
            visibilityPoints = row.optInt("visibility_points"),
            visibilityLabel = row.optString("visibility_label", "Normal discovery"),
            achievementTier = row.optString("achievement_tier", "New member"),
            nextAchievementTarget = if (row.isNull("achievement_next_target"))
                null else row.optInt("achievement_next_target", 1),
        )
    }

    /**
     * Wake the existing actor-validated, idempotent FCM dispatcher on new signup/login.
     * Referral notification rows are created by the server (not by clients).
     */
    suspend fun dispatchConfirmedReferralPushes() = withContext(Dispatchers.IO) {
        val accessToken = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
            ?: return@withContext
        val rows = invoke("blink_my_referral_push_ids", JSONObject(), authenticated = true)
        for (i in 0 until rows.length().coerceAtMost(3)) {
            val notificationId = rows.getJSONObject(i).optString("notification_id")
            if (notificationId.isBlank()) continue
            val req = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/send-activity-notification")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $accessToken")
                .post(JSONObject().put("notification_id", notificationId).toString()
                    .toRequestBody(mediaType))
                .build()
            client.newCall(req).execute().use { response ->
                // Don't block registration if delivery is delayed or push is unavailable.
                if (!response.isSuccessful) {
                    throw IllegalStateException("Referral push dispatch unavailable (${response.code}).")
                }
            }
        }
    }

    suspend fun growthStats(): BlinkReferralGrowthStats = withContext(Dispatchers.IO) {
        val row = invoke("blink_referral_growth_stats", JSONObject(), authenticated = true)
            .optJSONObject(0) ?: return@withContext BlinkReferralGrowthStats()
        BlinkReferralGrowthStats(
            profileViewsThisWeek = row.optLong("profile_views_this_week"),
            followersGainedThisWeek = row.optLong("followers_gained_this_week"),
            shareActions = row.optLong("share_actions"),
        )
    }

    suspend fun leaderboard(): List<BlinkReferralLeaderboardEntry> = withContext(Dispatchers.IO) {
        val rows = invoke("blink_referral_leaderboard",
            JSONObject().put("p_days", 7).put("p_limit", 20), authenticated = true)
        (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            BlinkReferralLeaderboardEntry(
                position = item.optLong("position"),
                id = item.getString("id"),
                username = item.getString("username"),
                fullName = item.optString("full_name"),
                avatarUrl = item.optString("avatar_url").takeIf(String::isNotBlank),
                university = item.optString("university").takeIf(String::isNotBlank),
                confirmedReferrals = item.optLong("successful_referrals"),
            )
        }
    }

    suspend fun recordShareAction(channel: String): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseService.accessToken()?.takeIf(String::isNotBlank)
            ?: return@withContext false
        if (channel !in setOf("copy", "share", "web", "desktop")) return@withContext false
        val request = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/blink_record_referral_share")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("p_channel", channel).toString().toRequestBody(mediaType))
            .build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful && response.body?.string()?.trim() == "true"
        }
    }

    suspend fun recommendations(): List<BlinkReferralSuggestion> = withContext(Dispatchers.IO) {
        val rows = invoke(
            "blink_referral_recommendations", JSONObject().put("p_limit", 10),
            authenticated = true,
        )
        (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            BlinkReferralSuggestion(
                id = item.getString("id"),
                username = item.getString("username"),
                fullName = item.optString("full_name"),
                avatarUrl = item.optString("avatar_url").takeIf(String::isNotBlank),
                university = item.optString("university").takeIf(String::isNotBlank),
                isVerified = item.optBoolean("is_verified"),
                verificationBadge = item.optString("verification_badge").takeIf(String::isNotBlank),
            )
        }
    }
}
