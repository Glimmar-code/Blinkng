package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.BlinkDesignTokens
import com.blinkng.shared.BlinkChatReferenceStyle
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.ui.theme.MessagePalette
import com.example.ui.theme.MessageThemeMode

/** Telegram-style single caption + tightly spaced responsive photo tiles. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BlinkChatPhotoAlbumBubble(
    messages: List<ChatMessage>,
    palette: MessagePalette,
    onOpen: (ChatMessage) -> Unit,
    onReply: () -> Unit,
    onActions: () -> Unit
) {
    if (messages.isEmpty()) return
    val ordered = remember(messages) {
        messages.sortedWith(compareBy<ChatMessage> { it.imageAlbumPosition }.thenBy { it.id })
    }
    val anchor = ordered.first()
    val isMine = anchor.isFromMe
    val groupedCount = maxOf(anchor.imageAlbumSize, ordered.size)
    val visible = ordered.take(4)
    val caption = ordered.firstNotNullOfOrNull { photo ->
        photo.text.takeUnless { it.isBlank() || it.equals("Image", true) || it.equals("Photo", true) }
    }.orEmpty()
    val status = when {
        ordered.any { it.status == MessageStatus.FAILED } -> MessageStatus.FAILED
        ordered.any { it.status == MessageStatus.SENDING } -> MessageStatus.SENDING
        ordered.all { it.status == MessageStatus.READ } -> MessageStatus.READ
        ordered.all { it.status in setOf(MessageStatus.READ, MessageStatus.DELIVERED) } -> MessageStatus.DELIVERED
        else -> MessageStatus.SENT
    }
    val corner = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement =
        if (isMine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 306.dp).clip(corner)
                .background(if (palette.mode == MessageThemeMode.DARK && caption.isBlank())
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color.Transparent, Color.Transparent)
                        )
                    else if (isMine) palette.outgoingBrush()
                    else androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(palette.incomingBubble, palette.incomingBubble)
                    ))
                .border(1.dp,
                    if (palette.mode == MessageThemeMode.DARK) Color.Transparent
                    else palette.border.copy(alpha = .5f), corner)
                .combinedClickable(onClick = onReply, onLongClick = onActions)
                .padding(4.dp)
        ) {
            // Match the supplied 1 / 2 / 3 / 4-photo reference layouts.
            // A trio is one wide hero photo above two equal thumbnails, not
            // a half-empty 2×2 grid. Every tile still opens the existing viewer.
            when (visible.size) {
                1 -> AlbumPhotoTile(visible[0], 0, groupedCount,
                    Modifier.fillMaxWidth().height(196.dp), onOpen)
                2 -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    visible.forEachIndexed { index, photo ->
                        AlbumPhotoTile(photo, index, groupedCount,
                            Modifier.weight(1f).height(172.dp), onOpen)
                    }
                }
                3 -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // The current screenshot uses three equally sized adjacent photos.
                    visible.forEachIndexed { index, photo ->
                        AlbumPhotoTile(photo, index, groupedCount,
                            Modifier.weight(1f).height(112.dp), onOpen)
                    }
                }
                else -> {
                    repeat(2) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            (row * 2 until minOf((row + 1) * 2, visible.size))
                                .forEach { index ->
                                    AlbumPhotoTile(visible[index], index, groupedCount,
                                        Modifier.weight(1f).height(128.dp), onOpen)
                                }
                        }
                        if (row == 0) Spacer(Modifier.height(3.dp))
                    }
                }
            }
            if (caption.isNotBlank()) {
                var expanded by remember(anchor.imageAlbumId) { mutableStateOf(false) }
                Text(
                    text = caption,
                    color = if (isMine) palette.outgoingText else palette.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                )
                if (!expanded && caption.length > 180) Text(
                    "Read more",
                    color = palette.accent,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { expanded = true }.padding(start = 6.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(end = 7.dp, bottom = 4.dp, top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (status == MessageStatus.SENDING) "Sending…" else if (status == MessageStatus.FAILED)
                    "Waiting to retry" else ordered.last().timestamp,
                    color = if (status == MessageStatus.FAILED) palette.danger
                        else if (isMine && caption.isNotBlank() && palette.mode == MessageThemeMode.DARK)
                            palette.outgoingText.copy(alpha = .55f)
                        else palette.textSecondary,
                    fontSize = 10.sp
                )
                if (isMine && status !in setOf(MessageStatus.SENDING, MessageStatus.FAILED)) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        if (status == MessageStatus.SENT) Icons.Default.Check else Icons.Default.DoneAll,
                        contentDescription = when(status) {
                            MessageStatus.READ -> "Read"
                            MessageStatus.DELIVERED -> "Delivered"
                            else -> "Sent"
                        },
                        tint = if (status == MessageStatus.READ) Color(0xFF1685FF)
                            else if (isMine && caption.isNotBlank() && palette.mode == MessageThemeMode.DARK)
                                palette.outgoingText.copy(alpha = .55f) else palette.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/** Single media cell keeps download overlays and "+N" behavior consistent. */
@Composable
private fun AlbumPhotoTile(
    photo: ChatMessage,
    position: Int,
    total: Int,
    modifier: Modifier,
    onOpen: (ChatMessage) -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(Color(BlinkChatReferenceStyle.Incoming))
            .clickable { onOpen(photo) },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = photo.imagePreviewUrl ?: photo.attachedImageUrl
                ?: photo.localAttachmentUri,
            contentDescription = "Photo ${position + 1} of $total",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (position == 0 && total > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = .65f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(total.toString(), color = Color.White, fontSize = 11.sp)
            }
        }
        if (position == 3 && total > 4) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = .60f)),
                contentAlignment = Alignment.Center
            ) {
                Text("+${total - 4}", color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
        if (photo.status == MessageStatus.SENDING) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = .65f))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text("${photo.uploadProgress ?: 0}%", color = Color.White,
                    fontSize = 10.sp)
            }
        }
    }
}
