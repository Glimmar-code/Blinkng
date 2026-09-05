package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ChallengeGameType
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.VerificationBadge
import com.example.data.models.LeaderboardUser
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.*
import kotlinx.coroutines.delay

data class TriviaQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val category: String
)

data class GameLeader(
    val rank: Int,
    val name: String,
    val username: String,
    val avatarUrl: String,
    val score: Int,
    val streak: Int,
    val badge: VerificationBadge
)

@Composable
fun GameSection(
    userAvatar: String,
    leaderboardUsers: List<LeaderboardUser> = emptyList(),
    connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    connectHubActions: ConnectHubActions = ConnectHubActions(),
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    selectedTopTab: Int,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onConnectClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) DarkSurface else LightSurface
    val cardBorder = if (isDark) DarkBorder else LightBorder

    var selectedModeName by rememberSaveable { mutableStateOf(ChallengeGameType.GENERAL_KNOWLEDGE.name) }
    val selectedMode = remember(selectedModeName) {
        ChallengeGameType.entries.firstOrNull { it.name == selectedModeName }
            ?: ChallengeGameType.GENERAL_KNOWLEDGE
    }
    val questions = remember(selectedMode) { questionsForMode(selectedMode) }

    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isAnswerSubmitted by remember { mutableStateOf(false) }
    var roundScore by remember(selectedMode) { mutableIntStateOf(0) }
    var remainingSeconds by remember(selectedMode, currentQuestionIndex) {
        mutableIntStateOf(selectedMode.timedSeconds ?: 0)
    }
    var score by remember(connectHub.gameStats.score) { mutableIntStateOf(connectHub.gameStats.score) }
    var streak by remember(connectHub.gameStats.streak) { mutableIntStateOf(connectHub.gameStats.streak) }
    val coins = connectHub.gameStats.coins
    var focusPromptIndex by rememberSaveable { mutableIntStateOf(0) }
    val focusPrompts = remember {
        listOf(
            "Recall three key ideas from your last lecture without checking your notes.",
            "Solve the next question, then explain your reasoning in one clear sentence.",
            "Choose one weak topic and complete a focused five-minute review.",
            "Teach a concept aloud as if you were helping a first-year student."
        )
    }

    val currentQ = questions[currentQuestionIndex % questions.size]
    val latestActiveChallenge = remember(connectHub.gameChallenges) {
        connectHub.gameChallenges.firstOrNull {
            it.status == "accepted" || it.status == "in_progress"
        }
    }
    val activeChallenge = remember(connectHub.gameChallenges, selectedMode) {
        connectHub.gameChallenges.firstOrNull {
            (it.status == "accepted" || it.status == "in_progress") &&
                ChallengeGameType.fromApiName(it.gameType) == selectedMode
        }
    }

    LaunchedEffect(selectedMode, currentQuestionIndex, isAnswerSubmitted) {
        val timeLimit = selectedMode.timedSeconds
        if (timeLimit != null && !isAnswerSubmitted) {
            remainingSeconds = timeLimit
            while (remainingSeconds > 0 && !isAnswerSubmitted) {
                delay(1_000)
                remainingSeconds--
            }
            if (remainingSeconds <= 0 && !isAnswerSubmitted) {
                isAnswerSubmitted = true
                streak = 0
            }
        }
    }

    LaunchedEffect(latestActiveChallenge?.id) {
        latestActiveChallenge?.let { challenge ->
            selectedModeName = ChallengeGameType.fromApiName(challenge.gameType).name
        }
    }

    LaunchedEffect(selectedMode) {
        currentQuestionIndex = 0
        selectedOptionIndex = null
        isAnswerSubmitted = false
        roundScore = 0
    }

    val leaders = remember(leaderboardUsers) {
        leaderboardUsers
            .sortedBy { it.rank }
            .take(10)
            .map {
                GameLeader(
                    rank = it.rank,
                    name = it.fullName.ifBlank { it.username },
                    username = it.username,
                    avatarUrl = it.avatar,
                    score = it.points,
                    streak = it.streakDays,
                    badge = it.verificationBadge
                )
            }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Header
        item {
            GameHeader(
                userAvatar = userAvatar,
                onMenuClick = onOpenMenu,
                onNotificationClick = onOpenActivity,
                onProfileClick = { onProfileClick("you") }
            )
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }

        // Stats Banner (Score, Streak, Coins)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Score card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Points", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$score", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BlinkPink)
                    }
                }

                // Streak card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Streak", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("🔥 $streak", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    }
                }

                // Coins card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Coins", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("🪙 $coins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BlinkGold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Choose game mode", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChallengeGameType.entries.forEach { mode ->
                        val selected = mode == selectedMode
                        Surface(
                            modifier = Modifier.clickable { selectedModeName = mode.name },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) BlinkPink else cardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) BlinkPink else cardBorder
                            )
                        ) {
                            Row(
                                Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mode.emoji, fontSize = 17.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    mode.label,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                activeChallenge?.let {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BlinkGold.copy(alpha = .12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BlinkGold.copy(alpha = .45f))
                    ) {
                        Text(
                            "⚔️ Active ${selectedMode.label} challenge • finish this 5-question round to submit your score",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(14.dp)) }

        // Premium multi-mode challenge card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = BlinkPink.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = currentQ.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlinkPink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = if (selectedMode.timedSeconds != null && !isAnswerSubmitted) {
                                "⏱ ${remainingSeconds}s"
                            } else {
                                "Q ${((currentQuestionIndex) % questions.size) + 1} / ${questions.size}"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentQ.question,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Options
                    currentQ.options.forEachIndexed { index, option ->
                        val isSelected = selectedOptionIndex == index
                        val isCorrect = isAnswerSubmitted && index == currentQ.correctIndex
                        val isWrong = isAnswerSubmitted && isSelected && index != currentQ.correctIndex

                        val optionBg = when {
                            isCorrect -> BlinkOnlineGreen.copy(alpha = 0.2f)
                            isWrong -> Color(0xFFFF4D4D).copy(alpha = 0.2f)
                            isSelected -> BlinkPink.copy(alpha = 0.15f)
                            else -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)
                        }

                        val optionBorder = when {
                            isCorrect -> BlinkOnlineGreen
                            isWrong -> Color(0xFFFF4D4D)
                            isSelected -> BlinkPink
                            else -> cardBorder
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = optionBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, optionBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isAnswerSubmitted) {
                                    selectedOptionIndex = index
                                    isAnswerSubmitted = true
                                    if (index == currentQ.correctIndex) {
                                        val earned = if (selectedMode.timedSeconds != null) 70 else 50
                                        score += earned
                                        roundScore += earned
                                        streak += 1
                                    } else {
                                        streak = 0
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected || isCorrect) BlinkPink else Color.Transparent,
                                    border = if (!isSelected && !isCorrect) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant) else null,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Text(
                                        text = "${'A' + index}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected || isCorrect) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.wrapContentSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = option,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected || isCorrect) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isCorrect) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Correct",
                                        tint = BlinkOnlineGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isWrong) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Wrong",
                                        tint = Color(0xFFFF4D4D),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isAnswerSubmitted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentQ.explanation,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val finishingRound = (currentQuestionIndex % questions.size) == questions.lastIndex
                                if (finishingRound) {
                                    if (roundScore > 0) {
                                        connectHubActions.recordGameResult(selectedMode.apiName, roundScore)
                                    }
                                    activeChallenge?.let { challenge ->
                                        connectHubActions.submitChallengeScore(challenge.id, roundScore)
                                    }
                                    roundScore = 0
                                }
                                currentQuestionIndex++
                                selectedOptionIndex = null
                                isAnswerSubmitted = false
                            },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("next_trivia_question")
                        ) {
                            Text(if ((currentQuestionIndex % questions.size) == questions.lastIndex) "Finish Round ✦" else "Next Question →", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        // A deterministic study prompt keeps the daily experience useful and age-appropriate.
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Brain Boost",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = BlinkGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Focus practice",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlinkGold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BlinkPurple.copy(alpha = .15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = BlinkPurple,
                                modifier = Modifier.padding(11.dp).size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        AnimatedContent(
                            targetState = focusPromptIndex,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { it / 3 }) togetherWith
                                    (fadeOut() + slideOutVertically { -it / 3 })
                            },
                            label = "brainBoostPrompt"
                        ) { promptIndex ->
                            Text(
                                text = focusPrompts[promptIndex],
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            focusPromptIndex = (focusPromptIndex + 1) % focusPrompts.size
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDark) BlinkCream else BlinkBlack),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("next_brain_boost_button")
                    ) {
                        Text(
                            text = "Show another focus prompt",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isDark) BlinkBlack else BlinkCream
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        // Trivia Leaderboard
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Campus Game Champions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Top scorers across all Blink game modes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }

        if (leaders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No live leaderboard users yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }

        items(leaders.size) { index ->
            val leader = leaders[index]
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank badge
                    Surface(
                        shape = CircleShape,
                        color = when (leader.rank) {
                            1 -> BlinkGold
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> if (isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "${leader.rank}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (leader.rank <= 3) Color.Black else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.wrapContentSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onProfileClick(leader.username) }
                    ) {
                        AsyncImage(
                            model = leader.avatarUrl,
                            contentDescription = leader.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = leader.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedMark(badge = leader.badge)
                        }
                        Text(
                            text = "@${leader.username} • 🔥 ${leader.streak} Streak",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "${leader.score} pts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlinkPink
                    )
                }
            }
        }
    }
}

