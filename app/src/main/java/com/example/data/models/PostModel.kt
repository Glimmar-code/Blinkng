package com.example.data.models

enum class NotificationFilter(val label: String, val icon: String) {
    ALL("All", "🔔"),
    COMMENTS("Mentions & Comments", "💬"),
    LIKES("Likes & Saves", "❤️"),
    MARKET("Campus & Market", "🛍️")
}

data class PollOption(
    val id: String,
    val text: String,
    var votes: Int = 0,
    var isVotedByMe: Boolean = false
)

data class PostPoll(
    val question: String,
    val options: List<PollOption>,
    val totalVotes: Int = 0,
    val hasVoted: Boolean = false
)

data class FeedPost(
    val id: String,
    val author: String,
    val authorAvatar: String,
    val facultyTag: String = "",
    val isVerified: Boolean = false,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE,
    val timeAgo: String,
    val text: String,
    val images: List<String> = emptyList(),
    var likes: Int,
    var isLiked: Boolean = false,
    var commentsCount: Int,
    var sharesCount: Int,
    var repostsCount: Int = 0,
    var isRepostedByMe: Boolean = false,
    val repostId: String? = null,
    val repostedById: String? = null,
    val repostedByUsername: String? = null,
    var viewsCount: Int = 0,
    var isBookmarked: Boolean = false,
    val isReel: Boolean = false,
    val videoDuration: String = "0:00",
    val videoUrl: String? = null,
    val tags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val poll: PostPoll? = null,
    val audience: String = "Everyone",
    val category: String = "Campus Life",
    val location: String? = null,
    val linkUrl: String? = null,
    val allowComments: Boolean = true,
    val hideLikes: Boolean = false,
    val isPinned: Boolean = false,
    val isDisappearing: Boolean = false,
    val audioTitle: String? = null,
    val altText: String? = null,
    val isSponsored: Boolean = false,
    val adLabel: String? = null,
    val adCta: String? = null,
    val createdAt: String = "",
    val authorUsername: String = ""
)

data class PostDraft(
    val id: String = "draft_${System.currentTimeMillis()}",
    val text: String = "",
    val faculty: String = "SIMME",
    val imageUri: String? = null,
    val videoUri: String? = null,
    val isReel: Boolean = false,
    val tags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val category: String = "Campus Life",
    val audience: String = "Everyone",
    val location: String? = null,
    val linkUrl: String? = null,
    val allowComments: Boolean = true,
    val hideLikes: Boolean = false,
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val savedAtTimestamp: Long = System.currentTimeMillis(),
    val audioTrack: String? = null
)

data class ScheduledPost(
    val id: String = "sched_${System.currentTimeMillis()}",
    val post: FeedPost,
    val scheduledTimeMillis: Long,
    val scheduledTimeFormatted: String
)

data class Story(
    val id: String,
    val username: String,
    val avatar: String,
    val hasUnseen: Boolean = true,
    val isUser: Boolean = false,
    val storyImage: String = "",
    val caption: String = "",
    val timeAgo: String = "",
    val faculty: String = "",
    val university: String = "",
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class ActivityItem(
    val id: String,
    val user: String,
    val avatar: String,
    val action: String,
    val time: String,
    val rawTimestamp: String = "",
    val isUnread: Boolean = false,
    val category: NotificationFilter = NotificationFilter.ALL,
    val targetPostId: String? = null,
    val targetMarketId: String? = null,
    val targetUsername: String? = null,
    val targetType: String? = null,
    val previewText: String? = null,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class MarketItem(
    val id: String,
    val title: String,
    val price: Long,
    val images: List<String>,
    val sellerUsername: String,
    val sellerAvatar: String,
    val sellerName: String,
    val sellerPhone: String = "",
    val sellerWhatsapp: String = "",
    val sellerIsVerified: Boolean = false,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE,
    val sellerRating: Double = 0.0,
    val sellerReviewCount: Int = 0,
    val university: String = "",
    val location: String = "",
    val category: String,
    val condition: String = "Like New",
    val description: String,
    val postedTime: String = "Recently",
    val isFeatured: Boolean = false,
    val isSold: Boolean = false
)

data class LeaderboardUser(
    val rank: Int,
    val username: String,
    val fullName: String,
    val avatar: String,
    val points: Int,
    val faculty: String,
    val university: String,
    val level: String,
    val streakDays: Int = 0,
    val coins: Int = 0,
    val bestStreak: Int = 0,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class GameActionResult(
    val awardedScore: Int = 0,
    val awardedCoins: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0
)

data class CommentReply(
    val id: String,
    val user: String,
    val avatar: String,
    val text: String,
    val time: String = "Just now",
    var likes: Int = 0,
    var isLiked: Boolean = false,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class Comment(
    val id: String,
    val user: String,
    val avatar: String,
    val text: String,
    val time: String,
    var likes: Int,
    var isLiked: Boolean = false,
    val replies: List<CommentReply> = emptyList(),
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: String = "Just now",
    val isFromMe: Boolean = false,
    val conversationId: String? = null,
    val senderUsername: String = "",
    val receiverId: String = "",
    val receiverUsername: String = "",
    val rawTimestamp: String = "",
    val isRead: Boolean = true,
    val status: MessageStatus = MessageStatus.SENT,
    val isVoiceNote: Boolean = false,
    val voiceDuration: String = "",
    val attachedImageUrl: String? = null,
    val attachedVideoUrl: String? = null
)

data class ChatConversation(
    val id: String,
    val partnerUsername: String,
    val partnerId: String = "",
    val partnerName: String,
    val partnerAvatar: String,
    val isOnline: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val lastMessageRawTime: String = "",
    val unreadCount: Int = 0,
    val isVerified: Boolean = false,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE,
    val faculty: String = "SIMME",
    val lastSeen: String = "Last seen recently",
    val messages: MutableList<ChatMessage> = mutableListOf()
)
