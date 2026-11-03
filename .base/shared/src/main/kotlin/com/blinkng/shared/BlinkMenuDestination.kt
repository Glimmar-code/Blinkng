package com.blinkng.shared

/**
 * Stable BLINK Menu destinations shared by Android and Windows.
 * Display labels may change, but [key] is a durable route identifier.
 */
enum class BlinkMenuDestination(val key: String, val title: String) {
    PROFILE("profile", "My Profile"),
    REFER_GROW("refer_grow", "Refer & Grow"),
    LEADERBOARD("leaderboard", "Leaderboard"),
    WALLET("wallet", "Wallet & Coins"),
    NOTIFICATIONS("notifications", "Notifications"),
    SAVED("saved", "Saved Items"),
    VERIFICATION("verification", "Verification"),
    UPDATES("updates", "Update Center"),
    SETTINGS("settings", "Settings"),
    HELP("help", "Help & Support"),
    LOGOUT("logout", "Log Out");

    companion object {
        fun fromKey(key: String): BlinkMenuDestination? = entries.firstOrNull { it.key == key }
    }
}
