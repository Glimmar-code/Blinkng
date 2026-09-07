package com.example.data.models

data class ServerGameQuestion(
    val id: String,
    val gameType: String,
    val category: String,
    val prompt: String,
    val options: List<String>,
    val difficulty: Int,
    val timeLimitSeconds: Int?,
    val stimulus: String? = null,
    val saved: Boolean = false
)

data class ServerGameRound(
    val id: String,
    val gameType: String,
    val questions: List<ServerGameQuestion>,
    val answeredQuestionIds: Set<String> = emptySet(),
    val score: Int = 0,
    val coinsEarned: Int = 0,
    val correctCount: Int = 0,
    val isDaily: Boolean = false,
    val resumed: Boolean = false
)

data class ServerGameAnswerResult(
    val correct: Boolean,
    val correctIndex: Int,
    val explanation: String,
    val awardedScore: Int,
    val awardedCoins: Int,
    val streak: Int,
    val bestStreak: Int,
    val totalScore: Int,
    val totalCoins: Int,
    val roundScore: Int,
    val roundCoins: Int,
    val correctCount: Int,
    val completed: Boolean,
    val duplicate: Boolean = false
)

data class GameDashboard(
    val score: Int = 0,
    val coins: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val worldRank: Int = 0,
    val todayAnswers: Int = 0,
    val todayCorrect: Int = 0,
    val dailyGoal: Int = 15
)

data class GameHistoryItem(
    val id: String,
    val gameType: String,
    val score: Int,
    val coinsEarned: Int,
    val correctCount: Int,
    val questionCount: Int,
    val completedAt: String
)

data class GameLeaderboardEntry(
    val rank: Int,
    val userId: String,
    val name: String,
    val username: String,
    val avatarUrl: String,
    val university: String,
    val faculty: String,
    val department: String,
    val score: Int,
    val streak: Int,
    val verificationBadge: VerificationBadge,
    val isMe: Boolean = false
)
