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
    val facultyTag: String = "SIMME",
    val isVerified: Boolean = true,
    val verificationBadge: VerificationBadge = VerificationBadge.BLUE,
    val timeAgo: String,
    val text: String,
    val images: List<String> = emptyList(),
    var likes: Int,
    var isLiked: Boolean = false,
    var commentsCount: Int,
    var sharesCount: Int,
    var viewsCount: Int = 1450,
    var isBookmarked: Boolean = false,
    val isReel: Boolean = false,
    val videoDuration: String = "0:30",
    val videoUrl: String? = null,
    val tags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val poll: PostPoll? = null
)

data class Story(
    val id: String,
    val username: String,
    val avatar: String,
    val hasUnseen: Boolean = true,
    val isUser: Boolean = false,
    val storyImage: String = "",
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class ActivityItem(
    val id: String,
    val user: String,
    val avatar: String,
    val action: String,
    val time: String,
    val isUnread: Boolean = false,
    val category: NotificationFilter = NotificationFilter.ALL,
    val targetPostId: String? = null,
    val targetMarketId: String? = null,
    val previewText: String? = null,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class MarketItem(
    val id: String,
    val title: String,
    val price: Long, // Price in Nigerian Naira (₦)
    val images: List<String>,
    val sellerUsername: String,
    val sellerAvatar: String,
    val sellerName: String,
    val sellerPhone: String = "+234 812 345 6789",
    val sellerWhatsapp: String = "+2348123456789",
    val sellerIsVerified: Boolean = true,
    val verificationBadge: VerificationBadge = VerificationBadge.BLUE,
    val sellerRating: Double = 4.8,
    val sellerReviewCount: Int = 34,
    val university: String = "University of Lagos",
    val location: String = "Akoka Campus, Lagos",
    val category: String,
    val condition: String = "Like New",
    val description: String,
    val postedTime: String = "2 hours ago",
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
    val streakDays: Int = 12,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class CommentReply(
    val id: Long,
    val user: String,
    val avatar: String,
    val text: String,
    val time: String = "Just now",
    var likes: Int = 0,
    var isLiked: Boolean = false,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class Comment(
    val id: Long,
    val user: String,
    val avatar: String,
    val text: String,
    val time: String,
    var likes: Int,
    var isLiked: Boolean = false,
    val replies: List<CommentReply> = emptyList(),
    val verificationBadge: VerificationBadge = VerificationBadge.NONE
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: String,
    val isFromMe: Boolean,
    val isRead: Boolean = true,
    val isVoiceNote: Boolean = false,
    val voiceDuration: String = "",
    val attachedImageUrl: String? = null
)

data class ChatConversation(
    val id: String,
    val partnerUsername: String,
    val partnerName: String,
    val partnerAvatar: String,
    val isOnline: Boolean,
    val lastMessage: String,
    val lastMessageTime: String,
    val unreadCount: Int = 0,
    val isVerified: Boolean = false,
    val verificationBadge: VerificationBadge = VerificationBadge.NONE,
    val faculty: String = "SIMME",
    val lastSeen: String = "Last seen recently",
    val messages: MutableList<ChatMessage> = mutableListOf()
)
