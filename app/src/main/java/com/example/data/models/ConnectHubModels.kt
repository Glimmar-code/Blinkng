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
    val challengedId: String,
    val gameType: String,
    val status: String,
    val challengerScore: Int? = null,
    val challengedScore: Int? = null,
    val winnerId: String? = null,
    val createdAt: String = ""
)

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

data class DailySpinReward(
    val label: String,
    val awardedScore: Int = 0,
    val awardedCoins: Int = 0
)
