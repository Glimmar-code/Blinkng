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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.VerificationBadge
import com.example.data.models.LeaderboardUser
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()

    val cardBg = if (isDark) DarkSurface else LightSurface
    val cardBorder = if (isDark) DarkBorder else LightBorder

    // Trivia State
    val questions = remember {
        listOf(
            TriviaQuestion(
                id = "q1",
                question = "Which is the oldest university in Nigeria, founded in 1948?",
                options = listOf("University of Lagos", "University of Ibadan", "Ahmadu Bello University", "University of Nigeria, Nsukka"),
                correctIndex = 1,
                explanation = "University of Ibadan (UI) was established in 1948 as University College Ibadan.",
                category = "Campus History"
            ),
            TriviaQuestion(
                id = "q2",
                question = "What does the abbreviation 'JAMB' stand for in Nigerian higher education?",
                options = listOf(
                    "Joint Admissions and Matriculation Board",
                    "Junior Academic Management Board",
                    "Joint Association of Matriculated Brethren",
                    "Judicial Academic Monitoring Bureau"
                ),
                correctIndex = 0,
                explanation = "JAMB is the official entrance examination board for tertiary-level institutions in Nigeria.",
                category = "Academics"
            ),
            TriviaQuestion(
                id = "q3",
                question = "Which Nigerian University is situated along the Lagos Lagoon?",
                options = listOf("Covenant University", "LASU", "University of Lagos (UNILAG)", "Babcock University"),
                correctIndex = 2,
                explanation = "UNILAG main campus in Akoka is famously bounded by the serene Lagos Lagoon.",
                category = "Campus Life"
            ),
            TriviaQuestion(
                id = "q4",
                question = "What is the motto of Obafemi Awolowo University (OAU), Ile-Ife?",
                options = listOf("In Deed and In Truth", "For Learning and Culture", "Character and Sound Knowledge", "Excellence in Action"),
                correctIndex = 1,
                explanation = "OAU's Latin motto is 'Doctrina Sana Ac Virtus', translated to 'For Learning and Culture'.",
                category = "Tradition"
            ),
            TriviaQuestion(
                id = "q5",
                question = "Which degree classification requires a CGPA of 4.50 and above (on a 5.0 scale)?",
                options = listOf("Second Class Upper (2:1)", "First Class Honours", "Distinction Cum Laude", "Summa Merit"),
                correctIndex = 1,
                explanation = "A CGPA of 4.50 – 5.00 earns First Class Honours in Nigerian universities.",
                category = "Academics"
            )
        )
    }

    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isAnswerSubmitted by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(350) }
    var streak by remember { mutableIntStateOf(3) }
    var coins by remember { mutableIntStateOf(120) }

    // Spin Wheel State
    var isSpinning by remember { mutableStateOf(false) }
    var spinReward by remember { mutableStateOf<String?>(null) }
    val spinRotation = remember { Animatable(0f) }

    val currentQ = questions[currentQuestionIndex % questions.size]

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

        // Top navigation tabs
        item {
            TopNavigationRow(
                selected = selectedTopTab,
                onHome = onHomeClick,
                onReel = onReelClick,
                onConnect = onConnectClick,
                onGame = onGameClick
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

        // Campus Trivia Challenge Card
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
                            text = "Q ${((currentQuestionIndex) % questions.size) + 1} / ${questions.size}",
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
                                        score += 50
                                        streak += 1
                                        coins += 15
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
                            Text("Next Question →", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        // Daily Campus Lucky Wheel
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
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Campus Wheel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = BlinkGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Free Daily Spin",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlinkGold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Animated Wheel Graphic
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(140.dp)
                            .rotate(spinRotation.value)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(4.dp, Brush.sweepGradient(listOf(BlinkPink, BlinkGold, BlinkPurple, BlinkOnlineGreen, BlinkPink))),
                            modifier = Modifier.fillMaxSize()
                        ) {}

                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Spin Wheel",
                            tint = BlinkGold,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (spinReward != null) {
                        Text(
                            text = "🎉 You won $spinReward!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlinkOnlineGreen
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            if (!isSpinning) {
                                isSpinning = true
                                spinReward = null
                                coroutineScope.launch {
                                    val target = spinRotation.value + 1440f + (0..360).random()
                                    spinRotation.animateTo(
                                        targetValue = target,
                                        animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
                                    )
                                    val rewards = listOf("+25 Campus Coins", "+50 Campus Coins", "+100 Points", "2x Streak Multiplier", "+10 Coins")
                                    val won = rewards.random()
                                    spinReward = won
                                    coins += 35
                                    isSpinning = false
                                }
                            }
                        },
                        enabled = !isSpinning,
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDark) BlinkCream else BlinkBlack),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("spin_wheel_button")
                    ) {
                        Text(
                            text = if (isSpinning) "Spinning..." else "Spin Wheel 🎡",
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
                    text = "Campus Trivia Champions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Top scorers across all faculties this week",
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
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = 38.dp,
                bottom = 8.dp
            ),
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

        Text(
            text = "Game",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onNotificationClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsNone,
                contentDescription = "Notifications",
                modifier = Modifier.size(25.dp)
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onProfileClick() }
        ) {
            AsyncImage(
                model = userAvatar,
                contentDescription = "Profile",
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
