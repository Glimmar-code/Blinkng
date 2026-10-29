package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.example.data.models.ChatMessage

/** An album includes only confirmed videos with the same server-recorded ID. */
@Composable
internal fun BlinkChatVideoAlbumBubble(messages: List<ChatMessage>) {
    val videos = remember(messages) {
        messages.sortedBy { it.imageAlbumPosition }
            .filter { !it.attachedVideoUrl.isNullOrBlank() && !it.deletedForEveryone }
            .distinctBy { it.id }
    }
    if (videos.isEmpty()) return
    var selected by remember(videos) { mutableIntStateOf(-1) }
    val context = LocalContext.current
    val groups = when (videos.size) {
        1 -> listOf(videos)
        2 -> listOf(videos)
        3 -> listOf(videos.take(1), videos.drop(1))
        else -> videos.take(4).chunked(2)
    }
    Column(Modifier.widthIn(max = 276.dp).clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF17283A)).padding(4.dp)) {
        groups.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { message ->
                    val index = videos.indexOfFirst { it.id == message.id }
                    Box(
                        Modifier.weight(1f)
                            .height(if (videos.size <= 2) 162.dp else 120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101C2C))
                            .clickable { selected = index },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = remember(message.attachedVideoUrl) {
                                ImageRequest.Builder(context)
                                    .data(message.attachedVideoUrl)
                                    .videoFrameMillis(800L).build()
                            },
                            contentDescription = "Video " + (index + 1),
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                        Text("▶", color = Color.White, fontSize = 26.sp,
                            modifier = Modifier.align(Alignment.Center))
                        if (index == 3 && videos.size > 4) {
                            Box(Modifier.fillMaxSize()
                                .background(Color(0x99060D16)),
                                contentAlignment = Alignment.Center) {
                                Text("+" + (videos.size - 4),
                                    color = Color.White, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
        }
        Text(videos.size.toString() + " videos",
            modifier = Modifier.padding(8.dp),
            color = Color(0xFFB7C7DA), fontSize = 11.sp)
    }
    if (selected in videos.indices) {
        Dialog(onDismissRequest = { selected = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color(0xFF060D16))) {
                Row(Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = -1 }) {
                        Text("Close", color = Color.White)
                    }
                    Text("Video " + (selected + 1) + " of " + videos.size,
                        color = Color.White, modifier = Modifier.weight(1f))
                    BlinkChatDownloadAction(videos[selected].attachedVideoUrl, "video", "mp4")
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    var playing by remember(selected) { mutableStateOf(false) }
                    BlinkChatInlineVideo(
                        url = videos[selected].attachedVideoUrl.orEmpty(),
                        isActive = playing,
                        onActivate = { playing = true },
                        onDeactivate = { playing = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(enabled = selected > 0, onClick = { selected-- }) {
                        Text("Previous")
                    }
                    OutlinedButton(enabled = selected < videos.lastIndex,
                        onClick = { selected++ }) { Text("Next") }
                }
            }
        }
    }
}
