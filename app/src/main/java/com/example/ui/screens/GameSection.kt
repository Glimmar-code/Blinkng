package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.ChallengeGameType
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.GameDashboard
import com.example.data.models.GameHistoryItem
import com.example.data.models.GameLeaderboardEntry
import com.example.data.models.LeaderboardUser
import com.example.data.models.ServerGameAnswerResult
import com.example.data.models.ServerGameQuestion
import com.example.data.models.ServerGameRound
import com.example.data.repository.GameRepository
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class GameHubPane(val label: String) {
    PLAY("Play"), HISTORY("History"), LEADERBOARD("Ranks")
}

private enum class GameRankScope(val api: String, val label: String) {
    GLOBAL("global", "Global"),
    UNIVERSITY("university", "University"),
    FACULTY("faculty", "Faculty"),
    DEPARTMENT("department", "Department"),
    FRIENDS("friends", "Friends")
}

private enum class GameRankPeriod(val api: String, val label: String) {
    DAILY("daily", "Today"),
    WEEKLY("weekly", "Week"),
    MONTHLY("monthly", "Month"),
    ALL_TIME("all_time", "All time")
}

private data class RoundAnswerRecord(
    val question: ServerGameQuestion,
    val selectedIndex: Int,
    val result: ServerGameAnswerResult
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
    val repository = remember { GameRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedModeName by rememberSaveable { mutableStateOf(ChallengeGameType.GENERAL_KNOWLEDGE.name) }
    val selectedMode = remember(selectedModeName) {
        ChallengeGameType.entries.firstOrNull { it.name == selectedModeName }
            ?: ChallengeGameType.GENERAL_KNOWLEDGE
    }
    var paneName by rememberSaveable { mutableStateOf(GameHubPane.PLAY.name) }
    val pane = remember(paneName) { GameHubPane.entries.firstOrNull { it.name == paneName } ?: GameHubPane.PLAY }

    var dashboard by remember { mutableStateOf<GameDashboard?>(null) }
    var history by remember { mutableStateOf<List<GameHistoryItem>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<GameLeaderboardEntry>>(emptyList()) }
    var rankScopeName by rememberSaveable { mutableStateOf(GameRankScope.GLOBAL.name) }
    var rankPeriodName by rememberSaveable { mutableStateOf(GameRankPeriod.ALL_TIME.name) }
    val rankScope = remember(rankScopeName) { GameRankScope.valueOf(rankScopeName) }
    val rankPeriod = remember(rankPeriodName) { GameRankPeriod.valueOf(rankPeriodName) }

    var round by remember { mutableStateOf<ServerGameRound?>(null) }
    var currentQuestionIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var answerResult by remember { mutableStateOf<ServerGameAnswerResult?>(null) }
    val answerRecords = remember { mutableStateListOf<RoundAnswerRecord>() }
    var isLoadingRound by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var questionStartedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var memoryPreviewVisible by remember { mutableStateOf(false) }
    var showRoundSummary by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Incorrect or unclear") }
    var reportDetails by remember { mutableStateOf("") }

    val latestActiveChallenge = remember(connectHub.gameChallenges) {
        connectHub.gameChallenges.firstOrNull { it.status == "accepted" || it.status == "in_progress" }
    }
    val activeChallenge = remember(connectHub.gameChallenges, selectedMode) {
        connectHub.gameChallenges.firstOrNull {
            (it.status == "accepted" || it.status == "in_progress") &&
                ChallengeGameType.fromApiName(it.gameType) == selectedMode
        }
    }

    fun refreshGameData() {
        scope.launch {
            repository.fetchDashboard().onSuccess { dashboard = it }
            repository.fetchHistory().onSuccess { history = it }
        }
    }

    fun refreshLeaderboard() {
        scope.launch {
            repository.fetchLeaderboard(rankPeriod.api, rankScope.api).onSuccess { leaderboard = it }
        }
    }

    fun startRound(daily: Boolean = false) {
        if (isLoadingRound) return
        isLoadingRound = true
        errorMessage = null
        answerResult = null
        selectedOptionIndex = null
        showRoundSummary = false
        answerRecords.clear()
        scope.launch {
            repository.startRound(
                gameType = selectedMode.apiName,
                challengeId = activeChallenge?.id,
                daily = daily
            ).fold(
                onSuccess = { loaded ->
                    round = loaded
                    val firstOpen = loaded.questions.indexOfFirst { it.id !in loaded.answeredQuestionIds }
                    currentQuestionIndex = if (firstOpen >= 0) firstOpen else 0
                    if (loaded.answeredQuestionIds.size >= loaded.questions.size && loaded.questions.isNotEmpty()) {
                        showRoundSummary = true
                    }
                    refreshGameData()
                },
                onFailure = { errorMessage = it.message ?: "Couldn't start the game round." }
            )
            isLoadingRound = false
        }
    }

    val currentQuestion = round?.questions?.getOrNull(currentQuestionIndex)

    fun submitAnswer(index: Int) {
        val activeRound = round ?: return
        val question = currentQuestion ?: return
        if (isSubmitting || answerResult != null) return
        isSubmitting = true
        errorMessage = null
        val responseMs = (System.currentTimeMillis() - questionStartedAtMs).toInt().coerceIn(0, 120_000)
        scope.launch {
            repository.submitAnswer(activeRound.id, question.id, index, responseMs).fold(
                onSuccess = { result ->
                    selectedOptionIndex = index.takeIf { it >= 0 }
                    answerResult = result
                    if (answerRecords.none { it.question.id == question.id }) {
                        answerRecords += RoundAnswerRecord(question, index, result)
                    }
                    round = activeRound.copy(
                        answeredQuestionIds = activeRound.answeredQuestionIds + question.id,
                        score = result.roundScore,
                        coinsEarned = result.roundCoins,
                        correctCount = result.correctCount
                    )
                    dashboard = (dashboard ?: GameDashboard()).copy(
                        score = result.totalScore,
                        coins = result.totalCoins,
                        streak = result.streak,
                        bestStreak = result.bestStreak,
                        todayAnswers = (dashboard?.todayAnswers ?: 0) + if (result.duplicate) 0 else 1,
                        todayCorrect = (dashboard?.todayCorrect ?: 0) + if (result.correct && !result.duplicate) 1 else 0
                    )
                    if (result.completed) {
                        activeChallenge?.let { challenge ->
                            connectHubActions.submitChallengeScore(challenge.id, result.roundScore)
                        }
                        refreshGameData()
                        refreshLeaderboard()
                    }
                },
                onFailure = { errorMessage = it.message ?: "Couldn't submit this answer." }
            )
            isSubmitting = false
        }
    }

    fun nextQuestion() {
        val activeRound = round ?: return
        val result = answerResult ?: return
        if (result.completed) {
            showRoundSummary = true
            answerResult = null
            return
        }
        val next = activeRound.questions.indices.firstOrNull { index ->
            index > currentQuestionIndex && activeRound.questions[index].id !in activeRound.answeredQuestionIds
        } ?: activeRound.questions.indices.firstOrNull { index ->
            activeRound.questions[index].id !in activeRound.answeredQuestionIds
        }
        if (next == null) {
            showRoundSummary = true
            return
        }
        currentQuestionIndex = next
        selectedOptionIndex = null
        answerResult = null
    }

    LaunchedEffect(Unit) {
        repository.fetchDashboard().onSuccess { dashboard = it }
        repository.fetchHistory().onSuccess { history = it }
    }

    LaunchedEffect(rankScope, rankPeriod) {
        repository.fetchLeaderboard(rankPeriod.api, rankScope.api).onSuccess { leaderboard = it }
    }

    LaunchedEffect(latestActiveChallenge?.id) {
        latestActiveChallenge?.let {
            selectedModeName = ChallengeGameType.fromApiName(it.gameType).name
        }
    }

    LaunchedEffect(currentQuestion?.id) {
        val question = currentQuestion ?: return@LaunchedEffect
        selectedOptionIndex = null
        answerResult = null
        memoryPreviewVisible = !question.stimulus.isNullOrBlank()
        if (memoryPreviewVisible) {
            delay(3_000)
            memoryPreviewVisible = false
        }
        questionStartedAtMs = System.currentTimeMillis()
        remainingSeconds = question.timeLimitSeconds ?: 0
    }

    LaunchedEffect(currentQuestion?.id, memoryPreviewVisible, answerResult, isSubmitting) {
        val question = currentQuestion ?: return@LaunchedEffect
        val timeLimit = question.timeLimitSeconds ?: return@LaunchedEffect
        if (memoryPreviewVisible || answerResult != null || isSubmitting) return@LaunchedEffect
        if (remainingSeconds <= 0) remainingSeconds = timeLimit
        while (remainingSeconds > 0 && answerResult == null && !isSubmitting) {
            delay(1_000)
            remainingSeconds--
        }
        if (remainingSeconds <= 0 && answerResult == null && !isSubmitting) {
            submitAnswer(-1)
        }
    }

    if (showReportDialog && currentQuestion != null) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report question") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tell Blink why this question needs review.", fontSize = 13.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Incorrect or unclear", "Outdated", "Duplicate", "Other").forEach { reason ->
                            FilterChip(
                                selected = reportReason == reason,
                                onClick = { reportReason = reason },
                                label = { Text(reason) }
                            )
                        }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it.take(500) },
                        label = { Text("Optional details") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val questionId = currentQuestion.id
                    scope.launch {
                        repository.reportQuestion(questionId, reportReason, reportDetails)
                            .onFailure { errorMessage = it.message }
                        showReportDialog = false
                        reportDetails = ""
                    }
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            GameHeader(
                userAvatar = userAvatar,
                onMenuClick = onOpenMenu,
                onNotificationClick = onOpenActivity,
                onProfileClick = { onProfileClick("you") }
            )
        }

        item {
            TopNavigationRow(
                selected = selectedTopTab,
                onHome = onHomeClick,
                onReel = onReelClick,
                onConnect = onConnectClick,
                onGame = onGameClick
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            GameStatsPanel(
                dashboard = dashboard ?: GameDashboard(
                    score = connectHub.gameStats.score,
                    coins = connectHub.gameStats.coins,
                    streak = connectHub.gameStats.streak,
                    bestStreak = connectHub.gameStats.bestStreak
                )
            )
        }

        item { Spacer(Modifier.height(14.dp)) }

        item {
            GamePaneTabs(
                selected = pane,
                onSelected = { paneName = it.name }
            )
        }

        item { Spacer(Modifier.height(14.dp)) }

        when (pane) {
            GameHubPane.PLAY -> {
                item {
                    DailyChallengeCard(
                        enabled = !isLoadingRound && round == null,
                        onPlay = { startRound(daily = true) }
                    )
                }

                item { Spacer(Modifier.height(14.dp)) }

                item {
                    ModeSelector(
                        selectedMode = selectedMode,
                        onModeSelected = { mode ->
                            if (round == null || showRoundSummary) {
                                selectedModeName = mode.name
                                showRoundSummary = false
                                round = null
                            }
                        }
                    )
                }

                if (activeChallenge != null) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        ActiveChallengeCard(selectedMode.label)
                    }
                }

                if (errorMessage != null) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        ErrorCard(errorMessage.orEmpty(), onRetry = {
                            errorMessage = null
                            if (round == null) startRound() else refreshGameData()
                        })
                    }
                }

                if (isLoadingRound) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                } else if (showRoundSummary && round != null) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        RoundSummaryCard(
                            round = round!!,
                            records = answerRecords,
                            onPlayAgain = {
                                round = null
                                showRoundSummary = false
                                answerRecords.clear()
                                startRound()
                            },
                            onTryAnother = {
                                round = null
                                showRoundSummary = false
                                answerRecords.clear()
                            },
                            onChallengeFriends = onConnectClick,
                            onShare = {
                                val r = round ?: return@RoundSummaryCard
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "I scored ${r.score} points in ${selectedMode.label} on Blink — ${r.correctCount}/${r.questions.size} correct."
                                    )
                                }
                                context.startActivity(Intent.createChooser(share, "Share Blink game result"))
                            }
                        )
                    }
                } else if (round != null && currentQuestion != null) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        QuestionCard(
                            question = currentQuestion,
                            questionNumber = currentQuestionIndex + 1,
                            totalQuestions = round!!.questions.size,
                            remainingSeconds = remainingSeconds,
                            memoryPreviewVisible = memoryPreviewVisible,
                            selectedIndex = selectedOptionIndex,
                            result = answerResult,
                            isSubmitting = isSubmitting,
                            onAnswer = ::submitAnswer,
                            onNotSure = { submitAnswer(-1) },
                            onNext = ::nextQuestion,
                            onToggleSaved = {
                                val q = currentQuestion
                                scope.launch {
                                    repository.toggleSavedQuestion(q.id).onSuccess { saved ->
                                        round = round?.copy(
                                            questions = round!!.questions.map {
                                                if (it.id == q.id) it.copy(saved = saved) else it
                                            }
                                        )
                                    }.onFailure { errorMessage = it.message }
                                }
                            },
                            onReport = { showReportDialog = true }
                        )
                    }
                } else {
                    item {
                        Spacer(Modifier.height(12.dp))
                        GameModeHero(
                            mode = selectedMode,
                            hasChallenge = activeChallenge != null,
                            onPlay = { startRound() }
                        )
                    }
                }

                item { Spacer(Modifier.height(18.dp)) }
                item { AchievementStrip(dashboard ?: GameDashboard()) }
            }

            GameHubPane.HISTORY -> {
                if (history.isEmpty()) {
                    item { EmptyGameState("No completed rounds yet", "Finish a round and it will appear here.") }
                } else {
                    items(history, key = { it.id }) { item -> HistoryCard(item) }
                }
            }

            GameHubPane.LEADERBOARD -> {
                item {
                    LeaderboardFilters(
                        scope = rankScope,
                        period = rankPeriod,
                        onScope = { rankScopeName = it.name },
                        onPeriod = { rankPeriodName = it.name }
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }

                val liveEntries = if (leaderboard.isNotEmpty()) leaderboard else leaderboardUsers.take(10).map {
                    GameLeaderboardEntry(
                        rank = it.rank,
                        userId = "",
                        name = it.fullName.ifBlank { it.username },
                        username = it.username,
                        avatarUrl = it.avatar,
                        university = "",
                        faculty = "",
                        department = "",
                        score = it.points,
                        streak = it.streakDays,
                        verificationBadge = it.verificationBadge
                    )
                }

                if (liveEntries.isEmpty()) {
                    item { EmptyGameState("No ranked players yet", "Complete a game round to enter the rankings.") }
                } else {
                    if (liveEntries.size >= 3) {
                        item { PodiumCard(liveEntries.take(3), onProfileClick) }
                    }
                    items(liveEntries, key = { "${it.rank}:${it.username}" }) { entry ->
                        LeaderboardRow(entry, onProfileClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 34.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "Menu", modifier = Modifier.size(27.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Blink Games", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Play • learn • compete", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onNotificationClick, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications")
        }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onProfileClick)
        ) {
            AsyncImage(
                model = userAvatar,
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
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
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopTabItem("Home", selected == 0, onHome)
        TopTabItem("Reel", selected == 1, onReel)
        TopTabItem("Connect", selected == 2, onConnect)
        TopTabItem("Game", selected == 3, onGame)
    }
}

@Composable
private fun TopTabItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun GameStatsPanel(dashboard: GameDashboard) {
    val level = (dashboard.score / 1_000) + 1
    val levelProgress = (dashboard.score % 1_000) / 1_000f
    val dailyProgress = (dashboard.todayAnswers.toFloat() / dashboard.dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Points", dashboard.score.toString(), Icons.Default.Bolt, BlinkPink, Modifier.weight(1f))
            StatCard("Coins", dashboard.coins.toString(), Icons.Default.WorkspacePremium, BlinkGold, Modifier.weight(1f))
            StatCard("Streak", dashboard.streak.toString(), Icons.Default.LocalFireDepartment, Color(0xFFFF8A00), Modifier.weight(1f))
            StatCard("Best", dashboard.bestStreak.toString(), Icons.Default.MilitaryTech, BlinkPurple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Level $level", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (dashboard.worldRank > 0) "#${dashboard.worldRank} global" else "Unranked",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(progress = levelProgress, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily goal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${dashboard.todayAnswers}/${dashboard.dailyGoal}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlinkPink)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(progress = dailyProgress, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
            Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GamePaneTabs(selected: GameHubPane, onSelected: (GameHubPane) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GameHubPane.entries.forEach { pane ->
            val icon = when (pane) {
                GameHubPane.PLAY -> Icons.Default.SportsEsports
                GameHubPane.HISTORY -> Icons.Default.History
                GameHubPane.LEADERBOARD -> Icons.Default.Leaderboard
            }
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelected(pane) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected == pane) BlinkPink else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (selected == pane) BlinkPink else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (selected == pane) Color.White else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(5.dp))
                    Text(pane.label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (selected == pane) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun DailyChallengeCard(enabled: Boolean, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = BlinkPurple.copy(alpha = .11f),
        border = BorderStroke(1.dp, BlinkPurple.copy(alpha = .35f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = BlinkPurple.copy(alpha = .18f)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BlinkPurple, modifier = Modifier.padding(11.dp).size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Daily Challenge", fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("One shared mixed set • counts toward your daily goal", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onPlay,
                enabled = enabled,
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlinkPurple)
            ) { Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ModeSelector(selectedMode: ChallengeGameType, onModeSelected: (ChallengeGameType) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Choose a game", fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text("Difficulty adapts from your recent answers.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ChallengeGameType.entries.forEach { mode ->
                val selected = mode == selectedMode
                Surface(
                    modifier = Modifier.width(142.dp).clickable { onModeSelected(mode) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) BlinkPink.copy(alpha = .12f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) BlinkPink else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(mode.emoji, fontSize = 22.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(mode.label, fontWeight = FontWeight.Black, fontSize = 12.5.sp, maxLines = 1)
                        Text(modeDescription(mode), fontSize = 9.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
                        Spacer(Modifier.height(7.dp))
                        Text(modeMeta(mode), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (selected) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun modeDescription(mode: ChallengeGameType) = when (mode) {
    ChallengeGameType.BRAIN_MIX -> "Fast mixed questions across skills."
    ChallengeGameType.MATH_SPRINT -> "Timed arithmetic and number challenges."
    ChallengeGameType.LOGIC -> "Sequences, deduction and patterns."
    ChallengeGameType.MEMORY -> "Memorise first, then answer from recall."
    ChallengeGameType.WORD_POWER -> "Vocabulary, grammar and word puzzles."
    ChallengeGameType.GENERAL_KNOWLEDGE -> "Campus, science and everyday knowledge."
}

private fun modeMeta(mode: ChallengeGameType) = when (mode) {
    ChallengeGameType.BRAIN_MIX -> "10 sec • mixed"
    ChallengeGameType.MATH_SPRINT -> "15 sec • speed"
    ChallengeGameType.LOGIC -> "25 sec • reasoning"
    ChallengeGameType.MEMORY -> "3 sec preview"
    ChallengeGameType.WORD_POWER -> "20 sec • language"
    ChallengeGameType.GENERAL_KNOWLEDGE -> "20 sec • quiz"
}

@Composable
private fun ActiveChallengeCard(label: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BlinkGold.copy(alpha = .12f),
        border = BorderStroke(1.dp, BlinkGold.copy(alpha = .4f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Text("⚔️ Active $label challenge • your verified round score will submit when you finish.", modifier = Modifier.padding(12.dp), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GameModeHero(mode: ChallengeGameType, hasChallenge: Boolean, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mode.emoji, fontSize = 38.sp)
            Spacer(Modifier.height(6.dp))
            Text(mode.label, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text(modeDescription(mode), textAlign = TextAlign.Center, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeaturePill(Icons.Default.School, "Adaptive")
                FeaturePill(Icons.Default.Timer, "Timed")
                FeaturePill(Icons.Default.EmojiEvents, if (hasChallenge) "Challenge" else "Ranked")
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlinkPink)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (hasChallenge) "Play challenge" else "Start 5-question round", fontWeight = FontWeight.Bold)
            }
            Text("Answers are checked by Blink's server before points or coins are awarded.", modifier = Modifier.padding(top = 9.dp), fontSize = 9.5.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeaturePill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuestionCard(
    question: ServerGameQuestion,
    questionNumber: Int,
    totalQuestions: Int,
    remainingSeconds: Int,
    memoryPreviewVisible: Boolean,
    selectedIndex: Int?,
    result: ServerGameAnswerResult?,
    isSubmitting: Boolean,
    onAnswer: (Int) -> Unit,
    onNotSure: () -> Unit,
    onNext: () -> Unit,
    onToggleSaved: () -> Unit,
    onReport: () -> Unit
) {
    val progress = (questionNumber.toFloat() / totalQuestions.coerceAtLeast(1)).coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(100.dp), color = BlinkPink.copy(alpha = .12f)) {
                    Text(question.category, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BlinkPink)
                }
                Spacer(Modifier.weight(1f))
                Text("Q $questionNumber/$totalQuestions", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                if (question.timeLimitSeconds != null && result == null && !memoryPreviewVisible) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(100.dp), color = if (remainingSeconds <= 5) Color(0xFFFF5252).copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (remainingSeconds <= 5) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(3.dp))
                            Text("${remainingSeconds}s", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
            Spacer(Modifier.height(15.dp))

            AnimatedContent(
                targetState = memoryPreviewVisible,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "memoryPreview"
            ) { preview ->
                if (preview) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = BlinkPurple, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(9.dp))
                        Text("Memorise this", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(question.stimulus.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        Text("It will disappear before the question.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                } else {
                    Column {
                        Text(question.prompt, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(14.dp))
                        question.options.forEachIndexed { index, option ->
                            AnswerOption(
                                index = index,
                                text = option,
                                selected = selectedIndex == index,
                                correct = result != null && result.correctIndex == index,
                                wrong = result != null && selectedIndex == index && result.correctIndex != index,
                                enabled = result == null && !isSubmitting,
                                onClick = { onAnswer(index) }
                            )
                        }
                        if (result == null) {
                            TextButton(onClick = onNotSure, enabled = !isSubmitting, modifier = Modifier.align(Alignment.End)) {
                                Text("Not sure — skip", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (isSubmitting) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking answer…", fontSize = 11.sp)
                }
            }

            if (result != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = if (result.correct) BlinkOnlineGreen.copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (result.correct) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null, tint = if (result.correct) BlinkOnlineGreen else Color(0xFFFF5252), modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (result.correct) "Correct • +${result.awardedScore} pts • +${result.awardedCoins} coins" else "Review this one", fontWeight = FontWeight.Black, fontSize = 11.5.sp)
                        }
                        if (result.explanation.isNotBlank()) {
                            Text(result.explanation, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onToggleSaved) {
                        Icon(if (question.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (question.saved) "Saved" else "Save", fontSize = 10.5.sp)
                    }
                    TextButton(onClick = onReport) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Report", fontSize = 10.5.sp)
                    }
                }
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("next_game_question")
                ) {
                    Text(if (result.completed) "View round result" else "Next question", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AnswerOption(
    index: Int,
    text: String,
    selected: Boolean,
    correct: Boolean,
    wrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val border = when {
        correct -> BlinkOnlineGreen
        wrong -> Color(0xFFFF5252)
        selected -> BlinkPink
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bg = when {
        correct -> BlinkOnlineGreen.copy(alpha = .10f)
        wrong -> Color(0xFFFF5252).copy(alpha = .08f)
        selected -> BlinkPink.copy(alpha = .08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (selected || correct) BlinkPink else Color.Transparent, border = if (!selected && !correct) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant) else null, modifier = Modifier.size(23.dp)) {
                Text(('A'.code + index).toChar().toString(), fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = if (selected || correct) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 12.5.sp, fontWeight = if (selected || correct) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
            if (correct) Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = BlinkOnlineGreen, modifier = Modifier.size(18.dp))
            if (wrong) Icon(Icons.Default.Cancel, contentDescription = "Incorrect", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RoundSummaryCard(
    round: ServerGameRound,
    records: List<RoundAnswerRecord>,
    onPlayAgain: () -> Unit,
    onTryAnother: () -> Unit,
    onChallengeFriends: () -> Unit,
    onShare: () -> Unit
) {
    val accuracy = if (round.questions.isEmpty()) 0 else (round.correctCount * 100 / round.questions.size)
    val mistakes = records.filterNot { it.result.correct }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(19.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = BlinkGold.copy(alpha = .15f)) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = BlinkGold, modifier = Modifier.padding(14.dp).size(34.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Round complete", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("${round.correctCount}/${round.questions.size} correct • $accuracy% accuracy", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("Score", round.score.toString(), Modifier.weight(1f))
                MiniMetric("Coins", round.coinsEarned.toString(), Modifier.weight(1f))
                MiniMetric("Mistakes", mistakes.size.toString(), Modifier.weight(1f))
            }
            if (mistakes.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Review mistakes", modifier = Modifier.fillMaxWidth(), fontSize = 12.sp, fontWeight = FontWeight.Black)
                mistakes.take(3).forEach { record ->
                    Text("• ${record.question.prompt}", modifier = Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = BlinkPink)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Play again", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TextButton(onClick = onTryAnother, modifier = Modifier.weight(1f)) { Text("Another mode", fontSize = 10.5.sp) }
                TextButton(onClick = onChallengeFriends, modifier = Modifier.weight(1f)) { Text("Challenge", fontSize = 10.5.sp) }
                TextButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Share", fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AchievementStrip(dashboard: GameDashboard) {
    val achievements = listOf(
        Triple(Icons.Default.School, "First steps", dashboard.score >= 100),
        Triple(Icons.Default.LocalFireDepartment, "5 streak", dashboard.bestStreak >= 5),
        Triple(Icons.Default.MilitaryTech, "1K club", dashboard.score >= 1_000),
        Triple(Icons.Default.WorkspacePremium, "Coin collector", dashboard.coins >= 100)
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Achievements", fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            achievements.forEach { (icon, label, unlocked) ->
                Surface(shape = RoundedCornerShape(16.dp), color = if (unlocked) BlinkGold.copy(alpha = .12f) else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, if (unlocked) BlinkGold.copy(alpha = .4f) else MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = if (unlocked) BlinkGold else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(item: GameHistoryItem) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = BlinkPink.copy(alpha = .12f)) {
                Icon(Icons.Default.History, contentDescription = null, tint = BlinkPink, modifier = Modifier.padding(9.dp).size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.gameType.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Black, fontSize = 12.5.sp)
                Text("${item.correctCount}/${item.questionCount} correct • ${item.coinsEarned} coins", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${item.score} pts", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BlinkPink)
        }
    }
}

@Composable
private fun LeaderboardFilters(
    scope: GameRankScope,
    period: GameRankPeriod,
    onScope: (GameRankScope) -> Unit,
    onPeriod: (GameRankPeriod) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Campus Game Champions", fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GameRankScope.entries.forEach { item ->
                FilterChip(selected = scope == item, onClick = { onScope(item) }, label = { Text(item.label, fontSize = 10.5.sp) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GameRankPeriod.entries.forEach { item ->
                FilterChip(selected = period == item, onClick = { onPeriod(item) }, label = { Text(item.label, fontSize = 10.5.sp) })
            }
        }
    }
}

@Composable
private fun PodiumCard(entries: List<GameLeaderboardEntry>, onProfileClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = BlinkGold.copy(alpha = .08f),
        border = BorderStroke(1.dp, BlinkGold.copy(alpha = .28f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            listOfNotNull(entries.getOrNull(1), entries.getOrNull(0), entries.getOrNull(2)).forEach { entry ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp).clickable { onProfileClick(entry.username) }) {
                    Text(if (entry.rank == 1) "👑" else "#${entry.rank}", fontSize = if (entry.rank == 1) 20.sp else 12.sp, fontWeight = FontWeight.Black)
                    AsyncImage(model = entry.avatarUrl, fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile), error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile), contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.size(if (entry.rank == 1) 52.dp else 44.dp).clip(CircleShape))
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("${entry.score} pts", fontSize = 9.5.sp, color = BlinkPink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: GameLeaderboardEntry, onProfileClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (entry.isMe) BlinkPink.copy(alpha = .08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (entry.isMe) BlinkPink.copy(alpha = .35f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${entry.rank}", modifier = Modifier.width(34.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (entry.rank <= 3) BlinkGold else MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(model = entry.avatarUrl, fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile), error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile), contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp).clip(CircleShape).clickable { onProfileClick(entry.username) })
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name.ifBlank { entry.username }, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(4.dp))
                    VerifiedMark(entry.verificationBadge, size = 12.dp)
                }
                Text("@${entry.username} • 🔥 ${entry.streak}", fontSize = 9.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${entry.score} pts", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = BlinkPink)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyGameState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, modifier = Modifier.padding(top = 9.dp), fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text(subtitle, textAlign = TextAlign.Center, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
