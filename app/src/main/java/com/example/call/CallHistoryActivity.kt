package com.example.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseService
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CallHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)
        setContent {
            BlinkTheme {
                CallHistoryScreen(onBack = ::finish)
            }
        }
    }
}

private enum class CallHistoryFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    VOICE("Voice"),
    VIDEO("Video")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CallRepository() }
    var calls by remember { mutableStateOf<List<BlinkCall>>(emptyList()) }
    var peers by remember { mutableStateOf<Map<String, CallPeer>>(emptyMap()) }
    var filter by remember { mutableStateOf(CallHistoryFilter.ALL) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        errorText = null
        repository.fetchRecentCalls(75)
            .onSuccess { rows ->
                calls = rows
                val currentUserId = repository.currentUserId()
                val ids = rows.map { it.otherUserId(currentUserId) }.filter { it.isNotBlank() }.distinct()
                val loadedPeers = ids.map { id ->
                    scope.async {
                        id to (repository.fetchPeer(id).getOrNull() ?: CallPeer(id = id))
                    }
                }.awaitAll().toMap()
                peers = loadedPeers
            }
            .onFailure { error ->
                errorText = error.message ?: "Unable to load call history."
            }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    val currentUserId = repository.currentUserId()
    val filtered = remember(calls, filter) {
        calls.filter { call ->
            when (filter) {
                CallHistoryFilter.ALL -> true
                CallHistoryFilter.MISSED -> call.status == CallStatus.MISSED
                CallHistoryFilter.VOICE -> call.type == CallType.AUDIO
                CallHistoryFilter.VIDEO -> call.type == CallType.VIDEO
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Call history", fontWeight = FontWeight.Bold)
                        Text("Voice and video calls", fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 10.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CallHistoryFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }

            errorText?.let { message ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (!loading && filtered.isEmpty() && errorText == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No calls here yet", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.size(5.dp))
                            Text(
                                "Blink voice and video calls will appear here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            items(filtered, key = { it.id }) { call ->
                val peerId = call.otherUserId(currentUserId)
                val peer = peers[peerId] ?: CallPeer(id = peerId)
                CallHistoryRow(
                    call = call,
                    peer = peer,
                    outgoing = call.callerId == currentUserId,
                    onVoice = {
                        BlinkCallLauncher.startOutgoing(
                            context = context,
                            conversationId = call.conversationId,
                            calleeId = peer.id,
                            calleeUsername = peer.username,
                            calleeName = peer.name,
                            calleeAvatar = peer.avatar,
                            type = CallType.AUDIO
                        )
                    },
                    onVideo = {
                        BlinkCallLauncher.startOutgoing(
                            context = context,
                            conversationId = call.conversationId,
                            calleeId = peer.id,
                            calleeUsername = peer.username,
                            calleeName = peer.name,
                            calleeAvatar = peer.avatar,
                            type = CallType.VIDEO
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    call: BlinkCall,
    peer: CallPeer,
    outgoing: Boolean,
    onVoice: () -> Unit,
    onVideo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(peer.name.ifBlank { peer.username.ifBlank { "Blink user" } }, fontWeight = FontWeight.Bold)
                    if (peer.username.isNotBlank()) {
                        Text("@${peer.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    if (call.type == CallType.VIDEO) "VIDEO" else "VOICE",
                    color = BlinkPink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.size(9.dp))
            Text(
                buildString {
                    append(if (outgoing) "Outgoing" else "Incoming")
                    append(" • ")
                    append(statusLabel(call.status))
                    if (call.durationSeconds > 0) {
                        append(" • ")
                        append(formatCallDuration(call.durationSeconds))
                    }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (call.createdAt.isNotBlank()) {
                Text(
                    formatTimestamp(call.createdAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onVoice, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("Voice")
                }
                Button(onClick = onVideo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("Video")
                }
            }
        }
    }
}

private fun statusLabel(status: CallStatus): String = when (status) {
    CallStatus.RINGING -> "Ringing"
    CallStatus.CONNECTING -> "Connecting"
    CallStatus.CONNECTED -> "Connected"
    CallStatus.DECLINED -> "Declined"
    CallStatus.BUSY -> "Busy"
    CallStatus.MISSED -> "Missed"
    CallStatus.CANCELLED -> "Cancelled"
    CallStatus.ENDED -> "Ended"
    CallStatus.FAILED -> "Failed"
}

private fun formatCallDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun formatTimestamp(value: String): String = runCatching {
    val instant = Instant.parse(value)
    DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(value)
