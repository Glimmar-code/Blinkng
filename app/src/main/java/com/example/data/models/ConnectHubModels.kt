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

data class ConnectHubSnapshot(
    val roommates: List<RoommateListing> = emptyList(),
    val mentors: List<MentorListing> = emptyList(),
    val readingMates: List<ReadingMateListing> = emptyList(),
    val housingAgents: List<HousingAgentListing> = emptyList(),
    val gameChallenges: List<GameChallenge> = emptyList(),
    val gameStats: GameProfileStats = GameProfileStats()
)

data class DailySpinReward(
    val label: String,
    val awardedScore: Int = 0,
    val awardedCoins: Int = 0
)
