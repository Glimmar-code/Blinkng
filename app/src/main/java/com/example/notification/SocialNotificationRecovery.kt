package com.example.notification

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Bridges FCM delivery and REST gap recovery for social/admin notifications.
 *
 * FCM and the notification recovery worker are intentionally independent. Without a
 * shared watermark, a notification that was already shown by FCM can be reconstructed
 * again after logout/login while its server row is still unread. This per-user watermark
 * makes recovery idempotent without marking the notification as read for the user.
 */
object SocialNotificationRecovery {
    private const val PREFS = "blink_notification_sync"
    private const val PUSH_WATERMARK_PREFIX = "last_social_push_received_at_"

    fun markPushReceived(context: Context, userId: String) {
        if (userId.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PUSH_WATERMARK_PREFIX + userId, utcNow())
            .apply()
    }

    fun effectiveCursor(context: Context, userId: String, persistedCursor: String): String {
        if (userId.isBlank()) return persistedCursor
        val pushWatermark = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PUSH_WATERMARK_PREFIX + userId, "")
            .orEmpty()
        return when {
            persistedCursor.isBlank() -> pushWatermark
            pushWatermark.isBlank() -> persistedCursor
            pushWatermark > persistedCursor -> pushWatermark
            else -> persistedCursor
        }
    }

    private fun utcNow(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}
