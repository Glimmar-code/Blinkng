package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.blinkng.shared.BlinkChatReferenceStyle
import com.example.ui.theme.MessagePalette

/** An album includes only confirmed videos with the same server-recorded ID. */
@Composable
internal fun BlinkChatVideoAlbumBubble(messages: List<ChatMessage>, palette: MessagePalette) {
    val videos = remember(messages) {
        messages.sortedBy { it.imageAlbumPosition }
            .filter { !it.attachedVideoUrl.isNullOrBlank() && !it.deletedForEveryone }
            .distinctBy { it.id }
    }
    if (videos.isEmpty()) return
    var selected by remember(videos) { mutableIntStateOf(-1) }
    val context = LocalContext.current
    val isMine = videos.first().isFromMe
    val latest = videos.last()
    val albumStatus = BlinkChatAlbumStatus.aggregate(videos.map { it.status })
    val groups = when (videos.size) {
        1 -> listOf(videos)
        2 -> listOf(videos)
        3 -> listOf(videos.take(1), videos.drop(1))
        else -> videos.take(4).chunked(2)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement =
        if (isMine) Arrangement.End else Arrangement.Start) {
      Column(Modifier.widthIn(max = 276.dp).clip(RoundedCornerShape(14.dp))
        .padding(4.dp)) {
        groups.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { message ->
                    val index = videos.indexOfFirst { it.id == message.id }
                    Box(
                        Modifier.weight(1f)
                            .aspectRatio(BlinkChatReferenceStyle.VideoTileAspect)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(BlinkChatReferenceStyle.Canvas))
                            .clickable(
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClickLabel = "Play video ${index + 1} of ${videos.size}",
                                onClick = { selected = index }
                            )
                            .testTag("chat_reference_album_video_" + index),
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
                        Box(Modifier.size(38.dp).background(Color.Black.copy(alpha = .55f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(27.dp))
                        }
                        if (index == 3 && videos.size > 4) {
                            Box(Modifier.fillMaxSize()
                                .background(Color(0x99090D10)),
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (albumStatus) {
                    MessageStatus.FAILED -> "Waiting to retry"
                    MessageStatus.SENDING -> "Sending…"
                    else -> latest.timestamp
                },
                color = if (albumStatus == MessageStatus.FAILED) palette.danger
                    else palette.textSecondary,
                fontSize = 10.sp
            )
            if (isMine && albumStatus !in setOf(MessageStatus.SENDING, MessageStatus.FAILED)) {
                Spacer(Modifier.width(3.dp))
                Icon(
                    if (albumStatus == MessageStatus.SENT) Icons.Default.Check
                    else Icons.Default.DoneAll,
                    contentDescription = when (albumStatus) {
                        MessageStatus.READ -> "Read"
                        MessageStatus.DELIVERED -> "Delivered"
                        else -> "Sent"
                    },
                    tint = if (albumStatus == MessageStatus.READ)
                        Color(BlinkChatReferenceStyle.BlueBright)
                    else palette.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
      }
    }
    if (selected in videos.indices) {
        Dialog(onDismissRequest = { selected = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color(BlinkChatReferenceStyle.Canvas))) {
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
