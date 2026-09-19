package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Thin authenticated RPC client for Blink Store/VIP. All coin and entitlement rules stay server-side. */
class BlinkEconomyService {
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

    suspend fun state() = runCatching { rpc("get_blink_store_state") }
    suspend fun boostableContent() = runCatching { rpc("get_my_blink_boostable_content") }
    suspend fun economyStatus() = runCatching { rpc("get_blink_economy_status") }
    suspend fun dailyMissions() = runCatching { rpc("get_my_daily_missions") }
    suspend fun progressHub() = runCatching { rpc("get_my_progress_hub") }

    suspend fun claimDailyMission(missionKey: String) = runCatching {
        require(missionKey.isNotBlank()) { "Choose a mission first." }
        rpc(
            "claim_daily_mission",
            JSONObject().put("p_mission_key", missionKey.trim())
        )
    }

    suspend fun claimWeeklyMission(missionKey: String) = runCatching {
        require(missionKey.isNotBlank()) { "Choose a weekly mission first." }
        rpc(
            "claim_weekly_mission",
            JSONObject().put("p_mission_key", missionKey.trim())
        )
    }

    suspend fun claimWeeklyCompletionChest() = runCatching {
        rpc("claim_weekly_completion_chest")
    }

    suspend fun claimAchievement(achievementKey: String) = runCatching {
        require(achievementKey.isNotBlank()) { "Choose an achievement first." }
        rpc(
            "claim_blink_achievement",
            JSONObject().put("p_achievement_key", achievementKey.trim())
        )
    }

    suspend fun claimProgressReward(rewardKey: String) = runCatching {
        require(rewardKey.isNotBlank()) { "Choose a milestone reward first." }
        rpc(
            "claim_blink_progress_reward",
            JSONObject().put("p_reward_key", rewardKey.trim())
        )
    }

    suspend fun beginRewardedAdClaim() = runCatching {
        rpc("begin_blink_rewarded_ad_claim")
    }

    suspend fun completeRewardedAdClaim(claimId: String) = runCatching {
        require(claimId.isNotBlank()) { "Reward claim is missing." }
        rpc(
            "complete_blink_rewarded_ad_claim",
            JSONObject().put("p_claim_id", claimId)
        )
    }

    suspend fun verifyBlueWithCoins() = runCatching {
        rpc("purchase_blink_blue_verification_with_coins")
    }

    suspend fun createCoinPurchaseOrder(packId: String) = runCatching {
        require(packId.isNotBlank()) { "Choose a Blink Coin pack." }
        rpc(
            "create_blink_coin_purchase_order",
            JSONObject().put("p_pack_id", packId.trim())
        )
    }

    suspend fun purchase(catalogId: String, quantity: Int = 1, multiplier: Int = 1) = runCatching {
        rpc("purchase_blink_item", JSONObject()
            .put("p_catalog_id", catalogId)
            .put("p_quantity", quantity.coerceIn(1, 20))
            .put("p_boost_multiplier", multiplier))
    }

    suspend fun activate(inventoryId: String, targetId: String? = null) = runCatching {
        rpc("activate_blink_item", JSONObject()
            .put("p_inventory_id", inventoryId)
            .put("p_target_id", targetId?.takeIf(String::isNotBlank) ?: JSONObject.NULL))
    }

    suspend fun equip(inventoryId: String, slot: String, enabled: Boolean) = runCatching {
        rpc("set_blink_item_equipped", JSONObject()
            .put("p_inventory_id", inventoryId)
            .put("p_slot", slot)
            .put("p_enabled", enabled))
    }

    suspend fun claimVip(benefit: String) = runCatching {
        rpc("claim_blink_vip_benefit", JSONObject().put("p_benefit", benefit))
    }

    suspend fun renewVip() = runCatching { rpc("renew_blink_vip") }

    suspend fun setAutoRenew(enabled: Boolean) = runCatching {
        rpc("set_blink_vip_auto_renew", JSONObject().put("p_enabled", enabled))
    }

    suspend fun giftVip(username: String) = runCatching {
        require(username.trim().isNotEmpty()) { "Enter a Blink username." }
        rpc("gift_blink_vip", JSONObject().put("p_recipient_username", username.trim()))
    }

