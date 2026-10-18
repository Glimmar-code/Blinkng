package com.example.call

import android.content.Context
import java.time.Instant
import java.util.UUID

/**
 * Per-account incoming call notification guard.
 *
 * FCM messages can be duplicated and delivered out of order. Persist short
 * tombstones so a delayed invite cannot ring after an answer or cancellation,
 * even when Android restarts the process between messages.
 */
internal object CallNotificationLedger {
    private const val STORE = "blink_call_notification_ledger"
    private const val INVITE_WINDOW_MS = 70_000L
    private const val TERMINAL_WINDOW_MS = 24 * 60 * 60 * 1000L

    fun isValidCallId(callId: String): Boolean =
        runCatching { UUID.fromString(callId).toString().equals(callId, ignoreCase = true) }
            .getOrDefault(false)

    fun isFreshInvite(timeoutAt: String?, nowMillis: Long): Boolean {
        if (timeoutAt.isNullOrBlank()) return true // older server payloads
        val expiry = runCatching { Instant.parse(timeoutAt).toEpochMilli() }.getOrNull()
            ?: return false
        return expiry > nowMillis
    }

    fun validRecipient(
        currentUserId: String,
        recipientId: String?,
        callerId: String?,
        calleeId: String?,
        incoming: Boolean
    ): Boolean {
        if (currentUserId.isBlank() || recipientId.isNullOrBlank() ||
            currentUserId != recipientId) return false
        if (callerId.isNullOrBlank() || calleeId.isNullOrBlank()) return false
        if (callerId == calleeId) return false
        return if (incoming) calleeId == currentUserId
        else currentUserId == callerId || currentUserId == calleeId
    }

    @Synchronized
    fun claimInvite(context: Context, accountId: String, callId: String, timeoutAt: String?): Boolean {
        val now = System.currentTimeMillis()
        if (accountId.isBlank() || !isValidCallId(callId) ||
            !isFreshInvite(timeoutAt, now)) return false

        val prefs = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val key = "$accountId:$callId"
        val terminalAt = prefs.getLong("done:$key", 0)
        if (terminalAt > 0 && now - terminalAt in 0 until TERMINAL_WINDOW_MS) return false

        val shownAt = prefs.getLong("invite:$key", 0)
        if (shownAt > 0 && now - shownAt in 0 until INVITE_WINDOW_MS) return false

        val editor = prefs.edit()
        for ((oldKey, value) in prefs.all) {
            val time = value as? Long ?: continue
            if (time <= 0 || time > now || now - time > TERMINAL_WINDOW_MS) editor.remove(oldKey)
        }
        editor.putLong("invite:$key", now).apply()
        return true
    }

    @Synchronized
    fun markUpdated(context: Context, accountId: String, callId: String, event: String) {
        if (accountId.isBlank() || !isValidCallId(callId)) return
        if (event.lowercase() !in setOf("answered", "cancelled", "declined", "ended", "missed", "failed", "busy")) return
        val key = "$accountId:$callId"
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putLong("done:$key", System.currentTimeMillis())
            .remove("invite:$key").apply()
    }
}
