package com.example.data

import com.example.data.models.*

object BlinkDemoData {

    fun initialStories(): List<Story> = listOf(
        Story("s_me", "Your Story", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&h=200&fit=crop", hasUnseen = false, isUser = true),
        Story("s_1", "zara.edit", "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=200&h=200&fit=crop", hasUnseen = true, storyImage = "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&fit=crop"),
        Story("s_2", "unilag_press", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&h=200&fit=crop", hasUnseen = true, storyImage = "https://images.unsplash.com/photo-1523240795612-9a054b0db644?w=800&fit=crop"),
        Story("s_3", "aluta_deals", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&h=200&fit=crop", hasUnseen = true, storyImage = "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800&fit=crop"),
        Story("s_4", "tech_lagos", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop", hasUnseen = false, storyImage = "https://images.unsplash.com/photo-1531482615713-2afd69097998?w=800&fit=crop"),
        Story("s_5", "simme_execs", "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=200&h=200&fit=crop", hasUnseen = false, storyImage = "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=800&fit=crop")
    )

    fun initialPosts(): List<FeedPost> = listOf(
        FeedPost(
            id = "p_1",
            author = "zara.editorial",
            authorAvatar = "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=200&h=200&fit=crop",
            facultyTag = "SIMME",
            isVerified = true,
            timeAgo = "18m ago",
            text = "Campus Fashion Gala 2026 was pure art. Congratulations to all student designers who showcased tonight! ✨🖤 #UNILAG #BlinkCampus #Editorial",
            images = listOf(
                "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&fit=crop",
                "https://images.unsplash.com/photo-1509631179647-0177331693ae?w=800&fit=crop"
            ),
            likes = 1240,
            isLiked = true,
            commentsCount = 89,
            sharesCount = 42,
            viewsCount = 3820,
            isBookmarked = false
        ),
        FeedPost(
            id = "p_2",
            author = "kemi_eng",
            authorAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&h=200&fit=crop",
            facultyTag = "ENGINEERING",
            isVerified = true,
            timeAgo = "1h ago",
            text = "Built a solar inverter prototype in the systems lab today with @efe.design! Faculty defense ready ⚡🔌 Who else is grinding in the faculty library?",
            images = listOf(
                "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=800&fit=crop"
            ),
            likes = 856,
            isLiked = false,
            commentsCount = 64,
            sharesCount = 19,
            viewsCount = 2410,
            isBookmarked = true
        ),
        FeedPost(
            id = "p_3",
            author = "aluta_market_hq",
            authorAvatar = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=200&h=200&fit=crop",
            facultyTag = "SBMS",
            isVerified = true,
            timeAgo = "3h ago",
            text = "🚨 ALUTA FLASH SALE: Verified student sellers have listed over 50+ laptops, phone accessories, and hostel essentials. Tap the Market tab to browse verified listings with direct WhatsApp connect!",
            images = listOf(
                "https://images.unsplash.com/photo-1519389950473-47ba0277781c?w=800&fit=crop"
            ),
            likes = 2100,
            isLiked = true,
            commentsCount = 132,
            sharesCount = 88,
            viewsCount = 5900,
            isBookmarked = false
        ),
        FeedPost(
            id = "p_4",
            author = "chidi_law",
            authorAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&h=200&fit=crop",
            facultyTag = "LAW",
            isVerified = false,
            timeAgo = "5h ago",
            text = "Mock trial competition semifinals tomorrow at the Faculty of Law moot court. Come support the defense team! ⚖️🏛️ #LawDinner #UNILAGLaw",
            images = emptyList(),
            likes = 430,
            isLiked = false,
            commentsCount = 28,
            sharesCount = 12,
            viewsCount = 1180,
            isBookmarked = false
        )
    )

    fun initialReels(): List<FeedPost> = listOf(
        FeedPost(
            id = "r_1",
            author = "zara.editorial",
            authorAvatar = "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=200&h=200&fit=crop",
            facultyTag = "SIMME",
            isVerified = true,
            timeAgo = "1h ago",
            text = "Autumn & Ankara street couture drop tonight ✦ #fashion @efe.design #BlinkReels",
            images = listOf("https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=1000&fit=crop"),
            likes = 12000000,
            isLiked = true,
            commentsCount = 561,
            sharesCount = 24000,
            viewsCount = 450000,
            isBookmarked = true,
            isReel = true,
            videoDuration = "0:45"
        ),
        FeedPost(
            id = "r_2",
            author = "campus_vibes_ng",
            authorAvatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=200&h=200&fit=crop",
            facultyTag = "ARTS",
            isVerified = true,
            timeAgo = "4h ago",
            text = "Freshers welcome concert energy was unmatched! 🔥🎵 @burnaboy @wizkid sounds in Lagos",
            images = listOf("https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1000&fit=crop"),
            likes = 890000,
            isLiked = false,
            commentsCount = 412,
            sharesCount = 15300,
            viewsCount = 195000,
            isBookmarked = false,
            isReel = true,
            videoDuration = "0:30"
        ),
        FeedPost(
            id = "r_3",
            author = "tech_lagos",
            authorAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop",
            facultyTag = "ENGINEERING",
            isVerified = true,
            timeAgo = "6h ago",
            text = "How we automated our hostel electricity monitor in 48 hours using Arduino & Blink API 💻⚡",
            images = listOf("https://images.unsplash.com/photo-1531482615713-2afd69097998?w=1000&fit=crop"),
            likes = 340000,
            isLiked = true,
            commentsCount = 189,
            sharesCount = 8900,
            viewsCount = 82000,
            isBookmarked = true,
            isReel = true,
            videoDuration = "1:00"
        )
    )

    fun initialMarketItems(): List<MarketItem> = listOf(
        MarketItem(
            id = "m_1",
            title = "MacBook Pro M1 (16GB / 512GB SSD) - Space Gray",
            price = 680000,
            images = listOf(
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800&fit=crop",
                "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=800&fit=crop"
            ),
            sellerUsername = "efe.design",
            sellerAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&h=200&fit=crop",
            sellerName = "Efe Chukwu",
            sellerPhone = "+234 809 123 4567",
            sellerWhatsapp = "+2348091234567",
            sellerIsVerified = true,
            sellerRating = 4.9,
            sellerReviewCount = 42,
            university = "University of Lagos (UNILAG)",
            location = "New Hall Hostel, UNILAG",
            category = "Laptops & Computers",
            condition = "Pre-owned (Mint 9/10)",
            description = "Super clean MacBook Pro M1 13-inch. Battery health at 91%, comes with original 67W fast charger and protective clear shell. Ideal for coding, UI/UX design and video rendering.",
            postedTime = "1 hour ago",
            isFeatured = true
        ),
        MarketItem(
            id = "m_2",
            title = "Sony WH-1000XM4 Noise Canceling Headphones",
            price = 195000,
            images = listOf(
                "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&fit=crop"
            ),
            sellerUsername = "zara.editorial",
            sellerAvatar = "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=200&h=200&fit=crop",
            sellerName = "Zara Bello",
            sellerPhone = "+234 814 555 0192",
            sellerWhatsapp = "+2348145550192",
            sellerIsVerified = true,
            sellerRating = 5.0,
            sellerReviewCount = 18,
            university = "University of Lagos (UNILAG)",
            location = "Moremi Hall, UNILAG",
            category = "Audio & Headphones",
            condition = "Brand New / Sealed",
            description = "Industry leading noise cancellation for focused library study sessions. Long battery life (30hrs), touch controls, premium carrying case included.",
            postedTime = "3 hours ago",
            isFeatured = true
        ),
        MarketItem(
            id = "m_3",
            title = "Anker 20,000mAh 22.5W Fast Charge Power Bank",
            price = 32000,
            images = listOf(
                "https://images.unsplash.com/photo-1609592424368-dc81c5be336a?w=800&fit=crop"
            ),
            sellerUsername = "campus_tech_deals",
            sellerAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop",
            sellerName = "Campus Tech Hub",
            sellerPhone = "+234 802 333 4444",
            sellerWhatsapp = "+2348023334444",
            sellerIsVerified = true,
            sellerRating = 4.8,
            sellerReviewCount = 89,
            university = "University of Lagos (UNILAG)",
            location = "Faculty of Science Quad, UNILAG",
            category = "Power Banks & Chargers",
            condition = "Brand New",
            description = "Essential power solution for campus light outages. Charges iPhone or Samsung up to 4 full times. Dual USB-A & USB-C Power Delivery.",
            postedTime = "5 hours ago",
            isFeatured = false
        ),
        MarketItem(
            id = "m_4",
            title = "Nike Air Jordan 1 Retro High - Size 43/44",
            price = 45000,
            images = listOf(
                "https://images.unsplash.com/photo-1552346154-21d32810aba3?w=800&fit=crop"
            ),
            sellerUsername = "sneaker_plug_lagos",
            sellerAvatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=200&h=200&fit=crop",
            sellerName = "Tunde Styles",
            sellerPhone = "+234 816 777 8899",
            sellerWhatsapp = "+2348167778899",
            sellerIsVerified = false,
            sellerRating = 4.6,
            sellerReviewCount = 14,
            university = "Yaba College of Technology",
            location = "Yaba, Lagos",
            category = "Shoes & Footwear",
            condition = "Brand New in Box",
            description = "Clean kicks for campus flex. Comes in full original box with extra black and red laces. Instant pickup at UNILAG gate or Yaba tech.",
            postedTime = "6 hours ago",
            isFeatured = false
        )
    )

    fun initialLeaderboard(): List<LeaderboardUser> = listOf(
        LeaderboardUser(1, "zara.editorial", "Zara Bello", "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=200&h=200&fit=crop", 14820, "SIMME", "University of Lagos", "400L", streakDays = 28),
        LeaderboardUser(2, "efe.design", "Efe Chukwu", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&h=200&fit=crop", 12940, "ENGINEERING", "University of Lagos", "400L", streakDays = 20),
        LeaderboardUser(3, "kemi_eng", "Kemi Adeleke", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&h=200&fit=crop", 11200, "ENGINEERING", "University of Lagos", "300L", streakDays = 15),
        LeaderboardUser(4, "chidi_law", "Chidi Okafor", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&h=200&fit=crop", 9850, "LAW", "University of Lagos", "500L", streakDays = 12),
        LeaderboardUser(5, "luna.style", "Luna Williams", "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200&h=200&fit=crop", 8740, "ARTS", "University of Ibadan", "200L", streakDays = 10),
        LeaderboardUser(6, "tunde_sci", "Tunde Balogun", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop", 7620, "SCIENCE", "Obafemi Awolowo University", "400L", streakDays = 9),
        LeaderboardUser(7, "amara_med", "Amara Nwosu", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=200&h=200&fit=crop", 6930, "MEDICINE", "University of Nigeria, Nsukka", "500L", streakDays = 7)
    )

    fun initialConversations(): List<ChatConversation> = listOf(
        ChatConversation(
            id = "c_1",
            partnerUsername = "zara.editorial",
            partnerName = "Zara Bello",
            partnerAvatar = "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=200&h=200&fit=crop",
            isOnline = true,
            lastMessage = "Are you bringing the prototype to the demo?",
            lastMessageTime = "12:30 PM",
            unreadCount = 2,
            isVerified = true,
            faculty = "SIMME",
            messages = mutableListOf(
                ChatMessage("m_1", "zara.editorial", "Hey Efe! Saw your design on Blink", "12:15 PM", false),
                ChatMessage("m_2", "user_me", "Thanks Zara! Really appreciate it 🙏", "12:18 PM", true),
                ChatMessage("m_3", "zara.editorial", "Are you bringing the prototype to the demo?", "12:30 PM", false)
            )
        ),
        ChatConversation(
            id = "c_2",
            partnerUsername = "kemi_eng",
            partnerName = "Kemi Adeleke",
            partnerAvatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&h=200&fit=crop",
            isOnline = false,
            lastMessage = "Sent the circuit diagram files on drive",
            lastMessageTime = "Yesterday",
            unreadCount = 0,
            isVerified = true,
            faculty = "ENGINEERING",
            lastSeen = "2 hours ago",
            messages = mutableListOf(
                ChatMessage("m_10", "kemi_eng", "Sent the circuit diagram files on drive", "Yesterday", false)
            )
        ),
        ChatConversation(
            id = "c_3",
            partnerUsername = "campus_tech_deals",
            partnerName = "Campus Tech Hub",
            partnerAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop",
            isOnline = true,
            lastMessage = "Yes, the 20,000mAh Anker is available for pickup at Science Quad!",
            lastMessageTime = "Oct 14",
            unreadCount = 0,
            isVerified = true,
            faculty = "SCIENCE",
            messages = mutableListOf(
                ChatMessage("m_20", "user_me", "Hi, is the Anker power bank still available?", "Oct 14", true),
                ChatMessage("m_21", "campus_tech_deals", "Yes, the 20,000mAh Anker is available for pickup at Science Quad!", "Oct 14", false)
            )
        )
    )

    fun initialActivities(): List<ActivityItem> = listOf(
        ActivityItem(
            id = "a_1",
            user = "zara.editorial",
            avatar = "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=200&h=200&fit=crop",
            action = "liked your post in Engineering",
            time = "2m ago",
            isUnread = true,
            category = NotificationFilter.LIKES,
            targetPostId = "p_2",
            previewText = "Built a solar inverter prototype in the systems lab today with @efe.design! ⚡"
        ),
        ActivityItem(
            id = "a_2",
            user = "luna.style",
            avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200&h=200&fit=crop",
            action = "commented: 'Need this shoot BTS!' on your post",
            time = "15m ago",
            isUnread = true,
            category = NotificationFilter.COMMENTS,
            targetPostId = "p_1",
            previewText = "Campus Fashion Gala 2026 was pure art. Congratulations to all student designers!"
        ),
        ActivityItem(
            id = "a_3",
            user = "kemi_eng",
            avatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&h=200&fit=crop",
            action = "mentioned you in a comment: '@efe.design check this out!'",
            time = "45m ago",
            isUnread = true,
            category = NotificationFilter.COMMENTS,
            targetPostId = "p_2",
            previewText = "Built a solar inverter prototype in the systems lab today!"
        ),
        ActivityItem(
            id = "a_4",
            user = "aluta_market_hq",
            avatar = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=200&h=200&fit=crop",
            action = "featured your MacBook Pro listing on Aluta Market",
            time = "3h ago",
            isUnread = false,
            category = NotificationFilter.MARKET,
            targetMarketId = "m_1",
            previewText = "MacBook Pro M1 (16GB / 512GB SSD) - Space Gray"
        ),
        ActivityItem(
            id = "a_5",
            user = "chidi_law",
            avatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&h=200&fit=crop",
            action = "saved your post to their bookmarks",
            time = "5h ago",
            isUnread = false,
            category = NotificationFilter.LIKES,
            targetPostId = "p_1",
            previewText = "Campus Fashion Gala 2026 was pure art."
        ),
        ActivityItem(
            id = "a_6",
            user = "campus_tech_deals",
            avatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop",
            action = "replied to your market inquiry on WhatsApp & Blink",
            time = "8h ago",
            isUnread = false,
            category = NotificationFilter.MARKET,
            targetMarketId = "m_3",
            previewText = "Anker 20,000mAh 22.5W Fast Charge Power Bank"
        )
    )

    fun initialComments(): List<Comment> = listOf(
        Comment(
            id = 1L,
            user = "marco_v",
            avatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&h=200&fit=crop",
            text = "This is fire 🔥🔥 @sophia_kim you have to see this design!",
            time = "10m ago",
            likes = 84,
            isLiked = false,
            replies = listOf(
                CommentReply(11L, "sophia_kim", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=200&h=200&fit=crop", "I'm totally obsessed 😍 clean execution!", "5m ago", 12, true)
            )
        ),
        Comment(
            id = 2L,
            user = "luna.style",
            avatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&h=200&fit=crop",
            text = "The typography and lighting on this is unreal ✨ #editorial",
            time = "32m ago",
            likes = 46,
            isLiked = true
        ),
        Comment(
            id = 3L,
            user = "kai.lens",
            avatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop",
            text = "Need the camera and lighting setup breakdown please 🙏",
            time = "1h ago",
            likes = 12,
            isLiked = false
        )
    )
}
