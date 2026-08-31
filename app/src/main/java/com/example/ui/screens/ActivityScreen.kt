package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ActivityItem
import com.example.data.models.NotificationFilter
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    activities: List<ActivityItem>,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit,
    onNotificationClick: (ActivityItem) -> Unit,
    isDark: Boolean,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {}
) {
    var filter by remember { mutableStateOf(NotificationFilter.ALL) }
    val visible = remember(activities, filter) { if (filter == NotificationFilter.ALL) activities else activities.filter { it.category == filter } }
    val unread = activities.count { it.isUnread }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("Notifications", fontWeight = FontWeight.Bold)
                        Text(if (unread == 0) "You're all caught up" else "$unread unread", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NotificationFilter.values()) { item ->
                    val selected = filter == item
                    FilterChip(selected = selected, onClick = { filter = item }, label = { Text(item.label, fontSize = 12.sp) }, leadingIcon = { Text(item.icon, fontSize = 12.sp) })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = BlinkPink) }
                !errorMessage.isNullOrBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp)); Text("Notifications couldn't load", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp)); Text(errorMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp)); Button(onClick = onRefresh) { Text("Try again") }
                    }
                }
                visible.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = BlinkPink.copy(alpha = .12f), modifier = Modifier.size(72.dp)) { Icon(Icons.Default.Notifications, null, tint = BlinkPink, modifier = Modifier.padding(20.dp)) }
                        Spacer(Modifier.height(14.dp)); Text("No notifications", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp)); Text("Likes, follows, comments and other activity will appear here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { item ->
                        NotificationCard(item, onProfileClick, onNotificationClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: ActivityItem, onProfileClick: (String) -> Unit, onNotificationClick: (ActivityItem) -> Unit) {
    val accent = when (item.category) { NotificationFilter.LIKES -> BlinkPink; NotificationFilter.COMMENTS -> BlinkPurple; NotificationFilter.MARKET -> Color(0xFF22C55E); NotificationFilter.ALL -> BlinkPink }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (item.isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (item.isUnread) accent.copy(alpha = .32f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { onNotificationClick(item) }
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { onProfileClick(item.user) }) {
                AsyncImage(model = item.avatar, contentDescription = item.user, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Surface(shape = CircleShape, color = accent, modifier = Modifier.size(19.dp).align(Alignment.BottomEnd)) { Text(item.category.icon, fontSize = 9.sp, modifier = Modifier.padding(4.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${item.user}", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onProfileClick(item.user) })
                    Spacer(Modifier.width(4.dp)); VerifiedMark(item.verificationBadge, size = 12.dp)
                }
                Spacer(Modifier.height(3.dp)); Text(item.action, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                if (!item.previewText.isNullOrBlank()) { Spacer(Modifier.height(3.dp)); Text(item.previewText!!, maxLines = 2, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(5.dp)); Text(item.time, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.isUnread) Surface(shape = CircleShape, color = accent, modifier = Modifier.size(9.dp)) {}
        }
    }
}
