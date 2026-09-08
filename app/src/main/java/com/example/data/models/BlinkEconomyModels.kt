package com.example.data.models

enum class BlinkStoreItemType { CONSUMABLE, TIMED, PERMANENT, CONTENT_SPECIFIC, PASS }
enum class BlinkStoreTarget { NONE, PROFILE, POST, REEL, COMMENT, CHAT, MARKETPLACE, APP }
enum class BlinkInventoryStatus { AVAILABLE, ACTIVE, PERMANENT, USED, EXPIRED }

data class BlinkStoreItem(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val category: String,
    val price: Int,
    val type: BlinkStoreItemType,
    val target: BlinkStoreTarget = BlinkStoreTarget.NONE,
    val durationSeconds: Long? = null,
    val stackable: Boolean = false,
    val vipOnly: Boolean = false,
    val boostMultipliers: List<Int> = emptyList()
)

data class BlinkInventoryItem(
    val id: String,
    val catalogId: String,
    val name: String,
    val iconKey: String,
    val status: BlinkInventoryStatus,
    val quantity: Int = 1,
    val purchasedAt: String = "",
    val activatedAt: String? = null,
    val expiresAt: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val boostMultiplier: Int? = null
)

data class BlinkCoinTransaction(
    val id: String,
    val kind: String,
    val itemName: String,
    val amount: Long,
    val balanceAfter: Long,
    val createdAt: String
)

data class BlinkVipState(
    val active: Boolean = false,
    val expiresAt: String? = null,
    val remainingSeconds: Long = 0L,
    val completedPasses: Int = 0,
    val cumulativeVipDays: Int = 0,
    val vipStreak: Int = 0,
    val autoRenew: Boolean = false,
    val unclaimedPostBoosts2x: Int = 0,
    val unclaimedReelBoosts2x: Int = 0,
    val profileSpotlightsRemaining: Int = 0,
    val postSpotlightsRemaining: Int = 0,
    val reelSpotlightsRemaining: Int = 0,
    val marketplaceHighlightsRemaining: Int = 0,
    val dailyCoinBonusClaimed: Boolean = false
)

data class BlinkBoost(
    val id: String,
    val contentId: String,
    val contentType: BlinkStoreTarget,
    val multiplier: Int,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val extraImpressions: Long = 0,
    val profileVisits: Long = 0,
    val followersAttributed: Long = 0
)

data class BlinkEconomySnapshot(
    val balance: Long = 0,
    val catalog: List<BlinkStoreItem> = emptyList(),
    val inventory: List<BlinkInventoryItem> = emptyList(),
    val transactions: List<BlinkCoinTransaction> = emptyList(),
    val vip: BlinkVipState = BlinkVipState(),
    val activeBoosts: List<BlinkBoost> = emptyList()
)

data class BlinkPurchaseResult(
    val success: Boolean,
    val message: String,
    val balance: Long,
    val inventoryId: String? = null
)

data class BlinkActivationResult(
    val success: Boolean,
    val message: String,
    val expiresAt: String? = null
)

/**
 * Single source of truth for the 70-item Blink Store. The backend migration seeds the
 * same ids/prices and remains authoritative for charging and entitlement validation.
 */
