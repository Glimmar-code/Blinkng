package com.example.notification

import android.content.Context
import com.example.data.supabase.SupabaseService

/**
 * Prevents the same direct message from alerting once via FCM and again via
 * realtime/recovery. The server remains the authority for read/unread state;
 * this ledger only removes duplicate delivery paths on the current install.
 */
object MessageNotificationLedger {
    private const val PREFS = "blink_message_notification_ledger"
    private const val MAX_IDS = 500
    private val lock = Any()

    fun claim(context: Context, messageId: String): Boolean {
        val cleanId = messageId.trim()
        if (cleanId.isBlank()) return true

        val userKey = SupabaseService().getCurrentUserId().orEmpty().ifBlank { "anonymous" }
        val key = "notified_$userKey"
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        synchronized(lock) {
            val existing = prefs.getString(key, "")
                .orEmpty()
                .split('|')
                .filter(String::isNotBlank)
            if (cleanId in existing) return false

            val updated = (existing.takeLast(MAX_IDS - 1) + cleanId).joinToString("|")
            prefs.edit().putString(key, updated).apply()
            return true
        }
    }
}
