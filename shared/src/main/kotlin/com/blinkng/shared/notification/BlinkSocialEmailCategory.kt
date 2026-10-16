package com.blinkng.shared.notification

/**
 * One opt-in, persisted Supabase preference per social email category.
 *
 * These are separate from Android push notification categories and do not
 * affect Supabase Auth signup confirmation or password-recovery email flows.
 * All default to false until explicit user consent.
 */
enum class BlinkSocialEmailCategory(
    val column: String,
    val title: String,
    val description: String,
) {
    NEW_FOLLOWER(
        "new_follower_enabled", "New followers",
        "A daily update when new people follow you",
    ),
    UNREAD_MESSAGES(
        "unread_messages_enabled", "Unread conversations",
        "A gentle daily reminder, without private chat text",
    ),
    POST_ENGAGEMENT(
        "post_engagement_enabled", "Post engagement",
        "Weekly highlights for meaningful post activity",
    ),
    REEL_MILESTONE(
        "reel_milestone_enabled", "Reel milestones",
        "When server-recorded reel viewing milestones are reached",
    ),
    LEADERBOARD(
        "leaderboard_enabled", "Leaderboard updates",
        "A weekly summary when your official rank changes",
    );

    companion object {
        val allColumns: List<String> = entries.map { it.column }
        fun fromColumn(key: String): BlinkSocialEmailCategory? =
            entries.firstOrNull { it.column == key }
    }
}
