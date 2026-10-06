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

data class BlinkDropDiscovery(
    val dropId: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorName: String,
    val creatorAvatar: String,
    val totalCoins: Long,
    val rewardPerUser: Int,
    val winnerCount: Int,
    val claimedCount: Int,
    val status: String,
    val action: String,
    val createdAt: String,
)

/**
 * Authenticated client for BLINK Drops.
 *
 * The Android client never modifies balances directly. Reserve, reward, refund,
 * eligibility and anti-replay rules are authoritative Supabase RPCs.
 */
class BlinkDropsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun rpc(name: String, payload: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) error("Your Blink session has expired. Please sign in again.")

            fun request(): Request {
                val access = SupabaseService.accessToken()?.takeIf(String::isNotBlank)
                    ?: error("A signed-in Blink account is required.")
                return Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$name")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $access")
                    .header("Accept", "application/json")
                    .post(payload.toString().toRequestBody(mediaType))
                    .build()
            }

            var response = client.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!session.refreshSession()) error("Your Blink session has expired. Please sign in again.")
                response = client.newCall(request()).execute()
            }
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) error(readableError(raw, it.code))
                if (raw.isBlank()) JSONObject() else JSONObject(raw)
            }
        }

    suspend fun state(limit: Int = 20) = runCatching {
        rpc("get_blink_drops_state", JSONObject().put("p_limit", limit.coerceIn(1, 50)))
    }

    suspend fun discovery(limit: Int = 3): Result<List<BlinkDropDiscovery>> = runCatching {
        val root = rpc(
            "get_blink_drop_discovery",
            JSONObject().put("p_limit", limit.coerceIn(1, 5))
        )
        val items = root.optJSONArray("items") ?: JSONArray()
        buildList {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                add(
                    BlinkDropDiscovery(
                        dropId = item.optString("drop_id"),
                        creatorId = item.optString("creator_id"),
                        creatorUsername = item.optString("creator_username"),
                        creatorName = item.optString("creator_name"),
                        creatorAvatar = item.optString("creator_avatar"),
                        totalCoins = item.optLong("total_coins", 0L),
                        rewardPerUser = item.optInt("reward_per_user", 0),
                        winnerCount = item.optInt("winner_count", 0),
                        claimedCount = item.optInt("claimed_count", 0),
                        status = item.optString("status"),
                        action = item.optString("action"),
                        createdAt = item.optString("created_at"),
                    )
                )
            }
        }
    }

    suspend fun createDrop(
        targetType: String,
        targetId: String,
        action: String,
        rewardPerUser: Int,
        winnerCount: Int,
        audienceScope: String,
        durationHours: Int,
    ) = runCatching {
        rpc(
            "create_blink_drop",
            JSONObject()
                .put("p_target_type", targetType.trim().uppercase())
                .put("p_target_id", targetId.trim())
                .put("p_action", action.trim().uppercase())
                .put("p_reward_per_user", rewardPerUser)
                .put("p_winner_count", winnerCount)
                .put("p_audience_scope", audienceScope.trim().uppercase())
                .put("p_duration_hours", durationHours)
        )
    }

    suspend fun completeAction(dropId: String, commentText: String? = null) = runCatching {
        rpc(
            "complete_blink_drop_action",
            JSONObject()
                .put("p_drop_id", dropId.trim())
                .put(
                    "p_comment_text",
                    commentText?.trim()?.takeIf(String::isNotBlank) ?: JSONObject.NULL
                )
        )
    }

    suspend fun cancelDrop(dropId: String) = runCatching {
        rpc("cancel_blink_drop", JSONObject().put("p_drop_id", dropId.trim()))
    }

    suspend fun followCreator(creatorId: String) = runCatching {
        rpc("follow_blink_drop_creator", JSONObject().put("p_creator_id", creatorId.trim()))
    }

    private fun readableError(raw: String, code: Int): String {
        val message = runCatching { JSONObject(raw).optString("message") }.getOrDefault(raw)
        return when {
            message.contains("INSUFFICIENT_BLINK_COINS") -> "You don't have enough BLINK Coins for this Drop."
            message.contains("DROP_REWARD_INVALID") -> "Each person must receive a multiple of 100 BLINK Coins."
            message.contains("DROP_WINNER_COUNT_INVALID") -> "Choose a valid number of recipients."
            message.contains("DROP_BUDGET_TOO_LARGE") -> "That Drop is above the supported budget."
            message.contains("DROP_DURATION_INVALID") -> "Choose a supported Drop duration."
            message.contains("DROP_TARGET_NOT_OWNED") -> "Choose one of your own active posts, Reels or listings."
            message.contains("DROP_ACTION_INVALID") -> "That action is not available for the selected content."
            message.contains("DROP_NOT_FOUND") -> "That BLINK Drop could not be found."
            message.contains("DROP_NOT_ACTIVE") -> "That BLINK Drop is no longer active."
            message.contains("DROP_NOT_ELIGIBLE") -> "You were not an eligible follower when this Drop started."
            message.contains("DROP_ALREADY_CLAIMED") -> "You already received this Drop reward."
            message.contains("DROP_FULL") -> "All rewards in this Drop have been claimed."
            message.contains("DROP_ACTION_ALREADY_DONE") -> "You already completed that action before entering this Drop."
            message.contains("DROP_COMMENT_REQUIRED") -> "Write a comment to complete this Drop."
            message.contains("DROP_COMMENT_TOO_LONG") -> "Your comment is too long."
            message.contains("UNIVERSITY_REQUIRED_FOR_DROP") -> "Add your university before creating a campus-only Drop."
            message.contains("INVALID_CREATOR") -> "That creator cannot be followed."
            message.isNotBlank() -> message
            else -> "BLINK Drops request failed ($code)."
        }
    }
}