    suspend fun sendDigitalGift(inventoryId: String, username: String, message: String = "") = runCatching {
        require(inventoryId.isNotBlank()) { "Digital gift inventory is missing." }
        require(username.trim().isNotEmpty()) { "Enter a Blink username." }
        require(message.length <= 200) { "Gift message must be 200 characters or less." }
        rpc("send_blink_digital_gift", JSONObject()
            .put("p_inventory_id", inventoryId)
            .put("p_recipient_username", username.trim())
            .put("p_message", message.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL))
    }

    /**
     * Returns only public-safe premium identity state for another Blink account.
     * It may include a public Collection count/level, but never coin balances,
     * inventory quantities, spend totals, private analytics or target content ids.
     */
    suspend fun publicPremiumStyleByUsername(username: String) = runCatching {
        val clean = username.trim().removePrefix("@")
        require(clean.isNotBlank()) { "Blink username is required." }
        rpc("get_blink_public_premium_style", JSONObject().put("p_username", clean))
    }

    /** Fetches the public-safe list of items another user owns for Blink Collection. */
    suspend fun publicCollectionByUsername(username: String) = runCatching {
        val clean = username.trim().removePrefix("@")
        require(clean.isNotBlank()) { "Blink username is required." }
        rpc("get_blink_public_collection", JSONObject().put("p_username", clean))
    }

    suspend fun vipStatusByUsername(username: String) = runCatching {
        rpc("get_blink_vip_status_by_username", JSONObject().put("p_username", username.trim()))
    }

    private fun readableError(raw: String, code: Int): String {
        val message = runCatching { JSONObject(raw).optString("message") }.getOrDefault(raw)
        return when {
            message.contains("INSUFFICIENT_BLINK_COINS") -> "You don't have enough Blink Coins."
            message.contains("ALREADY_OWNED") -> "You already own this item."
            message.contains("VIP_REQUIRED") -> "An active Blink VIP pass is required."
            message.contains("TARGET_REQUIRED") -> "Choose where you want to use this item."
            message.contains("BENEFIT_EXHAUSTED") -> "That VIP benefit has already been used for this pass."
            message.contains("ALREADY_CLAIMED_TODAY") -> "Today's VIP coin bonus is already claimed."
            message.contains("REWARDED_AD_CLAIM_EXPIRED") -> "That ad reward expired. Please watch another ad."
            message.contains("REWARDED_AD_TOO_SOON") -> "The ad reward is not ready yet."
            message.contains("REWARDED_AD_DAILY_LIMIT") -> "You've reached today's 15-ad rewarded limit."
            message.contains("INVALID_COIN_PACK") -> "That Blink Coin pack is no longer available."
            message.contains("COIN_PURCHASE_ORDER_NOT_FOUND") -> "That Blink Coin purchase could not be found."
            message.contains("PAYMENT_REFERENCE_REQUIRED") -> "The payment has not been verified yet."
            message.contains("MISSION_NOT_COMPLETE") -> "Complete the mission before claiming its reward."
            message.contains("MISSION_NOT_FOUND") -> "That daily mission is no longer available."
            message.contains("MISSION_REQUIRED") -> "Choose a mission first."
            message.contains("WEEKLY_CHEST_NOT_READY") -> "Complete at least 4 weekly missions to unlock the weekly chest."
            message.contains("ACHIEVEMENT_REQUIRED") -> "Choose an achievement first."
            message.contains("ACHIEVEMENT_NOT_FOUND") -> "That achievement is no longer available."
            message.contains("ACHIEVEMENT_LOCKED") -> "You have not unlocked that achievement yet."
            message.contains("REWARD_REQUIRED") -> "Choose a milestone reward first."
            message.contains("REWARD_NOT_FOUND") -> "That milestone reward is no longer available."
            message.contains("REWARD_LOCKED") -> "Reach the required level or streak before claiming this reward."
            message.contains("INVALID_RECIPIENT") -> "That account cannot receive this gift."
            message.contains("RECIPIENT_REQUIRED") -> "Enter the recipient's Blink username."
            message.contains("DIGITAL_GIFT_NOT_AVAILABLE") -> "This digital gift is no longer available in your Vault."
            message.contains("MESSAGE_TOO_LONG") -> "Gift message must be 200 characters or less."
            message.isNotBlank() -> message
            else -> "Blink Store request failed ($code)."
        }
    }
}
