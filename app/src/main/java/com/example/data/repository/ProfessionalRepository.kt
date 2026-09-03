package com.example.data.repository

import com.example.data.models.AppSettings
import com.example.data.models.MarketplaceOrder
import com.example.data.models.UserProfile
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ProfessionalRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun setPostPreference(postId: String, preference: String = "not_interested"): Boolean =
        rpc("set_post_preference", JSONObject().put("p_post_id", postId).put("p_preference", preference)).isNotBlank()

    suspend fun blockUser(username: String): Boolean = withContext(Dispatchers.IO) {
        val profile = supabaseService.fetchProfileByUsername(username.removePrefix("@")) ?: return@withContext false
        rpc("block_user", JSONObject().put("p_target_id", profile.id)).isNotBlank()
    }

    suspend fun unblockUser(userId: String): Boolean =
        rpc("unblock_user", JSONObject().put("p_target_id", userId)).isNotBlank()

    suspend fun reportUser(username: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        val profile = supabaseService.fetchProfileByUsername(username.removePrefix("@")) ?: return@withContext false
        rpc("report_user", JSONObject().put("p_user_id", profile.id).put("p_reason", reason.trim())).isNotBlank()
    }

    suspend fun fetchBlockedProfiles(): List<UserProfile> = withContext(Dispatchers.IO) {
        val uid = supabaseService.getCurrentUserId() ?: return@withContext emptyList()
        val arr = getArray("/rest/v1/blocks?blocker_id=eq.${encode(uid)}&select=blocked_id&order=created_at.desc")
        buildList {
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("blocked_id")
                if (id.isNotBlank()) supabaseService.fetchProfileById(id)?.let { add(it) }
            }
        }
    }

    suspend fun fetchSettings(): AppSettings = withContext(Dispatchers.IO) {
        val uid = supabaseService.getCurrentUserId() ?: return@withContext AppSettings()
        var arr = getArray("/rest/v1/user_settings?user_id=eq.${encode(uid)}&select=*&limit=1")
        if (arr.length() == 0) {
            write(
                "/rest/v1/user_settings?on_conflict=user_id",
                JSONObject().put("user_id", uid),
                "POST",
                "resolution=merge-duplicates,return=representation"
            )
            arr = getArray("/rest/v1/user_settings?user_id=eq.${encode(uid)}&select=*&limit=1")
        }
        if (arr.length() == 0) AppSettings() else parseSettings(arr.getJSONObject(0))
    }

    suspend fun updateSettings(settings: AppSettings): Boolean = withContext(Dispatchers.IO) {
        val uid = supabaseService.getCurrentUserId() ?: return@withContext false
        write(
            "/rest/v1/user_settings?on_conflict=user_id",
            JSONObject()
                .put("user_id", uid)
                .put("theme", settings.theme)
                .put("language", settings.language)
                .put("push_notifs_enabled", settings.pushNotifications)
                .put("email_notifs_enabled", settings.emailNotifications)
                .put("dm_privacy", settings.dmPrivacy)
                .put("private_account", settings.privateAccount)
                .put("show_online_status", settings.showOnlineStatus)
                .put("read_receipts", settings.readReceipts)
                .put("autoplay_videos", settings.autoplayVideos)
                .put("data_saver", settings.dataSaver)
                .put("reduce_motion", settings.reduceMotion)
                .put("updated_at", java.time.Instant.now().toString()),
            "POST",
            "resolution=merge-duplicates,return=minimal"
        )
    }

    suspend fun exportAccountData(): String =
        rpc("export_my_account_data", JSONObject())

    suspend fun deleteAccount(usernameConfirmation: String): Boolean =
        rpc("delete_my_account", JSONObject().put("p_confirmation", usernameConfirmation.trim())).isNotBlank()

    suspend fun createMarketplaceOrder(itemId: String, quantity: Int = 1): Result<String> =
        runCatching {
            rpc("create_marketplace_order", JSONObject().put("p_item_id", itemId).put("p_quantity", quantity))
                .trim().removeSurrounding(""")
        }.filterCatching { it.isNotBlank() }

    suspend fun fetchMarketplaceOrders(): List<MarketplaceOrder> = withContext(Dispatchers.IO) {
        val uid = supabaseService.getCurrentUserId() ?: return@withContext emptyList()
        getArray(
            "/rest/v1/marketplace_orders?or=(buyer_id.eq.${encode(uid)},seller_id.eq.${encode(uid)})&select=*&order=created_at.desc&limit=100"
        ).mapObjects(::parseOrder)
    }

    suspend fun updateOrderStatus(orderId: String, status: String): Boolean =
        rpc(
            "update_marketplace_order_status",
            JSONObject().put("p_order_id", orderId).put("p_status", status)
        ).isNotBlank()

    suspend fun fetchWishlistIds(): Set<String> = withContext(Dispatchers.IO) {
        val uid = supabaseService.getCurrentUserId() ?: return@withContext emptySet()
        getArray("/rest/v1/marketplace_wishlist?user_id=eq.${encode(uid)}&select=item_id")
            .mapObjects { it.optString("item_id") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun setWishlist(itemId: String, saved: Boolean): Boolean = withContext(Dispatchers.IO) {
        val uid = supabaseService.getCurrentUserId() ?: return@withContext false
        if (saved) {
            write(
                "/rest/v1/marketplace_wishlist?on_conflict=user_id,item_id",
                JSONObject().put("user_id", uid).put("item_id", itemId),
                "POST",
                "resolution=merge-duplicates,return=minimal"
            )
        } else {
            request(
                Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/marketplace_wishlist?user_id=eq.${encode(uid)}&item_id=eq.${encode(itemId)}")
                    .headers(authHeaders())
                    .delete()
                    .build()
            ).first
        }
    }

    suspend fun submitMarketplaceReview(orderId: String, revieweeId: String, rating: Int, comment: String): Boolean =
        withContext(Dispatchers.IO) {
            val uid = supabaseService.getCurrentUserId() ?: return@withContext false
            write(
                "/rest/v1/marketplace_reviews",
                JSONObject()
                    .put("order_id", orderId)
                    .put("reviewer_id", uid)
                    .put("reviewee_id", revieweeId)
                    .put("rating", rating.coerceIn(1, 5))
                    .put("comment", comment.trim().take(1000)),
                "POST"
            )
        }

    suspend fun updateMarketItem(itemId: String, fields: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val (ok, _) = request(
                Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/market_items?id=eq.${encode(itemId)}")
                    .headers(authHeaders())
                    .addHeader("Content-Type", "application/json")
                    .patch(fields.toString().toRequestBody(jsonType))
                    .build()
            )
            ok
        }

    suspend fun deleteMarketItem(itemId: String): Boolean = withContext(Dispatchers.IO) {
        request(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/market_items?id=eq.${encode(itemId)}")
                .headers(authHeaders())
                .delete()
                .build()
        ).first
    }

    suspend fun createGroup(title: String, memberIds: List<String>): Result<String> =
        runCatching {
            rpc(
                "create_group_conversation",
                JSONObject()
                    .put("p_title", title.trim())
                    .put("p_member_ids", JSONArray(memberIds.filter { it.isNotBlank() }))
            ).trim().removeSurrounding(""")
        }.filterCatching { it.isNotBlank() }

    suspend fun sendGroupMessage(conversationId: String, content: String): Boolean =
        rpc(
            "send_group_message",
            JSONObject().put("p_conversation_id", conversationId).put("p_content", content.trim())
        ).isNotBlank()

    suspend fun updateConversationSettings(
        conversationId: String,
        archived: Boolean? = null,
        mutedUntil: String? = null,
        disappearingSeconds: Int? = null,
        theme: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = supabaseService.getCurrentUserId() ?: return@withContext false
        val body = JSONObject().put("conversation_id", conversationId).put("user_id", uid)
        archived?.let { body.put("archived", it) }
        if (mutedUntil != null) body.put("muted_until", mutedUntil.ifBlank { JSONObject.NULL })
        disappearingSeconds?.let { body.put("disappearing_seconds", it.coerceIn(0, 2592000)) }
        theme?.let { body.put("theme", it.take(40)) }
        body.put("updated_at", java.time.Instant.now().toString())
        write(
            "/rest/v1/conversation_settings?on_conflict=conversation_id,user_id",
            body,
            "POST",
            "resolution=merge-duplicates,return=minimal"
        )
    }

    private fun parseSettings(o: JSONObject) = AppSettings(
        theme = o.optString("theme", "system"),
        language = o.optString("language", "en"),
        pushNotifications = o.optBoolean("push_notifs_enabled", true),
        emailNotifications = o.optBoolean("email_notifs_enabled", true),
        dmPrivacy = o.optString("dm_privacy", "everyone"),
        privateAccount = o.optBoolean("private_account", false),
        showOnlineStatus = o.optBoolean("show_online_status", true),
        readReceipts = o.optBoolean("read_receipts", true),
        autoplayVideos = o.optBoolean("autoplay_videos", true),
        dataSaver = o.optBoolean("data_saver", false),
        reduceMotion = o.optBoolean("reduce_motion", false)
    )

    private fun parseOrder(o: JSONObject) = MarketplaceOrder(
        id = o.optString("id"),
        itemId = o.optString("item_id"),
        buyerId = o.optString("buyer_id"),
        sellerId = o.optString("seller_id"),
        quantity = o.optInt("quantity", 1),
        unitPrice = o.optDouble("unit_price", 0.0),
        totalPrice = o.optDouble("total_price", 0.0),
        currency = o.optString("currency", "NGN"),
        status = o.optString("status"),
        createdAt = o.optString("created_at"),
        updatedAt = o.optString("updated_at")
    )

    private suspend fun rpc(name: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val (ok, raw) = request(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$name")
                .headers(authHeaders())
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonType))
                .build()
        )
        if (!ok) throw IllegalStateException(raw.ifBlank { "$name failed" })
        raw
    }

    private suspend fun getArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        val (ok, raw) = request(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}$path")
                .headers(authHeaders())
                .get()
                .build()
        )
        if (!ok) return@withContext JSONArray()
        JSONArray(if (raw.isBlank()) "[]" else raw)
    }

    private suspend fun write(
        path: String,
        body: JSONObject,
        method: String,
        prefer: String = "return=minimal"
    ): Boolean = withContext(Dispatchers.IO) {
        val requestBody = body.toString().toRequestBody(jsonType)
        val builder = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}$path")
            .headers(authHeaders())
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", prefer)
        val request = when (method) {
            "PATCH" -> builder.patch(requestBody).build()
            "PUT" -> builder.put(requestBody).build()
            else -> builder.post(requestBody).build()
        }
        request(request).first
    }

    private fun authHeaders(): okhttp3.Headers {
        val token = SupabaseService.accessToken() ?: throw IllegalStateException("Not authenticated.")
        return okhttp3.Headers.Builder()
            .add("apikey", SupabaseConfig.anonKey)
            .add("Authorization", "Bearer $token")
            .build()
    }

    private fun request(request: Request): Pair<Boolean, String> =
        client.newCall(request).execute().use { response ->
            response.isSuccessful to response.body?.string().orEmpty()
        }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList { for (i in 0 until length()) add(transform(getJSONObject(i))) }
}
