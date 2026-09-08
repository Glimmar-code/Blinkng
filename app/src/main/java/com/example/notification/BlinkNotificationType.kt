package com.example.notification

/**
 * Canonical notification types used by FCM, recovery and local preferences.
 * Unknown server values deliberately fall back to SOCIAL so older/newer payloads
 * remain visible without crashing an older Android build.
 */
enum class BlinkNotificationType(
    val preferenceKey: String,
    val critical: Boolean = false
) {
    MESSAGE("messages"),
    INCOMING_CALL("calls", critical = true),
    CALL_UPDATE("calls", critical = true),
    MARKET("market"),
    MARKET_ORDER("market"),
    LIKE("social"),
    COMMENT("comments"),
    REPLY("comments"),
    FOLLOW("follows"),
    MENTION("mentions"),
    REPOST("social"),
    STORY("stories"),
    REEL("reels"),
    ADMIN("admin"),
    SECURITY("security", critical = true),
    COIN("coins"),
    VIP("vip"),
    BOOST("boosts"),
    SOCIAL("social"),
    SYSTEM("admin"),
    UNKNOWN("social");

    companion object {
        fun fromWire(raw: String?): BlinkNotificationType {
            val value = raw.orEmpty().trim().lowercase()
            return when (value) {
                "message", "dm", "direct_message", "chat" -> MESSAGE
                "incoming_call", "call_invite" -> INCOMING_CALL
                "call_update", "call_status", "missed_call" -> CALL_UPDATE
                "market", "marketplace", "buyer_inquiry" -> MARKET
                "market_order", "order", "order_update" -> MARKET_ORDER
                "like", "post_like", "comment_like" -> LIKE
                "comment", "post_comment" -> COMMENT
                "reply", "comment_reply" -> REPLY
                "follow", "follower", "follow_request", "follow_accepted" -> FOLLOW
                "mention", "tag" -> MENTION
                "repost", "share" -> REPOST
                "story", "story_like", "story_reply", "story_reaction", "story_mention" -> STORY
                "reel", "reel_like", "reel_comment", "reel_reply", "reel_share" -> REEL
                "admin", "announcement", "admin_announcement", "broadcast" -> ADMIN
                "security", "login_alert", "password_changed", "account_alert" -> SECURITY
                "coin", "coins", "coin_credit", "coin_spent", "coin_reward" -> COIN
                "vip", "vip_activated", "vip_expiring", "vip_expired" -> VIP
                "boost", "boost_started", "boost_ended", "boost_result" -> BOOST
                "system" -> SYSTEM
                "social", "activity" -> SOCIAL
                "" -> SOCIAL
                else -> UNKNOWN
            }
        }
    }
}
