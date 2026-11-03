package com.example.items

import android.content.Context
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
import java.time.Instant
import java.util.concurrent.TimeUnit

data class BlinkMyLiveLocationSession(
    val id: String,
    val expiresAt: String,
    val recipientCount: Int
)

class BlinkLiveLocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun startSession(
        recipientIds: Collection<String>,
        durationMinutes: Int
    ): Result<BlinkLiveLocationSession> = withContext(Dispatchers.IO) {
        runCatching {
            require(durationMinutes in setOf(15, 60, 240)) {
                "Duration must be 15 minutes, 1 hour, or 4 hours."
            }
            val cleanIds = recipientIds
                .map(String::trim)
                .filter { it.isNotBlank() }
                .distinct()
            require(cleanIds.isNotEmpty()) { "Choose at least one person." }

            val payload = JSONObject()
                .put("p_recipient_ids", JSONArray(cleanIds))
                .put("p_duration_minutes", durationMinutes)
            val raw = rpc("start_live_location_session", payload)
            val row = JSONArray(raw.ifBlank { "[]" }).optJSONObject(0)
                ?: error("BLINK could not start live location.")
            BlinkLiveLocationSession(
                id = row.optString("session_id").ifBlank {
                    error("BLINK did not return a live-location session.")
                },
                expiresAt = row.optString("expires_at")
            )
        }
    }

    suspend fun currentSession(): Result<BlinkMyLiveLocationSession?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = rpc("get_my_active_live_location_session", JSONObject())
                val row = JSONArray(raw.ifBlank { "[]" }).optJSONObject(0)
                    ?: return@runCatching null
                BlinkMyLiveLocationSession(
                    id = row.optString("session_id"),
                    expiresAt = row.optString("expires_at"),
                    recipientCount = row.optInt("recipient_count", 0)
                ).takeIf { it.id.isNotBlank() }
            }
        }

    suspend fun updatePosition(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "Live-location session is missing." }
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                "Invalid coordinates."
            }
            rpc(
                "update_live_location_position",
                JSONObject()
                    .put("p_session_id", sessionId)
                    .put("p_latitude", latitude)
                    .put("p_longitude", longitude)
                    .put("p_accuracy_meters", accuracyMeters.coerceAtLeast(0f))
            )
            Unit
        }
    }

    suspend fun stopSession(sessionId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (sessionId.isBlank()) return@runCatching false
                rpc(
                    "stop_live_location_session",
                    JSONObject().put("p_session_id", sessionId)
                ).trim().equals("true", ignoreCase = true)
            }
        }

    suspend fun activeLocationsSharedWithMe(): Result<List<BlinkSharedLocation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rows = JSONArray(rpc("get_active_live_locations", JSONObject()).ifBlank { "[]" })
                buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        val sessionId = row.optString("session_id")
                        val ownerId = row.optString("owner_id")
                        val latitude = row.optDouble("latitude", Double.NaN)
                        val longitude = row.optDouble("longitude", Double.NaN)
                        if (
                            sessionId.isBlank() ||
                            ownerId.isBlank() ||
                            !latitude.isFinite() ||
                            !longitude.isFinite()
                        ) continue
                        add(
                            BlinkSharedLocation(
                                sessionId = sessionId,
                                ownerId = ownerId,
                                username = row.optString("username"),
                                fullName = row.optString("full_name"),
                                avatarUrl = row.optString("avatar_url"),
                                latitude = latitude,
                                longitude = longitude,
                                accuracyMeters = row.optDouble("accuracy_meters", 0.0).toFloat(),
                                updatedAt = row.optString("updated_at"),
                                expiresAt = row.optString("expires_at")
                            )
                        )
                    }
                }
            }
        }

    fun expiresAtMillis(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun rpc(function: String, body: JSONObject): String {
        SupabaseService.initialize(appContext)
        val token = SupabaseService.accessToken().orEmpty()
        check(token.isNotBlank()) { "Sign in to use this BLINK Item." }

        val request = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$function")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                parseError(raw).ifBlank { "BLINK Item request failed (${response.code})." }
            }
            return raw
        }
    }

    private fun parseError(raw: String): String =
        runCatching {
            val json = JSONObject(raw)
            json.optString("message").ifBlank {
                json.optString("details").ifBlank { json.optString("hint") }
            }
        }.getOrDefault("")
}
