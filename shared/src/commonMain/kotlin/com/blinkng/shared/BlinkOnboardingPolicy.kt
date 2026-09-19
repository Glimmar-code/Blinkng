package com.blinkng.shared

object BlinkOnboardingPolicy {
    const val REQUIRED_FOLLOWS: Int = 5
    const val PINNED_CREATOR_USERNAME: String = "futa_no1_blogger"
    const val TOTAL_STEPS: Int = 4

    val reservedUsernames: Set<String> = setOf(
        "admin",
        "blink",
        "blinkapp",
        "official",
        "support",
        "help",
        "moderator",
        "system",
        "security",
    )

    val genders: List<String> = listOf(
        "Male",
        "Female",
        "Prefer not to say",
    )

    val levels: List<String> = listOf(
        "100 Level",
        "200 Level",
        "300 Level",
        "400 Level",
        "500 Level",
        "600 Level",
        "700 Level",
        "Postgraduate",
        "Graduate",
        "Other",
    )

    val interestGroups: Map<String, List<String>> = linkedMapOf(
        "Campus & Community" to listOf(
            "Campus News", "Student Community", "Events", "Volunteering",
            "Leadership", "Clubs", "Scholarships", "Academic Tips",
        ),
        "Tech & Building" to listOf(
            "Technology", "Programming", "Android", "Web Development",
            "Artificial Intelligence", "Cyber Security", "Data Science",
            "Product Design", "Startups", "Engineering",
        ),
        "Entertainment" to listOf(
            "Afrobeats", "Music", "Movies", "Comedy", "Gaming",
            "Anime", "Photography", "Content Creation", "Memes",
        ),
        "Lifestyle" to listOf(
            "Fashion", "Beauty", "Food", "Fitness", "Travel",
            "Relationships", "Friendship", "Wellness",
        ),
        "Sports" to listOf(
            "Football", "Basketball", "Athletics", "Esports",
            "Formula 1", "Tennis",
        ),
        "Learning & Career" to listOf(
            "Business", "Entrepreneurship", "Finance", "Career",
            "Internships", "Books", "Science", "Mathematics",
            "Statistics", "Research",
        ),
    )

    val allInterests: List<String> = interestGroups.values.flatten().distinct()

    fun isStrongPassword(password: String): Boolean =
        password.length >= 8 &&
            password.any(Char::isLowerCase) &&
            password.any(Char::isUpperCase) &&
            password.any(Char::isDigit) &&
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() }

    fun normalizeUsername(raw: String): String =
        raw.trim()
            .lowercase()
            .removePrefix("@")
            .filter { it.isLetterOrDigit() || it == '_' || it == '.' }
            .take(25)

    fun usernameValidationMessage(raw: String): String? {
        val clean = normalizeUsername(raw)
        return when {
            !clean.matches(Regex("^[a-z0-9][a-z0-9._]{2,24}$")) ->
                "Use 3–25 letters, numbers, dots or underscores."
            clean in reservedUsernames || clean.startsWith("blink_") ->
                "That username is reserved. Try another one."
            else -> null
        }
    }
}
