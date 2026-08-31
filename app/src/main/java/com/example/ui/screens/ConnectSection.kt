package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.UserProfile
import com.example.data.repository.ProfileRepository
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Composable
fun ConnectSection(
    userAvatar: String,
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (String, String?, String?) -> Unit,
    selectedTopTab: Int,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onConnectClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val requested = remember { mutableStateMapOf<String, Boolean>() }
    var query by remember { mutableStateOf("") }

    suspend fun loadProfiles() {
        loading = true
        error = null
        try {
            val result = ProfileRepository(SupabaseService()).searchProfiles("*")
            profiles = result.filter { it.username.isNotBlank() }.distinctBy { it.id }
        } catch (e: Exception) {
            error = e.message ?: "Unable to load students."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadProfiles() }

    val filtered = profiles.filter {
        query.isBlank() || it.username.contains(query, true) || it.fullName.contains(query, true) ||
                it.university.contains(query, true) || it.faculty.contains(query, true) || it.department.contains(query, true)
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 38.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenMenu) { Icon(Icons.Default.MoreHoriz, "Menu") }
            Spacer(Modifier.weight(1f))
            Text("Connect", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenActivity) { Icon(Icons.Default.NotificationsNone, "Notifications") }
            AsyncImage(model = userAvatar, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onProfileClick("you") })
            Spacer(Modifier.width(4.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopTab("Home", selectedTopTab == 0, onHomeClick)
            TopTab("Reel", selectedTopTab == 1, onReelClick)
            TopTab("Connect", selectedTopTab == 2, onConnectClick)
            TopTab("Game", selectedTopTab == 3, onGameClick)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("Search real students") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } }) else null
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = BlinkPink) }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp)); Text("Couldn't load Connect", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp)); Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp)); Button(onClick = { scope.launch { loadProfiles() } }) { Text("Retry") }
                }
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PeopleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp)); Text("No students found", fontWeight = FontWeight.Bold)
                    Text("New students will appear here when they create profiles.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("People on Blink", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                items(filtered, key = { it.id }) { profile ->
                    val isRequested = requested[profile.id] == true
                    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = profile.avatarUrl, contentDescription = profile.fullName, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape).clickable { onProfileClick(profile.username) })
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(profile.fullName.ifBlank { profile.username }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (profile.verificationBadge.name != "NONE") { Spacer(Modifier.width(4.dp)); VerifiedMark(profile.verificationBadge, size = 13.dp) }
                                }
                                Text("@${profile.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(listOf(profile.university, profile.faculty).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val ok = sendConnectionRequest(profile.id)
                                            if (ok) requested[profile.id] = true
                                        }
                                    },
                                    enabled = !isRequested && profile.id != SupabaseService().getCurrentUserId(),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isRequested) BlinkOnlineGreen.copy(alpha = .16f) else BlinkPink, contentColor = if (isRequested) BlinkOnlineGreen else MaterialTheme.colorScheme.onPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(if (isRequested) Icons.Default.Check else Icons.Default.PersonAdd, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp)); Text(if (isRequested) "Requested" else "Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { onDirectMessage(profile.username, profile.fullName, profile.avatarUrl) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), modifier = Modifier.height(34.dp)) { Text("Message", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TopTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(100.dp), color = if (selected) BlinkPink else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { onClick() }) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

private suspend fun sendConnectionRequest(receiverId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val token = SupabaseService.accessToken() ?: return@withContext false
        val body = JSONObject().put("p_receiver_id", receiverId).toString().toRequestBody("application/json".toMediaType())
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build().newCall(
            Request.Builder().url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/send_connection_request").addHeader("apikey", SupabaseConfig.anonKey).addHeader("Authorization", "Bearer $token").post(body).build()
        ).execute().use { it.isSuccessful }
    } catch (_: Exception) { false }
}
