package com.example.data.models

enum class VerificationBadge {
    NONE, BLUE, GOLD
}

enum class AvailabilityStatus(val label: String) {
    NONE("None"),
    OPEN_FOR_OPPORTUNITIES("Open for opportunities"),
    AVAILABLE_FOR_FREELANCE("Available for freelance"),
    STUDY_BUDDY("Looking for study buddy"),
    BUSY("Busy / Exams")
}

data class ContactField(
    var value: String,
    var isPublic: Boolean = true
)

data class SocialLinks(
    var website: String = "",
    var linkedin: String = "",
    var twitter: String = "",
    var instagram: String = "",
    var featuredLink: String = "",
    var featuredLinkLabel: String = ""
)

data class SkillEndorsement(
    val skill: String,
    var endorsements: Int = 0,
    var endorsedByMe: Boolean = false
)

data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String = "trophy"
)

data class UserProfile(
    var id: String = "user_me",
    var fullName: String = "Efe Chukwu",
    var username: String = "efe.design",
    var avatarUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop",
    var coverPhotoUrl: String = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1000&h=400&fit=crop",
    var verificationBadge: VerificationBadge = VerificationBadge.BLUE,
    var professionalHeadline: String = "Product Designer • Creative Technologist",
    var currentJobTitle: String = "UI/UX Lead at CampusDev",
    var university: String = "University of Lagos (UNILAG)",
    var faculty: String = "Engineering",
    var department: String = "Systems Engineering",
    var courseOfStudy: String = "B.Sc. Systems Engineering",
    var academicLevel: String = "400 Level",
    var graduationYear: String = "Class of 2027",
    var bio: String = "Crafting digital experiences, campus entrepreneurship, building for the next billion users 🚀✨",
    var availability: AvailabilityStatus = AvailabilityStatus.AVAILABLE_FOR_FREELANCE,
    var countryOfOrigin: String = "🇳🇬 Nigeria",
    var currentCityState: String = "Lagos, Nigeria",
    var email: ContactField = ContactField("efe.chukwu@student.unilag.edu.ng", true),
    var phone: ContactField = ContactField("+234 809 123 4567", true),
    var whatsapp: ContactField = ContactField("+234 809 123 4567", true),
    var links: SocialLinks = SocialLinks(
        website = "https://efechukwu.design",
        linkedin = "linkedin.com/in/efechukwu",
        twitter = "x.com/efe_creative",
        instagram = "instagram.com/efe.lens",
        featuredLink = "https://github.com/efe-dev",
        featuredLinkLabel = "Portfolio & GitHub"
    ),
    var coreSkills: MutableList<String> = mutableListOf("Product Design", "Figma", "Jetpack Compose", "Kotlin", "Prototyping", "Aluta Commerce"),
    var skillEndorsements: MutableList<SkillEndorsement> = mutableListOf(
        SkillEndorsement("Product Design", 48, false),
        SkillEndorsement("Figma", 62, true),
        SkillEndorsement("Jetpack Compose", 35, false),
        SkillEndorsement("Kotlin", 29, false)
    ),
    var hobbies: List<String> = listOf("Photography", "Tech Meetups", "Gaming", "Afrobeats", "Football"),
    var languages: List<String> = listOf("English", "Yoruba", "Pidgin"),
    var favoriteQuote: String = "The best way to predict the future is to build it.",
    var followerCount: Int = 2450,
    var followingCount: Int = 380,
    var profileViewsThisWeek: Int = 312,
    var onlineNow: Boolean = true,
    var verifiedAtMillis: Long = 0L,
    var joinedLabel: String = "Joined Blink in October 2024",
    var isSellerActive: Boolean = true,
    var sellerStoreName: String = "Efe Tech Hub & Gadgets",
    var badges: List<AchievementBadge> = listOf(
        AchievementBadge("top_creator", "Top Creator", "Top 1% engagement in UNILAG"),
        AchievementBadge("verified_seller", "Verified Merchant", "50+ 5-star sales on Aluta Market"),
        AchievementBadge("streak_master", "20-Day Streak", "Consistent campus contributor")
    )
)
