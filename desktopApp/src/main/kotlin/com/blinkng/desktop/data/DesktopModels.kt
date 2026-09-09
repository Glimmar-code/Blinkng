package com.blinkng.desktop.data

data class DesktopSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtEpochSeconds: Long,
)

data class DesktopProfile(
    val id: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?,
    val university: String?,
    val faculty: String?,
    val department: String?,
    val bio: String?,
    val isVerified: Boolean,
    val verificationTier: String,
    val followerCount: Int,
    val followingCount: Int,
    val postsCount: Int,
    val coinBalance: Long,
    val isOnline: Boolean,
    val lastSeenAt: String?,
)

data class DesktopFeedPost(
    val id: String,
    val userId: String,
    val authorName: String,
    val authorUsername: String,
    val authorVerified: Boolean,
    val authorVerificationTier: String,
    val authorOnline: Boolean,
    val text: String?,
    val caption: String?,
    val imageUrl: String?,
    val videoUrl: String?,
    val images: List<String>,
    val hashtags: List<String>,
    val likeCount: Int,
    val commentCount: Int,
    val shareCount: Int,
    val viewCount: Int,
    val isReel: Boolean,
    val createdAt: String,
    val isLiked: Boolean,
)

data class DesktopComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorVerified: Boolean,
    val content: String,
    val likesCount: Int,
    val createdAt: String,
)

data class DesktopConversation(
    val id: String,
    val title: String,
    val avatarUrl: String?,
    val isGroup: Boolean,
    val lastMessageAt: String?,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null,
)

data class DesktopMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val mediaUrl: String?,
    val messageType: String,
    val isRead: Boolean,
    val deliveredAt: String?,
    val readAt: String?,
    val createdAt: String,
)

data class DesktopNotification(
    val id: String,
    val type: String,
    val text: String,
    val subText: String?,
    val postId: String?,
    val isRead: Boolean,
    val actorIsVip: Boolean,
    val vipPriority: Boolean,
    val createdAt: String,
)

data class DesktopMarketItem(
    val id: String,
    val title: String,
    val price: Long,
    val currency: String,
    val category: String,
    val condition: String,
    val description: String,
    val imageUrl: String?,
    val sellerId: String?,
    val sellerName: String,
    val sellerUsername: String,
    val sellerVerified: Boolean,
    val university: String,
    val location: String,
    val isFeatured: Boolean,
    val isSold: Boolean,
)

data class DesktopConnectListing(
    val id: String,
    val userId: String,
    val listingType: String,
    val title: String,
    val description: String,
    val university: String?,
    val department: String?,
    val academicLevel: String?,
    val location: String?,
    val tags: List<String>,
    val createdAt: String,
)

data class DesktopStoreItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val price: Int,
    val itemType: String,
    val targetType: String,
    val durationSeconds: Long?,
    val vipOnly: Boolean,
    val boostMultipliers: List<Int>,
)

data class DesktopInventoryItem(
    val id: String,
    val catalogId: String,
    val quantity: Int,
    val status: String,
    val purchasedAt: String,
    val activatedAt: String?,
    val expiresAt: String?,
    val targetType: String?,
    val targetId: String?,
    val boostMultiplier: Int?,
)

data class DesktopLeaderboardEntry(
    val userId: String,
    val name: String,
    val handle: String,
    val university: String?,
    val verificationTier: String,
    val worldScore: Long,
    val worldRank: Int?,
    val campusScore: Long,
    val campusRank: Int?,
)

data class DesktopUserSettings(
    val theme: String,
    val language: String,
    val pushNotificationsEnabled: Boolean,
    val emailNotificationsEnabled: Boolean,
    val dmPrivacy: String,
    val privateAccount: Boolean,
    val showOnlineStatus: Boolean,
    val readReceipts: Boolean,
    val autoplayVideos: Boolean,
    val dataSaver: Boolean,
    val reduceMotion: Boolean,
)

data class DesktopAdminCapability(
    val allowed: Boolean,
    val role: String?,
    val isOwner: Boolean,
)

data class DesktopSearchResults(
    val profiles: List<DesktopProfile>,
    val posts: List<DesktopFeedPost>,
)
