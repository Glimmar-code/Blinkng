package com.blinkng.desktop.ui

import androidx.compose.ui.graphics.Color
import com.blinkng.desktop.data.DesktopStoreItem

enum class DesktopPremiumVisibility {
    PUBLIC_IDENTITY,
    PUBLIC_CONTENT,
    SHARED_SOCIAL,
    DISTRIBUTION,
    PRIVATE_UTILITY,
    RECIPIENT_VISIBLE,
    BUNDLE,
}

data class DesktopPremiumExperience(
    val benefit: String,
    val visibleAt: String,
    val activationHint: String,
    val visibility: DesktopPremiumVisibility,
    val label: String,
    val motion: String,
    val priority: Int = 10,
) {
    val publicFacing: Boolean
        get() = visibility !in setOf(DesktopPremiumVisibility.PRIVATE_UTILITY, DesktopPremiumVisibility.BUNDLE)
}

/** Windows equivalent of the Android Blink Store experience catalog. */
fun DesktopStoreItem.premiumExperience(): DesktopPremiumExperience = when (id) {
    "profile_highlight_1h" -> experience("Makes your profile stand out for one hour.", "Profile, search and discovery", "Activate from Vault when you want the timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "HIGHLIGHT", "shimmer", 45)
    "comment_highlight" -> experience("Highlights one comment with premium treatment.", "Selected comment and thread", "Use it on one of your comments.", DesktopPremiumVisibility.PUBLIC_CONTENT, "COMMENT FX", "glow", 35)
    "comment_color" -> experience("Unlocks a premium comment accent.", "Comments and replies", "Apply it from Vault; remove it when you want the default style.", DesktopPremiumVisibility.SHARED_SOCIAL, "COLOR", "soft pulse", 24)
    "animated_like" -> experience("Unlocks a richer animated like reaction.", "Feed and Reel reactions", "Apply it from Vault as your active like effect.", DesktopPremiumVisibility.SHARED_SOCIAL, "LIKE FX", "burst", 18)
    "profile_glow_1h" -> experience("Adds premium profile glow for one hour.", "Profile and public identity surfaces", "Activate from Vault; the timer begins only after activation.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "GLOW", "pulse", 70)
    "chat_bubble_theme" -> experience("Unlocks premium message-bubble styling.", "Supported direct and group chats", "Apply it from Vault.", DesktopPremiumVisibility.SHARED_SOCIAL, "CHAT", "soft shift", 16)
    "reaction_pack" -> experience("Unlocks extra premium reactions.", "Chats and supported social reactions", "Apply it from Vault, then use the unlocked reactions.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "REACTIONS", "pop", 16)
    "profile_ring" -> experience("Adds a premium ring identity marker.", "Profile and public identity surfaces", "Apply it from Vault; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "RING", "halo", 62)
    "username_glow_24h" -> experience("Makes your public name glow for 24 hours.", "Profile, feed, comments and Reels", "Activate from Vault when you want the 24-hour timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "NAME GLOW", "shimmer", 78)
    "post_border" -> experience("Adds a premium border to one selected post.", "Selected post", "Use it and choose your post.", DesktopPremiumVisibility.PUBLIC_CONTENT, "POST FX", "edge glow", 42)
    "story_highlight" -> experience("Makes one story placement stand out.", "Story placements", "Use it and choose eligible content.", DesktopPremiumVisibility.PUBLIC_CONTENT, "STORY", "pulse", 34)
    "profile_background" -> experience("Unlocks a premium profile background.", "Public profile", "Apply it from Vault.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "THEME", "gradient shift", 50)
    "emoji_pack" -> experience("Unlocks exclusive Blink emoji.", "Messages using premium emoji", "Apply it from Vault, then use supported emoji pickers.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "EMOJI", "pop", 15)
    "animated_profile_ring" -> experience("Upgrades your identity ring with motion.", "Profile and public identity surfaces", "Apply it from Vault; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LIVE RING", "orbit", 82)
    "chat_background" -> experience("Unlocks a premium chat background.", "Your chat experience", "Apply it from Vault.", DesktopPremiumVisibility.PRIVATE_UTILITY, "CHAT BG", "gradient shift", 10)
    "profile_entrance_animation" -> experience("Adds premium motion when your profile is presented.", "Public profile", "Apply it from Vault.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "ENTRANCE", "spring reveal", 58)
    "post_highlight_1h" -> experience("Highlights one selected post for one hour.", "Selected post", "Use it, choose a post, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "HIGHLIGHT", "shimmer", 48)
    "reel_highlight_1h" -> experience("Highlights one selected Reel for one hour.", "Selected Reel", "Use it, choose a Reel, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "REEL FX", "shimmer", 48)
    "visitor_insights_24h" -> experience("Unlocks profile-visitor analytics for 24 hours.", "Private analytics", "Activate from Vault when you are ready to use it.", DesktopPremiumVisibility.PRIVATE_UTILITY, "INSIGHTS", "count up", 8)
    "notification_sound_pack" -> experience("Unlocks premium notification sounds.", "Your device notifications", "Apply it from Vault and choose an unlocked sound where supported.", DesktopPremiumVisibility.PRIVATE_UTILITY, "SOUNDS", "wave", 8)
    "app_icon_pack" -> experience("Unlocks extra Blink app icons.", "Your launcher", "Apply it from Vault and select an unlocked icon where supported.", DesktopPremiumVisibility.PRIVATE_UTILITY, "ICON", "flip", 8)
    "profile_spotlight_1h" -> experience("Puts your profile in a premium spotlight state for one hour.", "Profile and eligible discovery", "Activate from Vault when you want the window to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "SPOTLIGHT", "spotlight", 88)
    "username_font" -> experience("Unlocks a premium public-name treatment.", "Profile, feed and supported name surfaces", "Apply it from Vault; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "NAME", "type reveal", 68)
    "sticker_pack" -> experience("Unlocks a premium sticker collection.", "Supported chats", "Apply it from Vault, then use the stickers in supported chats.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "STICKERS", "pop", 14)
    "digital_gift" -> experience("Sends a premium Blink gift to another account.", "Recipient gift experience", "Use it, enter a recipient username and optional message.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "GIFT", "celebrate", 30)
    "post_boost" -> experience("Improves eligible distribution opportunity for one post without fake engagement.", "Selected post distribution and metrics", "Buy a strength, use it, then choose one post.", DesktopPremiumVisibility.DISTRIBUTION, "BOOSTED", "rise", 44)
    "reel_boost" -> experience("Improves eligible distribution opportunity for one Reel without fake engagement.", "Selected Reel distribution and metrics", "Buy a strength, use it, then choose one Reel.", DesktopPremiumVisibility.DISTRIBUTION, "BOOSTED", "rise", 44)
    "market_listing_highlight" -> experience("Highlights one Marketplace listing.", "Selected Marketplace listing", "Use it and choose an eligible listing.", DesktopPremiumVisibility.PUBLIC_CONTENT, "FEATURED", "edge glow", 46)
    "profile_discovery_boost" -> experience("Raises eligible profile-discovery opportunity for six hours.", "Profile/discovery surfaces", "Activate from Vault when you want the six-hour window to begin.", DesktopPremiumVisibility.DISTRIBUTION, "DISCOVERY", "rise", 52)
    "custom_profile_badge" -> experience("Adds a premium cosmetic badge to your public identity.", "Profile and public name surfaces", "Apply it from Vault; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "PREMIUM", "sparkle", 86)
    "profile_theme_3d" -> experience("Activates a premium profile theme for three days.", "Public profile", "Activate from Vault when you want the three-day timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "3D THEME", "depth reveal", 74)
    "animated_name" -> experience("Adds premium motion to your name identity.", "Profile and supported public name surfaces", "Apply it from Vault; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LIVE NAME", "shimmer", 90)
    "special_dm_theme" -> experience("Unlocks an exclusive direct-message theme.", "Supported DMs", "Apply it from Vault.", DesktopPremiumVisibility.SHARED_SOCIAL, "DM THEME", "gradient shift", 16)
    "post_spotlight_6h" -> experience("Gives one post a six-hour premium spotlight.", "Selected post", "Use it, choose a post, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 60)
    "reel_spotlight_6h" -> experience("Gives one Reel a six-hour premium spotlight.", "Selected Reel", "Use it, choose a Reel, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 60)
    "market_seller_spotlight" -> experience("Puts your Marketplace seller identity in a six-hour spotlight.", "Marketplace seller/discovery surfaces", "Activate from Vault when you want the window to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "SELLER PRO", "spotlight", 64)
    "profile_spotlight_24h" -> experience("Keeps your profile spotlighted for 24 hours.", "Profile and eligible discovery", "Activate from Vault when you want the window to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "SPOTLIGHT", "spotlight", 92)
    "post_boost_plus" -> experience("Provides a ready-to-use 2× six-hour post boost.", "Selected post distribution and metrics", "Use it and choose one post.", DesktopPremiumVisibility.DISTRIBUTION, "2X BOOST", "rise", 50)
    "reel_boost_plus" -> experience("Provides a ready-to-use 2× six-hour Reel boost.", "Selected Reel distribution and metrics", "Use it and choose one Reel.", DesktopPremiumVisibility.DISTRIBUTION, "2X BOOST", "rise", 50)
    "vip_theme" -> experience("Unlocks an exclusive theme while VIP is active.", "Your Blink theme", "Apply it from Vault while VIP is active.", DesktopPremiumVisibility.PRIVATE_UTILITY, "VIP THEME", "royal shimmer", 30)
    "profile_glow_7d" -> experience("Adds a premium public identity glow for seven days.", "Profile and public identity surfaces", "Activate from Vault when you want the seven-day timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "7D GLOW", "pulse", 94)
    "premium_profile_frame" -> experience("Adds a premium frame around your profile presence.", "Profile and public identity surfaces", "Apply it from Vault; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "FRAME", "edge glow", 96)
    "creator_badge" -> experience("Adds a premium creator badge to your public identity.", "Profile, feed and creator identity surfaces", "Apply it from Vault; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "CREATOR", "sparkle", 100)
    "post_spotlight_24h" -> experience("Gives one post a 24-hour premium spotlight.", "Selected post", "Use it, choose a post, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 70)
    "reel_spotlight_24h" -> experience("Gives one Reel a 24-hour premium spotlight.", "Selected Reel", "Use it, choose a Reel, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 70)
    "discovery_boost_7d" -> experience("Raises eligible profile-discovery opportunity for seven days.", "Profile/discovery surfaces", "Activate from Vault when you want the seven-day window to begin.", DesktopPremiumVisibility.DISTRIBUTION, "DISCOVERY", "rise", 76)
    "profile_theme_bundle" -> experience("Unlocks a collection of premium profile-theme choices.", "Public profile", "Apply it from Vault.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "THEMES", "gradient shift", 84)
    "creator_promo_bundle" -> experience("Breaks into creator promotion credits for posts, Reels and profile spotlight.", "Vault then promoted surfaces", "Activate once; included credits return to Vault for individual use.", DesktopPremiumVisibility.BUNDLE, "CREATOR KIT", "celebrate", 38)
    "market_promo_bundle" -> experience("Breaks into Marketplace listing highlights and seller spotlight time.", "Vault then Marketplace promotion", "Activate once; included promotional items return to Vault.", DesktopPremiumVisibility.BUNDLE, "MARKET KIT", "celebrate", 38)
    "blink_vip_10d" -> experience("Activates ten days of VIP identity, discount, boosts, rewards and premium entitlements.", "Profile, comments, Reels, search, notifications, leaderboard, Marketplace and chats", "Buy into Vault, then activate when you want the ten-day pass to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "VIP", "royal shimmer", 120)
    else -> experience(description, targetType.ifBlank { "Blink" }, if (itemType == "PERMANENT") "Apply it from Vault." else "Activate it from Vault when ready.", DesktopPremiumVisibility.PRIVATE_UTILITY, category.uppercase().take(10), "premium reveal")
}

private fun experience(
    benefit: String,
    visibleAt: String,
    activationHint: String,
    visibility: DesktopPremiumVisibility,
    label: String,
    motion: String,
    priority: Int = 10,
) = DesktopPremiumExperience(benefit, visibleAt, activationHint, visibility, label, motion, priority)

fun desktopPremiumAccent(experience: DesktopPremiumExperience): Color = when (experience.visibility) {
    DesktopPremiumVisibility.PUBLIC_IDENTITY -> Color(0xFF8B5CF6)
    DesktopPremiumVisibility.PUBLIC_CONTENT -> Color(0xFFEC4899)
    DesktopPremiumVisibility.SHARED_SOCIAL -> Color(0xFF3B82F6)
    DesktopPremiumVisibility.DISTRIBUTION -> Color(0xFFF59E0B)
    DesktopPremiumVisibility.PRIVATE_UTILITY -> Color(0xFF94A3B8)
    DesktopPremiumVisibility.RECIPIENT_VISIBLE -> Color(0xFF06B6D4)
    DesktopPremiumVisibility.BUNDLE -> Color(0xFFA855F7)
}

fun desktopEquipSlot(catalogId: String): String = when (catalogId) {
    "profile_ring", "animated_profile_ring", "premium_profile_frame" -> "profile_frame"
    "profile_background", "profile_theme_bundle" -> "profile_theme"
    "username_font", "animated_name" -> "name_style"
    "custom_profile_badge", "creator_badge" -> "profile_badge"
    "chat_bubble_theme", "special_dm_theme" -> "chat_theme"
    "reaction_pack", "emoji_pack", "sticker_pack" -> "social_pack_$catalogId"
    else -> catalogId
}
