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
    var id: String = "",
    var fullName: String = "",
    var username: String = "",
    var avatarUrl: String = "",
    var coverPhotoUrl: String = "",
    var verificationBadge: VerificationBadge = VerificationBadge.NONE,
    var professionalHeadline: String = "",
    var currentJobTitle: String = "",
    var university: String = "",
    var faculty: String = "",
    var department: String = "",
    var courseOfStudy: String = "",
    var academicLevel: String = "",
    var graduationYear: String = "",
    var bio: String = "",
    var availability: AvailabilityStatus = AvailabilityStatus.NONE,
    var countryOfOrigin: String = "",
    var currentCityState: String = "",
    var email: ContactField = ContactField("", true),
    var phone: ContactField = ContactField("", false),
    var whatsapp: ContactField = ContactField("", false),
    var links: SocialLinks = SocialLinks(
        website = "https://efechukwu.design",
        linkedin = "linkedin.com/in/efechukwu",
        twitter = "x.com/efe_creative",
        instagram = "instagram.com/efe.lens",
        featuredLink = "https://github.com/efe-dev",
        featuredLinkLabel = "Portfolio & GitHub"
    ),
    var coreSkills: MutableList<String> = mutableListOf(),
    var skillEndorsements: MutableList<SkillEndorsement> = mutableListOf(),
    var hobbies: List<String> = listOf(),
    var languages: List<String> = listOf(),
    var favoriteQuote: String = "",
    var followerCount: Int = 0,
    var followingCount: Int = 0,
    var profileViewsThisWeek: Int = 0,
    var dailyStreak: Int = 0,
    var worldRank: Int = 0,
    var campusRank: Int = 0,
    var onlineNow: Boolean = false,
    var verifiedAtMillis: Long = 0L,
    var joinedLabel: String = "",
    var isSellerActive: Boolean = false,
    var sellerStoreName: String = "",
    var badges: List<AchievementBadge> = listOf()
)
