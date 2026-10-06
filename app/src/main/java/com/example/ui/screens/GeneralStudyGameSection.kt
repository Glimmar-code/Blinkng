package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.GeneralStudyBank
import com.blinkng.shared.GeneralStudyDifficulty
import com.blinkng.shared.GeneralStudyQuestion
import com.example.R
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

private enum class GeneralStudyPane {
    CATALOG,
    SETUP,
    PLAY,
    RESULT,
}

@Composable
fun GeneralStudyGameSection(
    userAvatar: String,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    selectedTopTab: Int,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onConnectClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var paneName by rememberSaveable { mutableStateOf(GeneralStudyPane.CATALOG.name) }
    val pane = remember(paneName) { GeneralStudyPane.valueOf(paneName) }
    var difficultyName by rememberSaveable { mutableStateOf(GeneralStudyDifficulty.EASY.name) }
    val difficulty = remember(difficultyName) { GeneralStudyDifficulty.valueOf(difficultyName) }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var selectedOption by rememberSaveable { mutableIntStateOf(-1) }
    var answered by rememberSaveable { mutableStateOf(false) }

    val questions = remember(difficulty, seed) {
        if (seed == 0L) emptyList()
        else GeneralStudyBank.round(difficulty, seed, GeneralStudyBank.DEFAULT_ROUND_SIZE)
    }
    val question = questions.getOrNull(questionIndex)

    fun startRound() {
        seed = System.currentTimeMillis()
        questionIndex = 0
        score = 0
        selectedOption = -1
        answered = false
        paneName = GeneralStudyPane.PLAY.name
    }

    fun answer(index: Int) {
        if (answered || question == null) return
        selectedOption = index
        answered = true
        if (index == question.correctIndex) score += 1
    }

    fun next() {
        if (!answered) return
        if (questionIndex >= questions.lastIndex) {
            paneName = GeneralStudyPane.RESULT.name
        } else {
            questionIndex += 1
            selectedOption = -1
            answered = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            GameHeader(
                userAvatar = userAvatar,
                onMenuClick = onOpenMenu,
                onNotificationClick = onOpenActivity,
                onProfileClick = { onProfileClick("you") },
            )
        }
        item {
            TopNavigationRow(
                selected = selectedTopTab,
                onHome = onHomeClick,
                onReel = onReelClick,
                onConnect = onConnectClick,
                onGame = onGameClick,
            )
        }
        item { Spacer(Modifier.height(10.dp)) }

        item {
            AnimatedContent(
                targetState = pane,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "generalStudyPane",
            ) { current ->
                when (current) {
                    GeneralStudyPane.CATALOG -> CatalogPane(
                        onOpenGeneralStudy = { paneName = GeneralStudyPane.SETUP.name },
                    )
                    GeneralStudyPane.SETUP -> SetupPane(
                        difficulty = difficulty,
                        onDifficulty = { difficultyName = it.name },
                        onBack = { paneName = GeneralStudyPane.CATALOG.name },
                        onStart = ::startRound,
                    )
                    GeneralStudyPane.PLAY -> PlayPane(
                        difficulty = difficulty,
                        question = question,
                        questionNumber = questionIndex + 1,
                        totalQuestions = questions.size,
                        score = score,
                        selectedOption = selectedOption,
                        answered = answered,
                        onBack = {
                            paneName = GeneralStudyPane.SETUP.name
                            seed = 0L
                        },
                        onAnswer = ::answer,
                        onNext = ::next,
                    )
                    GeneralStudyPane.RESULT -> ResultPane(
                        difficulty = difficulty,
                        score = score,
                        total = questions.size,
                        onPlayAgain = ::startRound,
                        onChangeDifficulty = {
                            seed = 0L
                            paneName = GeneralStudyPane.SETUP.name
                        },
                        onGames = {
                            seed = 0L
                            paneName = GeneralStudyPane.CATALOG.name
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogPane(onOpenGeneralStudy: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Games", fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(
            "Start with General Study. More games will appear here as they are added.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenGeneralStudy),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, BlinkPurple.copy(alpha = .35f)),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = BlinkPurple.copy(alpha = .13f)) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = BlinkPurple,
                            modifier = Modifier.padding(13.dp).size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("General Study", fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(100.dp), color = BlinkPink.copy(alpha = .12f)) {
                                Text(
                                    "AVAILABLE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BlinkPink,
                                )
                            }
                        }
                        Text(
                            "20,000 questions across 8 study areas",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GeneralStudyDifficulty.entries.forEach { level ->
                        CountPill(level.label, "5,000")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    GeneralStudyBank.categories.joinToString(" • "),
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onOpenGeneralStudy,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open General Study", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("More games coming soon", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(
                        "New games can be added here without changing General Study.",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = BlinkPurple.copy(alpha = .08f),
        border = BorderStroke(1.dp, BlinkPurple.copy(alpha = .18f)),
    ) {
        Text(
            label + " · " + value,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SetupPane(
    difficulty: GeneralStudyDifficulty,
    onDifficulty: (GeneralStudyDifficulty) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to games")
            }
            Column {
                Text("General Study", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "Choose one of the four separated difficulty banks.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        GeneralStudyDifficulty.entries.forEach { level ->
            val selected = difficulty == level
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onDifficulty(level) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) BlinkPink.copy(alpha = .10f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (selected) BlinkPink else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) BlinkPink else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            when (level) {
                                GeneralStudyDifficulty.EASY -> "E"
                                GeneralStudyDifficulty.MEDIUM -> "M"
                                GeneralStudyDifficulty.HARD -> "H"
                                GeneralStudyDifficulty.EXPERT -> "X"
                            },
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            fontWeight = FontWeight.Black,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(level.label, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text(
                            "5,000 questions",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = BlinkPink)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = BlinkPurple.copy(alpha = .08f),
        ) {
            Column(Modifier.padding(15.dp)) {
                Text("Round format", fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                Text(
                    "10 mixed questions per round • instant answer feedback • works without loading all 20,000 questions into memory.",
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Start " + difficulty.label + " Round", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlayPane(
    difficulty: GeneralStudyDifficulty,
    question: GeneralStudyQuestion?,
    questionNumber: Int,
    totalQuestions: Int,
    score: Int,
    selectedOption: Int,
    answered: Boolean,
    onBack: () -> Unit,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
) {
    if (question == null) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            Text("Preparing your General Study round…")
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close round")
            }
            Column(Modifier.weight(1f)) {
                Text("General Study · " + difficulty.label, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(
                    question.category,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(shape = RoundedCornerShape(100.dp), color = BlinkPurple.copy(alpha = .10f)) {
                Text(
                    score.toString() + "/" + totalQuestions,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        val progress = if (totalQuestions == 0) 0f else questionNumber.toFloat() / totalQuestions
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
        Text(
            "Question " + questionNumber + " of " + totalQuestions,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    question.prompt,
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(16.dp))

                question.options.forEachIndexed { index, option ->
                    val isCorrect = answered && index == question.correctIndex
                    val isWrong = answered && index == selectedOption && index != question.correctIndex
                    val border = when {
                        isCorrect -> Color(0xFF2EAD67)
                        isWrong -> MaterialTheme.colorScheme.error
                        selectedOption == index -> BlinkPink
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val background = when {
                        isCorrect -> Color(0xFF2EAD67).copy(alpha = .10f)
                        isWrong -> MaterialTheme.colorScheme.error.copy(alpha = .08f)
                        selectedOption == index -> BlinkPink.copy(alpha = .08f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .clickable(enabled = !answered) { onAnswer(index) },
                        shape = RoundedCornerShape(16.dp),
                        color = background,
                        border = BorderStroke(1.dp, border),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (selectedOption == index || isCorrect) BlinkPink else Color.Transparent,
                                border = if (selectedOption != index && !isCorrect) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                                } else null,
                            ) {
                                Text(
                                    ('A'.code + index).toChar().toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (selectedOption == index || isCorrect) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                option,
                                modifier = Modifier.weight(1f),
                                fontSize = 12.5.sp,
                                fontWeight = if (selectedOption == index || isCorrect) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (isCorrect) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Correct answer",
                                    tint = Color(0xFF2EAD67),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                if (answered) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedOption == question.correctIndex) {
                            Color(0xFF2EAD67).copy(alpha = .09f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Column(Modifier.padding(13.dp)) {
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
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                    ) {
                        Text(
                            if (questionNumber >= totalQuestions) "View Result" else "Next Question",
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultPane(
    difficulty: GeneralStudyDifficulty,
    score: Int,
    total: Int,
    onPlayAgain: () -> Unit,
    onChangeDifficulty: () -> Unit,
    onGames: () -> Unit,
) {
    val percent = if (total == 0) 0 else score * 100 / total
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = BlinkPurple.copy(alpha = .12f)) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = BlinkPurple,
                modifier = Modifier.padding(18.dp).size(42.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Round complete", fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(
            difficulty.label + " · " + score + "/" + total + " correct · " + percent + "%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPlayAgain,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
        ) {
            Text("Play Again", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onChangeDifficulty) { Text("Change difficulty") }
        TextButton(onClick = onGames) { Text("Back to games") }
    }
}

@Composable
private fun GameHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 34.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "Menu", modifier = Modifier.size(27.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Blink Games", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("General Study first", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onNotificationClick, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications")
        }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onProfileClick),
        ) {
            AsyncImage(
                model = userAvatar,
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
    onGame: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
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
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
