package com.example.notification

import android.content.Context
import com.example.util.safeString
import java.time.Instant

/**
 * Device-side conversation notification mute state.
 *
 * The server remains authoritative for the conversation's durable muted flag. This store adds
 * expiry-aware enforcement on the receiving device so a timed mute can stop alerts immediately,
 * including while the app process is not open.
 */
object ConversationNotificationMuteStore {
    private const val PREFS = "blink_conversation_notification_mutes"
    private const val FOREVER = Long.MAX_VALUE

    fun mute(context: Context, conversationId: String, durationMillis: Long?) {
        val key = conversationId.trim()
        if (key.isBlank()) return
        val until = durationMillis
            ?.takeIf { it > 0L }
            ?.let { System.currentTimeMillis() + it }
            ?: FOREVER
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, until.toString())
            .apply()
    }

    fun unmute(context: Context, conversationId: String) {
        val key = conversationId.trim()
        if (key.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    fun isMuted(context: Context, conversationId: String): Boolean {
        val key = conversationId.trim()
        if (key.isBlank()) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val until = prefs.safeString(key, "")?.toLongOrNull() ?: return false
        if (until == FOREVER) return true
        if (until > System.currentTimeMillis()) return true
        prefs.edit().remove(key).apply()
        return false
    }

    fun mutedUntil(context: Context, conversationId: String): Instant? {
        val key = conversationId.trim()
        if (key.isBlank()) return null
        val until = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .safeString(key, "")
            ?.toLongOrNull()
            ?: return null
        if (until == FOREVER || until <= System.currentTimeMillis()) return null
        return Instant.ofEpochMilli(until)
    }
}
