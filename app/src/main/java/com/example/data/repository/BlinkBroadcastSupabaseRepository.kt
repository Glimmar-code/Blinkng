package com.example.data.repository

import com.blinkng.shared.broadcast.*
import com.example.auth.SupabaseSessionRefresher
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Android Broadcast adapter. All writes use authenticated RPCs/server-side checks.
 * No preview rewards or fake data are returned from this production adapter.
 */
class BlinkBroadcastSupabaseRepository(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build(),
    private val baseUrl: String = SupabaseConfig.url.trimEnd('/'),
    private val anonKey: String = SupabaseConfig.anonKey,
) : BlinkBroadcastRepositoryAdapter() {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override fun observeBroadcasts(): Flow<List<Broadcast>> = flow { emit(getActiveBroadcasts()) }

    override suspend fun getActiveBroadcasts(): List<Broadcast> {
        val rows = rpcArray("get_active_broadcasts", JSONObject()
            .put("p_surface", "in_app")
            .put("p_app_version", runCatching { com.example.BuildConfig.VERSION_NAME }.getOrDefault("")))
        return buildList {
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val b = item.optJSONObject("broadcast") ?: continue
                val campaign = item.optJSONObject("campaign_rule")
                val promo = item.optJSONObject("promo_rule")
                val mapped = parseBroadcast(b, campaign, promo).copy(
                    isDismissed = item.optBoolean("dismissed"),
                    isRead = item.optBoolean("read"),
                    claimState = claimStateFromMetadata(item.optJSONObject("claim_state")),
                )
                if (mapped.type == BroadcastType.CAMPAIGN) {
                    val participant = rpcObjectOrNull("get_campaign_progress", JSONObject().put("p_broadcast_id", mapped.id))
                    val participantRow = participant?.optJSONObject("participant")
                    add(mapped.copy(
                        joined = participantRow != null,
                        progress = participantRow?.optInt("progress", 0) ?: 0,
                        completed = participantRow?.optString("completed_at")?.isNotBlank() == true,
                    ))
                } else add(mapped)
            }
        }
    }

    override suspend fun dismiss(id: String) { rpcBoolean("dismiss_broadcast", JSONObject().put("p_broadcast_id", id)) }
    override suspend fun markRead(id: String) {
        val userId = authenticatedUserId()
        try {
            call("/rest/v1/broadcast_reads", "POST",
                JSONArray().put(JSONObject().put("broadcast_id", id).put("user_id", userId)).toString(), true,
                prefer = "return=minimal")
        } catch (error: IllegalStateException) {
            if (error.message?.contains("(409)") != true) throw error
        }
    }
    override suspend fun markAllRead() { getInbox().filterNot { it.read }.forEach { markRead(it.broadcast.id) } }
    override suspend fun trackImpression(id: String) { rpcBoolean("track_broadcast_impression", JSONObject().put("p_broadcast_id", id).put("p_surface", "in_app")) }
    override suspend fun trackClick(id: String) { rpcBoolean("track_broadcast_click", JSONObject().put("p_broadcast_id", id).put("p_surface", "in_app")) }

    override suspend fun joinCampaign(id: String) {
        val result = rpcObject("join_campaign", JSONObject().put("p_broadcast_id", id))
        if (!result.optBoolean("ok")) throw IllegalStateException(result.optString("error", "Unable to join campaign."))
    }

    override suspend fun getCampaignProgress(id: String): Int {
        val value = rpcObjectOrNull("get_campaign_progress", JSONObject().put("p_broadcast_id", id)) ?: return 0
        return value.optJSONObject("participant")?.optInt("progress", 0) ?: 0
    }

    override suspend fun getLeaderboard(id: String): List<BroadcastLeaderboardEntry> {
        val data = rpcObjectOrNull("get_campaign_leaderboard", JSONObject().put("p_broadcast_id", id)) ?: return emptyList()
        val rows = data.optJSONArray("top") ?: JSONArray()
        val entries = buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(BroadcastLeaderboardEntry(
                    rank = row.optInt("rank", i + 1),
                    name = row.optString("name").ifBlank { row.optString("handle").ifBlank { "BLINK user" } },
                    score = row.optInt("progress"),
                    isCurrentUser = false,
                ))
            }
        }
        val me = data.optJSONObject("me")
        if (me != null && me.optInt("rank", 0) > 0 && entries.none { it.rank == me.optInt("rank") }) {
            return entries + BroadcastLeaderboardEntry(me.optInt("rank"), "You", me.optInt("progress"), true)
        }
        return entries
    }

    override suspend fun claimPromo(id: String): BroadcastClaimState {
        val result = rpcObject("claim_promo", JSONObject()
            .put("p_broadcast_id", id)
            .put("p_idempotency_key", UUID.randomUUID().toString())
            .put("p_purchase_amount", 0))
        return claimStateFromResult(result)
    }

    override suspend fun getInbox(): List<BroadcastInboxItem> {
        val rows = rpcArray("get_broadcast_inbox", JSONObject().put("p_tab", "all").put("p_cursor", JSONObject.NULL).put("p_limit", 50))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val broadcast = parseBroadcast(row)
                val received = parseInstant(row.optString("created_at")) ?: Instant.now()
                add(BroadcastInboxItem(broadcast, received, row.optBoolean("is_read")))
            }
        }
    }

    override suspend fun getPrefs(): BroadcastNotificationPrefs {
        val row = rpcObjectOrNull("get_broadcast_prefs", JSONObject()) ?: return BroadcastNotificationPrefs()
        val start = row.optString("quiet_start").takeUnless { it.isBlank() || it == "null" } ?: "22:00"
        val end = row.optString("quiet_end").takeUnless { it.isBlank() || it == "null" } ?: "08:00"
        return BroadcastNotificationPrefs(
            pushEnabled = row.optBoolean("push_enabled", true),
            announcements = row.optBoolean("announcements", true),
            campaigns = row.optBoolean("campaigns", true),
            offers = row.optBoolean("offers", true),
            emailEnabled = row.optBoolean("email", false),
            quietHoursEnabled = row.optString("quiet_start").isNotBlank() && row.optString("quiet_end").isNotBlank(),
            quietHoursStart = start.take(5),
            quietHoursEnd = end.take(5),
        )
    }

    override suspend fun updatePrefs(prefs: BroadcastNotificationPrefs) {
        val current = getPrefs()
        if (!prefs.quietHoursEnabled && current.quietHoursEnabled) {
            throw IllegalStateException("The deployed preferences RPC cannot clear quiet-hours values yet. Other settings were not changed.")
        }
        val patch = JSONObject()
            .put("push_enabled", prefs.pushEnabled)
            .put("announcements", prefs.announcements)
            .put("campaigns", prefs.campaigns)
            .put("offers", prefs.offers)
            .put("email", prefs.emailEnabled)
            .put("timezone", "Africa/Lagos")
        if (prefs.quietHoursEnabled) patch.put("quiet_start", prefs.quietHoursStart).put("quiet_end", prefs.quietHoursEnd)
        rpcObject("update_broadcast_prefs", JSONObject().put("p_patch", patch))
    }

    override suspend fun registerDeviceToken(token: String) {
        call("/rest/v1/rpc/register_broadcast_device_token", "POST", JSONObject()
            .put("p_token", token)
            .put("p_platform", "android")
            .put("p_app_version", runCatching { com.example.BuildConfig.VERSION_NAME }.getOrDefault("")).toString(), true)
    }

    override suspend fun uploadBroadcastImage(imageBytes: ByteArray, mimeType: String): String {
        require(imageBytes.size <= 8 * 1024 * 1024) { "Broadcast image exceeds 8 MiB." }
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) { "Unsupported broadcast image format." }
        val ext = when (mimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "gif" }
        val path = "admin/${UUID.randomUUID()}.$ext"
        val request = request("/storage/v1/object/broadcast-images/${path}", "POST",
            imageBytes.toRequestBody(mimeType.toMediaType()), true,
            extraHeaders = mapOf("x-upsert" to "false"))
        val response = execute(request)
        if (!response.isSuccessful) throw apiError(response.code, response.body?.string().orEmpty())
        response.close()
        return "${baseUrl}/storage/v1/object/public/broadcast-images/${path}"
    }

    override suspend fun getAdminBroadcasts(): List<Broadcast> {
        val rows = restArray("/rest/v1/broadcasts?select=*&order=updated_at.desc")
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val campaign = if (row.optString("type") == "CAMPAIGN") {
                    restArray("/rest/v1/campaign_rules?select=*&broadcast_id=eq.$id").optJSONObject(0)
                } else null
                val promo = if (row.optString("type") == "PROMO") {
                    restArray("/rest/v1/promo_rules?select=*&broadcast_id=eq.$id").optJSONObject(0)
                } else null
                add(parseBroadcast(row, campaign, promo))
            }
        }
    }

    override suspend fun createBroadcast(draft: BroadcastDraft): Broadcast {
        val payload = draftPayload(draft, status = "draft")
        val created = rpcObject("admin_create_broadcast", JSONObject().put("p_payload", payload))
        val id = created.optString("id")
        return getAdminBroadcasts().firstOrNull { it.id == id } ?: parseBroadcast(payload.put("id", id))
    }

    override suspend fun updateBroadcast(id: String, draft: BroadcastDraft): Broadcast {
        val row = restArray("/rest/v1/broadcasts?select=version&id=eq.$id").optJSONObject(0)
            ?: throw IllegalStateException("Broadcast not found.")
        val patch = draftPayload(draft, status = null)
        patch.remove("type")
        patch.remove("confirm_everyone")
        val updated = rpcObject("admin_update_broadcast", JSONObject()
            .put("p_id", id).put("p_version", row.optInt("version")).put("p_patch", patch))
        when (draft.type) {
            BroadcastType.CAMPAIGN -> call("/rest/v1/campaign_rules?broadcast_id=eq.$id", "PATCH",
                JSONObject().put("goal_type", draft.rules.goalType?.name?.lowercase())
                    .put("target", draft.rules.target ?: 1).put("tiers", campaignTiers(draft.rules.rewardTiers)).toString(), true,
                prefer = "return=minimal")
            BroadcastType.PROMO -> call("/rest/v1/promo_rules?broadcast_id=eq.$id", "PATCH",
                JSONObject().put("reward_type", draft.rules.rewardType?.name?.lowercase() ?: "coins")
                    .put("reward_amount", draft.rules.rewardAmount ?: 0).put("per_user_limit", draft.rules.perUserLimit ?: 1)
                    .put("total_budget_cap", draft.rules.totalBudget ?: JSONObject.NULL)
                    .put("min_purchase", draft.rules.minimumPurchase ?: 0)
                    .put("reward_text", "${draft.rules.rewardAmount ?: 0} coins")
                    .put("placement", JSONArray(draft.rules.placement.toList())).toString(), true, prefer = "return=minimal")
            BroadcastType.ANNOUNCEMENT -> Unit
        }
        return parseBroadcast(updated)
    }

    override suspend fun scheduleBroadcast(id: String) {
        val row = restArray("/rest/v1/broadcasts?select=version,starts_at,channels&id=eq.$id").optJSONObject(0)
            ?: throw IllegalStateException("Broadcast not found.")
        val start = parseInstant(row.optString("starts_at"))
        val publishNow = start == null || !start.isAfter(Instant.now())
        rpcObject("admin_update_broadcast", JSONObject().put("p_id", id).put("p_version", row.optInt("version"))
            .put("p_patch", JSONObject().put("status", if (publishNow) "live" else "scheduled")))
        if (publishNow && row.optJSONObject("channels")?.optBoolean("push") == true) {
            val response = execute(request("/functions/v1/send-broadcast", "POST",
                JSONObject().put("broadcast_id", id).toString().toRequestBody(jsonType), true))
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(response.code, raw)
            response.close()
        }
    }

    override suspend fun endBroadcast(id: String) { call("/rest/v1/rpc/admin_end_broadcast", "POST", JSONObject().put("p_id", id).toString(), true) }
    override suspend fun deleteDraft(id: String) { call("/rest/v1/rpc/admin_delete_broadcast", "POST", JSONObject().put("p_id", id).toString(), true) }

    override suspend fun estimateAudience(filters: BroadcastAudienceFilter): BroadcastAudienceEstimate {
        val audience = audienceJson(filters)
        val count = rpcScalar("admin_estimate_broadcast_audience", JSONObject()
            .put("p_filters", audience).put("p_app_version", filters.appVersion.ifBlank { JSONObject.NULL }))
        return BroadcastAudienceEstimate(count.toInt().coerceAtLeast(0))
    }

    override suspend fun sendTest(id: String) {
        val body = JSONObject().put("broadcast_id", id)
        val response = execute(request("/functions/v1/send-test", "POST", body.toString().toRequestBody(jsonType), true))
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw apiError(response.code, raw)
        response.close()
    }

    override suspend fun getAnalytics(id: String): BroadcastAnalytics {
        val row = rpcObjectOrNull("admin_broadcast_analytics", JSONObject().put("p_id", id)) ?: return BroadcastAnalytics()
        val budget = row.optJSONObject("budget")
        val daily = row.optJSONArray("daily") ?: JSONArray()
        val series = buildList { for (i in 0 until daily.length()) add(daily.optJSONObject(i)?.optLong("impressions") ?: 0L) }
        return BroadcastAnalytics(
            reach = row.optLong("reach"),
            impressions = row.optLong("impressions"),
            clicks = row.optLong("clicks"),
            conversions = row.optLong("claims"),
            dismissals = row.optLong("dismissals"),
            claims = row.optLong("claims"),
            budgetUsed = budget?.optLong("used") ?: 0,
            budgetTotal = budget?.optLong("cap") ?: 0,
            series = series,
        )
    }

    override suspend fun getAuditLog(): List<BroadcastAuditEntry> {
        val rows = rpcArray("admin_get_broadcast_audit", JSONObject().put("p_id", JSONObject.NULL).put("p_limit", 100))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val after = row.optJSONObject("after")
                add(BroadcastAuditEntry(
                    id = row.optString("id"),
                    actor = row.optString("actor_id"),
                    action = row.optString("action"),
                    broadcastTitle = after?.optString("title").orEmpty(),
                    at = parseInstant(row.optString("created_at")) ?: Instant.now(),
                ))
            }
        }
    }

    private fun draftPayload(d: BroadcastDraft, status: String?): JSONObject {
        val channels = JSONObject()
        BroadcastChannel.entries.forEach { channel ->
            channels.put(channel.name.lowercase(), channel in d.channels)
        }
        val payload = JSONObject()
            .put("type", d.type.name)
            .put("title", d.title.trim())
            .put("body", d.body.trim())
            .put("image_url", d.imageUrl ?: JSONObject.NULL)
            .put("cta_label", d.ctaLabel)
            .put("deep_link", d.deepLink)
            .put("priority", d.priority)
            .put("starts_at", d.startsAt.toString())
            .put("ends_at", d.endsAt.toString())
            .put("timezone", d.timezone)
            .put("dismissible", d.dismissible)
            .put("is_blocking", d.isBlocking)
            .put("audience", audienceJson(d.audience))
            .put("channels", channels)
        if (status != null) payload.put("status", status)
        if (d.audience.kind == BroadcastAudienceKind.EVERYONE) payload.put("confirm_everyone", true)
        when (d.type) {
            BroadcastType.CAMPAIGN -> {
                payload.put("goal_type", d.rules.goalType?.name?.lowercase() ?: "invites")
                payload.put("target", d.rules.target ?: 1)
                // Campaign tiers now carry explicit coin amounts; never invent an amount.
                payload.put("tiers", campaignTiers(d.rules.rewardTiers))
            }
            BroadcastType.PROMO -> {
                payload.put("reward_type", d.rules.rewardType?.name?.lowercase() ?: "coins")
                payload.put("reward_amount", d.rules.rewardAmount ?: 0)
                payload.put("per_user_limit", d.rules.perUserLimit ?: 1)
                payload.put("total_budget_cap", d.rules.totalBudget ?: JSONObject.NULL)
                payload.put("min_purchase", d.rules.minimumPurchase ?: 0)
                payload.put("reward_text", "${d.rules.rewardAmount ?: 0} coins")
                payload.put("placement", JSONArray(d.rules.placement.toList()))
            }
            BroadcastType.ANNOUNCEMENT -> Unit
        }
        return payload
    }

    private fun audienceJson(filter: BroadcastAudienceFilter): JSONObject = when (filter.kind) {
        BroadcastAudienceKind.EVERYONE -> JSONObject().put("kind", "everyone")
        BroadcastAudienceKind.CAMPUS -> JSONObject().put("kind", "campus").put("campus_ids", JSONArray(filter.campuses.toList()))
        BroadcastAudienceKind.NEW_USERS -> JSONObject().put("kind", "new_users").put("new_users_days", 7)
        BroadcastAudienceKind.INACTIVE_7_DAYS -> JSONObject().put("kind", "inactive").put("inactive_days", 7)
        BroadcastAudienceKind.INACTIVE_30_DAYS -> JSONObject().put("kind", "inactive").put("inactive_days", 30)
        BroadcastAudienceKind.SELLERS -> JSONObject().put("kind", "sellers").put("sellers", true)
        BroadcastAudienceKind.VERIFIED -> JSONObject().put("kind", "verified").put("verified", true)
        BroadcastAudienceKind.APP_VERSION -> JSONObject().put("kind", "app_version").put("min_app_version", filter.appVersion)
    }

    private fun parseBroadcast(row: JSONObject, campaign: JSONObject? = null, promo: JSONObject? = null): Broadcast {
        val id = row.optString("id")
        val type = enumValueOr(row.optString("type"), BroadcastType.ANNOUNCEMENT)
        val audience = parseAudience(row.optJSONObject("audience"))
        val channelsJson = row.optJSONObject("channels") ?: JSONObject()
        val channels = BroadcastChannel.entries.filter { channelsJson.optBoolean(it.name.lowercase(), it == BroadcastChannel.IN_APP) }.toSet()
        val tiers = campaign?.optJSONArray("tiers") ?: JSONArray()
        val rewardTiers = buildList {
            for (i in 0 until tiers.length()) {
                val tier = tiers.optJSONObject(i) ?: continue
                add(BroadcastRewardTier(tier.optInt("threshold"), tier.optString("reward_text").ifBlank { tier.optString("reward") }, tier.optInt("amount", 0)))
            }
        }
        val placement = promo?.optJSONArray("placement")?.let { arr ->
            buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add) }
        }.orEmpty()
        val rules = BroadcastRules(
            goalType = campaign?.optString("goal_type")?.takeIf(String::isNotBlank)?.let { enumValueOr(it, CampaignGoalType.INVITES) },
            target = campaign?.optInt("target")?.takeIf { it > 0 },
            rewardTiers = rewardTiers,
            rewardType = promo?.optString("reward_type")?.takeIf(String::isNotBlank)?.let { enumValueOr(it, PromotionRewardType.COINS) },
            rewardAmount = promo?.optDouble("reward_amount")?.takeIf { it > 0 }?.toInt(),
            perUserLimit = promo?.optInt("per_user_limit")?.takeIf { it > 0 },
            totalBudget = promo?.optDouble("total_budget_cap")?.takeIf { it > 0 }?.toInt(),
            minimumPurchase = promo?.optDouble("min_purchase")?.takeIf { it > 0 }?.toInt(),
            placement = placement,
        )
        return Broadcast(
            id = id,
            type = type,
            title = row.optString("title"),
            body = row.optString("body"),
            imageUrl = row.optString("image_url").takeUnless { it.isBlank() || it == "null" },
            ctaLabel = row.optString("cta_label"),
            deepLink = row.optString("deep_link"),
            priority = row.optInt("priority"),
            startsAt = parseInstant(row.optString("starts_at")) ?: Instant.now(),
            endsAt = parseInstant(row.optString("ends_at")) ?: Instant.now().plusSeconds(86_400),
            dismissible = row.optBoolean("dismissible", true),
            isBlocking = row.optBoolean("is_blocking"),
            target = rules.target,
            rewardText = promo?.optString("reward_text").orEmpty(),
            status = enumValueOr(row.optString("status"), BroadcastStatus.LIVE),
            audience = audience,
            channels = channels,
            rules = rules,
            isPreview = false,
            placement = placement,
        )
    }

    private fun parseAudience(row: JSONObject?): BroadcastAudienceFilter {
        val kind = row?.optString("kind").orEmpty()
        val campuses = row?.optJSONArray("campus_ids") ?: row?.optJSONArray("university_ids") ?: JSONArray()
        val campusSet = buildSet { for (i in 0 until campuses.length()) campuses.optString(i).takeIf(String::isNotBlank)?.let(::add) }
        return when (kind) {
            "campus" -> BroadcastAudienceFilter(BroadcastAudienceKind.CAMPUS, campusSet)
            "new_users" -> BroadcastAudienceFilter(BroadcastAudienceKind.NEW_USERS)
            "inactive" -> if (row?.optInt("inactive_days", 7) == 30) BroadcastAudienceFilter(BroadcastAudienceKind.INACTIVE_30_DAYS) else BroadcastAudienceFilter(BroadcastAudienceKind.INACTIVE_7_DAYS)
            "sellers" -> BroadcastAudienceFilter(BroadcastAudienceKind.SELLERS)
            "verified" -> BroadcastAudienceFilter(BroadcastAudienceKind.VERIFIED)
            "app_version" -> BroadcastAudienceFilter(BroadcastAudienceKind.APP_VERSION, appVersion = row?.optString("min_app_version").orEmpty())
            else -> BroadcastAudienceFilter(BroadcastAudienceKind.EVERYONE)
        }
    }

    private fun claimStateFromMetadata(row: JSONObject?): BroadcastClaimState {
        val status = row?.optString("last_status").orEmpty().uppercase()
        return when (status) {
            "CLAIMED" -> BroadcastClaimState.CLAIMED
            "LIMIT_REACHED" -> BroadcastClaimState.LIMIT_REACHED
            "EXPIRED" -> BroadcastClaimState.EXPIRED
            "SOLD_OUT" -> BroadcastClaimState.SOLD_OUT
            else -> BroadcastClaimState.AVAILABLE
        }
    }

    private fun claimStateFromResult(result: JSONObject): BroadcastClaimState = claimStateFromMetadata(result).let { current ->
        when (result.optString("status").uppercase()) {
            "CLAIMED" -> BroadcastClaimState.CLAIMED
            "LIMIT_REACHED" -> BroadcastClaimState.LIMIT_REACHED
            "EXPIRED" -> BroadcastClaimState.EXPIRED
            "SOLD_OUT" -> BroadcastClaimState.SOLD_OUT
            else -> current
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOr(raw: String, fallback: T): T =
        runCatching { enumValueOf<T>(raw.uppercase()) }.getOrDefault(fallback)

    private fun parseInstant(value: String?): Instant? = value?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun campaignTiers(tiers: List<BroadcastRewardTier>): JSONArray = JSONArray().apply {
        tiers.forEach { tier ->
            put(JSONObject().put("threshold", tier.threshold).put("reward_type", "coins")
                .put("amount", tier.amount).put("reward_text", tier.reward))
        }
    }

    private suspend fun authenticatedUserId(): String {
        val response = execute(request("/auth/v1/user", "GET", null, true))
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw apiError(response.code, raw)
        response.close()
        return JSONObject(raw).optString("id").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Unable to identify the signed-in account.")
    }

    private suspend fun rpcArray(name: String, args: JSONObject): JSONArray = JSONArray(call("/rest/v1/rpc/$name", "POST", args.toString(), true))
    private suspend fun rpcObject(name: String, args: JSONObject): JSONObject = JSONObject(call("/rest/v1/rpc/$name", "POST", args.toString(), true))
    private suspend fun rpcObjectOrNull(name: String, args: JSONObject): JSONObject? = call("/rest/v1/rpc/$name", "POST", args.toString(), true).takeIf { it.isNotBlank() && it != "null" }?.let(::JSONObject)
    private suspend fun rpcBoolean(name: String, args: JSONObject) { call("/rest/v1/rpc/$name", "POST", args.toString(), true) }
    private suspend fun rpcScalar(name: String, args: JSONObject): Long = call("/rest/v1/rpc/$name", "POST", args.toString(), true).trim('"').toLongOrNull() ?: 0
    private suspend fun restArray(path: String): JSONArray = JSONArray(call(path, "GET", null, true))

    private suspend fun call(path: String, method: String, body: String?, authenticated: Boolean, prefer: String? = null): String {
        val req = request(path, method, body?.toRequestBody(jsonType), authenticated, prefer = prefer)
        val response = execute(req)
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw apiError(response.code, raw)
        response.close()
        return raw
    }

    private fun request(
        path: String,
        method: String,
        body: okhttp3.RequestBody?,
        authenticated: Boolean,
        extraHeaders: Map<String, String> = emptyMap(),
        prefer: String? = null,
    ): Request {
        val token = if (authenticated) SupabaseService.accessToken()?.takeIf(String::isNotBlank) ?: anonKey else anonKey
        val builder = Request.Builder().url(if (path.startsWith("http")) path else baseUrl + path)
            .header("apikey", anonKey).header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
        if (body != null) builder.header("Content-Type", if (body.contentType()?.toString()?.startsWith("image/") == true) body.contentType().toString() else "application/json")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        prefer?.let { builder.header("Prefer", it) }
        builder.method(method, body)
        return builder.build()
    }

    private suspend fun execute(request: Request): okhttp3.Response = withContext(Dispatchers.IO) {
        var response = http.newCall(request).execute()
        if (response.code == 401 && SupabaseService.accessToken().isNullOrBlank().not()) {
            val refresh = SupabaseService.refreshToken()?.takeIf(String::isNotBlank)
            if (refresh != null) {
                response.close()
                val refreshed = SupabaseSessionRefresher.refresh(refresh).getOrNull()
                if (refreshed != null) {
                    SupabaseService.saveSession(refreshed.accessToken, refreshed.refreshToken)
                    response = http.newCall(request.newBuilder().header("Authorization", "Bearer ${refreshed.accessToken}").build()).execute()
                }
            }
        }
        response
    }

    private fun apiError(code: Int, raw: String): IllegalStateException {
        val message = runCatching { JSONObject(raw).optString("message").ifBlank { JSONObject(raw).optString("error") } }.getOrNull()
        return IllegalStateException((message?.takeIf(String::isNotBlank) ?: "Broadcast request failed") + " ($code)")
    }
}
