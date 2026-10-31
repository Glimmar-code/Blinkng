package com.blinkng.shared.notification

/**
 * A canonical NOTIFICATION row may be a like, gift or follow — it is not
 * automatically an official BLINK announcement. Trust the event type first.
 * Shared between Android and Windows to prevent social actors becoming "Blink".
 */
object BlinkNotificationPresentation {
    fun isOfficial(targetType: String?, eventType: String?, action: String?): Boolean {
        val event = eventType.orEmpty().trim().lowercase()
        if (event in setOf("system", "admin", "announcement", "admin_announcement",
                "broadcast", "security", "login_alert", "account_alert")) return true
        if (event.isNotBlank()) return false
        if (targetType.equals("system", true)) return true
        if (!targetType.equals("notification", true)) return false
        val text = action.orEmpty().trim().lowercase()
        return text.startsWith("blink announcement") || text.startsWith("security alert") ||
            text.startsWith("official update") || text.startsWith("system notice")
    }
}
