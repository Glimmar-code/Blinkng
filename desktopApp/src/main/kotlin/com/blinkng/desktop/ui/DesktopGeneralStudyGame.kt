package com.blinkng.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.GeneralStudyBank
import com.blinkng.shared.GeneralStudyDifficulty
import com.blinkng.desktop.data.DesktopGameAnswer
import com.blinkng.desktop.data.DesktopGameHistory
import com.blinkng.desktop.data.DesktopGameLeader
import com.blinkng.desktop.data.DesktopGameQuestion
import com.blinkng.desktop.data.DesktopGameRepository
import com.blinkng.desktop.data.DesktopGameRound
import com.blinkng.desktop.data.DesktopSupabaseClient
import kotlinx.coroutines.launch

private enum class DesktopGeneralStudyPane {
    CATALOG,
    SETUP,
    PLAY,
    RESULT,
}

@Composable
fun DesktopGeneralStudyGame(client: DesktopSupabaseClient) {
    var paneName by rememberSaveable { mutableStateOf(DesktopGeneralStudyPane.CATALOG.name) }
    val pane = remember(paneName) { DesktopGeneralStudyPane.valueOf(paneName) }
    var difficultyName by rememberSaveable { mutableStateOf(GeneralStudyDifficulty.EASY.name) }
    val difficulty = remember(difficultyName) { GeneralStudyDifficulty.valueOf(difficultyName) }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedOption by rememberSaveable { mutableIntStateOf(-1) }
    var answered by rememberSaveable { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var round by remember { mutableStateOf<DesktopGameRound?>(null) }
    var answeredQuestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var answerResult by remember { mutableStateOf<DesktopGameAnswer?>(null) }
    var roundLoading by remember { mutableStateOf(false) }
    var answerLoading by remember { mutableStateOf(false) }
    var gameError by remember { mutableStateOf<String?>(null) }
    var questionStartedAt by remember { mutableLongStateOf(0L) }
    var history by remember { mutableStateOf<List<DesktopGameHistory>>(emptyList()) }
    var leaders by remember { mutableStateOf<List<DesktopGameLeader>>(emptyList()) }
    var statsLoading by remember { mutableStateOf(false) }
    var statsError by remember { mutableStateOf<String?>(null) }
    val repository = remember(client) { DesktopGameRepository(client) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(pane, questionIndex) { listState.scrollToItem(0) }

    val questions = round?.questions.orEmpty()
    val question = questions.getOrNull(questionIndex)
    val score = answerResult?.correctCount ?: round?.correctCount ?: 0

    fun loadGameStats() {
        if (statsLoading) return
        statsLoading = true
        statsError = null
        scope.launch {
            val historyResult = repository.fetchHistory()
            val leaderboardResult = repository.fetchLeaderboard()
            historyResult.onSuccess { history = it }
            leaderboardResult.onSuccess { leaders = it }
            statsError = historyResult.exceptionOrNull()?.message
                ?: leaderboardResult.exceptionOrNull()?.message
            statsLoading = false
        }
    }
    LaunchedEffect(pane) {
        if (pane == DesktopGeneralStudyPane.CATALOG) loadGameStats()
    }

    fun startRound() {
        confirmExit = false
        questionIndex = 0
        selectedOption = -1
        answered = false
        round = null
        answerResult = null
        gameError = null
        roundLoading = true
        scope.launch {
            repository.startRound("general_study", GeneralStudyBank.DEFAULT_ROUND_SIZE)
                .onSuccess { started ->
                    if (started.questions.isEmpty() || started.id.isBlank()) {
                        gameError = "The game service returned an empty round. Please try again."
                    } else {
                        round = started
                        answeredQuestionIds = started.answeredQuestionIds
                        val nextQuestion = started.questions.indexOfFirst { it.id !in answeredQuestionIds }
                        if (nextQuestion < 0) {
                            questionIndex = started.questions.lastIndex
                            paneName = DesktopGeneralStudyPane.RESULT.name
                            loadGameStats()
                        } else {
                            questionIndex = nextQuestion
                            questionStartedAt = System.currentTimeMillis()
                            paneName = DesktopGeneralStudyPane.PLAY.name
                        }
                    }
                }
                .onFailure { gameError = it.message ?: "Could not load a General Study round." }
            roundLoading = false
        }
    }

    fun submitAnswer(index: Int) {
        val activeRound = round ?: return
        val activeQuestion = question ?: return
        if (answered || answerLoading || index !in activeQuestion.options.indices) return
        selectedOption = index
        answerLoading = true
        gameError = null
        scope.launch {
            repository.submitAnswer(
                roundId = activeRound.id,
                questionId = activeQuestion.id,
                selectedIndex = index,
                responseMs = (System.currentTimeMillis() - questionStartedAt).toInt(),
            ).onSuccess { result ->
                answerResult = result
                answered = true
                answeredQuestionIds = answeredQuestionIds + activeQuestion.id
                round = activeRound.copy(score = result.roundScore, correctCount = result.correctCount)
            }.onFailure { gameError = it.message ?: "Your answer could not be submitted. Please retry." }
            answerLoading = false
        }
    }

    fun nextQuestion() {
        if (!answered || answerLoading) return
        val nextQuestion = questions.indices.firstOrNull {
            it > questionIndex && questions[it].id !in answeredQuestionIds
        }
        if (nextQuestion == null || answerResult?.completed == true) {
            paneName = DesktopGeneralStudyPane.RESULT.name
            loadGameStats()
        } else {
            questionIndex = nextQuestion
            selectedOption = -1
            answered = false
            answerResult = null
            gameError = null
            questionStartedAt = System.currentTimeMillis()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AnimatedContent(
                targetState = pane,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "desktopGeneralStudyPane",
            ) { current ->
                when (current) {
                    DesktopGeneralStudyPane.CATALOG -> DesktopGameCatalog(
                        onOpenGeneralStudy = { paneName = DesktopGeneralStudyPane.SETUP.name },
                        history = history,
                        leaders = leaders,
                        statsLoading = statsLoading,
                        statsError = statsError,
                        onRefresh = ::loadGameStats,
                    )

                    DesktopGeneralStudyPane.SETUP -> DesktopGeneralStudySetup(
                        difficulty = difficulty,
                        selectedCategory = selectedCategory,
                        onCategory = { selectedCategory = it },
                        onDifficulty = { difficultyName = it.name },
                        onBack = { paneName = DesktopGeneralStudyPane.CATALOG.name },
                        onStart = ::startRound,
                        loading = roundLoading,
                        error = gameError,
                    )

                    DesktopGeneralStudyPane.PLAY -> DesktopGeneralStudyRound(
                        difficulty = difficulty,
                        question = question,
                        questionNumber = questionIndex + 1,
                        totalQuestions = questions.size,
                        score = score,
                        serverScore = round?.score ?: 0,
                        selectedOption = selectedOption,
                        answered = answered,
                        answerResult = answerResult,
                        submitting = answerLoading,
                        error = gameError,
                        onClose = { confirmExit = true },
                        onAnswer = ::submitAnswer,
                        onRetryAnswer = { if (selectedOption >= 0) submitAnswer(selectedOption) },
                        onNext = ::nextQuestion,
                    )

                    DesktopGeneralStudyPane.RESULT -> DesktopGeneralStudyResult(
                        difficulty = difficulty,
                        score = score,
                        serverScore = round?.score ?: 0,
                        total = questions.size,
                        onPlayAgain = ::startRound,
                        onChangeDifficulty = {
                            paneName = DesktopGeneralStudyPane.SETUP.name
                        },
                        onGames = {
                            paneName = DesktopGeneralStudyPane.CATALOG.name
                        },
                    )
                }
            }
        }
    }
    if (confirmExit && pane == DesktopGeneralStudyPane.PLAY) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave General Study?") },
            text = { Text("Your unfinished quiz round will be discarded.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    round = null
                    paneName = DesktopGeneralStudyPane.SETUP.name
                }) { Text("Leave round") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Keep playing") }
            },
        )
    }
}

