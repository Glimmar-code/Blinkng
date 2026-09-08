package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gap recovery after reconnect/reinstall.
 *
 * Direct-message alerts are reconstructed only from messages that are still unread
 * for this account on Supabase. Previously read messages therefore never re-alert just
 * because local app storage or a notification cursor was lost.
 */
class NotificationSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun doWork(): Result {
        SupabaseService.initialize(applicationContext)
        val token = SupabaseService.accessToken() ?: return Result.success()
        val uid = SupabaseService().getCurrentUserId() ?: return Result.success()

        return try {
            recoverUnreadMessages(token)
            recoverSocialNotifications(token, uid)
            Result.success()
        } catch (_: UnauthorizedException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun recoverUnreadMessages(token: String) {
        val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/get_my_unread_message_notifications"
        val body = JSONObject().put("p_limit", 200).toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedException()
            if (!response.isSuccessful) error("Unread message recovery failed (${response.code})")

            val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            // Avoid a wall of alerts after a long offline period: show the newest unread
            // message per conversation. The unread count/history remains visible in the app.
            val newestByConversation = linkedMapOf<String, JSONObject>()
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val messageId = row.optString("message_id")
                val conversationId = row.optString("conversation_id").ifBlank { messageId }
                if (messageId.isBlank() || conversationId.isBlank()) continue
                newestByConversation[conversationId] = row
            }

            newestByConversation.values.forEach { row ->
                val senderUsername = row.optString("sender_username")
                if (senderUsername.isBlank()) return@forEach
                InstantChatNotification.show(
                    context = applicationContext,
                    senderUsername = senderUsername,
                    senderName = row.optString("sender_name").ifBlank { senderUsername },
                    messageText = row.optString("content").ifBlank { "New message" },
                    senderAvatar = row.optString("sender_avatar"),
                    conversationId = row.optString("conversation_id"),
                    messageId = row.optString("message_id")
                )
            }
        }
    }

    private fun recoverSocialNotifications(token: String, uid: String) {
        val prefs = applicationContext.getSharedPreferences("blink_notification_sync", Context.MODE_PRIVATE)
        val cursorKey = "last_social_created_at_$uid"
        val persistedCursor = prefs.getString(cursorKey, "") ?: ""
        // If FCM already displayed a social/admin alert, recovery must never reconstruct
        // server rows from before that delivery time on a later login.
        val lastSeen = SocialNotificationRecovery.effectiveCursor(applicationContext, uid, persistedCursor)
        val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/notifications?select=*&is_read=eq.false&order=created_at.asc&limit=1000"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedException()
            if (!response.isSuccessful) error("Social notification recovery failed (${response.code})")
            val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            var newest = lastSeen
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val created = row.optString("created_at")
                if (created.isNotBlank() && (newest.isBlank() || created > newest)) newest = created
                if (lastSeen.isNotBlank() && created <= lastSeen) continue

                val title = row.optString("text", "Blink notification")
                // Message rows are deliberately ignored here. Their authoritative read state
                // comes from public.messages via get_my_unread_message_notifications().
                if (title.contains(" sent you a message", ignoreCase = true)) continue

                BlinkNotificationHelper.showSocialNotification(
                    applicationContext,
                    title,
                    row.optString("sub_text", ""),
                    row.optString("post_id").takeIf { it.isNotBlank() && it != "null" }
                )
            }
            prefs.edit().putString(cursorKey, newest).apply()
        }
    }

    private class UnauthorizedException : Exception()
}
