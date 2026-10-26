package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import com.example.data.models.ChatConversation
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile

/** Real DM recipients, not simulated share suggestions. Every action is user initiated. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlinkNativeShareSheet(
    post: FeedPost,
    recentChats: List<ChatConversation>,
    suggestedUsers: List<UserProfile>,
    currentUsername: String,
    onDismiss: () -> Unit,
    onSendToUser: (String) -> Unit,
    onCopyLink: () -> Unit,
    onStory: () -> Unit,
    onWhatsApp: () -> Unit,
    onOtherApps: () -> Unit,
) {
    var search by remember(post.id) { mutableStateOf("") }
    val recents = remember(recentChats) {
        recentChats.filter { !it.isGroup && it.partnerUsername.isNotBlank() }
            .distinctBy { it.partnerUsername.lowercase() }.take(10)
    }
    val known = recents.map { it.partnerUsername.lowercase() }.toSet()
    val suggestions = suggestedUsers.filter {
        it.username.isNotBlank() && !it.username.equals(currentUsername, true) &&
            it.username.lowercase() !in known
    }.distinctBy { it.username.lowercase() }.take(12)
    val people = (recents.map { Triple(it.partnerUsername, it.partnerName, it.partnerAvatar) } +
        suggestions.map { Triple(it.username, it.fullName, it.avatarUrl) })
        .filter { search.isBlank() || it.first.contains(search, true) || it.second.contains(search, true) }
        .distinctBy { it.first.lowercase() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = Color(0xFF130E21),
        contentColor = Color.White
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (post.isReel) "Share Reel" else "Share Post",
                fontWeight = FontWeight.Black, fontSize = 21.sp)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = post.images.firstOrNull() ?: post.authorAvatar,
                    contentDescription = "Content preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF302740))
                )
                Column(Modifier.weight(1f)) {
                    Text(post.author, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(post.text.ifBlank { "Open on BLINK" },
                        color = Color(0xFFC1B9CF), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
            }
            OutlinedTextField(value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search BLINK users") },
                shape = RoundedCornerShape(15.dp))
            if (people.isNotEmpty()) {
                Text(if (search.isNotBlank()) "People" else "Recent chats & suggested",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(people, key = { it.first }) { person ->
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { onSendToUser(person.first) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(model = person.third, contentDescription = "Send to @${person.first}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(50.dp).clip(CircleShape)
                                    .background(Color(0xFF392661)))
                            Text(person.second.ifBlank { person.first },
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Text("No matching people", color = Color(0xFFC1B9CF), fontSize = 12.sp)
            }
            HorizontalDivider(color = Color.White.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    Triple(Icons.Rounded.ContentCopy, "Copy Link", onCopyLink),
                    Triple(Icons.Rounded.AutoStories, "Add to Story", onStory),
                    Triple(Icons.Rounded.Chat, "WhatsApp", onWhatsApp),
                    Triple(Icons.Rounded.Share, "Other Apps", onOtherApps)
                ).forEach { (icon, label, action) ->
                    Column(Modifier.width(77.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = action).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(46.dp).clip(CircleShape)
                            .background(Color(0xFF34225D)), contentAlignment = Alignment.Center) {
                            Icon(icon, label, tint = Color.White)
                        }
                        Text(label, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
