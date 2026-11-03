package com.example.items

import android.content.Context
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BlinkWeatherRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(latitude: Double, longitude: Double): Result<BlinkWeatherSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                SupabaseService.initialize(appContext)
                val token = SupabaseService.accessToken().orEmpty().ifBlank { SupabaseConfig.anonKey }
                val url = buildString {
                    append(SupabaseConfig.url.trimEnd('/'))
                    append("/functions/v1/blink-weather?lat=")
                    append(latitude)
                    append("&lon=")
                    append(longitude)
                }
                val request = Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    check(response.isSuccessful) {
                        "Weather service unavailable (${response.code})."
                    }
                    BlinkItemPreferences.saveWeatherCache(appContext, raw)
                    parse(raw)
                }
            }
        }

    fun cached(): BlinkWeatherSnapshot? =
        BlinkItemPreferences.cachedWeatherJson(appContext)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parse(it) }.getOrNull() }

    private fun parse(raw: String): BlinkWeatherSnapshot {
        val json = JSONObject(raw)
        val alertsJson = json.optJSONArray("alerts") ?: JSONArray()
        val alerts = buildList {
            for (index in 0 until alertsJson.length()) {
                val item = alertsJson.optJSONObject(index) ?: continue
                add(
                    BlinkWeatherAlert(
                        id = item.optString("id").ifBlank {
                            listOf(
                                item.optString("source"),
                                item.optString("title"),
                                item.optLong("startsAt", 0L).toString()
                            ).joinToString(":")
                        },
                        title = item.optString("title", "Weather alert"),
                        description = item.optString("description"),
                        instruction = item.optString("instruction"),
                        source = item.optString("source", "Weather authority"),
                        severity = item.optString("severity", "unknown"),
                        urgency = item.optString("urgency", "unknown"),
                        startsAtEpochSeconds = item.optLong("startsAt", 0L),
                        endsAtEpochSeconds = item.optLong("endsAt", 0L),
                        isOfficial = item.optBoolean("official", true)
                    )
                )
            }
        }

        val nextRain = json.opt("nextRainAt").let { value ->
            when (value) {
                is Number -> value.toLong().takeIf { it > 0L }
                is String -> value.toLongOrNull()?.takeIf { it > 0L }
                else -> null
            }
        }

        return BlinkWeatherSnapshot(
            temperatureC = json.optDouble("temperatureC", 0.0),
            feelsLikeC = json.optDouble("feelsLikeC", json.optDouble("temperatureC", 0.0)),
            humidityPercent = json.optInt("humidityPercent", 0).coerceIn(0, 100),
            windKph = json.optDouble("windKph", 0.0).coerceAtLeast(0.0),
            condition = json.optString("condition", "Unavailable"),
            precipitationProbabilityPercent =
                json.optInt("precipitationProbabilityPercent", 0).coerceIn(0, 100),
            nextRainAtEpochSeconds = nextRain,
            provider = json.optString("provider"),
            officialAlertsAvailable = json.optBoolean("officialAlertsAvailable", false),
            alerts = alerts,
            fetchedAtEpochSeconds = json.optLong(
                "fetchedAt",
                System.currentTimeMillis() / 1_000L
            )
        )
    }
}
