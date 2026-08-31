package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class NotificationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val token = SupabaseService.accessToken() ?: return Result.success()
        return try {
            val prefs = applicationContext.getSharedPreferences("blink_notification_sync", Context.MODE_PRIVATE)
            val lastSeen = prefs.getString("last_created_at", "") ?: ""
            val endpoint = buildString {
                append(SupabaseConfig.url.trimEnd('/'))
                append("/rest/v1/notifications?select=*&is_read=eq.false&order=created_at.asc&limit=20")
                if (lastSeen.isNotBlank()) append("&created_at=gt.${java.net.URLEncoder.encode(lastSeen, \"UTF-8\")}")
            }
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return Result.retry()
                if (!response.isSuccessful) return Result.retry()
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank() || raw == "[]") return Result.success()
                val rows = JSONArray(raw)
                var newest = lastSeen
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val created = row.optString("created_at", "")
                    if (created.isNotBlank()) newest = created
                    val title = row.optString("text", "Blink notification")
                    val body = row.optString("sub_text", "")
                    when (row.optString("type").lowercase()) {
                        "follow" -> BlinkNotificationHelper.showFollowNotification(applicationContext, body.removePrefix("@"))
                        else -> BlinkNotificationHelper.showSocialNotification(applicationContext, title, body, row.optString("post_id").takeIf { it.isNotBlank() })
                    }
                }
                if (newest.isNotBlank()) prefs.edit().putString("last_created_at", newest).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
