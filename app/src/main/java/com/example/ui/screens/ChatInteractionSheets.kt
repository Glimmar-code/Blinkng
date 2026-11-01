package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class AttachmentKind { Camera, Gallery, File, Contact, Location, Poll, Audio, Sticker }

enum class MessageAction {
    Reply, Copy, Forward, Star, Edit, Share, Report, DeleteForMe, DeleteForEveryone
}

private val SheetColor = Color(0xFF151518)

@Composable
private fun PopIn(delayMs: Long, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val s = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        s.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    Box(
        modifier.graphicsLayer {
            scaleX = s.value; scaleY = s.value; alpha = s.value.coerceIn(0f, 1f)
        }
    ) { content() }
}

/* ───────────────────────── Attachment sheet ───────────────────────── */

private data class AttachTile(
    val kind: AttachmentKind, val label: String, val icon: ImageVector, val color: Color,
)

private val attachTiles = listOf(
    AttachTile(AttachmentKind.Camera, "Camera", Icons.Filled.CameraAlt, Color(0xFFFF5C5C)),
    AttachTile(AttachmentKind.Gallery, "Gallery", Icons.Filled.PhotoLibrary, Color(0xFF8B5CF6)),
    AttachTile(AttachmentKind.File, "File", Icons.Filled.InsertDriveFile, Color(0xFF3B82F6)),
    AttachTile(AttachmentKind.Contact, "Contact", Icons.Filled.Person, Color(0xFF06B6D4)),
    AttachTile(AttachmentKind.Location, "Location", Icons.Filled.LocationOn, Color(0xFF22C55E)),
    AttachTile(AttachmentKind.Poll, "Poll", Icons.Filled.Poll, Color(0xFFF59E0B)),
    AttachTile(AttachmentKind.Audio, "Audio", Icons.Filled.Headphones, Color(0xFFF97316)),
    AttachTile(AttachmentKind.Sticker, "Sticker", Icons.Filled.StickyNote2, Color(0xFFEC4899)),
)

/** Telegram-style in-app attachment panel (replaces opening the raw Android picker). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(onDismiss: () -> Unit, onPick: (AttachmentKind) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        contentColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .padding(bottom = 20.dp).navigationBarsPadding()
        ) {
            Text("Share", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(20.dp))
            attachTiles.chunked(4).forEachIndexed { row, chunk ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    chunk.forEachIndexed { col, tile ->
                        PopIn(delayMs = (row * 4 + col) * 40L, modifier = Modifier.width(72.dp)) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable { onPick(tile.kind) }.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(58.dp).clip(CircleShape).background(tile.color),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(tile.icon, tile.label, tint = Color.White, modifier = Modifier.size(28.dp)) }
                                Spacer(Modifier.height(8.dp))
                                Text(tile.label, fontSize = 13.sp, color = Color(0xFFD0D0D4))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/* ───────────────────────── Message action sheet ───────────────────────── */

private val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onAction: (MessageAction) -> Unit,
) {
    val isText = message.content is ChatContent.Text
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding()) {
            // reactions
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(28.dp)).background(BlinkChatColors.PanelRaised)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                quickReactions.forEachIndexed { i, e ->
                    PopIn(delayMs = i * 35L) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).clickable { onReact(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 26.sp) }
                    }
                }
                PopIn(delayMs = quickReactions.size * 35L) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF38383D))
                            .clickable { onReact("➕") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, "More reactions", tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // preview
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(BlinkChatColors.Panel).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    message.snippet(), color = Color(0xFFC8C8CC), fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            ActionRow(Icons.Filled.Reply, "Reply") { onAction(MessageAction.Reply) }
            if (isText) ActionRow(Icons.Filled.ContentCopy, "Copy") { onAction(MessageAction.Copy) }
            ActionRow(Icons.Filled.Forward, "Forward") { onAction(MessageAction.Forward) }
            ActionRow(
                if (message.starred) Icons.Filled.StarBorder else Icons.Filled.Star,
                if (message.starred) "Unstar" else "Star",
            ) { onAction(MessageAction.Star) }
            if (message.outgoing && isText) ActionRow(Icons.Filled.Edit, "Edit") { onAction(MessageAction.Edit) }
            ActionRow(Icons.Filled.Share, "Share") { onAction(MessageAction.Share) }
            if (!message.outgoing) ActionRow(Icons.Filled.Flag, "Report", danger = true) { onAction(MessageAction.Report) }
            ActionRow(Icons.Filled.Delete, "Delete for me", danger = true) { onAction(MessageAction.DeleteForMe) }
            if (message.outgoing) {
                ActionRow(Icons.Filled.DeleteForever, "Delete for everyone", danger = true) {
                    onAction(MessageAction.DeleteForEveryone)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) BlinkChatColors.Danger else Color.White
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}
