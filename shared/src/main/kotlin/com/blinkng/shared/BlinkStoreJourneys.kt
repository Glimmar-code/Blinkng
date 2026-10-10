package com.blinkng.shared

/**
 * Goal-based discovery for the existing BLINK Store. All group IDs refer to products
 * already offered through the server-authoritative catalog. A journey is navigation,
 * not a paid bundle, discount, new entitlement, or a promise of extra reach.
 *
 * Features without real catalog entries stay PLANNED and are never sold by this UI.
 */
enum class BlinkStoreJourneyAvailability { LIVE, PLANNED }

data class BlinkStoreJourney(
    val id: String,
    val title: String,
    val description: String,
    val groupIds: List<String>,
    val availability: BlinkStoreJourneyAvailability = BlinkStoreJourneyAvailability.LIVE,
)

data class BlinkIdentityLook(
    val id: String,
    val title: String,
    val description: String,
    val entryGroupId: String,
    val groupIds: List<String>,
)

/**
 * Shared across Android and Windows to avoid selling different Store experiences.
 * Purchases still go through the authenticated Store RPC and require user confirmation.
 */
object BlinkStoreJourneys {
    val all: List<BlinkStoreJourney> = listOf(
        BlinkStoreJourney(
            "identity", "My BLINK Identity",
            "Personalize your avatar, name, profile theme and entrance.",
            listOf("profile_aura", "profile_themes", "avatar_style", "name_style", "profile_badges", "profile_entrance")
        ),
        BlinkStoreJourney(
            "gifts", "Celebrate someone",
            "Choose a digital gift or a friendly reaction; sending is always optional.",
            listOf("collectibles_gifts", "reaction_effects", "emoji_sticker_packs")
        ),
        BlinkStoreJourney(
            "promotion", "Share my work",
            "Explore clearly labelled promotions for posts, Reels and Marketplace listings. Reach is not guaranteed.",
            listOf("promote_post", "promote_reel", "promote_profile", "promote_marketplace")
        ),
        BlinkStoreJourney(
            "chat", "Make chats mine",
            "Change conversation themes, bubbles, stickers and reactions.",
            listOf("chat_themes", "emoji_sticker_packs", "reaction_effects")
        ),
        BlinkStoreJourney(
            "creator", "Creator studio",
            "Dress up posts, Reels and creator identity with existing cosmetic options.",
            listOf("creator_promotion", "post_style", "reel_style", "profile_badges")
        ),
        BlinkStoreJourney(
            "campus", "Campus-inspired style",
            "Create a personal look with profile themes and badges; official university collections are not yet on sale.",
            listOf("profile_themes", "profile_badges", "avatar_style")
        ),
        BlinkStoreJourney(
            "celebrate", "Celebrate a moment",
            "Explore birthday and profile celebration effects available in the Store.",
            listOf("celebration_themes", "profile_themes", "collectibles_gifts")
        ),
        BlinkStoreJourney(
            "app", "My app, my style",
            "Explore existing app and profile customization options.",
            listOf("app_customization", "profile_music", "profile_insights")
        ),
        BlinkStoreJourney(
            "game", "Game cosmetics",
            "Game outfits, entrances and character styles will appear only after real entitlements exist.",
            emptyList(), BlinkStoreJourneyAvailability.PLANNED
        ),
        BlinkStoreJourney(
            "university", "University collections",
            "University-specific names, colors and graduation collections require verified catalog assets.",
            emptyList(), BlinkStoreJourneyAvailability.PLANNED
        ),
        BlinkStoreJourney(
            "seasonal", "Seasonal drops",
            "Limited-time collections require real server-dated availability, prices and inventory.",
            emptyList(), BlinkStoreJourneyAvailability.PLANNED
        ),
    )

    val live: List<BlinkStoreJourney> = all.filter {
        it.availability == BlinkStoreJourneyAvailability.LIVE
    }

    val looks: List<BlinkIdentityLook> = listOf(
        BlinkIdentityLook(
            "signature", "Signature look",
            "A profile theme, avatar frame and name style. Choose each piece separately.",
            "profile_themes", listOf("profile_themes", "avatar_style", "name_style")
        ),
        BlinkIdentityLook(
            "creator", "Creator look",
            "Match an entrance with your creator badge and content styling.",
            "profile_entrance", listOf("profile_entrance", "profile_badges", "post_style")
        ),
        BlinkIdentityLook(
            "celebration", "Celebration look",
            "Combine celebration-themed profiles, a ring and a gift for a friend.",
            "celebration_themes", listOf("celebration_themes", "avatar_style", "collectibles_gifts")
        ),
    )

    fun byId(id: String): BlinkStoreJourney? = all.firstOrNull { it.id == id }

    fun availableGroups(journeyId: String?): List<BlinkStoreProductGroup> {
        if (journeyId == null) return BlinkStoreProductGroups.all
        val journey = byId(journeyId) ?: return emptyList()
        if (journey.availability != BlinkStoreJourneyAvailability.LIVE) return emptyList()
        return journey.groupIds.mapNotNull(BlinkStoreProductGroups::byId)
    }
}
