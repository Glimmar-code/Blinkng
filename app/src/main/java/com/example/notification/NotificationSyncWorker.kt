package com.example.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import com.example.util.safeBoolean
import com.example.util.safeString
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
        val service = SupabaseService()

        // WorkManager can wake up long after the access JWT was issued. Refresh before
        // sending protected recovery requests instead of repeatedly replaying a stale token.
        if (!service.restoreSession()) return retryOrStop()

        val uid = service.getCurrentUserId() ?: return Result.success()
        var token = SupabaseService.accessToken() ?: return retryOrStop()

        return try {
            recoverUnreadMessages(token, uid)
            recoverSocialNotifications(token, uid)
            Result.success()
        } catch (_: UnauthorizedException) {
            // A token can be revoked between restoreSession() and the request. Refresh once
            // and replay the idempotent recovery pass with the rotated token.
            if (!service.refreshSession()) return retryOrStop()
            token = SupabaseService.accessToken() ?: return retryOrStop()
            try {
                recoverUnreadMessages(token, uid)
                recoverSocialNotifications(token, uid)
                Result.success()
            } catch (_: Exception) {
                retryOrStop()
            }
        } catch (_: Exception) {
            retryOrStop()
        }
    }

    private fun retryOrStop(): Result {
        // Do not report an unresolved delivery gap as a successful synchronization.
        if (runAttemptCount < 7) return Result.retry()
        Log.e("BlinkNotificationSync", "Recovery exhausted after ${runAttemptCount + 1} attempts")
        return Result.failure()
    }

    private fun recoverUnreadMessages(token: String, uid: String) {
        if (!NotificationPreferenceStore.isAllowed(applicationContext, BlinkNotificationType.MESSAGE)) return

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
            val syncPrefs = applicationContext.getSharedPreferences(
                "blink_notification_sync",
                Context.MODE_PRIVATE
            )
            val initializedKey = "message_recovery_initialized_$uid"

            // On a fresh install/cleared app data, the local delivery ledger is empty while
            // Supabase can still contain months of unread history. Baseline those rows instead
            // of replaying stale heads-up alerts after sign-in. They remain unread in chat.
            if (!syncPrefs.safeBoolean(initializedKey, false)) {
                for (index in 0 until rows.length()) {
                    val messageId = rows.optJSONObject(index)?.optString("message_id").orEmpty()
                    if (messageId.isNotBlank()) {
                        MessageNotificationLedger.claim(applicationContext, messageId)
                    }
                }
                syncPrefs.edit().putBoolean(initializedKey, true).apply()
                return
            }

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
                val conversationId = row.optString("conversation_id")
                if (conversationId.isNotBlank() &&
                    ConversationNotificationMuteStore.isMuted(applicationContext, conversationId)) {
                    return@forEach
                }
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
        val cursorIdKey = "last_social_id_$uid"
        val lastSeen = prefs.safeString(cursorKey, "").orEmpty()
        val lastId = prefs.safeString(cursorIdKey, "").orEmpty()
        val isFirstRecovery = lastSeen.isBlank()
        var newestTime = lastSeen
        var newestId = lastId
        var offset = 0
        var pagesFetched = 0
        val pageSize = 500
        val maxPages = 20
        // Keep only recent alerts; the full unread history remains in the in-app feed.
        val candidates = mutableListOf<JSONObject>()

        while (true) {
            // A partial scan must not advance the cursor or older events will be skipped.
            if (pagesFetched >= maxPages) error("Social recovery exceeded $maxPages pages")
            val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/notifications?select=*&is_read=eq.false&order=created_at.asc,id.asc&limit=$pageSize&offset=$offset"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .build()
            val rows = client.newCall(request).execute().use { response ->
                if (response.code == 401) throw UnauthorizedException()
                if (!response.isSuccessful) error("Social notification recovery failed (${response.code})")
                JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            }
            pagesFetched++

            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val created = row.optString("created_at")
                val notificationId = row.optString("id")
                if (NotificationIdentity.isAfterCursor(created, notificationId, newestTime, newestId)) {
                    newestTime = created
                    newestId = notificationId
                }

                // First run: baseline historical unread rows without presenting stale alerts.
                if (isFirstRecovery) continue
                if (!NotificationIdentity.isAfterCursor(created, notificationId, lastSeen, lastId)) continue

                // DM notification recovery is handled by the authoritative message pipeline.
                val title = row.optString("text", "Blink notification")
                if (title.contains(" sent you a message", ignoreCase = true)) continue
                candidates.add(row)
                if (candidates.size > 20) candidates.removeAt(0)
            }
            if (rows.length() < pageSize) break
            offset += rows.length()
        }

        if (isFirstRecovery) {
            prefs.edit().putString(cursorKey, newestTime).putString(cursorIdKey, newestId).apply()
            return
        }

        candidates.forEach { row ->
            val notificationId = row.optString("id")
            if (notificationId.isNotBlank() &&
                SocialNotificationRecovery.wasShown(applicationContext, uid, notificationId)) return@forEach

            val rawType = when {
                row.optString("target_type").equals("story", ignoreCase = true) &&
                    row.optString("type").equals("like", ignoreCase = true) -> "story_like"
                row.optJSONObject("metadata")?.optBoolean("is_reply", false) == true -> "reply"
                else -> row.optString("type", "social")
            }
            val type = BlinkNotificationType.fromWire(rawType)
            if (!NotificationPreferenceStore.isAllowed(applicationContext, type)) return@forEach
            if (notificationId.isNotBlank()) {
                SocialNotificationRecovery.markShown(applicationContext, uid, notificationId)
            }

            val targetType = row.optString("target_type").takeIf { it.isNotBlank() && it != "null" }
            val targetId = row.optString("target_id").takeIf { it.isNotBlank() && it != "null" }
            val postId = row.optString("post_id").takeIf { it.isNotBlank() && it != "null" }
            BlinkNotificationHelper.showSocialNotification(
                context = applicationContext,
                title = row.optString("text", "Blink notification"),
                body = row.optString("sub_text", ""),
                targetPostId = postId,
                targetType = targetType,
                targetId = targetId,
                profileIdentifier = targetId.takeIf {
                    targetType.equals("profile", ignoreCase = true)
                }
            )
        }
        prefs.edit().putString(cursorKey, newestTime).putString(cursorIdKey, newestId).apply()
    }

    private class UnauthorizedException : Exception()
}
