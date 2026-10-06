package com.blinkng.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.blinkng.shared.GeneralStudyQuestion

private enum class DesktopGeneralStudyPane {
    CATALOG,
    SETUP,
    PLAY,
    RESULT,
}

@Composable
fun DesktopGeneralStudyGame() {
    var paneName by rememberSaveable { mutableStateOf(DesktopGeneralStudyPane.CATALOG.name) }
    val pane = remember(paneName) { DesktopGeneralStudyPane.valueOf(paneName) }
    var difficultyName by rememberSaveable { mutableStateOf(GeneralStudyDifficulty.EASY.name) }
    val difficulty = remember(difficultyName) { GeneralStudyDifficulty.valueOf(difficultyName) }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var selectedOption by rememberSaveable { mutableIntStateOf(-1) }
    var answered by rememberSaveable { mutableStateOf(false) }

    val questions = remember(difficulty, seed) {
        if (seed == 0L) emptyList()
        else GeneralStudyBank.round(
            difficulty = difficulty,
            seed = seed,
            count = GeneralStudyBank.DEFAULT_ROUND_SIZE,
        )
    }
    val question = questions.getOrNull(questionIndex)

    fun startRound() {
        seed = System.currentTimeMillis()
        questionIndex = 0
        score = 0
        selectedOption = -1
        answered = false
        paneName = DesktopGeneralStudyPane.PLAY.name
    }

    fun submitAnswer(index: Int) {
        if (answered || question == null) return
        selectedOption = index
        answered = true
        if (index == question.correctIndex) score += 1
    }

    fun nextQuestion() {
        if (!answered) return
        if (questionIndex >= questions.lastIndex) {
            paneName = DesktopGeneralStudyPane.RESULT.name
        } else {
            questionIndex += 1
            selectedOption = -1
            answered = false
        }
    }

    LazyColumn(
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
                    )

                    DesktopGeneralStudyPane.SETUP -> DesktopGeneralStudySetup(
                        difficulty = difficulty,
                        onDifficulty = { difficultyName = it.name },
                        onBack = { paneName = DesktopGeneralStudyPane.CATALOG.name },
                        onStart = ::startRound,
                    )

                    DesktopGeneralStudyPane.PLAY -> DesktopGeneralStudyRound(
                        difficulty = difficulty,
                        question = question,
                        questionNumber = questionIndex + 1,
                        totalQuestions = questions.size,
                        score = score,
                        selectedOption = selectedOption,
                        answered = answered,
                        onClose = {
                            seed = 0L
                            paneName = DesktopGeneralStudyPane.SETUP.name
                        },
                        onAnswer = ::submitAnswer,
                        onNext = ::nextQuestion,
                    )

                    DesktopGeneralStudyPane.RESULT -> DesktopGeneralStudyResult(
                        difficulty = difficulty,
                        score = score,
                        total = questions.size,
                        onPlayAgain = ::startRound,
                        onChangeDifficulty = {
                            seed = 0L
                            paneName = DesktopGeneralStudyPane.SETUP.name
                        },
                        onGames = {
                            seed = 0L
                            paneName = DesktopGeneralStudyPane.CATALOG.name
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopGameCatalog(onOpenGeneralStudy: () -> Unit) {
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
    }
}

@Composable
private fun DesktopGeneralStudySetup(
    difficulty: GeneralStudyDifficulty,
    onDifficulty: (GeneralStudyDifficulty) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
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
                    "Choose a difficulty bank. Each bank contains 5,000 questions.",
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

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Round format", fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(
                    "10 mixed questions per round with instant answer feedback. The 20,000-question bank is generated on demand so it stays lightweight.",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Button(
            onClick = onStart,
            shape = RoundedCornerShape(100.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Start " + difficulty.label + " Round", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DesktopGeneralStudyRound(
    difficulty: GeneralStudyDifficulty,
    question: GeneralStudyQuestion?,
    questionNumber: Int,
    totalQuestions: Int,
    score: Int,
    selectedOption: Int,
    answered: Boolean,
    onClose: () -> Unit,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
) {
    if (question == null) {
        Text("Preparing your General Study round…")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close round")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "General Study · " + difficulty.label,
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
                    "Score " + score + "/" + totalQuestions,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }

        val progress = if (totalQuestions == 0) 0f else questionNumber.toFloat() / totalQuestions
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
                    val correct = answered && index == question.correctIndex
                    val wrong = answered && index == selectedOption && index != question.correctIndex
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
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) {
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

                if (answered) {
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                if (selectedOption == question.correctIndex) "Correct" else "Correct answer",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.5.sp,
                            )
                            Text(
                                question.answer,
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
                difficulty.label + " · " + score + "/" + total + " correct · " + percent + "%",
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
