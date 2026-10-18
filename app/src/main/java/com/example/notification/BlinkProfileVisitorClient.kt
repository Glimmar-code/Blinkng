package com.example.notification

import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BlinkProfileVisitor(
    val id: String, val username: String, val name: String,
    val avatarUrl: String, val lastVisitedAt: String,
)
data class BlinkProfileVisitPreferences(val participate: Boolean = false, val notifyVisits: Boolean = false)
data class BlinkProfileVisitorSnapshot(
    val enabled: Boolean, val seenThrough: String, val visitors: List<BlinkProfileVisitor>,
)

/** Only consented, account-scoped RPCs return identifiable profile visitors. */
object BlinkProfileVisitorClient {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val base = SupabaseConfig.url.trimEnd('/')

    private suspend fun rpc(method: String, body: JSONObject = JSONObject()): String =
        withContext(Dispatchers.IO) {
            val token = SupabaseService.accessToken().orEmpty()
            check(token.isNotBlank()) { "Sign in to manage profile visitors." }
            val account = SupabaseService().getCurrentUserId().orEmpty()
            check(account.isNotBlank()) { "An active BLINK account is required." }
            val request = Request.Builder()
                .url("$base/rest/v1/rpc/$method")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(jsonType)).build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                check(response.isSuccessful) { "Profile visitors are unavailable (${response.code}). Try again." }
                check(SupabaseService().getCurrentUserId() == account) {
                    "Account changed while loading profile visitors."
                }
                raw
            }
        }

    suspend fun settings(): BlinkProfileVisitPreferences {
        val json = JSONObject(rpc("get_my_profile_visit_settings"))
        return BlinkProfileVisitPreferences(json.optBoolean("participate"), json.optBoolean("notify_visits"))
    }

    suspend fun saveSettings(participate: Boolean, notifyVisits: Boolean): BlinkProfileVisitPreferences {
        val json = JSONObject(rpc("set_my_profile_visit_settings", JSONObject()
            .put("p_participate", participate)
            .put("p_notify_visits", participate && notifyVisits)))
        return BlinkProfileVisitPreferences(json.optBoolean("participate"), json.optBoolean("notify_visits"))
    }

    suspend fun newCount(): Int = rpc("count_my_new_profile_visitors").trim().toInt().coerceAtLeast(0)

    suspend fun list(): BlinkProfileVisitorSnapshot {
        val json = JSONObject(rpc("list_my_profile_visitors"))
        val array = json.optJSONArray("visitors")
        val visitors = buildList {
            if (array != null) for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val username = row.optString("username")
                if (id.isBlank() || username.isBlank()) continue
                add(BlinkProfileVisitor(
                    id, username, row.optString("full_name").ifBlank { username },
                    row.optString("avatar_url").takeUnless { it == "null" }.orEmpty(),
                    row.optString("last_visited_at"),
                ))
            }
        }
        return BlinkProfileVisitorSnapshot(
            json.optBoolean("enabled"), json.optString("seen_through"), visitors,
        )
    }

    suspend fun acknowledge(seenThrough: String): Boolean {
        if (seenThrough.isBlank()) return false
        return rpc("ack_my_profile_visitors_seen", JSONObject()
            .put("p_seen_through", seenThrough)).trim() == "true"
    }

    suspend fun record(targetProfileId: String): Boolean {
        val viewerId = SupabaseService().getCurrentUserId().orEmpty()
        if (targetProfileId.isBlank() || viewerId.isBlank() || targetProfileId == viewerId) return false
        return rpc("record_opt_in_profile_visit", JSONObject()
            .put("p_profile_id", targetProfileId)).trim() == "true"
    }
}
