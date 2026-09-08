package com.example

import com.example.data.models.BlinkStoreItem
import com.example.data.models.BlinkStoreItemType
import com.example.data.models.BlinkStoreTarget

enum class BlinkExperienceVisibility {
    PUBLIC_IDENTITY,
    PUBLIC_CONTENT,
    SHARED_SOCIAL,
    DISTRIBUTION,
    PRIVATE_UTILITY,
    RECIPIENT_VISIBLE,
    BUNDLE
}

data class BlinkStoreExperience(
    val benefit: String,
    val visibleAt: String,
    val activationHint: String,
    val visibility: BlinkExperienceVisibility,
    val publicLabel: String,
    val motion: String,
    val priority: Int = 10
) {
    val publiclyVisible: Boolean
        get() = visibility in setOf(
            BlinkExperienceVisibility.PUBLIC_IDENTITY,
            BlinkExperienceVisibility.PUBLIC_CONTENT,
            BlinkExperienceVisibility.SHARED_SOCIAL,
            BlinkExperienceVisibility.DISTRIBUTION,
            BlinkExperienceVisibility.RECIPIENT_VISIBLE
        )
}

private val publicIdentityItems = setOf(
    "profile_highlight_1h", "profile_glow_1h", "profile_ring", "username_glow_24h",
    "profile_background", "animated_profile_ring", "profile_entrance_animation", "profile_spotlight_1h",
    "username_font", "custom_profile_badge", "profile_theme_3d", "animated_name",
    "market_seller_spotlight", "profile_spotlight_24h", "profile_glow_7d", "premium_profile_frame",
    "creator_badge", "discovery_boost_7d", "profile_theme_bundle", "blink_vip_10d",
    "profile_banner", "avatar_decoration", "profile_particle_effect", "birthday_profile_theme",
    "limited_edition_badge", "gift_crown", "gift_rose", "gift_trophy", "gift_galaxy",
    "profile_music_theme", "creator_intro_card", "vip_profile_entrance"
)

private val publicContentItems = setOf(
    "comment_highlight", "post_border", "story_highlight", "post_highlight_1h", "reel_highlight_1h",
    "market_listing_highlight", "post_spotlight_6h", "reel_spotlight_6h", "post_spotlight_24h",
    "reel_spotlight_24h", "reel_frame_effect", "post_entrance_animation",
    "comment_entrance_animation", "premium_poll_style"
)

private val sharedSocialItems = setOf(
    "comment_color", "animated_like", "chat_bubble_theme", "chat_background", "special_dm_theme",
    "super_reaction", "vip_comment_effect"
)

private val distributionItems = setOf(
    "post_boost", "reel_boost", "profile_discovery_boost", "post_boost_plus", "reel_boost_plus"
)

private val recipientVisibleItems = setOf(
    "reaction_pack", "emoji_pack", "sticker_pack", "digital_gift", "follow_animation", "vip_reaction_pack"
)

private val privateUtilityItems = setOf(
    "visitor_insights_24h", "notification_sound_pack", "app_icon_pack", "vip_theme"
)

private val bundleItems = setOf("creator_promo_bundle", "market_promo_bundle")

/**
 * Human-readable usefulness and visibility for every Blink Store item.
 * Pricing, ownership, activation, targeting and expiry remain server-authoritative.
 * Even private utilities are publicly discoverable as owned items through Blink Collection;
 * their private data/content is never exposed by this experience model.
 */
