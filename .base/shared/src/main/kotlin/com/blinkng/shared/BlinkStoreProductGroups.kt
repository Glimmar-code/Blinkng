package com.blinkng.shared

/**
 * Presentation-only grouping for the Blink Store.
 *
 * The existing catalog ids remain the server-authoritative purchasable entitlements.
 * These groups only reduce Store clutter and provide a shared Android/Windows browsing
 * structure. Nothing here changes pricing, ownership, activation, expiry, boosts,
 * ranking, coins or Supabase rules.
 */
data class BlinkStoreProductGroup(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val primaryItemId: String,
    val itemIds: List<String>,
)

object BlinkStoreProductGroups {
    val categories: List<String> = listOf(
        "For You",
        "Profile",
        "Social",
        "Content",
        "Promote",
        "Collectibles",
        "App",
    )

    val all: List<BlinkStoreProductGroup> = listOf(
        group(
            "profile_aura",
            "Profile Aura",
            "Profile",
            "Glow, aura and longer-lasting profile-light treatments.",
            "profile_highlight_1h",
            "profile_highlight_1h", "profile_glow_1h", "profile_glow_7d",
        ),
        group(
            "profile_themes",
            "Profile Themes",
            "Profile",
            "Full-profile backgrounds, coordinated themes, banners and atmosphere.",
            "profile_background",
            "profile_background", "profile_theme_3d", "profile_theme_bundle", "profile_banner", "profile_particle_effect",
        ),
        group(
            "avatar_style",
            "Avatar Style",
            "Profile",
            "Rings, animated rings, premium frames and avatar decorations.",
            "profile_ring",
            "profile_ring", "animated_profile_ring", "premium_profile_frame", "avatar_decoration",
        ),
        group(
            "name_style",
            "Name Style",
            "Profile",
            "Premium glow, font and motion treatments for your public name.",
            "username_glow_24h",
            "username_glow_24h", "username_font", "animated_name",
        ),
        group(
            "profile_badges",
            "Profile Badges",
            "Profile",
            "Cosmetic identity badges that stay separate from official verification.",
            "custom_profile_badge",
            "custom_profile_badge", "creator_badge", "limited_edition_badge",
        ),
        group(
            "profile_entrance",
            "Profile Entrance",
            "Profile",
            "Profile reveals, creator introductions and VIP entrance treatments.",
            "profile_entrance_animation",
            "profile_entrance_animation", "creator_intro_card", "vip_profile_entrance",
        ),
        group(
            "profile_music",
            "Profile Music",
            "Profile",
            "Optional profile soundtrack styling that visitors choose to play.",
            "profile_music_theme",
            "profile_music_theme",
        ),
        group(
            "celebration_themes",
            "Celebration Themes",
            "Profile",
            "Birthday and future seasonal profile celebrations.",
            "birthday_profile_theme",
            "birthday_profile_theme",
        ),
        group(
            "comment_style",
            "Comment Style",
            "Social",
            "Premium comment surfaces, highlights, entrances and VIP styling.",
            "comment_highlight",
            "comment_highlight", "comment_color", "comment_entrance_animation", "vip_comment_effect",
        ),
        group(
            "reaction_effects",
            "Reaction Effects",
            "Social",
            "Animated likes, super reactions and premium reaction packs.",
            "animated_like",
            "animated_like", "super_reaction", "reaction_pack", "vip_reaction_pack",
        ),
        group(
            "follow_effect",
            "Follow Effect",
            "Social",
            "A premium follow interaction visible on supported recipient surfaces.",
            "follow_animation",
            "follow_animation",
        ),
        group(
            "chat_themes",
            "Chat Themes",
            "Social",
            "Message bubbles, conversation backgrounds and special DM themes.",
            "chat_bubble_theme",
            "chat_bubble_theme", "chat_background", "special_dm_theme",
        ),
        group(
            "emoji_sticker_packs",
            "Emoji & Sticker Packs",
            "Social",
            "Blink-exclusive emoji and animated sticker collections.",
            "emoji_pack",
            "emoji_pack", "sticker_pack",
        ),
        group(
            "post_style",
            "Post Style",
            "Content",
            "Borders, highlights, entrance motion and premium poll styling.",
            "post_border",
            "post_border", "post_highlight_1h", "post_entrance_animation", "premium_poll_style",
        ),
        group(
            "reel_style",
            "Reel Style",
            "Content",
            "Premium Reel highlights and animated frame treatments.",
            "reel_highlight_1h",
            "reel_highlight_1h", "reel_frame_effect",
        ),
        group(
            "story_style",
            "Story Style",
            "Content",
            "Premium Story highlighting and opening treatment.",
            "story_highlight",
            "story_highlight",
        ),
        group(
            "promote_post",
            "Promote Post",
            "Promote",
            "Post boosts and Spotlight windows while preserving authentic engagement.",
            "post_boost",
            "post_boost", "post_boost_plus", "post_spotlight_6h", "post_spotlight_24h",
        ),
        group(
            "promote_reel",
            "Promote Reel",
            "Promote",
            "Reel boosts and Spotlight windows while preserving authentic engagement.",
            "reel_boost",
            "reel_boost", "reel_boost_plus", "reel_spotlight_6h", "reel_spotlight_24h",
        ),
        group(
            "promote_profile",
            "Promote Profile",
            "Promote",
            "Profile Spotlight and discovery windows without fake engagement.",
            "profile_spotlight_1h",
            "profile_spotlight_1h", "profile_spotlight_24h", "profile_discovery_boost", "discovery_boost_7d",
        ),
        group(
            "promote_marketplace",
            "Promote Marketplace",
            "Promote",
            "Listing highlights, seller Spotlight and Marketplace promotion credits.",
            "market_listing_highlight",
            "market_listing_highlight", "market_seller_spotlight", "market_promo_bundle",
        ),
        group(
            "creator_promotion",
            "Creator Promotion",
            "Promote",
            "Creator promotion credits for profile, posts and Reels.",
            "creator_promo_bundle",
            "creator_promo_bundle",
        ),
        group(
            "collectibles_gifts",
            "Collectibles & Gifts",
            "Collectibles",
            "Digital gifts plus crown, rose, trophy and galaxy collectibles.",
            "digital_gift",
            "digital_gift", "gift_crown", "gift_rose", "gift_trophy", "gift_galaxy",
        ),
        group(
            "app_customization",
            "App Customization",
            "App",
            "Notification sounds and launcher-icon customization.",
            "notification_sound_pack",
            "notification_sound_pack", "app_icon_pack",
        ),
        group(
            "profile_insights",
            "Profile Insights",
            "App",
            "Private aggregate profile analytics and future premium insights.",
            "visitor_insights_24h",
            "visitor_insights_24h",
        ),
        group(
            "blink_vip",
            "BLINK VIP",
            "App",
            "VIP pass plus its exclusive theme, comment, reaction and entrance variants.",
            "blink_vip_10d",
            "blink_vip_10d", "vip_theme", "vip_comment_effect", "vip_reaction_pack", "vip_profile_entrance",
        ),
    )

    fun byId(id: String): BlinkStoreProductGroup? =
        all.firstOrNull { it.id == id }

    fun groupsForCategory(category: String): List<BlinkStoreProductGroup> =
        if (category == "For You") all else all.filter { it.category == category }

    fun groupsContaining(catalogId: String): List<BlinkStoreProductGroup> =
        all.filter { catalogId in it.itemIds }

    val representedCatalogIds: Set<String>
        get() = all.flatMap(BlinkStoreProductGroup::itemIds).toSet()

    private fun group(
        id: String,
        title: String,
        category: String,
        description: String,
        primaryItemId: String,
        vararg itemIds: String,
    ) = BlinkStoreProductGroup(
        id = id,
        title = title,
        category = category,
        description = description,
        primaryItemId = primaryItemId,
        itemIds = itemIds.toList(),
    )
}
