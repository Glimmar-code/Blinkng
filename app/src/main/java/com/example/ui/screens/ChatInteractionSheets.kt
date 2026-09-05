package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.ui.theme.MessagePalette


data class ChatInteractionActions(
    val onReact: (String, ChatMessage, String) -> Unit = { _, _, _ -> },
    val onEdit: (String, ChatMessage, String) -> Unit = { _, _, _ -> },
    val onDeleteForMe: (String, ChatMessage) -> Unit = { _, _ -> },
    val onDeleteForEveryone: (String, ChatMessage) -> Unit = { _, _ -> },
    val onToggleStar: (String, ChatMessage) -> Unit = { _, _ -> },
    val onTogglePin: (String, ChatMessage) -> Unit = { _, _ -> },
    val onReportMessage: (ChatMessage, String) -> Unit = { _, _ -> },
    val onClearConversation: (ChatConversation) -> Unit = {},
    val onMuteConversation: (ChatConversation, Boolean) -> Unit = { _, _ -> },
    val onReportConversation: (ChatConversation, String) -> Unit = { _, _ -> }
)

private val CHAT_REACTIONS = listOf(
    "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇",
    "🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚",
    "😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩",
    "🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣",
    "😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬",
    "🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗",
    "🤔","🫣","🤭","🫢","🫡","🤫","🫠","🤥","😶","😐",
    "😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴",
    "🤤","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕",
    "👍","👎","👏","🙌","🤝","🙏","💪","🔥","❤️","💜"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsSheet(
    message: ChatMessage,
    palette: MessagePalette,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onToggleStar: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Text(
            "React",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(CHAT_REACTIONS) { emoji ->
                val selected = emoji in message.myReactions
                Surface(
                    shape = CircleShape,
                    color = if (selected) palette.accent.copy(alpha = .24f) else palette.glassElevated,
                    border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
                    modifier = Modifier.size(42.dp).clickable { onReaction(emoji) }
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = palette.border)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageActionChip("Reply", Icons.Default.Reply, palette, Modifier.weight(1f), onReply)
                MessageActionChip("Forward", Icons.Default.Forward, palette, Modifier.weight(1f), onForward)
                if (message.isFromMe && !message.deletedForEveryone) {
                    MessageActionChip("Edit", Icons.Default.Edit, palette, Modifier.weight(1f), onEdit)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageActionChip(if (message.isStarred) "Unstar" else "Star", Icons.Default.Star, palette, Modifier.weight(1f), onToggleStar)
                MessageActionChip("Share", Icons.Default.Share, palette, Modifier.weight(1f), onShare)
                MessageActionChip(if (message.isPinned) "Unpin" else "Pin", Icons.Default.Place, palette, Modifier.weight(1f), onTogglePin)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageActionChip("Delete for me", Icons.Default.Delete, palette, Modifier.weight(1f), onDeleteForMe, danger = true)
                if (message.isFromMe) {
                    MessageActionChip("Delete everyone", Icons.Default.ClearAll, palette, Modifier.weight(1f), onDeleteForEveryone, danger = true)
                }
                MessageActionChip("Report", Icons.Default.Report, palette, Modifier.weight(1f), onReport, danger = true)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MessageActionChip(
    label: String,
    icon: ImageVector,
    palette: MessagePalette,
    modifier: Modifier,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Surface(
        color = if (danger) palette.danger.copy(alpha = .10f) else palette.glassElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (danger) palette.danger.copy(alpha = .35f) else palette.border),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) palette.danger else palette.textPrimary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = if (danger) palette.danger else palette.textSecondary, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForwardMessageSheet(
    sourcePartner: String,
    conversations: List<ChatConversation>,
    palette: MessagePalette,
    onForward: (List<ChatConversation>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    val candidates = conversations.filterNot { it.partnerUsername.equals(sourcePartner, true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Forward message", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Choose up to 10 chats • ${selected.size}/10", color = palette.textSecondary, fontSize = 10.sp)
            }
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onForward(candidates.filter { it.id in selected }) }
            ) { Text("Send", color = palette.accent, fontWeight = FontWeight.Bold) }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(candidates, key = { it.id }) { conversation ->
                val checked = conversation.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) selected.remove(conversation.id)
                            else if (selected.size < 10) selected.add(conversation.id)
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (checked) palette.accent else palette.glassElevated,
                        border = BorderStroke(1.dp, if (checked) palette.accent else palette.border),
                        modifier = Modifier.size(28.dp)
                    ) { Box(contentAlignment = Alignment.Center) { if (checked) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) } }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(conversation.partnerName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${conversation.partnerUsername.removePrefix("@")}", color = palette.textSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatOverflowSheet(
    conversation: ChatConversation,
    palette: MessagePalette,
    pinnedOnly: Boolean,
    starredOnly: Boolean,
    onProfile: () -> Unit,
    onSearch: () -> Unit,
    onPinned: () -> Unit,
    onStarred: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Text(conversation.partnerName, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        OverflowRow("View profile", Icons.Default.Person, palette, onProfile)
        OverflowRow("Search in chat", Icons.Default.Search, palette, onSearch)
        OverflowRow(if (pinnedOnly) "Show all messages" else "Pinned messages", Icons.Default.Place, palette, onPinned)
        OverflowRow(if (starredOnly) "Show all messages" else "Starred messages", Icons.Default.Star, palette, onStarred)
        OverflowRow(if (conversation.isMuted) "Unmute notifications" else "Mute notifications", Icons.Default.VolumeOff, palette, onMute)
        HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
        OverflowRow("Delete chat", Icons.Default.Delete, palette, onDelete, danger = true)
        OverflowRow("Report conversation", Icons.Default.Report, palette, onReport, danger = true)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OverflowRow(
    label: String,
    icon: ImageVector,
    palette: MessagePalette,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) palette.danger else palette.textSecondary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, color = if (danger) palette.danger else palette.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

internal fun shareChatMessage(context: Context, message: ChatMessage) {
    val body = message.text.takeIf { it.isNotBlank() }
        ?: message.attachedVideoUrl
        ?: message.attachedImageUrl
        ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share message"))
}
