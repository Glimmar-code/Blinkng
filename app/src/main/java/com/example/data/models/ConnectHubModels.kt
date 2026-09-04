package com.example.data.models

data class RoommateListing(
    val id: String,
    val userId: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val budgetMin: Double? = null,
    val budgetMax: Double? = null,
    val moveInDate: String = "",
    val genderPreference: String = "",
    val roomType: String = ""
)

data class MentorListing(
    val id: String,
    val userId: String,
    val mode: String,
    val subjects: List<String> = emptyList(),
    val headline: String = "",
    val description: String = "",
    val preferredLevel: String = ""
)

data class ReadingMateListing(
    val id: String,
    val userId: String,
    val courses: List<String> = emptyList(),
    val studyStyle: String = "",
    val preferredTimes: List<String> = emptyList(),
    val preferredLocation: String = "",
    val description: String = ""
)

data class HousingAgentListing(
    val id: String,
    val userId: String,
    val businessName: String,
    val serviceAreas: List<String> = emptyList(),
    val bio: String = "",
    val verified: Boolean = false
)

data class HousingRequestListing(
    val id: String,
    val studentId: String,
    val title: String,
    val preferredLocation: String = "",
    val budgetMin: Double? = null,
    val budgetMax: Double? = null,
    val description: String = "",
    val status: String = "open",
    val createdAt: String = ""
)

data class GameChallenge(
    val id: String,
    val challengerId: String,
    val opponentId: String,
    val gameType: String,
    val status: String,
    val challengerScore: Int? = null,
    val opponentScore: Int? = null,
    val winnerId: String? = null,
    val createdAt: String = ""
)

enum class ChallengeGameType(
    val apiName: String,
    val label: String,
    val emoji: String,
    val timedSeconds: Int? = null
) {
    BRAIN_MIX("brain_mix", "Brain Mix", "✨", 10),
    MATH_SPRINT("math_sprint", "Math Sprint", "➗"),
    LOGIC("logic", "Logic Lab", "🧩"),
    MEMORY("memory", "Memory Flash", "🧠"),
    WORD_POWER("word_power", "Word Power", "🔤"),
    GENERAL_KNOWLEDGE("general_knowledge", "Quick Quiz", "🎓");

    companion object {
        fun fromApiName(value: String): ChallengeGameType = when (value.lowercase()) {
            "trivia" -> GENERAL_KNOWLEDGE
            "math" -> MATH_SPRINT
            "speed" -> BRAIN_MIX
            else -> entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) }
                ?: GENERAL_KNOWLEDGE
        }
    }
}

data class IdentityAvailability(
    val usernameAvailable: Boolean,
    val fullNameAvailable: Boolean
) {
    val isAvailable: Boolean get() = usernameAvailable && fullNameAvailable
}

data class GameProfileStats(
    val score: Int = 0,
    val coins: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0
)

data class SmartMatchCandidate(
    val userId: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String = "",
    val university: String = "",
    val faculty: String = "",
    val department: String = "",
    val academicLevel: String = "",
    val relationshipStatus: String = "",
    val onlineNow: Boolean = false,
    val lastSeenAt: String = "",
    val compatibilityScore: Int = 0,
    val commonSkills: List<String> = emptyList(),
    val commonHobbies: List<String> = emptyList()
)

data class ConnectRequestItem(
    val kind: String,
    val requestId: String,
    val direction: String,
    val status: String,
    val listingId: String? = null,
    val otherUserId: String = "",
    val createdAt: String = "",
    val title: String = ""
)

data class ConnectHubSnapshot(
    val roommates: List<RoommateListing> = emptyList(),
    val mentors: List<MentorListing> = emptyList(),
    val readingMates: List<ReadingMateListing> = emptyList(),
    val housingAgents: List<HousingAgentListing> = emptyList(),
    val housingRequests: List<HousingRequestListing> = emptyList(),
    val gameChallenges: List<GameChallenge> = emptyList(),
    val smartMatches: List<SmartMatchCandidate> = emptyList(),
    val requests: List<ConnectRequestItem> = emptyList(),
    val gameStats: GameProfileStats = GameProfileStats()
)
