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
 * Single source of truth for the 50-item Blink Store. The backend migration seeds the
 * same ids/prices and remains authoritative for charging and entitlement validation.
 */
object BlinkStoreCatalog {
    val items: List<BlinkStoreItem> = listOf(
        BlinkStoreItem("profile_highlight_1h", "Profile Highlight — 1 hour", "Highlight your profile in eligible discovery surfaces for one hour.", "auto_awesome", "Profile", 10, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 3600),
        BlinkStoreItem("comment_highlight", "Comment Highlight", "Give one selected comment a highlighted treatment.", "push_pin", "Social", 10, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.COMMENT, stackable = true),
        BlinkStoreItem("comment_color", "Special Comment Color", "Unlock a premium comment accent style.", "palette", "Social", 15, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.COMMENT),
        BlinkStoreItem("animated_like", "Animated Like Effect", "Unlock a premium like animation.", "favorite", "Social", 15, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("profile_glow_1h", "Profile Glow — 1 hour", "Activate a profile glow for one hour.", "flare", "Profile", 20, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 3600, stackable = true),
        BlinkStoreItem("chat_bubble_theme", "Custom Chat Bubble Theme", "Unlock an additional chat bubble theme.", "chat_bubble", "Chat", 20, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("reaction_pack", "Special Reaction Pack", "Unlock additional reactions.", "emoji_emotions", "Chat", 20, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("profile_ring", "Custom Profile Ring", "Unlock a custom profile ring.", "radio_button_unchecked", "Profile", 25, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("username_glow_24h", "Username Glow — 24 hours", "Apply a glowing username treatment for 24 hours.", "text_fields", "Profile", 25, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 86400, stackable = true),
        BlinkStoreItem("post_border", "Post Border Effect", "Apply a premium border to one post.", "crop_square", "Posts", 25, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, stackable = true),
        BlinkStoreItem("story_highlight", "Story Highlight Effect", "Highlight one story placement.", "auto_stories", "Posts", 30, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.POST, stackable = true),
        BlinkStoreItem("profile_background", "Profile Background Theme", "Unlock a profile background theme.", "wallpaper", "Profile", 30, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("emoji_pack", "Exclusive Emoji Pack", "Unlock an exclusive emoji pack.", "mood", "Chat", 30, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("animated_profile_ring", "Animated Profile Ring", "Unlock an animated profile ring.", "motion_photos_on", "Profile", 35, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("chat_background", "Chat Background Theme", "Unlock a premium chat background.", "wallpaper", "Chat", 35, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("profile_entrance_animation", "Profile Entrance Animation", "Unlock an entrance animation for your profile.", "animation", "Profile", 40, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("post_highlight_1h", "Post Highlight — 1 hour", "Highlight one selected post for one hour.", "star", "Posts", 40, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 3600, stackable = true),
        BlinkStoreItem("reel_highlight_1h", "Reel Highlight — 1 hour", "Highlight one selected reel for one hour.", "smart_display", "Reels", 40, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 3600, stackable = true),
        BlinkStoreItem("visitor_insights_24h", "Profile Visitor Insights — 24 hours", "See profile visitor analytics for 24 hours.", "visibility", "Analytics", 40, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 86400, stackable = true),
        BlinkStoreItem("notification_sound_pack", "Notification Sound Pack", "Unlock premium notification sounds.", "notifications_active", "App", 45, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("app_icon_pack", "Custom App Icon Pack", "Unlock additional Blink app icons.", "apps", "App", 50, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP),
        BlinkStoreItem("profile_spotlight_1h", "Profile Spotlight — 1 hour", "Give your profile a one-hour spotlight placement.", "lightbulb", "Profile", 50, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 3600, stackable = true),
        BlinkStoreItem("username_font", "Special Username Font", "Unlock an additional username font treatment.", "font_download", "Profile", 50, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("sticker_pack", "Premium Sticker Pack", "Unlock a premium sticker pack.", "sticky_note_2", "Chat", 50, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("digital_gift", "Digital Gift", "Send one Blink digital gift to another user.", "redeem", "Gifts", 50, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.PROFILE, stackable = true),
        BlinkStoreItem("post_boost", "Post Boost", "Boost one post. Choose 1×, 2×, 3× or 5× distribution strength when buying.", "trending_up", "Boosts", 60, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 21600, stackable = true, boostMultipliers = listOf(1, 2, 3, 5)),
        BlinkStoreItem("reel_boost", "Reel Boost", "Boost one reel. Choose 1×, 2×, 3× or 5× distribution strength when buying.", "rocket_launch", "Boosts", 60, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 21600, stackable = true, boostMultipliers = listOf(1, 2, 3, 5)),
        BlinkStoreItem("market_listing_highlight", "Marketplace Listing Highlight", "Highlight one marketplace listing.", "storefront", "Marketplace", 60, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.MARKETPLACE, 21600, stackable = true),
        BlinkStoreItem("profile_discovery_boost", "Profile Discovery Boost", "Increase eligible profile discovery opportunities for six hours.", "explore", "Boosts", 65, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 21600, stackable = true),
        BlinkStoreItem("custom_profile_badge", "Custom Profile Badge", "Unlock an additional cosmetic profile badge.", "workspace_premium", "Profile", 70, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("profile_theme_3d", "3-Day Profile Theme", "Use a premium profile theme for three days.", "brush", "Profile", 75, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 259200, stackable = true),
        BlinkStoreItem("animated_name", "Animated Name Effect", "Unlock an animated name treatment.", "animation", "Profile", 75, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("special_dm_theme", "Special DM Theme", "Unlock a premium direct-message theme.", "forum", "Chat", 80, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.CHAT),
        BlinkStoreItem("post_spotlight_6h", "Post Spotlight — 6 hours", "Spotlight one selected post for six hours.", "campaign", "Posts", 80, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 21600, stackable = true),
        BlinkStoreItem("reel_spotlight_6h", "Reel Spotlight — 6 hours", "Spotlight one selected reel for six hours.", "play_circle", "Reels", 80, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 21600, stackable = true),
        BlinkStoreItem("market_seller_spotlight", "Marketplace Seller Spotlight", "Spotlight your seller profile for six hours.", "shopping_bag", "Marketplace", 90, BlinkStoreItemType.TIMED, BlinkStoreTarget.MARKETPLACE, 21600, stackable = true),
        BlinkStoreItem("profile_spotlight_24h", "Profile Spotlight — 24 hours", "Give your profile a 24-hour spotlight placement.", "person_search", "Profile", 100, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 86400, stackable = true),
        BlinkStoreItem("post_boost_plus", "Post Boost Plus", "A ready-to-use 2× six-hour post boost.", "bolt", "Boosts", 100, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 21600, stackable = true, boostMultipliers = listOf(2)),
        BlinkStoreItem("reel_boost_plus", "Reel Boost Plus", "A ready-to-use 2× six-hour reel boost.", "whatshot", "Boosts", 100, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 21600, stackable = true, boostMultipliers = listOf(2)),
        BlinkStoreItem("vip_theme", "Exclusive VIP Theme", "Unlock a VIP theme; usable while VIP is active.", "diamond", "VIP", 120, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.APP, vipOnly = true),
        BlinkStoreItem("profile_glow_7d", "7-Day Profile Glow", "Activate a profile glow for seven days.", "brightness_7", "Profile", 120, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 604800, stackable = true),
        BlinkStoreItem("premium_profile_frame", "Premium Profile Frame", "Unlock a premium profile frame.", "account_box", "Profile", 130, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("creator_badge", "Premium Creator Badge", "Unlock a cosmetic creator badge.", "stars", "Creator", 150, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("post_spotlight_24h", "Post Spotlight — 24 hours", "Spotlight one selected post for 24 hours.", "volume_up", "Posts", 150, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.POST, 86400, stackable = true),
        BlinkStoreItem("reel_spotlight_24h", "Reel Spotlight — 24 hours", "Spotlight one selected reel for 24 hours.", "movie_filter", "Reels", 150, BlinkStoreItemType.CONTENT_SPECIFIC, BlinkStoreTarget.REEL, 86400, stackable = true),
        BlinkStoreItem("discovery_boost_7d", "7-Day Discovery Boost", "Increase eligible profile discovery opportunities for seven days.", "travel_explore", "Boosts", 180, BlinkStoreItemType.TIMED, BlinkStoreTarget.PROFILE, 604800, stackable = true),
        BlinkStoreItem("profile_theme_bundle", "Premium Profile Theme Bundle", "Unlock a collection of premium profile themes.", "style", "Profile", 200, BlinkStoreItemType.PERMANENT, BlinkStoreTarget.PROFILE),
        BlinkStoreItem("creator_promo_bundle", "Creator Promotion Bundle", "A bundle of creator spotlights and boost credits.", "campaign", "Creator", 250, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.PROFILE, stackable = true),
        BlinkStoreItem("market_promo_bundle", "Marketplace Promotion Bundle", "A bundle of marketplace highlights and seller spotlight time.", "local_mall", "Marketplace", 300, BlinkStoreItemType.CONSUMABLE, BlinkStoreTarget.MARKETPLACE, stackable = true),
        BlinkStoreItem("blink_vip_10d", "Blink VIP — 10 Days", "Ten days of Blink VIP benefits, rewards, styling, analytics and claimable boosts.", "crown", "VIP", 350, BlinkStoreItemType.PASS, BlinkStoreTarget.PROFILE, 864000, stackable = true)
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
