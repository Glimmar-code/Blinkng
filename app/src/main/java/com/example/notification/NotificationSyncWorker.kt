package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class NotificationSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result {
        val token = SupabaseService.accessToken() ?: return Result.success()
        val uid = SupabaseService().getCurrentUserId() ?: return Result.success()
        return try {
            val prefs = applicationContext.getSharedPreferences("blink_notification_sync", Context.MODE_PRIVATE)
            val cursorKey = "last_created_at_$uid"
            val countKey = "unread_count_$uid"
            val lastSeen = prefs.getString(cursorKey, "") ?: ""
            val endpoint = buildString {
                append(SupabaseConfig.url.trimEnd('/'))
                append("/rest/v1/notifications?select=*&is_read=eq.false&order=created_at.asc&limit=1000")
            }
            val request = Request.Builder().url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", "count=exact")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return Result.retry()
                if (!response.isSuccessful) return Result.retry()
                val raw = response.body?.string().orEmpty()
                val rows = if (raw.isBlank()) JSONArray() else JSONArray(raw)
                var newest = lastSeen
                var newlyShown = 0
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val created = row.optString("created_at", "")
                    if (created.isNotBlank() && (newest.isBlank() || created > newest)) newest = created
                    if (lastSeen.isBlank() || created > lastSeen) {
                        val title = row.optString("text", "Blink notification")
                        val body = row.optString("sub_text", "")
                        BlinkNotificationHelper.showSocialNotification(applicationContext, title, body, row.optString("post_id").takeIf { it.isNotBlank() && it != "null" })
                        newlyShown++
                    }
                }
                val contentRange = response.header("Content-Range")
                val totalUnread = contentRange?.substringAfter("/")?.toIntOrNull() ?: rows.length()
                prefs.edit().putString(cursorKey, newest).putInt(countKey, totalUnread).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
