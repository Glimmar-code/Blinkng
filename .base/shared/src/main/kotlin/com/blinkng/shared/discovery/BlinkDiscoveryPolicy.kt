package com.blinkng.shared.discovery

/**
 * Android and Windows use exactly the same first-login discovery sequence.
 * Times are minimum *active-session* eligibility times, not timers that force a dialog.
 * This catalog is bundled in the client; remote announcements arrive in a later phase.
 */
data class BlinkGuideStep(
    val id: String,
    val title: String,
    val description: String,
    val actionLabel: String,
    val routeId: String? = null,
    val earliestSessionMs: Long,
)

object BlinkDiscoveryPolicy {
    val steps: List<BlinkGuideStep> = listOf(
        BlinkGuideStep("welcome", "WELCOME TO BLINK", "Explore your campus, connect with students and discover everything BLINK offers.", "Explore BLINK", earliestSessionMs = 2_000),
        BlinkGuideStep("reels", "DISCOVER REELS", "Explore short videos from your community.", "Explore Reels", "reels", 15_000),
        BlinkGuideStep("shake_report", "DID YOU KNOW?", "Shake your phone to report a bug. You can also open Report Bug from the menu.", "Got it", earliestSessionMs = 30_000),
        BlinkGuideStep("ranking", "YOUR BLINK RANK", "See how you rank in your university and beyond.", "View ranking", "leaderboard", 60_000),
        BlinkGuideStep("coins", "EARN BLINK COINS", "Explore the available rewards and missions in BLINK.", "Got it", earliestSessionMs = 90_000),
        BlinkGuideStep("connect", "MEET YOUR COMMUNITY", "Discover Connect and find people across campus.", "Open Connect", "connect", 120_000),
        BlinkGuideStep("market", "DISCOVER MARKET", "Explore products and services from BLINK members.", "Visit Market", "marketplace", 180_000),
        BlinkGuideStep("games", "EXPLORE GAMES", "Discover games and optional BLINK LIFE experiences.", "View Games", "games", 240_000),
    )

    const val MAX_PER_SESSION = 3
    const val MIN_GAP_BETWEEN_STEPS_MS = 12_000L

    /**
     * No prompt is eligible unless the app is idle on a safe surface.
     * Completed/skipped IDs are scoped to the signed-in account by platform storage.
     */
    fun next(
        completed: Set<String>,
        elapsedSessionMs: Long,
        safeToShow: Boolean,
        promptsShownThisSession: Int,
        elapsedSinceLastPromptMs: Long,
    ): BlinkGuideStep? {
        if (!safeToShow || promptsShownThisSession >= MAX_PER_SESSION) return null
        if (promptsShownThisSession > 0 && elapsedSinceLastPromptMs < MIN_GAP_BETWEEN_STEPS_MS) return null
        return steps.firstOrNull { it.id !in completed && elapsedSessionMs >= it.earliestSessionMs }
    }

    fun allIds(): Set<String> = steps.mapTo(linkedSetOf()) { it.id }
}
