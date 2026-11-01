package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class ChatExtraAction {
    ViewProfile, Search, Media, Starred, ToggleMute, Wallpaper, ClearChat, Block, Report
}

/** Three-dot menu: springs out of the top-right corner. Destructive actions ask for confirmation. */
@Composable
fun BlinkChatExtrasDialog(
    conversation: BlinkConversation,
    onDismiss: () -> Unit,
    onAction: (ChatExtraAction) -> Unit,
) {
    var confirm by remember { mutableStateOf<ChatExtraAction?>(null) }
    val scale = remember { Animatable(0.82f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    fun pick(a: ChatExtraAction) {
        if (a == ChatExtraAction.ClearChat || a == ChatExtraAction.Block) confirm = a
        else { onAction(a); onDismiss() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss).statusBarsPadding()) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 12.dp)
                    .widthIn(min = 240.dp)
                    .graphicsLayer {
                        scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF202024))
                    .clickable(enabled = false) {}
                    .padding(vertical = 8.dp)
            ) {
                MenuItem(Icons.Filled.Person, "View profile") { pick(ChatExtraAction.ViewProfile) }
                MenuItem(Icons.Filled.Search, "Search") { pick(ChatExtraAction.Search) }
                MenuItem(Icons.Filled.PhotoLibrary, "Media, links and docs") { pick(ChatExtraAction.Media) }
                MenuItem(Icons.Filled.Star, "Starred messages") { pick(ChatExtraAction.Starred) }
                MenuItem(
                    if (conversation.muted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                    if (conversation.muted) "Unmute notifications" else "Mute notifications",
                ) { pick(ChatExtraAction.ToggleMute) }
                MenuItem(Icons.Filled.Wallpaper, "Wallpaper") { pick(ChatExtraAction.Wallpaper) }
                MenuItem(Icons.Filled.Delete, "Clear chat", danger = true) { pick(ChatExtraAction.ClearChat) }
                MenuItem(Icons.Filled.Block, "Block", danger = true) { pick(ChatExtraAction.Block) }
                MenuItem(Icons.Filled.Flag, "Report", danger = true) { pick(ChatExtraAction.Report) }
            }
        }
    }

    confirm?.let { action ->
        val isBlock = action == ChatExtraAction.Block
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Color(0xFF202024),
            title = { Text(if (isBlock) "Block ${conversation.name}?" else "Clear this chat?", color = Color.White) },
            text = {
                Text(
                    if (isBlock) "They won't be able to message or call you."
                    else "All messages in this chat will be removed from your device.",
                    color = Color(0xFFC8C8CC),
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(action); confirm = null; onDismiss() }) {
                    Text(if (isBlock) "Block" else "Clear", color = BlinkChatColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) BlinkChatColors.Danger else Color.White
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}
