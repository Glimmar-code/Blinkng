package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseService
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.launch
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private data class Question(val text: String, val options: List<String>, val answer: Int, val explanation: String)

@Composable
fun GameSection(
    userAvatar: String,
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
    val questions = remember {
        listOf(
            Question("Which Nigerian university was established in 1948 as University College Ibadan?", listOf("UNILAG", "University of Ibadan", "ABU", "UNN"), 1, "University of Ibadan was established in 1948."),
            Question("What does JAMB stand for?", listOf("Joint Admissions and Matriculation Board", "Junior Academic Management Board", "Joint Academic Merit Board", "Justice and Admissions Management Board"), 0, "JAMB means Joint Admissions and Matriculation Board."),
            Question("Which city is the University of Lagos main campus in?", listOf("Ibadan", "Akoka, Lagos", "Abuja", "Benin City"), 1, "UNILAG's main campus is in Akoka, Lagos.")
        )
    }
    val scope = rememberCoroutineScope()
    var points by remember { mutableIntStateOf(0) }
    var questionIndex by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var submitted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    suspend fun loadPoints() {
        loading = true
        try {
            val uid = SupabaseService().getCurrentUserId()
            if (!uid.isNullOrBlank()) {
                SupabaseService().fetchProfileById(uid)?.let { points = it.points }
            }
        } finally { loading = false }
    }
    LaunchedEffect(Unit) { loadPoints() }

    val q = questions[questionIndex % questions.size]

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 38.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenMenu) { Icon(Icons.Default.MoreHoriz, "Menu") }
            Spacer(Modifier.weight(1f)); Text("Game", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenActivity) { Icon(Icons.Default.NotificationsNone, "Notifications") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabPill("Home", selectedTopTab == 0, onHomeClick); TabPill("Reel", selectedTopTab == 1, onReelClick); TabPill("Connect", selectedTopTab == 2, onConnectClick); TabPill("Game", selectedTopTab == 3, onGameClick)
        }

        LazyColumn(contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Your Blink points", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (loading) "…" else points.toString(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = BlinkGold)
                        Text("Earned from real activity on Blink — not demo game data.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, null, tint = BlinkPink); Spacer(Modifier.width(8.dp)); Text("Campus Trivia", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp)); Text(q.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        q.options.forEachIndexed { index, option ->
                            val correct = submitted && index == q.answer
                            val wrong = submitted && selected == index && index != q.answer
                            OutlinedButton(
                                onClick = { if (!submitted) { selected = index; submitted = true } },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = when { correct -> MaterialTheme.colorScheme.secondaryContainer; wrong -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surface })
                            ) { Text(option, modifier = Modifier.fillMaxWidth()) }
                        }
                        if (submitted) {
                            Spacer(Modifier.height(8.dp)); Text(q.explanation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp)); Button(onClick = { questionIndex++; selected = -1; submitted = false }, modifier = Modifier.fillMaxWidth()) { Text("Next question") }
                        }
                    }
                }
            }
            item {
                OutlinedCard(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, null, tint = BlinkGold, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp));
                        Column(Modifier.weight(1f)) { Text("Real leaderboard", fontWeight = FontWeight.Bold); Text("Open Leaderboard to see actual users and points.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(100.dp), color = if (selected) BlinkPink else MaterialTheme.colorScheme.surfaceVariant, onClick = onClick) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}
