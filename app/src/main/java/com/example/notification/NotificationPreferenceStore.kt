package com.example.notification

import android.content.Context
import android.util.Log
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Local mirror of the server notification preference row.
 *
 * Defaults are permissive so an older account that has never saved preferences
 * keeps receiving the same alerts it received before this feature was introduced.
 */
object NotificationPreferenceStore {
    private const val TAG = "BlinkNotifyPrefs"
    private const val PREFS = "blink_notification_preferences"
    private const val MASTER = "master"
    private const val QUIET_ENABLED = "quiet_enabled"
    private const val QUIET_START = "quiet_start_minute"
    private const val QUIET_END = "quiet_end_minute"
    private const val TIMEZONE = "timezone"
    private const val DEFAULT_QUIET_START = 22 * 60
    private const val DEFAULT_QUIET_END = 7 * 60

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isAllowed(context: Context, type: BlinkNotificationType): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MASTER, true)) return false
        if (!prefs.getBoolean(type.preferenceKey, true)) return false
        if (!type.critical && isQuietNow(context)) return false
        return true
    }

    fun isIntendedForCurrentAccount(context: Context, recipientId: String?): Boolean {
        val target = recipientId.orEmpty().trim()
        if (target.isBlank()) return true // backward-compatible payload
        return runCatching {
            SupabaseService.initialize(context.applicationContext)
            val current = SupabaseService().getCurrentUserId().orEmpty().trim()
            current.isNotBlank() && current == target
        }.getOrDefault(false)
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(MASTER, enabled).apply()
        syncToServerAsync(context)
    }

    fun setCategoryEnabled(context: Context, type: BlinkNotificationType, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(type.preferenceKey, enabled).apply()
        syncToServerAsync(context)
    }

    fun isCategoryEnabled(context: Context, type: BlinkNotificationType): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(type.preferenceKey, true)

    fun isMasterEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(MASTER, true)

    fun setQuietHours(
        context: Context,
        enabled: Boolean,
        startMinute: Int = DEFAULT_QUIET_START,
        endMinute: Int = DEFAULT_QUIET_END,
        timezone: String = ZoneId.systemDefault().id
    ) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(QUIET_ENABLED, enabled)
            .putInt(QUIET_START, startMinute.coerceIn(0, 1439))
            .putInt(QUIET_END, endMinute.coerceIn(0, 1439))
            .putString(TIMEZONE, timezone)
            .apply()
        syncToServerAsync(context)
    }

    fun quietHoursEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(QUIET_ENABLED, false)

    private fun isQuietNow(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(QUIET_ENABLED, false)) return false
        val start = prefs.getInt(QUIET_START, DEFAULT_QUIET_START).coerceIn(0, 1439)
        val end = prefs.getInt(QUIET_END, DEFAULT_QUIET_END).coerceIn(0, 1439)
        if (start == end) return false

        val zone = runCatching {
            ZoneId.of(prefs.getString(TIMEZONE, ZoneId.systemDefault().id).orEmpty())
        }.getOrElse { ZoneId.systemDefault() }
        val now = ZonedDateTime.ofInstant(Instant.now(), zone)
        val minute = now.hour * 60 + now.minute
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    fun refreshFromServerAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch { refreshFromServer(context.applicationContext) }
    }

    fun syncToServerAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch { syncToServer(context.applicationContext) }
    }

    private fun refreshFromServer(context: Context) {
        runCatching {
            SupabaseService.initialize(context)
            val token = SupabaseService.accessToken() ?: return
            val uid = SupabaseService().getCurrentUserId() ?: return
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/notification_preferences?select=*&user_id=eq.$uid&limit=1")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                if (rows.length() == 0) return@use
                val row = rows.getJSONObject(0)
                val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                editor.putBoolean(MASTER, row.optBoolean("master_enabled", true))
                mapOf(
                    "messages" to "messages_enabled",
                    "calls" to "calls_enabled",
                    "social" to "social_enabled",
                    "mentions" to "mentions_enabled",
                    "comments" to "comments_enabled",
                    "follows" to "follows_enabled",
                    "market" to "market_enabled",
                    "admin" to "admin_enabled",
                    "coins" to "coins_enabled",
                    "stories" to "stories_enabled",
                    "reels" to "reels_enabled",
                    "vip" to "vip_enabled",
                    "boosts" to "boosts_enabled",
                    "security" to "security_enabled"
                ).forEach { (localKey, serverKey) ->
                    editor.putBoolean(localKey, row.optBoolean(serverKey, true))
                }
                editor.putBoolean(QUIET_ENABLED, row.optBoolean("quiet_hours_enabled", false))
                editor.putInt(QUIET_START, row.optInt("quiet_start_minute", DEFAULT_QUIET_START).coerceIn(0, 1439))
                editor.putInt(QUIET_END, row.optInt("quiet_end_minute", DEFAULT_QUIET_END).coerceIn(0, 1439))
                editor.putString(TIMEZONE, row.optString("timezone", ZoneId.systemDefault().id))
                editor.apply()
            }
        }.onFailure { Log.w(TAG, "Unable to refresh notification preferences", it) }
    }

    private fun syncToServer(context: Context) {
        runCatching {
            SupabaseService.initialize(context)
            val token = SupabaseService.accessToken() ?: return
            val uid = SupabaseService().getCurrentUserId() ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val payload = JSONObject()
                .put("user_id", uid)
                .put("master_enabled", prefs.getBoolean(MASTER, true))
                .put("messages_enabled", prefs.getBoolean("messages", true))
                .put("calls_enabled", prefs.getBoolean("calls", true))
                .put("social_enabled", prefs.getBoolean("social", true))
                .put("mentions_enabled", prefs.getBoolean("mentions", true))
                .put("comments_enabled", prefs.getBoolean("comments", true))
                .put("follows_enabled", prefs.getBoolean("follows", true))
                .put("market_enabled", prefs.getBoolean("market", true))
                .put("admin_enabled", prefs.getBoolean("admin", true))
                .put("coins_enabled", prefs.getBoolean("coins", true))
                .put("stories_enabled", prefs.getBoolean("stories", true))
                .put("reels_enabled", prefs.getBoolean("reels", true))
                .put("vip_enabled", prefs.getBoolean("vip", true))
                .put("boosts_enabled", prefs.getBoolean("boosts", true))
                .put("security_enabled", prefs.getBoolean("security", true))
                .put("quiet_hours_enabled", prefs.getBoolean(QUIET_ENABLED, false))
                .put("quiet_start_minute", prefs.getInt(QUIET_START, DEFAULT_QUIET_START).coerceIn(0, 1439))
                .put("quiet_end_minute", prefs.getInt(QUIET_END, DEFAULT_QUIET_END).coerceIn(0, 1439))
                .put("timezone", prefs.getString(TIMEZONE, ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/notification_preferences?on_conflict=user_id")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Notification preference sync failed: ${response.code}")
                }
            }
        }.onFailure { Log.w(TAG, "Unable to sync notification preferences", it) }
    }
}