fun BlinkStoreItem.premiumExperience(): BlinkStoreExperience {
    val visibility = when (id) {
        in publicIdentityItems -> BlinkExperienceVisibility.PUBLIC_IDENTITY
        in publicContentItems -> BlinkExperienceVisibility.PUBLIC_CONTENT
        in sharedSocialItems -> BlinkExperienceVisibility.SHARED_SOCIAL
        in distributionItems -> BlinkExperienceVisibility.DISTRIBUTION
        in recipientVisibleItems -> BlinkExperienceVisibility.RECIPIENT_VISIBLE
        in bundleItems -> BlinkExperienceVisibility.BUNDLE
        in privateUtilityItems -> BlinkExperienceVisibility.PRIVATE_UTILITY
        else -> when (target) {
            BlinkStoreTarget.PROFILE -> BlinkExperienceVisibility.PUBLIC_IDENTITY
            BlinkStoreTarget.POST, BlinkStoreTarget.REEL, BlinkStoreTarget.COMMENT, BlinkStoreTarget.MARKETPLACE -> BlinkExperienceVisibility.PUBLIC_CONTENT
            BlinkStoreTarget.CHAT -> BlinkExperienceVisibility.SHARED_SOCIAL
            else -> BlinkExperienceVisibility.PRIVATE_UTILITY
        }
    }

    val visibleAt = when (id) {
        "profile_highlight_1h", "profile_spotlight_1h", "profile_spotlight_24h", "profile_discovery_boost", "discovery_boost_7d" -> "Profile, Connect, search and eligible discovery surfaces"
        "profile_glow_1h", "profile_glow_7d", "profile_ring", "animated_profile_ring", "premium_profile_frame", "avatar_decoration" -> "Profile plus supported avatar and identity surfaces"
        "username_glow_24h", "username_font", "animated_name" -> "Profile, feed, comments, Reels and supported name surfaces"
        "profile_background", "profile_theme_3d", "profile_theme_bundle", "profile_banner", "profile_particle_effect", "birthday_profile_theme", "profile_music_theme", "creator_intro_card", "profile_entrance_animation", "vip_profile_entrance" -> "Your public profile"
        "custom_profile_badge", "creator_badge", "limited_edition_badge" -> "Profile and supported public identity surfaces"
        "comment_highlight", "comment_entrance_animation", "comment_color", "vip_comment_effect" -> "Comments and replies where supported"
        "animated_like", "super_reaction" -> "Supported Feed/Reel social interactions"
        "chat_bubble_theme", "chat_background", "special_dm_theme" -> "Supported direct and group conversations"
        "reaction_pack", "emoji_pack", "sticker_pack", "vip_reaction_pack" -> "Messages and reactions other people receive"
        "follow_animation" -> "Supported follow interactions and recipient notifications"
        "post_border", "post_highlight_1h", "post_spotlight_6h", "post_spotlight_24h", "post_entrance_animation", "premium_poll_style" -> "The selected/supported post surface"
        "story_highlight" -> "Story placements"
        "reel_highlight_1h", "reel_spotlight_6h", "reel_spotlight_24h", "reel_frame_effect" -> "The selected Reel and supported Reel placements"
        "post_boost", "post_boost_plus" -> "Selected post distribution and private boost metrics"
        "reel_boost", "reel_boost_plus" -> "Selected Reel distribution and private boost metrics"
        "market_listing_highlight" -> "Selected Marketplace listing"
        "market_seller_spotlight" -> "Marketplace seller and discovery surfaces"
        "visitor_insights_24h" -> "Private analytics; ownership only is public in Blink Collection"
        "notification_sound_pack" -> "Your device notification settings; ownership is public in Blink Collection"
        "app_icon_pack" -> "Your device launcher; ownership is public in Blink Collection"
        "vip_theme" -> "Your Blink app/profile theme where supported; ownership is public in Blink Collection"
        "digital_gift" -> "Recipient gift experience and public collection history"
        "gift_crown", "gift_rose", "gift_trophy", "gift_galaxy" -> "Your public Blink Collection and collectible showcase"
        "creator_promo_bundle" -> "Vault credits, then creator promotion surfaces"
        "market_promo_bundle" -> "Vault credits, then Marketplace promotion surfaces"
        "blink_vip_10d" -> "Profile, comments, Reels, search, notifications, leaderboard, Marketplace and chat surfaces that consume VIP status"
        else -> when (target) {
            BlinkStoreTarget.NONE -> "Blink Collection"
            else -> target.name.lowercase().replaceFirstChar(Char::uppercase)
        }
    }

    val activationHint = when {
        id == "digital_gift" -> "Press Use, choose the recipient, and optionally add a message."
        type == BlinkStoreItemType.PERMANENT -> "Apply it from Vault; the unlock stays in your collection permanently."
        type == BlinkStoreItemType.CONTENT_SPECIFIC -> "Press Use and choose exactly where to apply it."
        type == BlinkStoreItemType.PASS -> "Buy it into Vault, then activate when you want the pass timer to begin."
        type == BlinkStoreItemType.TIMED -> "Activate it from Vault only when you want the timer to begin."
        type == BlinkStoreItemType.CONSUMABLE -> "Keep it in Vault until you are ready to use it."
        else -> "Use it from Vault when you are ready."
    }

    val publicLabel = when (id) {
        "profile_highlight_1h" -> "HIGHLIGHT"
        "profile_glow_1h" -> "GLOW"
        "profile_glow_7d" -> "7D GLOW"
        "profile_ring" -> "RING"
        "animated_profile_ring" -> "LIVE RING"
        "premium_profile_frame" -> "FRAME"
        "username_glow_24h" -> "NAME GLOW"
        "username_font" -> "NAME"
        "animated_name" -> "LIVE NAME"
        "custom_profile_badge" -> "PREMIUM"
        "creator_badge" -> "CREATOR"
        "limited_edition_badge" -> "LIMITED"
        "profile_banner" -> "BANNER"
        "avatar_decoration" -> "AVATAR FX"
        "profile_particle_effect" -> "PROFILE FX"
        "birthday_profile_theme" -> "BIRTHDAY"
        "profile_music_theme" -> "PROFILE MUSIC"
        "creator_intro_card" -> "CREATOR INTRO"
        "vip_profile_entrance" -> "VIP ENTRANCE"
        "blink_vip_10d" -> "VIP"
        "post_boost", "reel_boost" -> "BOOSTED"
        "post_boost_plus", "reel_boost_plus" -> "2X BOOST"
        "super_reaction" -> "SUPER FX"
        "digital_gift" -> "GIFT"
        else -> category.uppercase().take(12)
    }

    val motion = when {
        id.contains("glow") || id.contains("highlight") -> "shimmer"
        id.contains("ring") || id == "avatar_decoration" -> "orbit"
        id.contains("spotlight") -> "spotlight"
        id.contains("entrance") || id == "creator_intro_card" -> "spring_reveal"
        id.contains("particle") || id.contains("galaxy") -> "particles"
        id.contains("reaction") || id.contains("like") -> "burst"
        id.contains("theme") || id.contains("background") || id == "profile_banner" -> "gradient_shift"
        id.contains("boost") -> "rise"
        id.contains("gift") || id.contains("birthday") -> "celebrate"
        else -> "premium_reveal"
    }

    val priority = when (id) {
        "blink_vip_10d", "vip_profile_entrance" -> 120
        "creator_badge" -> 100
        "limited_edition_badge" -> 98
        "premium_profile_frame" -> 96
        "profile_glow_7d" -> 94
        "avatar_decoration" -> 93
        "profile_spotlight_24h" -> 92
        "profile_particle_effect" -> 91
        "animated_name" -> 90
        "profile_spotlight_1h" -> 88
        "profile_banner" -> 87
        "custom_profile_badge" -> 86
        "profile_theme_bundle" -> 84
        "animated_profile_ring" -> 82
        "birthday_profile_theme" -> 80
        "username_glow_24h" -> 78
        "discovery_boost_7d" -> 76
        "profile_theme_3d" -> 74
        "profile_music_theme" -> 72
        "profile_glow_1h" -> 70
        "username_font" -> 68
        "market_seller_spotlight" -> 64
        "profile_ring" -> 62
        "creator_intro_card" -> 60
        "profile_entrance_animation" -> 58
        "profile_discovery_boost" -> 52
        "post_boost_plus", "reel_boost_plus" -> 50
        "post_boost", "reel_boost" -> 44
        else -> 30
    }

    return BlinkStoreExperience(
        benefit = description,
        visibleAt = visibleAt,
        activationHint = activationHint,
        visibility = visibility,
        publicLabel = publicLabel,
        motion = motion,
        priority = priority
    )
}