object BlinkStoreCatalog {
    val items: List<BlinkStoreItem> = listOf(
        BlinkStoreItem("profile_highlight_1h", "Profile Highlight — 1 hour", "Feature your profile in eligible Connect/Search discovery with a visible Highlighted treatment for one hour.", "auto_awesome", "Profile", 10, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 3600),
        BlinkStoreItem("comment_highlight", "Comment Highlight", "Give one selected comment a premium animated highlight so it stands out in its thread.", "push_pin", "Social", 10, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.COMMENT, stackable = true),
        BlinkStoreItem("comment_color", "Special Comment Color", "Unlock a recognizable premium accent style for your comments and replies.", "palette", "Social", 15, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.COMMENT),
        BlinkStoreItem("animated_like", "Animated Like Effect", "Unlock a premium animated Blink like effect visible during supported interactions.", "favorite", "Social", 15, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("profile_glow_1h", "Profile Glow — 1 hour", "Add a premium animated glow around your profile identity for one hour.", "flare", "Profile", 20, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 3600, stackable = true),
        BlinkStoreItem("chat_bubble_theme", "Custom Chat Bubble Theme", "Unlock a premium message-bubble design for supported conversations.", "chat_bubble", "Chat", 20, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("reaction_pack", "Special Reaction Pack", "Unlock exclusive animated reactions other people can receive and see.", "emoji_emotions", "Chat", 20, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("profile_ring", "Custom Profile Ring", "Unlock an equipable premium ring around your profile picture across supported Blink surfaces.", "radio_button_unchecked", "Profile", 25, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("username_glow_24h", "Username Glow — 24 hours", "Make your display name glow on supported identity surfaces for 24 hours.", "text_fields", "Profile", 25, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 86400, stackable = true),
        BlinkStoreItem("post_border", "Post Border Effect", "Apply a premium animated frame to one selected post.", "crop_square", "Posts", 25, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, stackable = true),
        BlinkStoreItem("story_highlight", "Story Highlight Effect", "Give one story a premium highlight treatment and opening effect.", "auto_stories", "Posts", 30, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.POST, stackable = true),
        BlinkStoreItem("profile_background", "Profile Background Theme", "Unlock a premium public profile background theme.", "wallpaper", "Profile", 30, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("emoji_pack", "Exclusive Emoji Pack", "Unlock Blink-exclusive emoji for supported conversations.", "mood", "Chat", 30, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("animated_profile_ring", "Animated Profile Ring", "Unlock a moving premium avatar ring visible across supported identity surfaces.", "motion_photos_on", "Profile", 35, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("chat_background", "Chat Background Theme", "Unlock a premium conversation background theme.", "wallpaper", "Chat", 35, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("profile_entrance_animation", "Profile Entrance Animation", "Play a premium entrance animation when another person opens your profile.", "animation", "Profile", 40, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("post_highlight_1h", "Post Highlight — 1 hour", "Visually highlight one selected post for one hour.", "star", "Posts", 40, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 3600, stackable = true),
        BlinkStoreItem("reel_highlight_1h", "Reel Highlight — 1 hour", "Visually highlight one selected Reel for one hour.", "smart_display", "Reels", 40, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 3600, stackable = true),
        BlinkStoreItem("visitor_insights_24h", "Profile Visitor Insights — 24 hours", "Unlock private aggregate profile-visitor analytics for 24 hours while ownership remains visible in your Blink Collection.", "visibility", "Analytics", 40, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 86400, stackable = true),
        BlinkStoreItem("notification_sound_pack", "Notification Sound Pack", "Unlock premium Blink notification sounds; ownership appears in your public Blink Collection.", "notifications_active", "App", 45, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("app_icon_pack", "Custom App Icon Pack", "Unlock premium Blink launcher icons; ownership appears in your public Blink Collection.", "apps", "App", 50, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("profile_spotlight_1h", "Profile Spotlight — 1 hour", "Place your profile in eligible Spotlight discovery surfaces for one hour.", "lightbulb", "Profile", 50, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 3600, stackable = true),
        BlinkStoreItem("username_font", "Special Username Font", "Unlock an equipable premium display-name font on supported identity surfaces.", "font_download", "Profile", 50, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("sticker_pack", "Premium Sticker Pack", "Unlock premium animated stickers other people can receive in supported conversations.", "sticky_note_2", "Chat", 50, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("digital_gift", "Digital Gift", "Send a collectible Blink digital gift to another user.", "redeem", "Gifts", 50, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.PROFILE, stackable = true),
        BlinkStoreItem("post_boost", "Post Boost", "Increase eligible distribution opportunity for one post with selectable 1×, 2×, 3× or 5× strength.", "trending_up", "Boosts", 60, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 21600, stackable = true, boostMultipliers = listOf(1, 2, 3, 5)),
        BlinkStoreItem("reel_boost", "Reel Boost", "Increase eligible distribution opportunity for one Reel with selectable 1×, 2×, 3× or 5× strength.", "rocket_launch", "Boosts", 60, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 21600, stackable = true, boostMultipliers = listOf(1, 2, 3, 5)),
        BlinkStoreItem("market_listing_highlight", "Marketplace Listing Highlight", "Give one Marketplace listing a premium highlighted card and featured treatment.", "storefront", "Marketplace", 60, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.MARKETPLACE, 21600, stackable = true),
        BlinkStoreItem("profile_discovery_boost", "Profile Discovery Boost", "Increase eligible profile-discovery opportunities for six hours without guaranteeing ranking.", "explore", "Boosts", 65, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 21600, stackable = true),
        BlinkStoreItem("custom_profile_badge", "Custom Profile Badge", "Unlock an equipable cosmetic profile badge that stays distinct from verification.", "workspace_premium", "Profile", 70, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("profile_theme_3d", "3-Day Profile Theme", "Transform your public profile with a premium coordinated theme for three days.", "brush", "Profile", 75, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 259200, stackable = true),
        BlinkStoreItem("animated_name", "Animated Name Effect", "Unlock an equipable animated display-name treatment.", "animation", "Profile", 75, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("special_dm_theme", "Special DM Theme", "Unlock a premium direct-message theme for supported conversations.", "forum", "Chat", 80, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("post_spotlight_6h", "Post Spotlight — 6 hours", "Place one selected post in eligible Spotlight surfaces for six hours.", "campaign", "Posts", 80, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 21600, stackable = true),
        BlinkStoreItem("reel_spotlight_6h", "Reel Spotlight — 6 hours", "Place one selected Reel in eligible Reel Spotlight surfaces for six hours.", "play_circle", "Reels", 80, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 21600, stackable = true),
        BlinkStoreItem("market_seller_spotlight", "Marketplace Seller Spotlight", "Feature your Marketplace seller storefront for six hours.", "shopping_bag", "Marketplace", 90, BlinkStoreItemType.TIMED, BlinkStoreTarget.MARKETPLACE, 21600, stackable = true),
        BlinkStoreItem("profile_spotlight_24h", "Profile Spotlight — 24 hours", "Keep your profile prominently featured in eligible discovery surfaces for 24 hours.", "person_search", "Profile", 100, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 86400, stackable = true),
        BlinkStoreItem("post_boost_plus", "Post Boost Plus", "One-tap ready-to-use 2× six-hour distribution boost for one post.", "bolt", "Boosts", 100, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 21600, stackable = true, boostMultipliers = listOf(2)),
        BlinkStoreItem("reel_boost_plus", "Reel Boost Plus", "One-tap ready-to-use 2× six-hour distribution boost for one Reel.", "whatshot", "Boosts", 100, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 21600, stackable = true, boostMultipliers = listOf(2)),
        BlinkStoreItem("vip_theme", "Exclusive VIP Theme", "Unlock a visibly premium VIP theme while Blink VIP is active.", "diamond", "VIP", 120, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP, vipOnly = true),
        BlinkStoreItem("profile_glow_7d", "7-Day Profile Glow", "Keep a premium animated profile-identity glow active for seven days.", "brightness_7", "Profile", 120, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 604800, stackable = true),
        BlinkStoreItem("premium_profile_frame", "Premium Profile Frame", "Unlock a premium avatar frame visible on supported public identity surfaces.", "account_box", "Profile", 130, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("creator_badge", "Premium Creator Badge", "Unlock a public Blink Creator identity badge that remains distinct from verification.", "stars", "Creator", 150, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("post_spotlight_24h", "Post Spotlight — 24 hours", "Prominently feature one selected post for 24 hours.", "volume_up", "Posts", 150, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 86400, stackable = true),
        BlinkStoreItem("reel_spotlight_24h", "Reel Spotlight — 24 hours", "Prominently feature one selected Reel for 24 hours.", "movie_filter", "Reels", 150, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 86400, stackable = true),
        BlinkStoreItem("discovery_boost_7d", "7-Day Discovery Boost", "Increase eligible profile-discovery opportunities across seven days without fake engagement.", "travel_explore", "Boosts", 180, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 604800, stackable = true),
        BlinkStoreItem("profile_theme_bundle", "Premium Profile Theme Bundle", "Permanently unlock a collection of premium public profile themes.", "style", "Profile", 200, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("creator_promo_bundle", "Creator Promotion Bundle", "Combine creator Spotlight and post/Reel boost credits in one promotion package.", "campaign", "Creator", 250, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.PROFILE, stackable = true),
        BlinkStoreItem("market_promo_bundle", "Marketplace Promotion Bundle", "Combine seller Spotlight and Marketplace listing-highlight promotion credits.", "local_mall", "Marketplace", 300, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.MARKETPLACE, stackable = true),
        BlinkStoreItem("blink_vip_10d", "Blink VIP — 10 Days", "Ten days of VIP identity styling, Store discount, analytics and claimable promotion benefits.", "crown", "VIP", 350, BlinkStoreItemType.PASS, BlinkStoreTarget.PROFILE, 864000, stackable = true),

        BlinkStoreItem("super_reaction", "Super Reaction", "Unlock an oversized animated premium reaction visible to people you interact with.", "favorite", "Social", 35, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("profile_banner", "Premium Profile Banner", "Unlock an animated premium banner behind your public profile header.", "wallpaper", "Profile", 85, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("avatar_decoration", "Avatar Decoration", "Unlock a floating premium decoration around your profile picture, separate from your frame or ring.", "auto_awesome", "Profile", 90, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("reel_frame_effect", "Reel Frame Effect", "Apply a premium animated border to one selected Reel.", "smart_display", "Reels", 45, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, stackable = true),
        BlinkStoreItem("post_entrance_animation", "Post Entrance Animation", "Give one selected post a premium entrance animation when it is opened.", "animation", "Posts", 45, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, stackable = true),
        BlinkStoreItem("profile_particle_effect", "Profile Particle Effect", "Unlock subtle premium particles and motion on your public profile.", "auto_awesome", "Profile", 95, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("comment_entrance_animation", "Comment Entrance Animation", "Give one selected comment a premium animated entrance treatment.", "animation", "Social", 30, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.COMMENT, stackable = true),
        BlinkStoreItem("follow_animation", "Exclusive Follow Animation", "Unlock a premium follow interaction effect recipients can notice.", "person_search", "Social", 65, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("birthday_profile_theme", "Birthday Profile Theme — 24 hours", "Activate a celebration profile theme visible for 24 hours.", "redeem", "Profile", 40, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 86400, stackable = true),
        BlinkStoreItem("limited_edition_badge", "Limited Edition Badge", "Own an equipable seasonal collectible badge that stays in your Blink Collection permanently.", "workspace_premium", "Collectibles", 100, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("gift_crown", "Gift Crown Collectible", "Own and display a premium crown collectible in your public Blink Collection.", "redeem", "Collectibles", 75, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("gift_rose", "Gift Rose Collectible", "Own and display an animated rose collectible in your public Blink Collection.", "redeem", "Collectibles", 30, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("gift_trophy", "Gift Trophy Collectible", "Own and display a premium trophy collectible in your public Blink Collection.", "emoji_events", "Collectibles", 60, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("gift_galaxy", "Gift Galaxy Collectible", "Own and display a high-tier animated galaxy collectible in your public Blink Collection.", "auto_awesome", "Collectibles", 150, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("profile_music_theme", "Profile Music Theme", "Unlock an optional short profile soundtrack visitors can choose to play; no forced autoplay.", "music_note", "Profile", 100, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("creator_intro_card", "Creator Intro Card", "Unlock an animated creator introduction card for first-time profile visitors.", "campaign", "Creator", 120, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("premium_poll_style", "Premium Poll Style", "Unlock premium poll backgrounds and motion styles for supported posts.", "palette", "Posts", 65, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("vip_comment_effect", "VIP Comment Effect", "Unlock an exclusive premium comment treatment while Blink VIP is active.", "diamond", "VIP", 50, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.COMMENT, vipOnly = true),
        BlinkStoreItem("vip_reaction_pack", "VIP Reaction Pack", "Unlock exclusive VIP reactions while your Blink VIP pass is active.", "diamond", "VIP", 70, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT, vipOnly = true),
        BlinkStoreItem("vip_profile_entrance", "VIP Profile Entrance", "Unlock an advanced premium profile entrance while Blink VIP is active.", "diamond", "VIP", 140, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE, vipOnly = true)
    )

    fun priceFor(item: BlinkStoreItem, multiplier: Int = 1): Int {
        if (item.boostMultipliers.isEmpty()) return item.price
        return when (multiplier) {
            1 -> item.price
            2 -> (item.price * 1.65).toInt()
            3 -> (item.price * 2.35).toInt()
            5 -> (item.price * 3.65).toInt()
            else -> item.price
        }
    }
}

/** Entitlements are centralized so every existing and future surface can enforce VIP consistently. */
data class VipEntitlements(
    val showVipBadgeEverywhere: Boolean = true,
    val vipInteractionNotifications: Boolean = true,
    val vipNotificationStyling: Boolean = true,
    val postBoostCredits2x: Int = 2,
    val reelBoostCredits2x: Int = 2,
    val profileSpotlights: Int = 1,
    val postSpotlights: Int = 1,
    val reelSpotlights: Int = 1,
    val marketplaceHighlights: Int = 1,
    val profileFrame: Boolean = true,
    val animatedProfileRing: Boolean = true,
    val usernameEffect: Boolean = true,
    val commentBadge: Boolean = true,
    val vipReactions: Boolean = true,
    val exclusiveStickers: Boolean = true,
    val exclusiveChatThemes: Boolean = true,
    val exclusiveProfileThemes: Boolean = true,
    val customProfileBackground: Boolean = true,
    val profileEntranceAnimation: Boolean = true,
    val followerCelebration: Boolean = true,
    val replyStyling: Boolean = true,
    val mentionStyling: Boolean = true,
    val notificationSound: Boolean = true,
    val vipDigitalGifts: Boolean = true,
    val giftAnimation: Boolean = true,
    val dailyCoinBonus: Int = 5,
    val storeDiscountPercent: Int = 10,
    val activityCoinBonusPercent: Int = 10,
    val earlyAccessStoreItems: Boolean = true,
    val earlyAccessReactionsThemes: Boolean = true,
    val limitedDrops: Boolean = true,
    val extraPinnedPosts: Int = 2,
    val extraPinnedReels: Int = 2,
    val extraSavedDrafts: Int = 10,
    val extraProfileLinks: Int = 3,
    val extraProfileCustomizationSlots: Int = 3,
    val extraFeaturedMedia: Int = 3,
    val visitorInsights: Boolean = true,
    val contentPerformanceSummary: Boolean = true,
    val bestPostIndicator: Boolean = true,
    val bestReelIndicator: Boolean = true,
    val followerGrowthSummary: Boolean = true,
    val analyticsCard: Boolean = true,
    val enhancedSellerCard: Boolean = true,
    val vipSellerBadge: Boolean = true,
    val connectVipBadge: Boolean = true,
    val discoveryPriorityStyling: Boolean = true,
    val roomBadge: Boolean = true,
    val chatAccent: Boolean = true,
    val groupAdminStyling: Boolean = true,
    val profileMusicSlot: Boolean = true,
    val collectiblePassBadges: Boolean = true,
    val vipStreakRewards: Boolean = true,
    val vipHistory: Boolean = true,
    val gifting: Boolean = true,
    val expiryReminder: Boolean = true,
    val walletCountdown: Boolean = true,
    val benefitTracker: Boolean = true,
    val unusedBoostCounter: Boolean = true,
    val manualClaimRewards: Boolean = true,
    val vipStoreTab: Boolean = true,
    val vipExclusivePrices: Boolean = true,
    val birthdayEffect: Boolean = true,
    val leaderboardDecoration: Boolean = true,
    val challengeRewards: Boolean = true,
    val activityCosmetics: Boolean = true,
    val milestoneBadges: Boolean = true,
    val profileShareStyle: Boolean = true,
    val qrProfileCardStyle: Boolean = true,
    val appAccentSelection: Boolean = true,
    val purchaseHistory: Boolean = true,
    val renewFromVault: Boolean = true,
    val oneTapRenew: Boolean = true,
    val autoRenewDefault: Boolean = false
)