@Composable
private fun DesktopGameCatalog(
    onOpenGeneralStudy: () -> Unit,
    history: List<DesktopGameHistory>,
    leaders: List<DesktopGameLeader>,
    statsLoading: Boolean,
    statsError: String?,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader(
            "Games",
            "General Study is the first Blink game. More games are coming soon.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenGeneralStudy),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .24f)),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                    ) {
                        Icon(
                            Icons.Rounded.School,
                            contentDescription = null,
                            modifier = Modifier.padding(14.dp).size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("General Study", fontSize = 23.sp, fontWeight = FontWeight.Black)
                        Text(
                            "20,000 questions across 8 study areas",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                    ) {
                        Text(
                            "AVAILABLE",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeneralStudyDifficulty.entries.forEach { difficulty ->
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                difficulty.label + " · 5,000",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Text(
                    GeneralStudyBank.categories.joinToString(" • "),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onOpenGeneralStudy,
                    shape = RoundedCornerShape(100.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open General Study", fontWeight = FontWeight.Bold)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("More games coming soon", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(
                        "This catalog is ready for additional Blink games without changing General Study.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game leaderboard", fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !statsLoading) { Text("Refresh") }
        }
        when {
            statsLoading && leaders.isEmpty() && history.isEmpty() -> CircularProgressIndicator(Modifier.size(22.dp))
            statsError != null && leaders.isEmpty() && history.isEmpty() -> Text(
                statsError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
            )
            leaders.isEmpty() -> Text("No leaderboard entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> leaders.take(5).forEach { entry ->
                Text(
                    "${entry.rank}. ${entry.name.ifBlank { "Player" }} · ${entry.score} points${if (entry.isMe) " · You" else ""}",
                    fontSize = 12.sp,
                )
            }
        }
        Text("Recent rounds", fontSize = 16.sp, fontWeight = FontWeight.Black)
        if (history.isEmpty()) {
            Text("Completed rounds will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            history.take(5).forEach { item ->
                Text("${item.correctCount}/${item.questionCount} correct · ${item.score} points", fontSize = 12.sp)
            }
        }
        if (statsError != null && (leaders.isNotEmpty() || history.isNotEmpty())) {
            Text("Some game data may be incomplete: $statsError", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DesktopGeneralStudySetup(
    difficulty: GeneralStudyDifficulty,
    selectedCategory: String,
    onCategory: (String) -> Unit,
    onDifficulty: (GeneralStudyDifficulty) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    loading: Boolean,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to games")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("General Study", fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(
                    "Questions and scoring are supplied by the game service.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GeneralStudyDifficulty.entries.forEach { level ->
                val selected = level == difficulty
                Surface(
                    modifier = Modifier.weight(1f).clickable { onDifficulty(level) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(level.label, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Spacer(Modifier.weight(1f))
                            if (selected) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "5,000 questions",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text("Study area", fontWeight = FontWeight.Black, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf("") + GeneralStudyBank.categories).forEach { category ->
                val selected = selectedCategory == category
                Surface(
                    modifier = Modifier.clickable { onCategory(category) },
                    shape = RoundedCornerShape(100.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                        else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        if (category.isBlank()) "All topics" else category,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Round format", fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(
                    "10 questions per round • the current game API does not send the difficulty or study-area selections; the server chooses the questions.",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        Button(
            onClick = onStart,
            enabled = !loading,
            shape = RoundedCornerShape(100.dp),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (loading) "Loading round…" else "Start General Study", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DesktopGeneralStudyRound(
    difficulty: GeneralStudyDifficulty,
    question: DesktopGameQuestion?,
    questionNumber: Int,
    totalQuestions: Int,
    score: Int,
    serverScore: Int,
    selectedOption: Int,
    answered: Boolean,
    answerResult: DesktopGameAnswer?,
    submitting: Boolean,
    error: String?,
    onClose: () -> Unit,
    onAnswer: (Int) -> Unit,
    onRetryAnswer: () -> Unit,
    onNext: () -> Unit,
) {
    if (question == null) {
        Text("Preparing your General Study round…")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !submitting) {
                Icon(Icons.Rounded.Close, contentDescription = "Close round")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "General Study",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    question.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
            ) {
                Text(
                    "$serverScore points · $score/$totalQuestions correct",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }

        val completed = (questionNumber - 1 + (if (answered) 1 else 0)).coerceIn(0, totalQuestions)
        val progress = if (totalQuestions == 0) 0f else completed.toFloat() / totalQuestions
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Text(
            "Question " + questionNumber + " of " + totalQuestions,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    question.prompt,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Black,
                )

                question.options.forEachIndexed { index, option ->
                    val correct = answered && index == answerResult?.correctIndex
                    val wrong = answered && index == selectedOption && answerResult?.correct == false
                    val border = when {
                        correct -> Color(0xFF2EAD67)
                        wrong -> MaterialTheme.colorScheme.error
                        selectedOption == index -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val background = when {
                        correct -> Color(0xFF2EAD67).copy(alpha = .10f)
                        wrong -> MaterialTheme.colorScheme.error.copy(alpha = .08f)
                        selectedOption == index -> MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !answered && !submitting) {
                            onAnswer(index)
                        },
                        shape = RoundedCornerShape(15.dp),
                        color = background,
                        border = BorderStroke(1.dp, border),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                ('A'.code + index).toChar().toString(),
                                modifier = Modifier.width(28.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                            )
                            Text(
                                option,
                                modifier = Modifier.weight(1f),
                                fontSize = 12.5.sp,
                                fontWeight = if (correct || selectedOption == index) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                            )
                            if (correct) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Correct answer",
                                    tint = Color(0xFF2EAD67),
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }

                if (submitting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Submitting answer…", fontSize = 11.sp)
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    if (!answered && !submitting && selectedOption >= 0) {
                        TextButton(onClick = onRetryAnswer) { Text("Retry submission") }
                    }
                }
                if (answered && answerResult != null) {
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                if (answerResult.correct) "Correct" else "Correct answer",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.5.sp,
                            )
                            Text(
                                answerResult.explanation.ifBlank {
                                    question.options.getOrNull(answerResult.correctIndex).orEmpty()
                                },
                                modifier = Modifier.padding(top = 3.dp),
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Button(
                        onClick = onNext,
                        shape = RoundedCornerShape(100.dp),
                    ) {
                        Text(
                            if (questionNumber >= totalQuestions) "View Result" else "Next Question",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopGeneralStudyResult(
    difficulty: GeneralStudyDifficulty,
    score: Int,
    serverScore: Int,
    total: Int,
    onPlayAgain: () -> Unit,
    onChangeDifficulty: () -> Unit,
    onGames: () -> Unit,
) {
    val percent = if (total == 0) 0 else score * 100 / total
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(18.dp).size(42.dp),
                )
            }
            Text("Round complete", fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(
                "General Study · $score/$total correct · $serverScore points · $percent%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = onPlayAgain, shape = RoundedCornerShape(100.dp)) {
                Text("Play Again", fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChangeDifficulty) { Text("Change difficulty") }
                TextButton(onClick = onGames) { Text("Back to games") }
            }
        }
    }
}