private fun questionsForMode(mode: ChallengeGameType): List<TriviaQuestion> = when (mode) {
    ChallengeGameType.GENERAL_KNOWLEDGE -> listOf(
        TriviaQuestion("t1", "Which is the oldest university in Nigeria, founded in 1948?", listOf("University of Lagos", "University of Ibadan", "Ahmadu Bello University", "University of Nigeria, Nsukka"), 1, "University of Ibadan was established in 1948.", "Campus History"),
        TriviaQuestion("t2", "What does JAMB stand for?", listOf("Joint Admissions and Matriculation Board", "Junior Academic Management Board", "Joint Association of Matriculated Brethren", "Judicial Academic Monitoring Bureau"), 0, "JAMB is the Joint Admissions and Matriculation Board.", "Academics"),
        TriviaQuestion("t3", "Which Nigerian university is situated along the Lagos Lagoon?", listOf("Covenant University", "LASU", "University of Lagos", "Babcock University"), 2, "UNILAG's Akoka campus borders the Lagos Lagoon.", "Campus Life"),
        TriviaQuestion("t4", "What is the motto of Obafemi Awolowo University?", listOf("In Deed and In Truth", "For Learning and Culture", "Character and Sound Knowledge", "Excellence in Action"), 1, "OAU's motto is commonly translated as For Learning and Culture.", "Tradition"),
        TriviaQuestion("t5", "On a 5.0 scale, which classification commonly starts at 4.50?", listOf("Second Class Upper", "First Class Honours", "Distinction Pass", "Merit"), 1, "4.50–5.00 is commonly First Class on a 5-point scale.", "Academics")
    )
    ChallengeGameType.MATH_SPRINT -> listOf(
        TriviaQuestion("m1", "18 × 7 = ?", listOf("116", "126", "136", "146"), 1, "18 × 7 = 126.", "Math Sprint"),
        TriviaQuestion("m2", "144 ÷ 12 = ?", listOf("10", "11", "12", "14"), 2, "144 ÷ 12 = 12.", "Math Sprint"),
        TriviaQuestion("m3", "15% of 200 = ?", listOf("20", "25", "30", "35"), 2, "0.15 × 200 = 30.", "Percentages"),
        TriviaQuestion("m4", "If x + 9 = 23, x = ?", listOf("12", "13", "14", "15"), 2, "23 − 9 = 14.", "Algebra"),
        TriviaQuestion("m5", "√225 = ?", listOf("12", "13", "14", "15"), 3, "15 × 15 = 225.", "Numbers")
    )
    ChallengeGameType.LOGIC -> listOf(
        TriviaQuestion("l1", "What comes next: 2, 6, 12, 20, 30, ?", listOf("36", "40", "42", "44"), 2, "Differences are +4,+6,+8,+10,+12.", "Sequence"),
        TriviaQuestion("l2", "All Zips are Nors. Some Nors are Veks. Which is guaranteed?", listOf("Some Zips are Veks", "All Nors are Zips", "All Zips are Nors", "No Veks are Zips"), 2, "Only the original statement that all Zips are Nors is guaranteed.", "Deduction"),
        TriviaQuestion("l3", "Odd one out: 16, 25, 36, 45, 49", listOf("16", "25", "45", "49"), 2, "45 is not a perfect square.", "Pattern"),
        TriviaQuestion("l4", "A clock shows 3:00. What is the angle between the hands?", listOf("30°", "60°", "90°", "120°"), 2, "At 3:00 the hands are 90° apart.", "Spatial"),
        TriviaQuestion("l5", "If CAT → DBU by shifting each letter +1, DOG → ?", listOf("EPH", "EOG", "FPH", "DPI"), 0, "D→E, O→P, G→H.", "Code")
    )
    ChallengeGameType.MEMORY -> listOf(
        TriviaQuestion("mry1", "Remember: PURPLE • 7 • STAR. Which number appeared?", listOf("5", "6", "7", "8"), 2, "The sequence contained 7.", "Memory"),
        TriviaQuestion("mry2", "Remember: BOOK • LAMP • TREE. Which item was second?", listOf("Book", "Lamp", "Tree", "Pen"), 1, "Lamp was second.", "Memory"),
        TriviaQuestion("mry3", "Remember: 4 • 9 • 2 • 6. Which came after 9?", listOf("4", "2", "6", "9"), 1, "2 followed 9.", "Memory"),
        TriviaQuestion("mry4", "Remember: RED • BLUE • GOLD. Which color was last?", listOf("Red", "Blue", "Gold", "Green"), 2, "Gold was last.", "Memory"),
        TriviaQuestion("mry5", "Remember: A3 • B8 • C1. What was paired with B?", listOf("1", "3", "8", "9"), 2, "B was paired with 8.", "Memory")
    )
    ChallengeGameType.BRAIN_MIX -> listOf(
        TriviaQuestion("b1", "What comes next: 3, 6, 12, 24, ?", listOf("30", "36", "42", "48"), 3, "Each number doubles, so 24 becomes 48.", "Pattern Sprint"),
        TriviaQuestion("b2", "Which word is closest in meaning to concise?", listOf("Brief", "Noisy", "Ancient", "Hidden"), 0, "Concise means brief and clear.", "Word Power"),
        TriviaQuestion("b3", "If all labs are rooms and this place is a lab, what must be true?", listOf("It is a room", "It is outdoors", "It is empty", "It is a library"), 0, "A lab must be a room under the stated rule.", "Quick Logic"),
        TriviaQuestion("b4", "Remember 8 • BLUE • K. Which color appeared?", listOf("Gold", "Green", "Blue", "Red"), 2, "Blue was the middle item.", "Memory Flash"),
        TriviaQuestion("b5", "27 + 16 = ?", listOf("41", "42", "43", "44"), 2, "27 + 16 = 43.", "Math Sprint")
    )
    ChallengeGameType.WORD_POWER -> listOf(
        TriviaQuestion("w1", "Choose the correctly spelled word.", listOf("Accomodate", "Acommodate", "Accommodate", "Acomodate"), 2, "Accommodate has two c's and two m's.", "Spelling"),
        TriviaQuestion("w2", "What is the opposite of scarce?", listOf("Rare", "Abundant", "Small", "Costly"), 1, "Abundant means available in large quantities.", "Vocabulary"),
        TriviaQuestion("w3", "Which word completes the analogy: Book is to read as song is to ___?", listOf("Listen", "Write", "Draw", "Count"), 0, "A book is read and a song is listened to.", "Analogy"),
        TriviaQuestion("w4", "Which word is a noun?", listOf("Quickly", "Create", "Curious", "Knowledge"), 3, "Knowledge names an idea, so it is a noun.", "Grammar"),
        TriviaQuestion("w5", "Rearrange L I S T E N to form another word.", listOf("Silent", "Tinsel", "Enlist", "All three"), 3, "Silent, tinsel and enlist all use the same letters.", "Anagram")
    )
}

@Composable
private fun GameHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 38.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Menu",
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onProfileClick() }
        ) {
            AsyncImage(
                model = userAvatar,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
    }
}

@Composable
private fun TopNavigationRow(
    selected: Int,
    onHome: () -> Unit,
    onReel: () -> Unit,
    onConnect: () -> Unit,
    onGame: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopTabItem(text = "Home", selected = selected == 0, onClick = onHome)
        Spacer(modifier = Modifier.width(8.dp))
        TopTabItem(text = "Reel", selected = selected == 1, onClick = onReel)
        Spacer(modifier = Modifier.width(8.dp))
        TopTabItem(text = "Connect", selected = selected == 2, onClick = onConnect)
        Spacer(modifier = Modifier.width(8.dp))
        TopTabItem(text = "Game", selected = selected == 3, onClick = onGame)
    }
}

@Composable
private fun TopTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
