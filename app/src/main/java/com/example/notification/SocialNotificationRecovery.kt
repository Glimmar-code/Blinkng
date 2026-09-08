package com.example.notification

import android.content.Context

/**
 * Durable per-account dedupe for social/admin notifications reconstructed after reconnect.
 *
 * A server notification can be displayed, remain unread, then be returned again by the
 * recovery query after logout/login. Remembering the server notification id prevents the
 * same alert from being posted twice without changing the user's read/unread state.
 */
object SocialNotificationRecovery {
    private const val PREFS = "blink_notification_sync"
    private const val SHOWN_IDS_PREFIX = "shown_social_notification_ids_"
    private const val MAX_REMEMBERED_IDS = 512

    @Synchronized
    fun wasShown(context: Context, userId: String, notificationId: String): Boolean {
        if (userId.isBlank() || notificationId.isBlank()) return false
        return readIds(context, userId).contains(notificationId)
    }

    @Synchronized
    fun markShown(context: Context, userId: String, notificationId: String) {
        if (userId.isBlank() || notificationId.isBlank()) return
        val ids = readIds(context, userId).toMutableList()
        if (ids.remove(notificationId)) {
            ids.add(notificationId)
        } else {
            ids.add(notificationId)
        }
        while (ids.size > MAX_REMEMBERED_IDS) ids.removeAt(0)

        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SHOWN_IDS_PREFIX + userId, ids.joinToString("\n"))
            .apply()
    }

    private fun readIds(context: Context, userId: String): List<String> {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SHOWN_IDS_PREFIX + userId, "")
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
    }
}
