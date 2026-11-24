package com.blinkng.desktop.data

import com.blinkng.shared.broadcast.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Windows adapter for the server-authoritative Broadcast contract.
 * It uses the app's existing authenticated desktop Supabase session.
 */
class DesktopBroadcastRepository(
    private val client: DesktopSupabaseClient = DesktopSupabaseClient(),
) : BlinkBroadcastRepositoryAdapter() {
    override fun observeBroadcasts(): Flow<List<Broadcast>> = flow { emit(getActiveBroadcasts()) }

    override suspend fun getActiveBroadcasts(): List<Broadcast> {
        val rows = rpcArray("get_active_broadcasts", JSONObject()
            .put("p_surface", "in_app").put("p_app_version", "desktop"))
        return buildList {
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val row = item.optJSONObject("broadcast") ?: continue
                val mapped = parse(row, item.optJSONObject("campaign_rule"), item.optJSONObject("promo_rule")).copy(
                    isDismissed = item.optBoolean("dismissed"),
                    isRead = item.optBoolean("read"),
                    claimState = claimStateFromMetadata(item.optJSONObject("claim_state")),
                )
                if (mapped.type == BroadcastType.CAMPAIGN) {
                    val progress = rpcObjectOrNull("get_campaign_progress", JSONObject().put("p_broadcast_id", mapped.id))
                    val participant = progress?.optJSONObject("participant")
                    add(mapped.copy(joined = participant != null, progress = participant?.optInt("progress") ?: 0,
                        completed = participant?.optString("completed_at").orEmpty().isNotBlank()))
                } else add(mapped)
            }
        }
    }

    override suspend fun dismiss(id: String) { rpc("dismiss_broadcast", JSONObject().put("p_broadcast_id", id)) }
    override suspend fun markRead(id: String) {
        val user = client.broadcastCurrentUserId ?: error("Desktop session is not signed in.")
        runCatching {
            client.broadcastPostArray("/rest/v1/broadcast_reads", JSONObject()
                .put("broadcast_id", id).put("user_id", user), "return=minimal")
        }.onFailure { if (!it.message.orEmpty().contains("409")) throw it }
    }
    override suspend fun markAllRead() { getInbox().filterNot { it.read }.forEach { markRead(it.broadcast.id) } }
    override suspend fun trackImpression(id: String) { rpc("track_broadcast_impression", JSONObject().put("p_broadcast_id", id).put("p_surface", "desktop")) }
    override suspend fun trackClick(id: String) { rpc("track_broadcast_click", JSONObject().put("p_broadcast_id", id).put("p_surface", "desktop")) }
    override suspend fun joinCampaign(id: String) {
        val result = rpcObject("join_campaign", JSONObject().put("p_broadcast_id", id))
        if (!result.optBoolean("ok")) error(result.optString("error", "Unable to join campaign."))
    }
    override suspend fun getCampaignProgress(id: String): Int =
        rpcObjectOrNull("get_campaign_progress", JSONObject().put("p_broadcast_id", id))
            ?.optJSONObject("participant")?.optInt("progress", 0) ?: 0

    override suspend fun getLeaderboard(id: String): List<BroadcastLeaderboardEntry> {
        val result = rpcObjectOrNull("get_campaign_leaderboard", JSONObject().put("p_broadcast_id", id)) ?: return emptyList()
        val top = result.optJSONArray("top") ?: JSONArray()
        val entries = buildList {
            for (i in 0 until top.length()) {
                val row = top.optJSONObject(i) ?: continue
                add(BroadcastLeaderboardEntry(row.optInt("rank", i + 1),
                    row.optString("name").ifBlank { row.optString("handle").ifBlank { "BLINK user" } },
                    row.optInt("progress")))
            }
        }
        val me = result.optJSONObject("me")
        if (me != null && me.optInt("rank") > 0 && entries.none { it.rank == me.optInt("rank") }) {
            return entries + BroadcastLeaderboardEntry(me.optInt("rank"), "You", me.optInt("progress"), true)
        }
        return entries
    }

    override suspend fun claimPromo(id: String): BroadcastClaimState {
        val result = rpcObject("claim_promo", JSONObject().put("p_broadcast_id", id)
            .put("p_idempotency_key", UUID.randomUUID().toString()).put("p_purchase_amount", 0))
        return claimStateFromResult(result)
    }

    override suspend fun getInbox(): List<BroadcastInboxItem> {
        val rows = rpcArray("get_broadcast_inbox", JSONObject().put("p_tab", "all").put("p_cursor", JSONObject.NULL).put("p_limit", 50))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(BroadcastInboxItem(parse(row), parseInstant(row.optString("created_at")) ?: Instant.now(), row.optBoolean("is_read")))
            }
        }
    }

    override suspend fun getPrefs(): BroadcastNotificationPrefs {
        val row = rpcObjectOrNull("get_broadcast_prefs", JSONObject()) ?: return BroadcastNotificationPrefs()
        val start = row.optString("quiet_start").takeIf { it.isNotBlank() && it != "null" } ?: "22:00"
        val end = row.optString("quiet_end").takeIf { it.isNotBlank() && it != "null" } ?: "08:00"
        return BroadcastNotificationPrefs(row.optBoolean("push_enabled", true), row.optBoolean("announcements", true),
            row.optBoolean("campaigns", true), row.optBoolean("offers", true), row.optBoolean("email", false),
            row.optString("quiet_start").isNotBlank() && row.optString("quiet_end").isNotBlank(), start.take(5), end.take(5))
    }

    override suspend fun updatePrefs(prefs: BroadcastNotificationPrefs) {
        val patch = JSONObject().put("push_enabled", prefs.pushEnabled).put("announcements", prefs.announcements)
            .put("campaigns", prefs.campaigns).put("offers", prefs.offers).put("email", prefs.emailEnabled)
            .put("quiet_start", if (prefs.quietHoursEnabled) prefs.quietHoursStart else JSONObject.NULL)
            .put("quiet_end", if (prefs.quietHoursEnabled) prefs.quietHoursEnd else JSONObject.NULL)
            .put("timezone", "Africa/Lagos")
        rpc("update_broadcast_prefs", JSONObject().put("p_patch", patch))
    }

    // Windows does not register Firebase tokens in this app; keep this no-op explicit.
    override suspend fun registerDeviceToken(token: String) = Unit

    override suspend fun uploadBroadcastImage(imageBytes: ByteArray, mimeType: String): String {
        require(imageBytes.size <= 8 * 1024 * 1024)
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif"))
        val ext = when (mimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "gif" }
        return client.broadcastUpload("admin/${UUID.randomUUID()}.$ext", imageBytes, mimeType)
    }

    override suspend fun getAdminBroadcasts(): List<Broadcast> {
        val rows = client.broadcastGetArray("/rest/v1/broadcasts?select=*&order=updated_at.desc")
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val campaign = if (row.optString("type") == "CAMPAIGN")
                    client.broadcastGetArray("/rest/v1/campaign_rules?select=*&broadcast_id=eq.$id").optJSONObject(0) else null
                val promo = if (row.optString("type") == "PROMO")
                    client.broadcastGetArray("/rest/v1/promo_rules?select=*&broadcast_id=eq.$id").optJSONObject(0) else null
                add(parse(row, campaign, promo))
            }
        }
    }

    override suspend fun createBroadcast(draft: BroadcastDraft): Broadcast {
        val result = rpcObject("admin_create_broadcast", JSONObject().put("p_payload", payload(draft, "draft")))
        val id = result.optString("id")
        return getAdminBroadcasts().firstOrNull { it.id == id } ?: parse(result)
    }

    override suspend fun updateBroadcast(id: String, draft: BroadcastDraft): Broadcast {
        val row = client.broadcastGetArray("/rest/v1/broadcasts?select=version&id=eq.$id").optJSONObject(0)
            ?: error("Broadcast not found.")
        val patch = payload(draft, null).apply { remove("type"); remove("confirm_everyone") }
        val updated = rpcObject("admin_update_broadcast", JSONObject().put("p_id", id)
            .put("p_version", row.optInt("version")).put("p_patch", patch))
        when (draft.type) {
            BroadcastType.CAMPAIGN -> client.broadcastPatch("/rest/v1/campaign_rules?broadcast_id=eq.$id",
                JSONObject().put("goal_type", draft.rules.goalType?.name?.lowercase()).put("target", draft.rules.target ?: 1)
                    .put("tiers", tiers(draft.rules.rewardTiers)))
            BroadcastType.PROMO -> client.broadcastPatch("/rest/v1/promo_rules?broadcast_id=eq.$id",
                JSONObject().put("reward_type", draft.rules.rewardType?.name?.lowercase() ?: "coins")
                    .put("reward_amount", draft.rules.rewardAmount ?: 0).put("per_user_limit", draft.rules.perUserLimit ?: 1)
                    .put("total_budget_cap", draft.rules.totalBudget ?: JSONObject.NULL).put("min_purchase", draft.rules.minimumPurchase ?: 0)
                    .put("reward_text", "${draft.rules.rewardAmount ?: 0} coins").put("placement", JSONArray(draft.rules.placement.toList())))
            BroadcastType.ANNOUNCEMENT -> Unit
        }
        return parse(updated)
    }

    override suspend fun scheduleBroadcast(id: String) {
        val row = client.broadcastGetArray("/rest/v1/broadcasts?select=version,starts_at,channels&id=eq.$id").optJSONObject(0)
            ?: error("Broadcast not found.")
        val start = parseInstant(row.optString("starts_at"))
        val publishNow = start == null || !start.isAfter(Instant.now())
        rpcObject("admin_update_broadcast", JSONObject().put("p_id", id).put("p_version", row.optInt("version"))
            .put("p_patch", JSONObject().put("status", if (publishNow) "live" else "scheduled")))
        if (publishNow && row.optJSONObject("channels")?.optBoolean("push") == true) {
            runCatching { client.broadcastInvoke("send-broadcast", JSONObject().put("broadcast_id", id)) }
        }
    }
    override suspend fun endBroadcast(id: String) { rpc("admin_end_broadcast", JSONObject().put("p_id", id)) }
    override suspend fun deleteDraft(id: String) { rpc("admin_delete_broadcast", JSONObject().put("p_id", id)) }

    override suspend fun estimateAudience(filters: BroadcastAudienceFilter): BroadcastAudienceEstimate =
        BroadcastAudienceEstimate(rpcScalar("admin_estimate_broadcast_audience", JSONObject()
            .put("p_filters", audience(filters)).put("p_app_version", filters.appVersion.ifBlank { JSONObject.NULL })).toInt().coerceAtLeast(0))

    override suspend fun sendTest(id: String) { client.broadcastInvoke("send-test", JSONObject().put("broadcast_id", id)) }

    override suspend fun getAnalytics(id: String): BroadcastAnalytics {
        val data = rpcObjectOrNull("admin_broadcast_analytics", JSONObject().put("p_id", id)) ?: return BroadcastAnalytics()
        val budget = data.optJSONObject("budget")
        val daily = data.optJSONArray("daily") ?: JSONArray()
        val series = buildList { for (i in 0 until daily.length()) add(daily.optJSONObject(i)?.optLong("impressions") ?: 0L) }
        return BroadcastAnalytics(data.optLong("reach"), data.optLong("impressions"), data.optLong("clicks"),
            data.optLong("claims"), data.optLong("dismissals"), data.optLong("claims"), budget?.optLong("used") ?: 0,
            budget?.optLong("cap") ?: 0, series)
    }

    override suspend fun getAuditLog(): List<BroadcastAuditEntry> {
        val rows = rpcArray("admin_get_broadcast_audit", JSONObject().put("p_id", JSONObject.NULL).put("p_limit", 100))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(BroadcastAuditEntry(row.optString("id"), row.optString("actor_id"), row.optString("action"),
                    row.optJSONObject("after")?.optString("title").orEmpty(), parseInstant(row.optString("created_at")) ?: Instant.now()))
            }
        }
    }

    private fun payload(d: BroadcastDraft, status: String?): JSONObject {
        val channels = JSONObject().apply { BroadcastChannel.entries.forEach { put(it.name.lowercase(), it in d.channels) } }
        val out = JSONObject().put("type", d.type.name).put("title", d.title.trim()).put("body", d.body.trim())
            .put("image_url", d.imageUrl ?: JSONObject.NULL).put("cta_label", d.ctaLabel).put("deep_link", d.deepLink)
            .put("priority", d.priority).put("starts_at", d.startsAt.toString()).put("ends_at", d.endsAt.toString())
            .put("timezone", d.timezone).put("dismissible", d.dismissible).put("is_blocking", d.isBlocking)
            .put("audience", audience(d.audience)).put("channels", channels)
        status?.let { out.put("status", it) }
        if (d.audience.kind == BroadcastAudienceKind.EVERYONE) out.put("confirm_everyone", true)
        when (d.type) {
            BroadcastType.CAMPAIGN -> out.put("goal_type", d.rules.goalType?.name?.lowercase() ?: "invites")
                .put("target", d.rules.target ?: 1).put("tiers", tiers(d.rules.rewardTiers))
            BroadcastType.PROMO -> out.put("reward_type", d.rules.rewardType?.name?.lowercase() ?: "coins")
                .put("reward_amount", d.rules.rewardAmount ?: 0).put("per_user_limit", d.rules.perUserLimit ?: 1)
                .put("total_budget_cap", d.rules.totalBudget ?: JSONObject.NULL).put("min_purchase", d.rules.minimumPurchase ?: 0)
                .put("reward_text", "${d.rules.rewardAmount ?: 0} coins").put("placement", JSONArray(d.rules.placement.toList()))
            BroadcastType.ANNOUNCEMENT -> Unit
        }
        return out
    }

    private fun tiers(list: List<BroadcastRewardTier>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("threshold", it.threshold).put("reward_type", "coins").put("amount", it.amount).put("reward_text", it.reward)) }
    }
    private fun audience(filter: BroadcastAudienceFilter): JSONObject = when (filter.kind) {
        BroadcastAudienceKind.EVERYONE -> JSONObject().put("kind", "everyone")
        BroadcastAudienceKind.CAMPUS -> JSONObject().put("kind", "campus").put("campus_ids", JSONArray(filter.campuses.toList()))
        BroadcastAudienceKind.NEW_USERS -> JSONObject().put("kind", "new_users").put("new_users_days", 7)
        BroadcastAudienceKind.INACTIVE_7_DAYS -> JSONObject().put("kind", "inactive").put("inactive_days", 7)
        BroadcastAudienceKind.INACTIVE_30_DAYS -> JSONObject().put("kind", "inactive").put("inactive_days", 30)
        BroadcastAudienceKind.SELLERS -> JSONObject().put("kind", "sellers").put("sellers", true)
        BroadcastAudienceKind.VERIFIED -> JSONObject().put("kind", "verified").put("verified", true)
        BroadcastAudienceKind.APP_VERSION -> JSONObject().put("kind", "app_version").put("min_app_version", filter.appVersion)
    }

    private fun parse(row: JSONObject, campaign: JSONObject? = null, promo: JSONObject? = null): Broadcast {
        val type = enumValueOr(row.optString("type"), BroadcastType.ANNOUNCEMENT)
        val channelsJson = row.optJSONObject("channels") ?: JSONObject()
        val channels = BroadcastChannel.entries.filter { channelsJson.optBoolean(it.name.lowercase(), it == BroadcastChannel.IN_APP) }.toSet()
        val tierRows = campaign?.optJSONArray("tiers") ?: JSONArray()
        val tierList = buildList {
            for (i in 0 until tierRows.length()) {
                val t = tierRows.optJSONObject(i) ?: continue
                add(BroadcastRewardTier(t.optInt("threshold"), t.optString("reward_text").ifBlank { t.optString("reward") }, t.optInt("amount")))
            }
        }
        val placement = promo?.optJSONArray("placement")?.let { a -> buildSet { for (i in 0 until a.length()) a.optString(i).takeIf(String::isNotBlank)?.let(::add) } }.orEmpty()
        val rules = BroadcastRules(
            goalType = campaign?.optString("goal_type")?.takeIf(String::isNotBlank)?.let { enumValueOr(it, CampaignGoalType.INVITES) },
            target = campaign?.optInt("target")?.takeIf { it > 0 }, rewardTiers = tierList,
            rewardType = promo?.optString("reward_type")?.takeIf(String::isNotBlank)?.let { enumValueOr(it, PromotionRewardType.COINS) },
            rewardAmount = promo?.optDouble("reward_amount")?.takeIf { it > 0 }?.toInt(),
            perUserLimit = promo?.optInt("per_user_limit")?.takeIf { it > 0 },
            totalBudget = promo?.optDouble("total_budget_cap")?.takeIf { it > 0 }?.toInt(),
            minimumPurchase = promo?.optDouble("min_purchase")?.takeIf { it > 0 }?.toInt(), placement = placement)
        val aud = row.optJSONObject("audience")
        val kind = aud?.optString("kind").orEmpty()
        val campusArray = aud?.optJSONArray("campus_ids") ?: JSONArray()
        val campusSet = buildSet { for (i in 0 until campusArray.length()) campusArray.optString(i).takeIf(String::isNotBlank)?.let(::add) }
        val filter = when(kind) {
            "campus" -> BroadcastAudienceFilter(BroadcastAudienceKind.CAMPUS, campusSet)
            "new_users" -> BroadcastAudienceFilter(BroadcastAudienceKind.NEW_USERS)
            "inactive" -> if (aud?.optInt("inactive_days",7)==30) BroadcastAudienceFilter(BroadcastAudienceKind.INACTIVE_30_DAYS) else BroadcastAudienceFilter(BroadcastAudienceKind.INACTIVE_7_DAYS)
            "sellers" -> BroadcastAudienceFilter(BroadcastAudienceKind.SELLERS)
            "verified" -> BroadcastAudienceFilter(BroadcastAudienceKind.VERIFIED)
            "app_version" -> BroadcastAudienceFilter(BroadcastAudienceKind.APP_VERSION, appVersion = aud?.optString("min_app_version").orEmpty())
            else -> BroadcastAudienceFilter()
        }
        return Broadcast(row.optString("id"), type, row.optString("title"), row.optString("body"),
            row.optString("image_url").takeUnless { it.isBlank() || it == "null" }, row.optString("cta_label"), row.optString("deep_link"),
            row.optInt("priority"), parseInstant(row.optString("starts_at")) ?: Instant.now(),
            parseInstant(row.optString("ends_at")) ?: Instant.now().plusSeconds(86400), row.optBoolean("dismissible",true),
            row.optBoolean("is_blocking"), rules.target, promo?.optString("reward_text").orEmpty(), claimStateFromMetadata(null),
            enumValueOr(row.optString("status"), BroadcastStatus.LIVE), filter, channels, rules,
            isRead = row.optBoolean("is_read"), isDismissed = row.optBoolean("dismissed"), placement = placement)
    }

    private fun claimStateFromMetadata(row: JSONObject?): BroadcastClaimState = when (row?.optString("last_status").orEmpty().uppercase()) {
        "CLAIMED" -> BroadcastClaimState.CLAIMED
        "LIMIT_REACHED" -> BroadcastClaimState.LIMIT_REACHED
        "EXPIRED" -> BroadcastClaimState.EXPIRED
        "SOLD_OUT" -> BroadcastClaimState.SOLD_OUT
        else -> BroadcastClaimState.AVAILABLE
    }
    private fun claimStateFromResult(result: JSONObject): BroadcastClaimState = when(result.optString("status").uppercase()) {
        "CLAIMED" -> BroadcastClaimState.CLAIMED
        "LIMIT_REACHED" -> BroadcastClaimState.LIMIT_REACHED
        "EXPIRED" -> BroadcastClaimState.EXPIRED
        "SOLD_OUT" -> BroadcastClaimState.SOLD_OUT
        else -> claimStateFromMetadata(result)
    }
    private inline fun <reified T: Enum<T>> enumValueOr(raw:String,fallback:T):T = runCatching { enumValueOf<T>(raw.uppercase()) }.getOrDefault(fallback)
    private fun parseInstant(value:String?):Instant?=value?.takeIf { it.isNotBlank()&&it!="null" }?.let { runCatching { Instant.parse(it) }.getOrNull() }
    private suspend fun rpc(name:String,args:JSONObject):Any=client.broadcastRpc(name,args)
    private suspend fun rpcObject(name:String,args:JSONObject):JSONObject=(rpc(name,args) as? JSONObject)?:error("Unexpected Broadcast RPC result.")
    private suspend fun rpcObjectOrNull(name:String,args:JSONObject):JSONObject?=runCatching { rpcObject(name,args) }.getOrNull()
    private suspend fun rpcArray(name:String,args:JSONObject):JSONArray=(rpc(name,args) as? JSONArray)?:JSONArray()
    private suspend fun rpcScalar(name:String,args:JSONObject):Long=(rpc(name,args) as? JSONObject)?.optString("value")?.trim()?.removeSurrounding("\"")?.toLongOrNull()?:0
}
