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
    "profile_highlight_1h" -> experience("Transforms your complete profile header into a coordinated premium identity surface for seven days.", "Full profile, avatar and identity accents", "Activate from Collection when you want the timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "AURA", "light sweep", 92)
    "comment_highlight" -> experience("Transforms one selected comment into a complete premium card with an animated edge and reaction glow.", "Entire selected comment and thread", "Use it on one of your comments.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "edge reveal", 96)
    "comment_color" -> experience("Applies a coordinated Aurora surface to your comments and replies.", "Entire comment and reply surface", "Apply it from Collection; remove it when you want the default style.", DesktopPremiumVisibility.SHARED_SOCIAL, "AURORA", "gradient drift", 78)
    "comment_entrance_animation" -> experience("Gives one selected comment a polished premium entrance and complete highlighted surface.", "Entire selected comment", "Use it on one of your comments.", DesktopPremiumVisibility.PUBLIC_CONTENT, "PREMIERE", "spring reveal", 92)
    "animated_like" -> experience("Unlocks a richer animated like reaction.", "Feed and Reel reactions", "Apply it from Collection as your active like effect.", DesktopPremiumVisibility.SHARED_SOCIAL, "LIKE FX", "burst", 18)
    "profile_glow_1h" -> experience("Adds premium profile glow for seven days.", "Profile and public identity surfaces", "Activate from Collection; the timer begins only after activation.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "GLOW", "pulse", 70)
    "chat_bubble_theme" -> experience("Unlocks premium message-bubble styling.", "Supported direct and group chats", "Apply it from Collection.", DesktopPremiumVisibility.SHARED_SOCIAL, "CHAT", "soft shift", 16)
    "reaction_pack" -> experience("Unlocks extra premium reactions.", "Chats and supported social reactions", "Apply it from Collection, then use the unlocked reactions.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "REACTIONS", "pop", 16)
    "profile_ring" -> experience("Adds a premium ring identity marker.", "Profile and public identity surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "RING", "halo", 62)
    "username_glow_24h" -> experience("Makes your public name glow for seven days.", "Profile, feed, comments and Reels", "Activate from Collection when you want the seven-day timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "NAME GLOW", "shimmer", 78)
    "post_border" -> experience("Adds a premium border to one selected post.", "Selected post", "Use it and choose your post.", DesktopPremiumVisibility.PUBLIC_CONTENT, "POST FX", "edge glow", 42)
    "story_highlight" -> experience("Makes one story placement stand out.", "Story placements", "Use it and choose eligible content.", DesktopPremiumVisibility.PUBLIC_CONTENT, "STORY", "pulse", 34)
    "profile_background" -> experience("Unlocks a premium profile background.", "Public profile", "Apply it from Collection.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "THEME", "gradient shift", 50)
    "emoji_pack" -> experience("Unlocks exclusive Blink emoji.", "Messages using premium emoji", "Apply it from Collection, then use supported emoji pickers.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "EMOJI", "pop", 15)
    "animated_profile_ring" -> experience("Upgrades your identity ring with motion.", "Profile and public identity surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LIVE RING", "orbit", 82)
    "chat_background" -> experience("Unlocks a premium chat background.", "Your chat experience", "Apply it from Collection.", DesktopPremiumVisibility.PRIVATE_UTILITY, "CHAT BG", "gradient shift", 10)
    "profile_entrance_animation" -> experience("Adds premium motion when your profile is presented.", "Public profile", "Apply it from Collection.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "ENTRANCE", "spring reveal", 58)
    "post_highlight_1h" -> experience("Highlights one selected post for seven days.", "Selected post", "Use it, choose a post, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "HIGHLIGHT", "shimmer", 48)
    "reel_highlight_1h" -> experience("Highlights one selected Reel for seven days.", "Selected Reel", "Use it, choose a Reel, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "REEL FX", "shimmer", 48)
    "visitor_insights_24h" -> experience("Unlocks profile-visitor analytics for seven days.", "Private analytics", "Activate from Collection when you are ready to use it.", DesktopPremiumVisibility.PRIVATE_UTILITY, "INSIGHTS", "count up", 8)
    "notification_sound_pack" -> experience("Unlocks premium notification sounds.", "Your device notifications", "Apply it from Collection and choose an unlocked sound where supported.", DesktopPremiumVisibility.PRIVATE_UTILITY, "SOUNDS", "wave", 8)
    "app_icon_pack" -> experience("Unlocks extra Blink app icons.", "Your launcher", "Apply it from Collection and select an unlocked icon where supported.", DesktopPremiumVisibility.PRIVATE_UTILITY, "ICON", "flip", 8)
    "profile_spotlight_1h" -> experience("Puts your profile in a premium spotlight state for seven days.", "Profile and eligible discovery", "Activate from Collection when you want the window to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "SPOTLIGHT", "spotlight", 88)
    "username_font" -> experience("Unlocks a premium public-name treatment.", "Profile, feed and supported name surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "NAME", "type reveal", 68)
    "sticker_pack" -> experience("Unlocks a premium sticker collection.", "Supported chats", "Apply it from Collection, then use the stickers in supported chats.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "STICKERS", "pop", 14)
    "digital_gift" -> experience("Sends a premium Blink gift to another account.", "Recipient gift experience", "Use it, enter a recipient username and optional message.", DesktopPremiumVisibility.RECIPIENT_VISIBLE, "GIFT", "celebrate", 30)
    "post_boost" -> experience("Improves eligible distribution opportunity for one post without fake engagement.", "Selected post distribution and metrics", "Buy a strength, use it, then choose one post.", DesktopPremiumVisibility.DISTRIBUTION, "BOOSTED", "rise", 44)
    "reel_boost" -> experience("Improves eligible distribution opportunity for one Reel without fake engagement.", "Selected Reel distribution and metrics", "Buy a strength, use it, then choose one Reel.", DesktopPremiumVisibility.DISTRIBUTION, "BOOSTED", "rise", 44)
    "market_listing_highlight" -> experience("Highlights one Marketplace listing.", "Selected Marketplace listing", "Use it and choose an eligible listing.", DesktopPremiumVisibility.PUBLIC_CONTENT, "FEATURED", "edge glow", 46)
    "profile_discovery_boost" -> experience("Raises eligible profile-discovery opportunity for six hours.", "Profile/discovery surfaces", "Activate from Collection when you want the six-hour window to begin.", DesktopPremiumVisibility.DISTRIBUTION, "DISCOVERY", "rise", 52)
    "custom_profile_badge" -> experience("Adds a premium cosmetic badge to your public identity.", "Profile and public name surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "PREMIUM", "sparkle", 86)
    "profile_theme_3d" -> experience("Activates a premium profile theme for thirty days.", "Public profile", "Activate from Collection when you want the thirty-day timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "3D THEME", "depth reveal", 74)
    "animated_name" -> experience("Adds premium motion to your name identity.", "Profile and supported public name surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LIVE NAME", "shimmer", 90)
    "special_dm_theme" -> experience("Unlocks an exclusive direct-message theme.", "Supported DMs", "Apply it from Collection.", DesktopPremiumVisibility.SHARED_SOCIAL, "DM THEME", "gradient shift", 16)
    "post_spotlight_6h" -> experience("Gives one post a six-hour premium spotlight.", "Selected post", "Use it, choose a post, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 60)
    "reel_spotlight_6h" -> experience("Gives one Reel a six-hour premium spotlight.", "Selected Reel", "Use it, choose a Reel, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 60)
    "market_seller_spotlight" -> experience("Puts your Marketplace seller identity in a six-hour spotlight.", "Marketplace seller/discovery surfaces", "Activate from Collection when you want the window to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "SELLER PRO", "spotlight", 64)
    "profile_spotlight_24h" -> experience("Keeps your profile spotlighted for thirty days.", "Profile and eligible discovery", "Activate from Collection when you want the window to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "SPOTLIGHT", "spotlight", 92)
    "post_boost_plus" -> experience("Provides a ready-to-use 2× six-hour post boost.", "Selected post distribution and metrics", "Use it and choose one post.", DesktopPremiumVisibility.DISTRIBUTION, "2X BOOST", "rise", 50)
    "reel_boost_plus" -> experience("Provides a ready-to-use 2× six-hour Reel boost.", "Selected Reel distribution and metrics", "Use it and choose one Reel.", DesktopPremiumVisibility.DISTRIBUTION, "2X BOOST", "rise", 50)
    "vip_theme" -> experience("Unlocks an exclusive theme while VIP is active.", "Your Blink theme", "Apply it from Collection while VIP is active.", DesktopPremiumVisibility.PRIVATE_UTILITY, "VIP THEME", "royal shimmer", 30)
    "profile_glow_7d" -> experience("Adds a premium public identity glow for seven days.", "Profile and public identity surfaces", "Activate from Collection when you want the seven-day timer to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "7D GLOW", "pulse", 94)
    "premium_profile_frame" -> experience("Adds a premium frame around your profile presence.", "Profile and public identity surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "FRAME", "edge glow", 96)
    "creator_badge" -> experience("Adds a premium creator badge to your public identity.", "Profile, feed and creator identity surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "CREATOR", "sparkle", 100)
    "post_spotlight_24h" -> experience("Gives one post a thirty-day premium spotlight.", "Selected post", "Use it, choose a post, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 70)
    "reel_spotlight_24h" -> experience("Gives one Reel a thirty-day premium spotlight.", "Selected Reel", "Use it, choose a Reel, and the timer begins.", DesktopPremiumVisibility.PUBLIC_CONTENT, "SPOTLIGHT", "spotlight", 70)
    "discovery_boost_7d" -> experience("Raises eligible profile-discovery opportunity for seven days.", "Profile/discovery surfaces", "Activate from Collection when you want the seven-day window to begin.", DesktopPremiumVisibility.DISTRIBUTION, "DISCOVERY", "rise", 76)
    "profile_theme_bundle" -> experience("Unlocks a collection of premium profile-theme choices.", "Public profile", "Apply it from Collection.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "THEMES", "gradient shift", 84)
    "creator_promo_bundle" -> experience("Breaks into creator promotion credits for posts, Reels and profile spotlight.", "Collection then promoted surfaces", "Activate once; included credits return to Collection for individual use.", DesktopPremiumVisibility.BUNDLE, "CREATOR KIT", "celebrate", 38)
    "market_promo_bundle" -> experience("Breaks into Marketplace listing highlights and seller spotlight time.", "Collection then Marketplace promotion", "Activate once; included promotional items return to Collection.", DesktopPremiumVisibility.BUNDLE, "MARKET KIT", "celebrate", 38)
    "blink_vip_10d" -> experience("Activates thirty days of VIP identity, discount, boosts, rewards and premium entitlements.", "Profile, comments, Reels, search, notifications, leaderboard, Marketplace and chats", "Buy into Collection, then activate when you want the thirty-day pass to begin.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "VIP", "royal shimmer", 120)
    "campus_signature_theme" -> experience(description, "Public profile and campus identity surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "CAMPUS", "gradient drift", 88)
    "campus_signature_frame" -> experience(description, "Profile and public avatar surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "CAMPUS", "edge reveal", 86)
    "campus_signature_nameplate" -> experience(description, "Profile, feed, comments, Reels and name surfaces", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "CAMPUS", "light sweep", 84)
    "campus_signature_chat" -> experience(description, "Supported direct and group conversations", "Apply it from Collection; ownership is permanent.", DesktopPremiumVisibility.SHARED_SOCIAL, "CAMPUS", "gradient drift", 72)
    "level_10_neon_frame" -> experience(description, "Profile and public avatar surfaces", "Buy it or claim it free after reaching Level 10.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LEVEL 10", "light sweep", 90)
    "level_25_signature_nameplate" -> experience(description, "Profile, feed, comments, Reels and name surfaces", "Buy it or claim it free after reaching Level 25.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LEVEL 25", "light sweep", 94)
    "level_50_legend_aura" -> experience(description, "Public profile and supported identity surfaces", "Buy it or claim it free after reaching Level 50.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LEGEND", "particles", 110)
    "christmas_2026_profile_theme" -> experience(description, "Public profile", "Available only during the server-controlled seasonal window.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LIMITED", "particles", 98)
    "christmas_2026_nameplate" -> experience(description, "Profile, feed, comments, Reels and name surfaces", "Available only during the server-controlled seasonal window.", DesktopPremiumVisibility.PUBLIC_IDENTITY, "LIMITED", "light sweep", 96)
    else -> experience(description, targetType.ifBlank { "Blink" }, if (itemType == "PERMANENT") "Apply it from Collection." else "Activate it from Collection when ready.", DesktopPremiumVisibility.PRIVATE_UTILITY, category.uppercase().take(10), "premium reveal")
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
    "profile_ring", "animated_profile_ring", "premium_profile_frame",
    "campus_signature_frame", "level_10_neon_frame" -> "profile_frame"
    "profile_background", "profile_theme_bundle", "campus_signature_theme",
    "christmas_2026_profile_theme" -> "profile_theme"
    "username_font", "animated_name", "campus_signature_nameplate",
    "level_25_signature_nameplate", "christmas_2026_nameplate" -> "name_style"
    "level_50_legend_aura" -> "profile_aura"
    "custom_profile_badge", "creator_badge" -> "profile_badge"
    "chat_bubble_theme", "special_dm_theme" -> "chat_theme"
    "reaction_pack", "emoji_pack", "sticker_pack" -> "social_pack_$catalogId"
    else -> catalogId
}
