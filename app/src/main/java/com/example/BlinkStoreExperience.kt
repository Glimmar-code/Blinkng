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

/**
 * Human-readable, single source of truth for the usefulness of every Blink Store item.
 * This is intentionally separate from pricing/charging; the server remains authoritative
 * for balances, ownership, activation and expiry.
 */
fun BlinkStoreItem.premiumExperience(): BlinkStoreExperience = when (id) {
    "profile_highlight_1h" -> BlinkStoreExperience(
        "Makes your profile stand out in eligible discovery placements for one hour.",
        "Profile, search and discovery surfaces",
        "Activate it from Vault when you want the one-hour timer to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "HIGHLIGHT",
        "shimmer",
        45
    )
    "comment_highlight" -> BlinkStoreExperience(
        "Gives one of your comments a premium highlighted treatment so it is easier to notice.",
        "The selected comment and its thread",
        "Choose one of your comments when you press Use.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "COMMENT FX",
        "glow",
        35
    )
    "comment_color" -> BlinkStoreExperience(
        "Unlocks a premium comment accent that identifies your comments as customized.",
        "Comments and replies where supported",
        "Apply it once from Vault; remove it later if you want the default style.",
        BlinkExperienceVisibility.SHARED_SOCIAL,
        "COLOR",
        "soft_pulse",
        24
    )
    "animated_like" -> BlinkStoreExperience(
        "Unlocks a richer animated reaction when you like supported content.",
        "Feed and Reel like interactions",
        "Apply it from Vault to make it your active like effect.",
        BlinkExperienceVisibility.SHARED_SOCIAL,
        "LIKE FX",
        "burst",
        18
    )
    "profile_glow_1h" -> BlinkStoreExperience(
        "Adds a premium glow identity treatment for one hour.",
        "Profile plus public name/identity surfaces",
        "Activate it from Vault; the timer begins only after activation.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "GLOW",
        "pulse",
        70
    )
    "chat_bubble_theme" -> BlinkStoreExperience(
        "Unlocks a premium message-bubble appearance for your chats.",
        "Supported direct and group chats",
        "Apply it from Vault, then use the theme in chat customization.",
        BlinkExperienceVisibility.SHARED_SOCIAL,
        "CHAT",
        "soft_shift",
        16
    )
    "reaction_pack" -> BlinkStoreExperience(
        "Unlocks additional premium reactions you can send to other people.",
        "Chats and supported social reactions",
        "Apply it from Vault, then pick the new reactions from supported reaction trays.",
        BlinkExperienceVisibility.RECIPIENT_VISIBLE,
        "REACTIONS",
        "pop",
        16
    )
    "profile_ring" -> BlinkStoreExperience(
        "Adds a premium ring identity marker around your profile presence.",
        "Profile and public identity surfaces",
        "Apply it from Vault; it stays unlocked permanently.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "RING",
        "halo",
        62
    )
    "username_glow_24h" -> BlinkStoreExperience(
        "Makes your public name carry a premium glow treatment for 24 hours.",
        "Profile, feed, comments, Reels and other name surfaces",
        "Activate it from Vault when you want the 24-hour timer to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "NAME GLOW",
        "shimmer",
        78
    )
    "post_border" -> BlinkStoreExperience(
        "Adds a premium border treatment to one selected post.",
        "The selected post in feed/profile placements",
        "Press Use and choose the post you want to decorate.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "POST FX",
        "edge_glow",
        42
    )
    "story_highlight" -> BlinkStoreExperience(
        "Makes one story placement look more premium and easier to notice.",
        "Story placements",
        "Press Use and choose eligible story/post content.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "STORY",
        "pulse",
        34
    )
    "profile_background" -> BlinkStoreExperience(
        "Unlocks a premium background treatment for your profile.",
        "Your public profile",
        "Apply it from Vault to use the premium profile background.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "THEME",
        "gradient_shift",
        50
    )
    "emoji_pack" -> BlinkStoreExperience(
        "Unlocks exclusive Blink emoji for supported conversations.",
        "Messages you send with the premium emoji",
        "Apply it from Vault, then use the emoji from supported chat pickers.",
        BlinkExperienceVisibility.RECIPIENT_VISIBLE,
        "EMOJI",
        "pop",
        15
    )
    "animated_profile_ring" -> BlinkStoreExperience(
        "Upgrades your identity ring with a premium animated treatment.",
        "Profile and public identity surfaces",
        "Apply it from Vault; it remains unlocked permanently.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "LIVE RING",
        "orbit",
        82
    )
    "chat_background" -> BlinkStoreExperience(
        "Unlocks a premium chat background for your own conversation experience.",
        "Your chat screen",
        "Apply it from Vault, then select it in supported chat customization.",
        BlinkExperienceVisibility.PRIVATE_UTILITY,
        "CHAT BG",
        "gradient_shift",
        10
    )
    "profile_entrance_animation" -> BlinkStoreExperience(
        "Adds a premium entrance motion when your profile identity is presented.",
        "Your public profile",
        "Apply it from Vault to make it your active profile entrance effect.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "ENTRANCE",
        "spring_reveal",
        58
    )
    "post_highlight_1h" -> BlinkStoreExperience(
        "Gives one selected post a premium highlighted treatment for one hour.",
        "The selected post",
        "Press Use, choose a post, and the one-hour timer starts immediately.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "HIGHLIGHT",
        "shimmer",
        48
    )
    "reel_highlight_1h" -> BlinkStoreExperience(
        "Gives one selected Reel a premium highlighted treatment for one hour.",
        "The selected Reel",
        "Press Use, choose a Reel, and the one-hour timer starts immediately.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "REEL FX",
        "shimmer",
        48
    )
    "visitor_insights_24h" -> BlinkStoreExperience(
        "Unlocks your own profile-visitor analytics window for 24 hours.",
        "Private analytics only",
        "Activate it from Vault when you are ready to use the 24-hour analytics window.",
        BlinkExperienceVisibility.PRIVATE_UTILITY,
        "INSIGHTS",
        "count_up",
        8
    )
    "notification_sound_pack" -> BlinkStoreExperience(
        "Unlocks premium Blink notification tones on your device.",
        "Your device notifications",
        "Apply it from Vault, then choose a premium sound in supported notification settings.",
        BlinkExperienceVisibility.PRIVATE_UTILITY,
        "SOUNDS",
        "wave",
        8
    )
    "app_icon_pack" -> BlinkStoreExperience(
        "Unlocks additional Blink app-icon choices.",
        "Your device launcher",
        "Apply it from Vault, then choose an unlocked app icon where supported.",
        BlinkExperienceVisibility.PRIVATE_UTILITY,
        "ICON",
        "flip",
        8
    )
    "profile_spotlight_1h" -> BlinkStoreExperience(
        "Puts your profile into a premium spotlight state for one hour.",
        "Profile and eligible discovery surfaces",
        "Activate it from Vault when you want the one-hour window to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "SPOTLIGHT",
        "spotlight",
        88
    )
    "username_font" -> BlinkStoreExperience(
        "Unlocks an extra premium name treatment for your public identity.",
        "Profile, feed and supported username/name surfaces",
        "Apply it from Vault; it remains unlocked permanently.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "NAME",
        "type_reveal",
        68
    )
    "sticker_pack" -> BlinkStoreExperience(
        "Unlocks a premium sticker collection you can send to other users.",
        "Supported chats",
        "Apply it from Vault, then use the stickers from supported chat pickers.",
        BlinkExperienceVisibility.RECIPIENT_VISIBLE,
        "STICKERS",
        "pop",
        14
    )
    "digital_gift" -> BlinkStoreExperience(
        "Lets you send a premium Blink gift directly to another account.",
        "Recipient gift experience",
        "Press Use, enter the recipient username and optionally add a message.",
        BlinkExperienceVisibility.RECIPIENT_VISIBLE,
        "GIFT",
        "celebrate",
        30
    )
    "post_boost" -> BlinkStoreExperience(
        "Increases eligible distribution opportunity for one post without creating fake engagement.",
        "Selected post distribution and boost metrics",
        "Buy a strength, press Use, then choose one of your posts.",
        BlinkExperienceVisibility.DISTRIBUTION,
        "BOOSTED",
        "rise",
        44
    )
    "reel_boost" -> BlinkStoreExperience(
        "Increases eligible distribution opportunity for one Reel without creating fake engagement.",
        "Selected Reel distribution and boost metrics",
        "Buy a strength, press Use, then choose one of your Reels.",
        BlinkExperienceVisibility.DISTRIBUTION,
        "BOOSTED",
        "rise",
        44
    )
    "market_listing_highlight" -> BlinkStoreExperience(
        "Highlights one marketplace listing so it carries a premium promotional treatment.",
        "Selected Marketplace listing",
        "Press Use and choose one eligible listing.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "FEATURED",
        "edge_glow",
        46
    )
    "profile_discovery_boost" -> BlinkStoreExperience(
        "Raises eligible profile-discovery opportunity for six hours without guaranteeing ranking.",
        "Profile/discovery surfaces",
        "Activate it from Vault when you want the six-hour window to begin.",
        BlinkExperienceVisibility.DISTRIBUTION,
        "DISCOVERY",
        "rise",
        52
    )
    "custom_profile_badge" -> BlinkStoreExperience(
        "Adds a premium cosmetic badge to your public identity.",
        "Profile and public name surfaces",
        "Apply it from Vault; it remains unlocked permanently.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "PREMIUM",
        "sparkle",
        86
    )
    "profile_theme_3d" -> BlinkStoreExperience(
        "Activates a richer premium profile theme for three days.",
        "Your public profile",
        "Activate it from Vault when you want the three-day timer to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "3D THEME",
        "depth_reveal",
        74
    )
    "animated_name" -> BlinkStoreExperience(
        "Adds premium motion to the way your name identity is presented.",
        "Profile and supported public name surfaces",
        "Apply it from Vault; it remains unlocked permanently.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "LIVE NAME",
        "shimmer",
        90
    )
    "special_dm_theme" -> BlinkStoreExperience(
        "Unlocks a premium direct-message theme.",
        "Supported direct-message conversations",
        "Apply it from Vault, then select it in supported DM customization.",
        BlinkExperienceVisibility.SHARED_SOCIAL,
        "DM THEME",
        "gradient_shift",
        16
    )
    "post_spotlight_6h" -> BlinkStoreExperience(
        "Gives one selected post a premium six-hour spotlight window.",
        "The selected post",
        "Press Use, choose a post, and the six-hour timer starts.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "SPOTLIGHT",
        "spotlight",
        60
    )
    "reel_spotlight_6h" -> BlinkStoreExperience(
        "Gives one selected Reel a premium six-hour spotlight window.",
        "The selected Reel",
        "Press Use, choose a Reel, and the six-hour timer starts.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "SPOTLIGHT",
        "spotlight",
        60
    )
    "market_seller_spotlight" -> BlinkStoreExperience(
        "Puts your Marketplace seller identity into a premium spotlight state for six hours.",
        "Marketplace seller/discovery surfaces",
        "Activate it from Vault when you want the six-hour timer to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "SELLER PRO",
        "spotlight",
        64
    )
    "profile_spotlight_24h" -> BlinkStoreExperience(
        "Keeps your profile in a premium spotlight state for 24 hours.",
        "Profile and eligible discovery surfaces",
        "Activate it from Vault when you want the 24-hour timer to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "SPOTLIGHT",
        "spotlight",
        92
    )
    "post_boost_plus" -> BlinkStoreExperience(
        "Provides a ready-to-use 2× six-hour distribution boost for one post.",
        "Selected post distribution and boost metrics",
        "Press Use and choose one of your posts.",
        BlinkExperienceVisibility.DISTRIBUTION,
        "2X BOOST",
        "rise",
        50
    )
    "reel_boost_plus" -> BlinkStoreExperience(
        "Provides a ready-to-use 2× six-hour distribution boost for one Reel.",
        "Selected Reel distribution and boost metrics",
        "Press Use and choose one of your Reels.",
        BlinkExperienceVisibility.DISTRIBUTION,
        "2X BOOST",
        "rise",
        50
    )
    "vip_theme" -> BlinkStoreExperience(
        "Unlocks an exclusive theme while Blink VIP is active.",
        "Your Blink app/profile theme where supported",
        "Apply it from Vault while VIP is active.",
        BlinkExperienceVisibility.PRIVATE_UTILITY,
        "VIP THEME",
        "royal_shimmer",
        30
    )
    "profile_glow_7d" -> BlinkStoreExperience(
        "Adds a long-running premium glow identity treatment for seven days.",
        "Profile and public identity surfaces",
        "Activate it from Vault when you want the seven-day timer to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "7D GLOW",
        "pulse",
        94
    )
    "premium_profile_frame" -> BlinkStoreExperience(
        "Adds a premium frame identity treatment around your profile presence.",
        "Profile and supported public identity surfaces",
        "Apply it from Vault; it remains unlocked permanently.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "FRAME",
        "edge_glow",
        96
    )
    "creator_badge" -> BlinkStoreExperience(
        "Adds a premium cosmetic creator badge to your identity.",
        "Profile, feed and supported creator identity surfaces",
        "Apply it from Vault; it remains unlocked permanently.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "CREATOR",
        "sparkle",
        100
    )
    "post_spotlight_24h" -> BlinkStoreExperience(
        "Gives one selected post a premium 24-hour spotlight window.",
        "The selected post",
        "Press Use, choose a post, and the 24-hour timer starts.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "SPOTLIGHT",
        "spotlight",
        70
    )
    "reel_spotlight_24h" -> BlinkStoreExperience(
        "Gives one selected Reel a premium 24-hour spotlight window.",
        "The selected Reel",
        "Press Use, choose a Reel, and the 24-hour timer starts.",
        BlinkExperienceVisibility.PUBLIC_CONTENT,
        "SPOTLIGHT",
        "spotlight",
        70
    )
    "discovery_boost_7d" -> BlinkStoreExperience(
        "Raises eligible profile-discovery opportunity for seven days without fake engagement.",
        "Profile/discovery surfaces",
        "Activate it from Vault when you want the seven-day window to begin.",
        BlinkExperienceVisibility.DISTRIBUTION,
        "DISCOVERY",
        "rise",
        76
    )
    "profile_theme_bundle" -> BlinkStoreExperience(
        "Unlocks a collection of premium profile-theme choices.",
        "Your public profile",
        "Apply it from Vault, then use the unlocked profile theme choices where supported.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "THEMES",
        "gradient_shift",
        84
    )
    "creator_promo_bundle" -> BlinkStoreExperience(
        "Breaks into creator promotion credits: Post Boost Plus, Reel Boost Plus and Profile Spotlight.",
        "Vault first, then the selected promoted surfaces",
        "Activate the bundle once; its included credits are added back into your Vault for individual use.",
        BlinkExperienceVisibility.BUNDLE,
        "CREATOR KIT",
        "celebrate",
        38
    )
    "market_promo_bundle" -> BlinkStoreExperience(
        "Breaks into Marketplace listing highlights and seller spotlight time.",
        "Vault first, then Marketplace promotional surfaces",
        "Activate the bundle once; its included promotional items are added to your Vault.",
        BlinkExperienceVisibility.BUNDLE,
        "MARKET KIT",
        "celebrate",
        38
    )
    "blink_vip_10d" -> BlinkStoreExperience(
        "Activates ten days of VIP identity, Store discount, claimable boosts, rewards and premium entitlements.",
        "Profile, comments, Reels, search, notifications, leaderboard, Marketplace and chat surfaces that consume VIP status",
        "Buy it into Vault, then activate when you want the ten-day pass to begin.",
        BlinkExperienceVisibility.PUBLIC_IDENTITY,
        "VIP",
        "royal_shimmer",
        120
    )
    else -> BlinkStoreExperience(
        benefit = description,
        visibleAt = when (target) {
            BlinkStoreTarget.NONE -> "Blink"
            else -> target.name.lowercase().replaceFirstChar(Char::uppercase)
        },
        activationHint = when (type) {
            BlinkStoreItemType.PERMANENT -> "Apply it from Vault to use the permanent unlock."
            BlinkStoreItemType.CONTENT_SPECIFIC -> "Press Use and choose where to apply it."
            else -> "Activate it from Vault when you are ready to use it."
        },
        visibility = BlinkExperienceVisibility.PRIVATE_UTILITY,
        publicLabel = category.uppercase().take(10),
        motion = "premium_reveal"
    )
}
