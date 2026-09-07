package com.blinkng.shared

/**
 * Platform-neutral Blinkng feature contract compiled into both Android and Windows.
 *
 * Keep product identity, route ids and feature availability here. Platform projects
 * provide their own UI/adapters but must not invent different route identifiers or
 * product semantics.
 */
enum class BlinkFeatureId(val routeId: String) {
    HOME("home"),
    REELS("reels"),
    CONNECT("connect"),
    MESSAGES("messages"),
    MARKETPLACE("marketplace"),
    GAMES("games"),
    NOTIFICATIONS("notifications"),
    STORE("store"),
    LEADERBOARD("leaderboard"),
    PROFILE("profile"),
    ADMIN("admin"),
    SETTINGS("settings"),
}

data class BlinkFeatureSpec(
    val id: BlinkFeatureId,
    val title: String,
    val description: String,
    val requiresAuthentication: Boolean = true,
)

object BlinkFeatureCatalog {
    val all: List<BlinkFeatureSpec> = listOf(
        BlinkFeatureSpec(BlinkFeatureId.HOME, "Home", "Feed, stories and creation"),
        BlinkFeatureSpec(BlinkFeatureId.REELS, "Reels", "Short-form video"),
        BlinkFeatureSpec(BlinkFeatureId.CONNECT, "Connect", "Campus matching and communities"),
        BlinkFeatureSpec(BlinkFeatureId.MESSAGES, "Messages", "Chats, voice and video calls"),
        BlinkFeatureSpec(BlinkFeatureId.MARKETPLACE, "Marketplace", "Campus buying and selling"),
        BlinkFeatureSpec(BlinkFeatureId.GAMES, "Games", "Games, challenges and rewards"),
        BlinkFeatureSpec(BlinkFeatureId.NOTIFICATIONS, "Notifications", "Activity and official messages"),
        BlinkFeatureSpec(BlinkFeatureId.STORE, "Blink Store", "Coins, VIP, boosts and purchases"),
        BlinkFeatureSpec(BlinkFeatureId.LEADERBOARD, "Leaderboard", "Campus and global rankings"),
        BlinkFeatureSpec(BlinkFeatureId.PROFILE, "Profile", "Profile, posts, reels and account identity"),
        BlinkFeatureSpec(BlinkFeatureId.ADMIN, "Admin", "Professional administration center"),
        BlinkFeatureSpec(BlinkFeatureId.SETTINGS, "Settings", "Account, privacy and app preferences"),
    )

    private val byRoute: Map<String, BlinkFeatureSpec> = all.associateBy { it.id.routeId }

    fun find(routeId: String): BlinkFeatureSpec? = byRoute[routeId]

    fun require(routeId: String): BlinkFeatureSpec =
        requireNotNull(find(routeId)) { "Unknown Blinkng route: $routeId" }
}
