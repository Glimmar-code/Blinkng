package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DesktopCall(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val calleeId: String,
    val callType: String,
    val status: String,
    val createdAt: String,
    val timeoutAt: String,
)

data class AdminDashboardSnapshot(
    val raw: JSONObject,
    val sections: JSONArray,
)

/**
 * High-trust Windows actions routed through the existing Supabase RPCs.
 * Client code never edits coin balances, VIP state, admin roles, or call lifecycle rows directly.
 */
class DesktopRpcActions(private val client: DesktopSupabaseClient) {
    private val baseUrl = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_URL") ?: System.getenv("VITE_SUPABASE_URL"),
        BlinkBackendDefaults.SUPABASE_URL,
    ).trimEnd('/')
    private val anonKey = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_ANON_KEY") ?: System.getenv("VITE_SUPABASE_ANON_KEY"),
        BlinkBackendDefaults.SUPABASE_ANON_KEY,
    )
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun purchaseStoreItem(catalogId: String, quantity: Int = 1, boostMultiplier: Int? = null): JSONObject =
        rpc("purchase_blink_item", JSONObject()
            .put("p_catalog_id", catalogId)
            .put("p_quantity", quantity.coerceAtLeast(1))
            .put("p_boost_multiplier", boostMultiplier ?: JSONObject.NULL))

    // Reporting and rewards are controlled by the same RLS/RPC policies as Android.
    suspend fun submitBugReport(description: String): JSONObject =
        rpc("submit_blink_bug_report", JSONObject()
            .put("p_description", description.trim())
            .put("p_surface", "windows-desktop")
            .put("p_app_version", "desktop")
            .put("p_screenshot", false))

    suspend fun myBugReports(): JSONArray =
        when (val rows = rpcAny("get_my_blink_bug_reports", JSONObject())) {
            is JSONArray -> rows
            else -> JSONArray()
        }

    suspend fun adminBugReports(): JSONArray {
        requireAdmin()
        return when (val rows = rpcAny("admin_list_blink_bug_reports", JSONObject())) {
            is JSONArray -> rows
            else -> JSONArray()
        }
    }

    suspend fun reviewBugReport(id: String, status: String, rewardCoins: Int, note: String): JSONObject {
        requireAdmin()
        return rpc("admin_review_blink_bug_report", JSONObject()
            .put("p_report_id", id)
            .put("p_status", status)
            .put("p_reward_coins", rewardCoins)
            .put("p_note", note.take(600)))
    }

    // Free-entry BLINK Contest. All writes enforce authenticated server-side rules.
    suspend fun contestList(): JSONArray =
        rpcAny("list_blink_contests", JSONObject()) as? JSONArray ?: JSONArray()
    suspend fun contestDetail(id: String): JSONObject =
        rpc("get_blink_contest", JSONObject().put("p_contest_id", id))
    suspend fun contestCandidate(contestId: String, entryId: String): JSONObject =
        rpc("get_blink_contest_candidate", JSONObject().put("p_contest_id", contestId)
            .put("p_entry_id", entryId))
    suspend fun contestApply(id: String, statement: String, mediaUrl: String): JSONObject =
        rpc("apply_blink_contest", JSONObject().put("p_contest_id", id)
            .put("p_statement", statement.trim())
            .put("p_media_url", mediaUrl.trim().takeIf { it.isNotEmpty() } ?: JSONObject.NULL))
    suspend fun contestVote(id: String, entry: String): JSONObject =
        rpc("vote_blink_contest", JSONObject().put("p_contest_id", id).put("p_entry_id", entry))
    suspend fun adminContestList(): JSONArray {
        requireAdmin()
        return rpcAny("admin_list_blink_contests", JSONObject()) as? JSONArray ?: JSONArray()
    }
    suspend fun adminContestEntries(id: String): JSONArray {
        requireAdmin()
        return rpcAny("admin_list_blink_contest_entries",
            JSONObject().put("p_contest_id", id)) as? JSONArray ?: JSONArray()
    }
    suspend fun adminContestSave(data: JSONObject): JSONObject {
        requireAdmin()
        return rpc("admin_save_blink_contest",
            JSONObject().put("p_id", JSONObject.NULL).put("p_data", data))
    }
    suspend fun adminContestAction(id: String, action: String): JSONObject {
        requireAdmin()
        return rpc("admin_transition_blink_contest",
            JSONObject().put("p_contest_id", id).put("p_action", action))
    }
    suspend fun contestRecordShare(contestId: String, entryId: String, channel: String): JSONObject =
        rpc("record_blink_contest_share", JSONObject().put("p_contest_id", contestId)
            .put("p_entry_id", entryId).put("p_channel", channel))
    suspend fun adminContestInsights(id: String): JSONObject {
        requireAdmin()
        return rpc("admin_blink_contest_insights", JSONObject().put("p_contest_id", id))
    }
    suspend fun adminContestVoteReview(id: String, action: String, reason: String): JSONObject {
        requireAdmin()
        return rpc("admin_review_blink_contest_vote", JSONObject().put("p_vote_id", id)
            .put("p_action", action).put("p_reason", reason))
    }
    suspend fun adminContestFinalize(id: String): JSONObject {
        requireAdmin()
        return rpc("admin_finalize_blink_contest", JSONObject().put("p_contest_id", id))
    }
    suspend fun adminContestPrize(id: String, status: String, reference: String, note: String): JSONObject {
        requireAdmin()
        return rpc("admin_record_blink_contest_prize", JSONObject().put("p_prize_id", id)
            .put("p_status", status).put("p_reference", if (reference.isBlank()) JSONObject.NULL else reference.trim())
            .put("p_note", note))
    }
    suspend fun adminContestReview(id: String, action: String): JSONObject {
        requireAdmin()
        return rpc("admin_review_blink_contest_entry",
            JSONObject().put("p_entry_id", id).put("p_action", action))
    }

    suspend fun getStoreState(): JSONObject = rpc("get_blink_store_state", JSONObject())

    suspend fun getEconomyStatus(): JSONObject = rpc("get_blink_economy_status", JSONObject())

    suspend fun getDailyMissions(): JSONObject = rpc("get_my_daily_missions", JSONObject())

    suspend fun getProgressHub(): JSONObject = rpc("get_my_progress_hub", JSONObject())

    suspend fun claimDailyMission(missionKey: String): JSONObject =
        rpc("claim_daily_mission", JSONObject().put("p_mission_key", missionKey.trim()))

    suspend fun claimWeeklyMission(missionKey: String): JSONObject =
        rpc("claim_weekly_mission", JSONObject().put("p_mission_key", missionKey.trim()))

    suspend fun claimWeeklyCompletionChest(): JSONObject =
        rpc("claim_weekly_completion_chest", JSONObject())

    suspend fun claimAchievement(achievementKey: String): JSONObject =
        rpc("claim_blink_achievement", JSONObject().put("p_achievement_key", achievementKey.trim()))

    suspend fun claimProgressReward(rewardKey: String): JSONObject =
        rpc("claim_blink_progress_reward", JSONObject().put("p_reward_key", rewardKey.trim()))

    suspend fun purchaseBlueVerificationWithCoins(): JSONObject =
        rpc("purchase_blink_blue_verification_with_coins", JSONObject())

    suspend fun createCoinPurchaseOrder(packId: String): JSONObject =
        rpc("create_blink_coin_purchase_order", JSONObject().put("p_pack_id", packId.trim()))

    /**
     * Only starts provider-hosted checkout for server-configured fixed-price packs.
     * The provider webhook/server verification, never this client, fulfills coins.
     */
    suspend fun initializePaystackCoinCheckout(packId: String): JSONObject = withContext(Dispatchers.IO) {
        require(packId.matches(Regex("coins_[0-9]+"))) { "Choose a valid coin pack." }
        requireSessionUserId()
        val body = JSONObject().put("kind", "COIN_PACK").put("pack_id", packId)
        val request = requestBuilder("/functions/v1/paystack-initialize")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        JSONObject(execute(request))
    }

    suspend fun verifyPaystackCoinCheckout(orderId: String): JSONObject = withContext(Dispatchers.IO) {
        require(orderId.isNotBlank()) { "Payment order is missing." }
        requireSessionUserId()
        val body = JSONObject().put("order_id", orderId.trim())
        val request = requestBuilder("/functions/v1/paystack-verify")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        JSONObject(execute(request))
    }

    suspend fun activateStoreItem(inventoryId: String, targetId: String? = null): JSONObject =
        rpc("activate_blink_item", JSONObject()
            .put("p_inventory_id", inventoryId)
            .put("p_target_id", targetId?.takeIf(String::isNotBlank) ?: JSONObject.NULL))

    suspend fun equipStoreItem(inventoryId: String, slot: String, enabled: Boolean): JSONObject =
        rpc("set_blink_item_equipped", JSONObject()
            .put("p_inventory_id", inventoryId)
            .put("p_slot", slot)
            .put("p_enabled", enabled))

    suspend fun sendDigitalGift(inventoryId: String, username: String, message: String = ""): JSONObject =
        rpc("send_blink_digital_gift", JSONObject()
            .put("p_inventory_id", inventoryId)
            .put("p_recipient_username", username.trim().removePrefix("@"))
            .put("p_message", message.trim().takeIf(String::isNotBlank) ?: JSONObject.NULL))

    suspend fun getPublicPremiumStyle(username: String): JSONObject =
        rpc("get_blink_public_premium_style", JSONObject().put("p_username", username.trim().removePrefix("@")))

    suspend fun renewVip(): JSONObject = rpc("renew_blink_vip", JSONObject())

    suspend fun setVipAutoRenew(enabled: Boolean): JSONObject =
        rpc("set_blink_vip_auto_renew", JSONObject().put("p_enabled", enabled))

    suspend fun claimVipBenefit(benefit: String): JSONObject =
        rpc("claim_blink_vip_benefit", JSONObject().put("p_benefit", benefit))

    suspend fun getBoostableContent(): JSONObject = rpc("get_my_blink_boostable_content", JSONObject())
    suspend fun getBoostableContentPage(type: String, offset: Int, limit: Int = 40): JSONObject =
        rpc("get_my_blink_boostable_content_page", JSONObject()
            .put("p_target_type",type)
            .put("p_offset",offset.coerceAtLeast(0))
            .put("p_limit",limit.coerceIn(1,50)))


    suspend fun getBoostGrowthState(): JSONObject =
        rpc("get_blink_boost_growth_state_v2", JSONObject())

    suspend fun getBoostMissions(limit: Int = 12): JSONObject =
        rpc("get_blink_boost_missions", JSONObject().put("p_limit", limit.coerceIn(1, 30)))

    suspend fun getPromotedBoostSlots(surface: String, limit: Int = 3): JSONObject =
        rpc(
            "get_blink_promoted_slots",
            JSONObject()
                .put("p_surface", surface.trim().uppercase())
                .put("p_limit", limit.coerceIn(1, 10)),
        )

    suspend fun quoteBoostCampaign(
        targetType: String,
        targetId: String,
        boostPower: Int,
        objective: String,
        audienceScope: String,
        durationDays: Int,
        targetUniversity: String? = null,
    ): JSONObject = rpc(
        "quote_blink_boost_campaign_v2",
        JSONObject()
            .put("p_target_type", targetType.trim().uppercase())
            .put("p_target_id", targetId.trim())
            .put("p_boost_power", boostPower.coerceIn(1, 100))
            .put("p_objective", objective.trim().uppercase())
            .put("p_audience_scope", audienceScope.trim().uppercase())
            .put("p_duration_days", durationDays)
            .put("p_target_university", targetUniversity?.trim()?.takeIf(String::isNotBlank) ?: JSONObject.NULL),
    )

    suspend fun createBoostCampaign(
        targetType: String,
        targetId: String,
        boostPower: Int,
        objective: String,
        audienceScope: String,
        durationDays: Int,
        requestId: String,
        targetUniversity: String? = null,
    ): JSONObject = rpc(
        "create_blink_boost_campaign_v2",
        JSONObject()
            .put("p_target_type", targetType.trim().uppercase())
            .put("p_target_id", targetId.trim())
            .put("p_boost_power", boostPower.coerceIn(1, 100))
            .put("p_objective", objective.trim().uppercase())
            .put("p_audience_scope", audienceScope.trim().uppercase())
            .put("p_duration_days", durationDays)
            .put("p_request_id", requestId)
            .put("p_target_university", targetUniversity?.trim()?.takeIf(String::isNotBlank) ?: JSONObject.NULL),
    )

    suspend fun cancelBoostCampaign(campaignId: String): JSONObject =
        rpc("cancel_blink_boost_campaign", JSONObject().put("p_campaign_id", campaignId.trim()))

    suspend fun getDropsState(): JSONObject = rpc("get_blink_drops_state_v2", JSONObject().put("p_limit", 30))
    suspend fun getDropsPage(scope: String, offset: Int, limit: Int = 30): JSONObject =
        rpc("get_blink_drop_page", JSONObject()
            .put("p_scope",scope)
            .put("p_offset",offset.coerceAtLeast(0))
            .put("p_limit",limit.coerceIn(1,50)))
    suspend fun getDropById(id: String): JSONObject =
        rpc("get_blink_drop_by_id", JSONObject().put("p_drop_id",id))


    suspend fun recommendBoostPower(input: com.blinkng.shared.BlinkBoostCampaignRequest, budget: Long): JSONObject =
        rpc("recommend_blink_boost_power", JSONObject()
            .put("p_target_type", input.targetType).put("p_target_id", input.targetId).put("p_budget", budget)
            .put("p_objective", input.objective).put("p_audience_scope", input.audience)
            .put("p_duration_days", input.duration).put("p_target_university", input.university ?: JSONObject.NULL))

    suspend fun quoteDrop(input: com.blinkng.shared.BlinkDropRequest): JSONObject = rpc("quote_blink_drop", dropParams(input))

    suspend fun createDrop(input: com.blinkng.shared.BlinkDropRequest, requestId: String): JSONObject =
        rpc("create_blink_drop_v2", dropParams(input).put("p_request_id", requestId))

    private fun dropParams(input: com.blinkng.shared.BlinkDropRequest) = JSONObject()
        .put("p_target_type", input.targetType).put("p_target_id", input.targetId)
        .put("p_action", input.action).put("p_reward_per_user", input.reward)
        .put("p_winner_count", input.recipients).put("p_audience_scope", input.audience)
        .put("p_duration_hours", input.hours)

    suspend fun cancelDrop(id: String): JSONObject = rpc("cancel_blink_drop", JSONObject().put("p_drop_id", id))

    suspend fun completeDropAction(id: String, comment: String?): JSONObject =
        rpc("complete_blink_drop_action", JSONObject().put("p_drop_id", id)
            .put("p_comment_text", comment?.takeIf(String::isNotBlank) ?: JSONObject.NULL))

    suspend fun followDropCreator(id: String): JSONObject =
        rpc("follow_blink_drop_creator", JSONObject().put("p_creator_id", id))

    suspend fun recordBoostDelivery(campaignId: String, eventType: String, surface: String): JSONObject =
        rpc(
            "record_blink_boost_delivery",
            JSONObject()
                .put("p_campaign_id", campaignId.trim())
                .put("p_event_type", eventType.trim().uppercase())
                .put("p_surface", surface.trim().uppercase()),
        )

    suspend fun completeBoostMissionAction(
        campaignId: String,
        action: String,
        commentText: String? = null,
    ): JSONObject =
        rpc(
            "complete_blink_boost_mission_action",
            JSONObject()
                .put("p_campaign_id", campaignId.trim())
                .put("p_action", action.trim().lowercase())
                .put("p_comment_text", commentText?.trim()?.takeIf(String::isNotBlank) ?: JSONObject.NULL),
        )

    suspend fun recordContentView(postId: String): JSONObject =
        rpc(
            "record_content_view",
            JSONObject()
                .put("p_post_id", postId.trim())
                .put("p_event_id", UUID.randomUUID().toString()),
        )

    suspend fun bookmarkPost(postId: String): JSONObject =
        rpc("bookmark_post", JSONObject().put("p_post_id", postId.trim()))

    suspend fun adminDashboard(): AdminDashboardSnapshot {
        requireAdmin()
        val stats = rpc("admin_dashboard_stats", JSONObject())
        val sectionsValue = rpcAny("admin_list_sections_v3", JSONObject())
        val sections = when (sectionsValue) {
            is JSONArray -> sectionsValue
            is JSONObject -> sectionsValue.optJSONArray("sections") ?: JSONArray().put(sectionsValue)
            else -> JSONArray()
        }
        return AdminDashboardSnapshot(stats, sections)
    }

    suspend fun adminSearchAnnouncementUsers(query: String): JSONArray {
        requireAdmin()
        return rpcAny(
            "admin_search_users_v2",
            JSONObject().put("p_query", query.take(100)).put("p_limit", 20),
        ) as? JSONArray ?: JSONArray()
    }

    suspend fun adminSendInAppAnnouncement(
        message: String,
        userId: String? = null,
        university: String? = null,
    ): Int {
        requireAdmin()
        require(message.isNotBlank() && message.length <= 2_000)
        val result = rpc("admin_send_announcement", JSONObject()
            .put("p_message", message.trim())
            .put("p_target_user_id", userId ?: JSONObject.NULL)
            .put("p_target_university", university ?: JSONObject.NULL)
            .put("p_verification_filter", "all"))
        return result.optInt("delivered", 0)
    }

    /** Read-only unpublished content for the existing Admin dashboard. */
    suspend fun adminReadLiveConfig(): JSONArray {
        requireAdmin()
        return rpc("admin_read_blink_live_config", JSONObject()).optJSONArray("items") ?: JSONArray()
    }

    /** Only owner authorization in the RPC can publish, regardless of UI state. */
    suspend fun adminPublishLiveConfig(
        key: String, value: JSONObject, enabled: Boolean, revision: Long,
    ): JSONObject {
        requireAdmin()
        require(key in setOf("announcement", "promotion", "display", "feature_visibility"))
        return rpc("admin_publish_blink_live_config", JSONObject()
            .put("p_key", key).put("p_value", value)
            .put("p_enabled", enabled).put("p_expected_revision", revision))
    }

    /** Public-facing campaigns are filtered by Supabase role and server schedule. */
    suspend fun liveCampaigns(placement: String): JSONArray {
        if (placement !in com.blinkng.shared.live.BlinkLiveCampaignPolicy.placements) return JSONArray()
        val result = rpcAny("get_blink_live_campaigns",
            JSONObject().put("p_placement", placement).put("p_app_version", 0))
        return result as? JSONArray ?: JSONArray()
    }

    suspend fun recordLiveCampaignEvent(placement: String, id: String, kind: String): Boolean {
        if (kind !in setOf("impression", "click", "dismiss")) return false
        val result = rpcAny("record_blink_live_campaign_event",
            JSONObject().put("p_id", id).put("p_placement", placement)
                .put("p_app_version", 0).put("p_event", kind))
        return result == true
    }

    suspend fun adminLiveCampaigns(): JSONArray {
        requireAdmin()
        return rpcAny("admin_list_blink_live_campaigns", JSONObject()) as? JSONArray ?: JSONArray()
    }

    suspend fun adminLiveCampaignAnalytics(days: Int): JSONObject {
        requireAdmin()
        require(days in setOf(7, 30, 90)) { "Choose a 7, 30, or 90-day reporting window." }
        return rpc("admin_blink_live_campaign_analytics", JSONObject().put("p_days", days))
    }

    suspend fun adminSaveLiveCampaign(id: String?, draft: JSONObject, revision: Long?): JSONObject {
        requireAdmin()
        return rpc("admin_save_blink_live_campaign", JSONObject()
            .put("p_id", id ?: JSONObject.NULL)
            .put("p_data", draft)
            .put("p_expected_revision", revision ?: JSONObject.NULL))
    }

    suspend fun adminTransitionLiveCampaign(id: String, revision: Long, action: String): JSONObject {
        requireAdmin()
        require(action in setOf("submit","approve","pause","archive"))
        return rpc("admin_transition_blink_live_campaign", JSONObject()
            .put("p_id", id).put("p_expected_revision", revision).put("p_action", action))
    }

    suspend fun adminLiveCampaignHistory(id: String): JSONArray {
        requireAdmin()
        return rpcAny("admin_blink_live_campaign_history",
            JSONObject().put("p_id", id)) as? JSONArray ?: JSONArray()
    }

    suspend fun adminRestoreLiveCampaign(id: String, current: Long, past: Long): JSONObject {
        requireAdmin()
        return rpc("admin_restore_blink_live_campaign", JSONObject()
            .put("p_id", id).put("p_expected_revision", current)
            .put("p_history_revision", past))
    }

    /** Locally compresss large artwork; RLS still restricts uploads to the owner. */
    suspend fun uploadLiveBanner(file: java.io.File): String = withContext(Dispatchers.IO) {
        requireAdmin()
        val image = DesktopLiveBannerCompressor.prepare(file)
        // Server-side Storage validation enforces the MIME and 2 MB limits again.
        requireSessionUserId()
        val path = "banners/${java.util.UUID.randomUUID()}.${image.extension}"
        val bucket = "blink-live-banners"
        execute(requestBuilder("/storage/v1/object/$bucket/$path")
            .header("x-upsert", "false")
            .post(image.bytes.toRequestBody(image.mime.toMediaType()))
            .build())
        "$baseUrl/storage/v1/object/public/$bucket/$path"
    }

    suspend fun uploadContestMedia(file: java.io.File): String = withContext(Dispatchers.IO) {
        require(file.isFile) { "Choose an existing media file." }
        val extension = file.extension.lowercase()
        val mime = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            else -> throw IllegalArgumentException("Choose a JPG, PNG, WebP or MP4 file.")
        }
        com.blinkng.shared.BlinkContestMediaPolicy.validate(mime, file.length())
        val owner = requireSessionUserId()
        val path = "$owner/${UUID.randomUUID()}.$extension"
        execute(requestBuilder("/storage/v1/object/blink-contest-private/$path")
            .header("x-upsert", "false")
            .post(file.asRequestBody(mime.toMediaType())).build())
        "$baseUrl/functions/v1/blink-contest-private-media/$path"
    }

    suspend fun adminGlobalSearch(query: String): JSONObject {
        requireAdmin()
        return rpc("admin_global_search_v2", JSONObject().put("p_query", query.trim()))
    }

    suspend fun adminFeatures(sectionKey: String?, query: String = ""): JSONObject {
        requireAdmin()
        return rpc("admin_list_features_v3", JSONObject()
            .put("p_section_key", sectionKey ?: JSONObject.NULL)
            .put("p_query", query))
    }

    suspend fun adminExecuteFeature(
        featureId: Int,
        entityRef: String? = null,
        text: String? = null,
        amount: Long? = null,
        durationHours: Int? = null,
        options: JSONObject = JSONObject(),
    ): JSONObject {
        requireAdmin()
        return rpc("admin_execute_feature_v3", JSONObject()
            .put("p_feature_id", featureId)
            .put("p_entity_ref", entityRef ?: JSONObject.NULL)
            .put("p_text", text ?: JSONObject.NULL)
            .put("p_amount", amount ?: JSONObject.NULL)
            .put("p_duration_hours", durationHours ?: JSONObject.NULL)
            .put("p_options", options))
    }

    suspend fun startCall(conversationId: String, callType: String): DesktopCall = withContext(Dispatchers.IO) {
        val userId = requireSessionUserId()
        val participants = getArray(
            "/rest/v1/conversation_participants?conversation_id=eq.${encode(conversationId)}&user_id=neq.${encode(userId)}&select=user_id&limit=1",
        )
        val calleeId = participants.optJSONObject(0)?.optString("user_id").orEmpty()
        require(calleeId.isNotBlank()) { "No other participant was found for this conversation." }
        parseCall(rpc("start_call", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_callee_id", calleeId)
            .put("p_call_type", callType)))
    }

    suspend fun answerCall(callId: String): DesktopCall =
        parseCall(rpc("answer_call", JSONObject().put("p_call_id", callId)))

    suspend fun declineCall(callId: String): DesktopCall =
        parseCall(rpc("decline_call", JSONObject().put("p_call_id", callId)))

    suspend fun endCall(callId: String, reason: String = "ended"): DesktopCall =
        parseCall(rpc("end_call", JSONObject().put("p_call_id", callId).put("p_reason", reason)))

    suspend fun markCallConnected(callId: String): DesktopCall =
        parseCall(rpc("mark_call_connected", JSONObject().put("p_call_id", callId)))

    suspend fun incomingRingingCalls(): List<DesktopCall> = withContext(Dispatchers.IO) {
        val userId = requireSessionUserId()
        val rows = getArray(
            "/rest/v1/calls?callee_id=eq.${encode(userId)}&status=eq.ringing&timeout_at=gt.${encode(Instant.now().toString())}" +
                "&select=id,conversation_id,caller_id,callee_id,call_type,status,created_at,timeout_at&order=created_at.desc&limit=5",
        )
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let(::parseCall) }
    }

    suspend fun callSignals(callId: String, afterId: Long = 0): JSONArray =
        getArray(
            "/rest/v1/call_signals?call_id=eq.${encode(callId)}&id=gt.$afterId&select=id,call_id,sender_id,kind,payload,created_at&order=id.asc&limit=200",
        )

    suspend fun sendCallSignal(callId: String, kind: String, payload: JSONObject) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("call_id", callId)
            .put("sender_id", requireSessionUserId())
            .put("kind", kind)
            .put("payload", payload)
        post("/rest/v1/call_signals", body)
    }

    private suspend fun requireAdmin() {
        val capability = client.fetchAdminCapability()
        require(capability.allowed) { "This account is not authorized for Blinkng administration." }
    }

    private suspend fun requireSessionUserId(): String {
        client.fetchProfile() // refreshes token/session before sensitive action
        return client.session?.userId ?: throw IllegalStateException("Sign in to continue.")
    }

    private suspend fun rpc(name: String, body: JSONObject): JSONObject {
        val value = rpcAny(name, body)
        return when (value) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0) ?: JSONObject().put("items", value)
            else -> JSONObject().put("value", value?.toString())
        }
    }

    private suspend fun rpcAny(name: String, body: JSONObject): Any = withContext(Dispatchers.IO) {
        requireSessionUserId()
        val request = requestBuilder("/rest/v1/rpc/$name")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        val text = execute(request).trim()
        when {
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> JSONObject(text)
            text.equals("true", true) || text.equals("false", true) -> text.toBoolean()
            else -> text
        }
    }

    private suspend fun getArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        requireSessionUserId()
        val text = execute(requestBuilder(path).get().build())
        if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    private suspend fun post(path: String, body: JSONObject) = withContext(Dispatchers.IO) {
        requireSessionUserId()
        execute(requestBuilder(path)
            .header("Prefer", "return=minimal")
            .post(body.toString().toRequestBody(jsonMedia))
            .build())
        Unit
    }

    private fun requestBuilder(path: String): Request.Builder {
        val session = client.session ?: throw IllegalStateException("Sign in to continue.")
        return Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("message").ifBlank {
                        JSONObject(text).optString("error_description").ifBlank { JSONObject(text).optString("error") }
                    }
                }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "Blinkng request failed (${response.code})." })
            }
            return text
        }
    }

    private fun parseCall(row: JSONObject): DesktopCall = DesktopCall(
        id = row.optString("id"),
        conversationId = row.optString("conversation_id"),
        callerId = row.optString("caller_id"),
        calleeId = row.optString("callee_id"),
        callType = row.optString("call_type"),
        status = row.optString("status"),
        createdAt = row.optString("created_at"),
        timeoutAt = row.optString("timeout_at"),
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
