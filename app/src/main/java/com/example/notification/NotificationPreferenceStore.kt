package com.example.notification

import android.content.Context
import android.util.Log
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import com.example.util.safeBoolean
import com.example.util.safeInt
import com.example.util.safeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    enum class SyncStatus { SAVING, SYNCED, FAILED }
    private val _syncStatus = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    val syncStatus: StateFlow<Map<String, SyncStatus>> = _syncStatus
    private fun updateSyncStatus(uid: String, status: SyncStatus) {
        if (uid.isNotBlank()) _syncStatus.value = _syncStatus.value + (uid to status)
    }
    fun retrySync(context: Context) = syncToServerAsync(context)
    private fun accountPrefs(context: Context, accountId: String? = null): android.content.SharedPreferences {
        val appContext = context.applicationContext
        val uid = accountId ?: runCatching {
            SupabaseService.initialize(appContext)
            SupabaseService().getCurrentUserId().orEmpty()
        }.getOrDefault("")
        return appContext.getSharedPreferences(
            "${PREFS}_${uid.ifBlank { "signed_out" }}", Context.MODE_PRIVATE
        )
    }
    private const val PENDING_SYNC = "pending_sync"
    private const val MASTER = "master"
    private const val CHAT_PREVIEW_MODE = "chat_preview_mode"
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
        val prefs = accountPrefs(context)
        if (!prefs.safeBoolean(MASTER, true)) return false
        if (!prefs.safeBoolean(type.preferenceKey, true)) return false
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

    /**
     * A short-lived FCM callback cannot fetch chat settings from the server.
     * Mirror the last server-confirmed choice in an account-specific preference.
     * Until chat settings have been loaded, use hidden rather than leak a preview.
     */
    fun chatPreviewMode(context: Context): String {
        val accountId = runCatching {
            SupabaseService.initialize(context.applicationContext)
            SupabaseService().getCurrentUserId().orEmpty()
        }.getOrDefault("")
        if (accountId.isBlank()) return "hidden"
        return ChatNotificationPreviewPolicy.normalized(
            accountPrefs(context, accountId).safeString(CHAT_PREVIEW_MODE, "hidden")
        )
    }

    fun rememberChatPreviewMode(context: Context, accountId: String, mode: String): Boolean {
        if (accountId.isBlank()) return false
        val currentId = runCatching {
            SupabaseService.initialize(context.applicationContext)
            SupabaseService().getCurrentUserId().orEmpty()
        }.getOrDefault("")
        if (accountId != currentId) return false
        accountPrefs(context, accountId).edit()
            .putString(CHAT_PREVIEW_MODE, ChatNotificationPreviewPolicy.normalized(mode))
            .apply()
        return true
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        accountPrefs(context)
            .edit().putBoolean(MASTER, enabled).putBoolean(PENDING_SYNC, true).apply()
        syncToServerAsync(context)
    }

    fun setCategoryEnabled(context: Context, type: BlinkNotificationType, enabled: Boolean) {
        accountPrefs(context)
            .edit().putBoolean(type.preferenceKey, enabled).putBoolean(PENDING_SYNC, true).apply()
        syncToServerAsync(context)
    }

    fun isCategoryEnabled(context: Context, type: BlinkNotificationType): Boolean =
        accountPrefs(context)
            .safeBoolean(type.preferenceKey, true)

    fun isMasterEnabled(context: Context): Boolean =
        accountPrefs(context)
            .safeBoolean(MASTER, true)

    fun setQuietHours(
        context: Context,
        enabled: Boolean,
        startMinute: Int = DEFAULT_QUIET_START,
        endMinute: Int = DEFAULT_QUIET_END,
        timezone: String = ZoneId.systemDefault().id
    ) {
        accountPrefs(context)
            .edit()
            .putBoolean(QUIET_ENABLED, enabled)
            .putInt(QUIET_START, startMinute.coerceIn(0, 1439))
            .putInt(QUIET_END, endMinute.coerceIn(0, 1439))
            .putString(TIMEZONE, timezone)
            .putBoolean(PENDING_SYNC, true)
            .apply()
        syncToServerAsync(context)
    }

    fun quietHoursEnabled(context: Context): Boolean =
        accountPrefs(context)
            .safeBoolean(QUIET_ENABLED, false)

    fun quietStartMinute(context: Context): Int =
        accountPrefs(context)
            .safeInt(QUIET_START, DEFAULT_QUIET_START)
            .coerceIn(0, 1439)

    fun quietEndMinute(context: Context): Int =
        accountPrefs(context)
            .safeInt(QUIET_END, DEFAULT_QUIET_END)
            .coerceIn(0, 1439)

    private fun isQuietNow(context: Context): Boolean {
        val prefs = accountPrefs(context)
        if (!prefs.safeBoolean(QUIET_ENABLED, false)) return false
        val start = prefs.safeInt(QUIET_START, DEFAULT_QUIET_START).coerceIn(0, 1439)
        val end = prefs.safeInt(QUIET_END, DEFAULT_QUIET_END).coerceIn(0, 1439)
        if (start == end) return false

        val zone = runCatching {
            ZoneId.of(prefs.safeString(TIMEZONE, ZoneId.systemDefault().id).orEmpty())
        }.getOrElse { ZoneId.systemDefault() }
        val now = ZonedDateTime.ofInstant(Instant.now(), zone)
        val minute = now.hour * 60 + now.minute
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    fun refreshFromServerAsync(context: Context) {
        val uid = runCatching { SupabaseService().getCurrentUserId().orEmpty() }.getOrDefault("")
        if (uid.isBlank()) return
        // Never replace offline local choices with a stale server snapshot.
        if (accountPrefs(context, uid).safeBoolean(PENDING_SYNC, false)) {
            syncToServerAsync(context)
            return
        }
        CoroutineScope(Dispatchers.IO).launch { refreshFromServer(context.applicationContext, uid) }
    }

    fun syncToServerAsync(context: Context) {
        val uid = runCatching { SupabaseService().getCurrentUserId().orEmpty() }.getOrDefault("")
        if (uid.isBlank()) return
        updateSyncStatus(uid, SyncStatus.SAVING)
        CoroutineScope(Dispatchers.IO).launch {
            syncMutex.withLock { syncToServer(context.applicationContext, uid) }
        }
    }

    private suspend fun refreshFromServer(context: Context, expectedUid: String) {
        runCatching {
            SupabaseService.initialize(context)
            val service = SupabaseService()
            if (!service.restoreSession()) return
            val token = SupabaseService.accessToken() ?: return
            val uid = service.getCurrentUserId() ?: return
            if (uid != expectedUid || accountPrefs(context, uid).safeBoolean(PENDING_SYNC, false)) return
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
                // A delayed response from another account must never alter this account.
                if (SupabaseService().getCurrentUserId() != expectedUid ||
                    accountPrefs(context, expectedUid).safeBoolean(PENDING_SYNC, false)) return@use
                val editor = accountPrefs(context, expectedUid).edit()
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

    private suspend fun syncToServer(context: Context, expectedUid: String) {
        runCatching {
            SupabaseService.initialize(context)
            val service = SupabaseService()
            if (!service.restoreSession()) throw IllegalStateException("BLINK session unavailable")
            val token = SupabaseService.accessToken()
                ?: throw IllegalStateException("BLINK access token unavailable")
            val uid = service.getCurrentUserId()
                ?: throw IllegalStateException("BLINK account unavailable")
            if (uid != expectedUid) throw IllegalStateException("BLINK account changed during sync")
            val prefs = accountPrefs(context, expectedUid)
            val payload = JSONObject()
                .put("user_id", uid)
                .put("master_enabled", prefs.safeBoolean(MASTER, true))
                .put("messages_enabled", prefs.safeBoolean("messages", true))
                .put("calls_enabled", prefs.safeBoolean("calls", true))
                .put("social_enabled", prefs.safeBoolean("social", true))
                .put("mentions_enabled", prefs.safeBoolean("mentions", true))
                .put("comments_enabled", prefs.safeBoolean("comments", true))
                .put("follows_enabled", prefs.safeBoolean("follows", true))
                .put("market_enabled", prefs.safeBoolean("market", true))
                .put("admin_enabled", prefs.safeBoolean("admin", true))
                .put("coins_enabled", prefs.safeBoolean("coins", true))
                .put("stories_enabled", prefs.safeBoolean("stories", true))
                .put("reels_enabled", prefs.safeBoolean("reels", true))
                .put("vip_enabled", prefs.safeBoolean("vip", true))
                .put("boosts_enabled", prefs.safeBoolean("boosts", true))
                .put("security_enabled", prefs.safeBoolean("security", true))
                .put("quiet_hours_enabled", prefs.safeBoolean(QUIET_ENABLED, false))
                .put("quiet_start_minute", prefs.safeInt(QUIET_START, DEFAULT_QUIET_START).coerceIn(0, 1439))
                .put("quiet_end_minute", prefs.safeInt(QUIET_END, DEFAULT_QUIET_END).coerceIn(0, 1439))
                .put("timezone", prefs.safeString(TIMEZONE, ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id)
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
                    throw IllegalStateException("Notification preference sync failed: ${response.code}")
                }
            }
            if (SupabaseService().getCurrentUserId() == expectedUid) {
                accountPrefs(context, expectedUid).edit().putBoolean(PENDING_SYNC, false).apply()
                updateSyncStatus(expectedUid, SyncStatus.SYNCED)
            }
        }.onFailure {
            updateSyncStatus(expectedUid, SyncStatus.FAILED)
            Log.w(TAG, "Unable to sync notification preferences", it)
        }
    }
}
