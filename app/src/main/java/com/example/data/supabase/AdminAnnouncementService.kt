package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AdminAnnouncementDetail(
    val activityId: String,
    val notificationId: String,
    val campaignId: String,
    val topic: String,
    val subtopic: String,
    val message: String,
    val senderName: String,
    val createdAt: String
)

class AdminAnnouncementService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    suspend fun fetchForActivity(activityId: String): Result<AdminAnnouncementDetail?> = runCatching {
        require(activityId.isNotBlank()) { "Notification activity is required." }
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) {
                throw IllegalStateException("Your Blink session has expired. Please sign in again.")
            }

            // Official announcements are stored both in notifications and activities.
            // The existing authenticated RPC expects an activity UUID, not the
            // "notification:<uuid>" identifier shown by the canonical inbox.
            var resolvedActivityId = activityId.trim()
            if (resolvedActivityId.startsWith("notification:")) {
                val notificationId = resolvedActivityId.removePrefix("notification:")
                require(runCatching { java.util.UUID.fromString(notificationId) }.isSuccess) {
                    "Invalid BLINK announcement."
                }
                val uid = session.getCurrentUserId()
                    ?: throw IllegalStateException("A signed-in Blink account is required.")
                val jwt = SupabaseService.accessToken()
                    ?: throw IllegalStateException("Your Blink session has expired.")
                val lookup = Request.Builder()
                    .url("$baseUrl/rest/v1/activities?select=id&recipient_id=eq.$uid&entity_type=eq.notification&entity_id=eq.$notificationId&limit=1")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $jwt")
                    .get()
                    .build()
                resolvedActivityId = client.newCall(lookup).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Couldn't resolve this BLINK announcement.")
                    }
                    org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                        .optJSONObject(0)?.optString("id").orEmpty()
                }
                if (resolvedActivityId.isBlank()) return@withContext null
            }

            fun request(): Request {
                val token = SupabaseService.accessToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("A signed-in Blink account is required.")
                val body = JSONObject()
                    .put("p_activity_id", resolvedActivityId)
                    .toString()
                    .toRequestBody(jsonMediaType)
                return Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/get_admin_message_detail")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .post(body)
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
                val raw = it.body?.string().orEmpty().trim()
                if (!it.isSuccessful) {
                    throw IllegalStateException(parseError(raw, "Couldn't open this Blink message."))
                }
                if (raw.isBlank() || raw == "null") return@withContext null

                val json = JSONObject(raw)
                AdminAnnouncementDetail(
                    activityId = json.optString("activity_id", activityId),
                    notificationId = json.optString("notification_id"),
                    campaignId = json.optString("campaign_id"),
                    topic = json.optString("topic", "Blink").ifBlank { "Blink" },
                    subtopic = json.optString("subtopic"),
                    message = json.optString("message"),
                    senderName = json.optString("sender_name", "Blink Admin").ifBlank { "Blink Admin" },
                    createdAt = json.optString("created_at")
                )
            }
        }
    }

    private fun parseError(body: String, fallback: String): String = runCatching {
        val json = JSONObject(body)
        json.optString("message").takeIf { it.isNotBlank() }
            ?: json.optString("error_description").takeIf { it.isNotBlank() }
            ?: fallback
    }.getOrDefault(fallback)
}
