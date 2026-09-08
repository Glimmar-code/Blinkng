package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
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

    suspend fun getStoreState(): JSONObject = rpc("get_blink_store_state", JSONObject())

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
