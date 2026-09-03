package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import com.example.ui.theme.BlinkGold
import androidx.compose.animation.core.animateFloat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.data.models.VerificationBadge
import com.example.ui.components.FacultyBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun MessagesConnectionNotice() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Messages are offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "Your recent conversations stay visible. Reconnect to send or receive new messages.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatConnectionNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Offline — reconnect to send messages.",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ============================================================================
// MESSAGES HOME
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    conversations: List<ChatConversation>,
    activePartner: String?,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    isConnected: Boolean = true
) {

    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    var selectedFilter by rememberSaveable {
        mutableStateOf("All")
    }

    var showSearch by rememberSaveable {
        mutableStateOf(false)
    }

    var showNewChat by rememberSaveable {
        mutableStateOf(false)
    }

    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    var compactMode by rememberSaveable {
        mutableStateOf(false)
    }

    val onlineCount =
        conversations.count {
            it.isOnline
        }

    val unreadCount =
        conversations.sumOf {
            it.unreadCount
        }

    val filteredConversations =
        remember(
            conversations,
            searchQuery,
            selectedFilter,
            compactMode
        ) {

            conversations.filter { conversation ->

                val matchesSearch =
                    searchQuery.isBlank() ||
                            conversation.partnerName
                                .contains(
                                    searchQuery,
                                    ignoreCase = true
                                ) ||
                            conversation.partnerUsername
                                .contains(
                                    searchQuery,
                                    ignoreCase = true
                                ) ||
                            conversation.lastMessage
                                .contains(
                                    searchQuery,
                                    ignoreCase = true
                                )

                val matchesFilter =
                    when (selectedFilter) {

                        "Unread" ->
                            conversation.unreadCount > 0

                        "Online" ->
                            conversation.isOnline

                        "Sellers" ->
                            conversation.partnerUsername
                                .contains(
                                    "shop",
                                    ignoreCase = true
                                )

                        "Groups" ->
                            conversation.partnerName
                                .contains(
                                    "group",
                                    ignoreCase = true
                                )

                        else ->
                            true
                    }

                matchesSearch && matchesFilter
            }
        }

    val listState =
        rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("messages_screen")
    ) {

        LazyColumn(
            state = listState,
            contentPadding =
                PaddingValues(
                    bottom = 130.dp
                ),
            modifier =
                Modifier.fillMaxSize()
        ) {

            item(
                key = "messages_header"
            ) {

                MessagesHeader(
                    totalChats = conversations.size,
                    unreadCount = unreadCount,
                    onlineCount = onlineCount,
                    showSearch = showSearch,
                    onToggleSearch = {
                        showSearch = !showSearch
                    },
                    onNewChat = {
                        showNewChat = true
                    },
                    onSettings = {
                        showSettings = true
                    }
                )
            }

            if (!isConnected) {
                item(key = "messages_connection_notice") {
                    MessagesConnectionNotice()
                }
            }

            if (showSearch) {

                item(
                    key = "message_search"
                ) {

                    PremiumMessageSearch(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                        },
                        onClear = {
                            searchQuery = ""
                        }
                    )
                }
            }

            item(
                key = "online_users"
            ) {

                OnlineStudentsRail(
                    conversations = conversations,
                    onOpenConversation =
                        onOpenConversation,
                    onProfileClick =
                        onProfileClick
                )
            }

            item(
                key = "filters"
            ) {

                MessageFilterRail(
                    selectedFilter =
                        selectedFilter,
                    onSelected = {
                        selectedFilter = it
                    }
                )
            }

            item(
                key = "inbox_summary"
            ) {

                InboxSummaryCard(
                    total = conversations.size,
                    unread = unreadCount,
                    online = onlineCount
                )
            }

            item(
                key = "section_header"
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 18.dp,
                                end = 17.dp,
                                top = 12.dp,
                                bottom = 6.dp
                            ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = when {
                            selectedFilter == "Unread" ->
                                "Unread messages"

                            selectedFilter == "Online" ->
                                "Online conversations"

                            selectedFilter == "Sellers" ->
                                "Marketplace conversations"

                            selectedFilter == "Groups" ->
                                "Group conversations"

                            else ->
                                "All messages"
                        },
                        fontSize = 15.sp,
                        fontWeight =
                            FontWeight.Black
                    )

                    Spacer(
                        modifier =
                            Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            compactMode = !compactMode
                        }
                    ) {

                        Icon(
                            imageVector =
                                if (compactMode)
                                    Icons.Default.ViewAgenda
                                else
                                    Icons.Default.ViewCompact,
                            contentDescription =
                                "Toggle conversation density"
                        )
                    }
                }
            }

            if (filteredConversations.isEmpty()) {

                item(
                    key = "empty_messages"
                ) {

                    EmptyMessagesState(
                        searching =
                            searchQuery.isNotBlank(),
                        onClear = {
                            searchQuery = ""
                            selectedFilter = "All"
                        },
                        onStartChat = {
                            showNewChat = true
                        }
                    )
                }

            } else {

                items(
                    items = filteredConversations,
                    key = {
                        it.id
                    }
                ) { conversation ->

                    ConversationListItem(
                        conversation = conversation,
                        compact = compactMode,
                        onClick = {
                            onOpenConversation(
                                conversation.partnerUsername
                            )
                        },
                        onProfileClick = {
                            onProfileClick(
                                conversation.partnerUsername
                            )
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible =
                unreadCount > 0,
            modifier =
                Modifier
                    .align(
                        Alignment.BottomEnd
                    )
                    .navigationBarsPadding()
                    .padding(
                        end = 18.dp,
                        bottom = 88.dp
                    ),
            enter =
                scaleIn() +
                        fadeIn(),
            exit =
                scaleOut() +
                        fadeOut()
        ) {

            ScrollToUnreadButton(
                unreadCount = unreadCount,
                onClick = {
                    scopeAnimateToUnread(
                        listState
                    )
                }
            )
        }
    }

    if (showNewChat) {

        NewChatSheet(
            conversations = conversations,
            onDismiss = {
                showNewChat = false
            },
            onOpenConversation = {
                showNewChat = false
                onOpenConversation(it)
            },
            onProfileClick = onProfileClick
        )
    }

    if (showSettings) {

        MessagesSettingsSheet(
            onDismiss = {
                showSettings = false
            }
        )
    }
}

// ============================================================================
// HEADER
// ============================================================================

@Composable
private fun MessagesHeader(
    totalChats: Int,
    unreadCount: Int,
    onlineCount: Int,
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 18.dp,
                    end = 14.dp,
                    top = 48.dp,
                    bottom = 8.dp
                )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Surface(
                shape = CircleShape,
                color =
                    BlinkPink.copy(
                        alpha = 0.10f
                    )
            ) {

                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = "Messages",
                    tint = BlinkPink,
                    modifier =
                        Modifier.padding(
                            10.dp
                        )
                )
            }

            Spacer(
                modifier =
                    Modifier.width(9.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = "Messages",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            "$totalChats chats",
                        fontSize = 9.5.sp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(5.dp)
                                .background(
                                    BlinkOnlineGreen,
                                    CircleShape
                                )
                    )

                    Spacer(
                        modifier =
                            Modifier.width(4.dp)
                    )

                    Text(
                        "$onlineCount online",
                        fontSize = 9.5.sp,
                        color =
                            BlinkOnlineGreen
                    )
                }
            }

            IconButton(
                onClick = onToggleSearch
            ) {

                Icon(
                    if (showSearch)
                        Icons.Default.Close
                    else
                        Icons.Default.Search,
                    contentDescription =
                        "Search messages"
                )
            }

            IconButton(
                onClick = onNewChat,
                modifier =
                    Modifier.testTag(
                        "new_chat_button"
                    )
            ) {

                Icon(
                    Icons.Default.Edit,
                    contentDescription =
                        "New chat"
                )
            }

            IconButton(
                onClick = onSettings
            ) {

                Icon(
                    Icons.Default.MoreVert,
                    contentDescription =
                        "Message settings"
                )
            }
        }

        if (unreadCount > 0) {

            Spacer(
                modifier =
                    Modifier.height(9.dp)
            )

            Surface(
                shape =
                    RoundedCornerShape(
                        100.dp
                    ),
                color =
                    BlinkPink.copy(
                        alpha = 0.10f
                    )
            ) {

                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        Icons.Default.MarkEmailUnread,
                        contentDescription = null,
                        tint = BlinkPink,
                        modifier =
                            Modifier.size(
                                13.dp
                            )
                    )

                    Spacer(
                        modifier =
                            Modifier.width(4.dp)
                    )

                    Text(
                        "$unreadCount unread messages",
                        color = BlinkPink,
                        fontSize = 9.5.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// SEARCH
// ============================================================================

@Composable
private fun PremiumMessageSearch(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 15.dp,
                    vertical = 4.dp
                ),
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant
                .copy(alpha = 0.55f),
        shape =
            RoundedCornerShape(
                100.dp
            )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            modifier =
                Modifier.padding(
                    horizontal = 10.dp
                )
        ) {

            Icon(
                Icons.Default.Search,
                contentDescription =
                    "Search",
                tint =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        "Search people, messages, usernames...",
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        imeAction =
                            ImeAction.Search
                    ),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor =
                            Color.Transparent,
                        unfocusedContainerColor =
                            Color.Transparent,
                        focusedIndicatorColor =
                            Color.Transparent,
                        unfocusedIndicatorColor =
                            Color.Transparent
                    ),
                modifier =
                    Modifier.weight(1f)
            )

            AnimatedVisibility(
                visible = value.isNotBlank()
            ) {

                IconButton(
                    onClick = onClear
                ) {

                    Icon(
                        Icons.Default.Clear,
                        contentDescription =
                            "Clear search"
                    )
                }
            }
        }
    }
}

// ============================================================================
// ONLINE RAIL
// ============================================================================

@Composable
private fun OnlineStudentsRail(
    conversations: List<ChatConversation>,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit
) {

    val online =
        conversations.filter {
            it.isOnline
        }

    if (online.isEmpty()) {
        return
    }

    Column(
        modifier =
            Modifier.padding(
                top = 5.dp
            )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                "Online now",
                fontSize = 12.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.width(5.dp)
            )

            Surface(
                shape =
                    RoundedCornerShape(
                        100.dp
                    ),
                color =
                    BlinkOnlineGreen.copy(
                        alpha = 0.10f
                    )
            ) {

                Text(
                    "${online.size}",
                    color =
                        BlinkOnlineGreen,
                    fontSize = 8.sp,
                    fontWeight =
                        FontWeight.Bold,
                    modifier =
                        Modifier.padding(
                            horizontal = 6.dp,
                            vertical = 3.dp
                        )
                )
            }
        }

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 15.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            items(
                online,
                key = {
                    "online_${it.id}"
                }
            ) { conversation ->

                OnlineStudentItem(
                    conversation = conversation,
                    onClick = {
                        onOpenConversation(
                            conversation.partnerUsername
                        )
                    },
                    onProfileClick = {
                        onProfileClick(
                            conversation.partnerUsername
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun OnlineStudentItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    onProfileClick: () -> Unit
) {

    val pulse =
        rememberInfiniteTransition(
            label = "online_avatar_pulse"
        )

    val ringAlpha by
        pulse.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.8f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(1100),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label = "online_ring_alpha"
        )

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(62.dp)
    ) {

        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .clickable {
                        onClick()
                    }
        ) {

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(
                            2.dp,
                            BlinkOnlineGreen.copy(
                                alpha =
                                    ringAlpha
                            ),
                            CircleShape
                        )
                        .padding(2.dp)
            ) {

                AsyncImage(
                    model =
                        conversation.partnerAvatar,
                    contentDescription =
                        conversation.partnerName,
                    contentScale =
                        ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(
                                CircleShape
                            )
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(13.dp)
                        .align(
                            Alignment.BottomEnd
                        )
                        .background(
                            BlinkOnlineGreen,
                            CircleShape
                        )
                        .border(
                            2.dp,
                            MaterialTheme
                                .colorScheme
                                .surface,
                            CircleShape
                        )
            )
        }

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            conversation.partnerName,
            fontSize = 9.sp,
            fontWeight =
                FontWeight.SemiBold,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// FILTERS
// ============================================================================

@Composable
private fun MessageFilterRail(
    selectedFilter: String,
    onSelected: (String) -> Unit
) {

    val filters =
        listOf(
            "All",
            "Unread",
            "Online",
            "Sellers",
            "Groups"
        )

    LazyRow(
        contentPadding =
            PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(7.dp)
    ) {

        items(filters) { filter ->

            FilterChip(
                selected =
                    selectedFilter == filter,
                onClick = {
                    onSelected(filter)
                },
                label = {
                    Text(
                        filter,
                        fontSize = 10.sp
                    )
                }
            )
        }
    }
}

// ============================================================================
// SUMMARY
// ============================================================================

@Composable
private fun InboxSummaryCard(
    total: Int,
    unread: Int,
    online: Int
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 5.dp
                ),
        shape =
            RoundedCornerShape(
                19.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.36f)
            )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    11.dp
                ),
            horizontalArrangement =
                Arrangement.SpaceEvenly
        ) {

            MessageStat(
                icon =
                    Icons.Default.Chat,
                value =
                    total.toString(),
                label = "Chats"
            )

            MessageStat(
                icon =
                    Icons.Default.MarkEmailUnread,
                value =
                    unread.toString(),
                label = "Unread"
            )

            MessageStat(
                icon =
                    Icons.Default.Circle,
                value =
                    online.toString(),
                label = "Online",
                tint =
                    BlinkOnlineGreen
            )

            MessageStat(
                icon =
                    Icons.Default.Security,
                value = "24/7",
                label = "Private"
            )
        }
    }
}

@Composable
private fun MessageStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color = BlinkPink
) {

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier.size(16.dp)
        )

        Spacer(
            modifier =
                Modifier.height(2.dp)
        )

        Text(
            value,
            fontWeight =
                FontWeight.Bold,
            fontSize = 11.sp
        )

        Text(
            label,
            fontSize = 8.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}

// ============================================================================
// CONVERSATION ITEM
// ============================================================================

@Composable
private fun ConversationListItem(
    conversation: ChatConversation,
    compact: Boolean,
    onClick: () -> Unit,
    onProfileClick: () -> Unit
) {

    var pressed by rememberSaveable(
        conversation.id
    ) {
        mutableStateOf(false)
    }

    val scale by animateFloatAsState(
        targetValue =
            if (pressed)
                0.975f
            else
                1f,
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy
            ),
        label = "conversation_scale"
    )

    LaunchedEffect(pressed) {

        if (pressed) {
            delay(140)
            pressed = false
        }
    }

    val unread =
        conversation.unreadCount > 0

    Card(
        shape =
            RoundedCornerShape(
                if (compact)
                    14.dp
                else
                    18.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (unread)
                        BlinkPink.copy(
                            alpha = 0.045f
                        )
                    else
                        MaterialTheme
                            .colorScheme
                            .surface
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    if (unread)
                        1.5.dp
                    else
                        0.dp
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .padding(
                    horizontal =
                        if (compact)
                            18.dp
                        else
                            15.dp,
                    vertical = 3.dp
                )
                .clickable {
                    pressed = true
                    onClick()
                }
                .testTag(
                    "conversation_item_${conversation.partnerUsername}"
                )
                .animateContentSize(
                    animationSpec =
                        tween(
                            220,
                            easing =
                                FastOutSlowInEasing
                        )
                )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    if (compact)
                        10.dp
                    else
                        12.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            ConversationAvatar(
                conversation =
                    conversation,
                onClick =
                    onProfileClick
            )

            Spacer(
                modifier =
                    Modifier.width(
                        if (compact)
                            9.dp
                        else
                            11.dp
                    )
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        conversation.partnerName,
                        fontSize =
                            if (compact)
                                13.sp
                            else
                                14.sp,
                        fontWeight =
                            if (unread)
                                FontWeight.Black
                            else
                                FontWeight.Bold,
                        modifier =
                            Modifier.weight(1f),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    if (
                        conversation.verificationBadge !=
                        VerificationBadge.NONE
                    ) {

                        VerifiedMark(
                            badge =
                                conversation.verificationBadge,
                            size =
                                12.dp
                        )

                    } else if (
                        conversation.isVerified
                    ) {

                        VerifiedMark(
                            badge =
                                VerificationBadge.BLUE,
                            size =
                                12.dp
                        )
                    }

                    if (
                        conversation.isOnline
                    ) {

                        Spacer(
                            modifier =
                                Modifier.width(5.dp)
                        )

                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        BlinkOnlineGreen,
                                        CircleShape
                                    )
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(6.dp)
                    )

                    Text(
                        conversation.lastMessageTime,
                        fontSize = 9.sp,
                        color =
                            if (unread)
                                BlinkPink
                            else
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                        fontWeight =
                            if (unread)
                                FontWeight.Bold
                            else
                                FontWeight.Normal
                    )
                }

                if (!compact) {

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )
                }

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        conversation.lastMessage,
                        fontSize =
                            if (compact)
                                11.sp
                            else
                                12.sp,
                        color =
                            if (unread)
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                            else
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                        fontWeight =
                            if (unread)
                                FontWeight.SemiBold
                            else
                                FontWeight.Normal,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        modifier =
                            Modifier.weight(1f)
                    )

                    if (unread) {

                        Spacer(
                            modifier =
                                Modifier.width(6.dp)
                        )

                        Surface(
                            shape = CircleShape,
                            color = BlinkPink
                        ) {

                            Text(
                                text =
                                    if (
                                        conversation.unreadCount >
                                        99
                                    )
                                        "99+"
                                    else
                                        conversation
                                            .unreadCount
                                            .toString(),
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight =
                                    FontWeight.Black,
                                modifier =
                                    Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 4.dp
                                    )
                            )
                        }
                    }
                }

                if (!compact) {

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                5.dp
                            )
                    ) {

                        CompactContextChip(
                            icon =
                                if (
                                    conversation.isOnline
                                )
                                    Icons.Default.Circle
                                else
                                    Icons.Default.Schedule,
                            text =
                                if (
                                    conversation.isOnline
                                )
                                    "Online"
                                else
                                    "Chat",
                            tint =
                                if (
                                    conversation.isOnline
                                )
                                    BlinkOnlineGreen
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription =
                    "Open conversation",
                tint =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.size(16.dp)
            )
        }
    }
}

// ============================================================================
// AVATAR
// ============================================================================

@Composable
private fun ConversationAvatar(
    conversation: ChatConversation,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .size(52.dp)
                .clickable {
                    onClick()
                }
    ) {

        AsyncImage(
            model =
                conversation.partnerAvatar,
            contentDescription =
                conversation.partnerName,
            contentScale =
                ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
        )

        if (conversation.isOnline) {

            Box(
                modifier =
                    Modifier
                        .size(13.dp)
                        .align(
                            Alignment.BottomEnd
                        )
                        .background(
                            BlinkOnlineGreen,
                            CircleShape
                        )
                        .border(
                            2.dp,
                            MaterialTheme
                                .colorScheme
                                .surface,
                            CircleShape
                        )
            )
        }
    }
}

// ============================================================================
// COMPACT CHIP
// ============================================================================

@Composable
private fun CompactContextChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier.size(8.dp)
        )

        Spacer(
            modifier =
                Modifier.width(2.dp)
        )

        Text(
            text,
            fontSize = 7.5.sp,
            color = tint
        )
    }
}

// ============================================================================
// EMPTY STATE
// ============================================================================

@Composable
private fun EmptyMessagesState(
    searching: Boolean,
    onClear: () -> Unit,
    onStartChat: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 32.dp,
                    vertical = 45.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Surface(
            shape = CircleShape,
            color =
                BlinkPink.copy(
                    alpha = 0.10f
                )
        ) {

            Icon(
                imageVector =
                    if (searching)
                        Icons.Default.SearchOff
                    else
                        Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = BlinkPink,
                modifier =
                    Modifier.padding(
                        18.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.height(15.dp)
        )

        Text(
            text =
                if (searching)
                    "No conversations found"
                else
                    "Your inbox is empty",
            fontSize = 18.sp,
            fontWeight =
                FontWeight.Black
        )

        Spacer(
            modifier =
                Modifier.height(5.dp)
        )

        Text(
            text =
                if (searching)
                    "Try another student name, username, or keyword."
                else
                    "Start a conversation with someone on campus.",
            fontSize = 11.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        if (searching) {

            OutlinedButton(
                onClick = onClear
            ) {

                Text("Clear search")
            }

        } else {

            Button(
                onClick = onStartChat,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                BlinkPink
                        )
            ) {

                Icon(
                    Icons.Default.Edit,
                    contentDescription = null
                )

                Spacer(
                    modifier =
                        Modifier.width(5.dp)
                )

                Text("Start a chat")
            }
        }
    }
}

// ============================================================================
// SCROLL BUTTON
// ============================================================================

@Composable
private fun ScrollToUnreadButton(
    unreadCount: Int,
    onClick: () -> Unit
) {

    val pulse =
        rememberInfiniteTransition(
            label = "unread_button"
        )

    val scale by
        pulse.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.03f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(850),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label =
                "unread_button_scale"
        )

    Surface(
        shape =
            RoundedCornerShape(
                100.dp
            ),
        color = BlinkPink,
        shadowElevation = 8.dp,
        modifier =
            Modifier
                .scale(scale)
                .clickable {
                    onClick()
                }
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 9.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription =
                    "Jump to unread messages",
                tint = Color.White,
                modifier =
                    Modifier.size(17.dp)
            )

            Spacer(
                modifier =
                    Modifier.width(4.dp)
            )

            Text(
                "$unreadCount unread",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight =
                    FontWeight.Black
            )
        }
    }
}

private fun scopeAnimateToUnread(
    state: androidx.compose.foundation.lazy.LazyListState
) {
    // Intentionally kept simple because your current model does
    // not expose a guaranteed unread message index.
    // Scrolling to the inbox list start is the safe fallback.
    kotlinx.coroutines.GlobalScope.launch {
        state.animateScrollToItem(0)
    }
}

// ============================================================================
// NEW CHAT SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    conversations: List<ChatConversation>,
    onDismiss: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit
) {

    var search by rememberSaveable {
        mutableStateOf("")
    }

    val candidates =
        remember(
            conversations,
            search
        ) {

            conversations.filter {

                search.isBlank() ||
                        it.partnerName.contains(
                            search,
                            ignoreCase = true
                        ) ||
                        it.partnerUsername.contains(
                            search,
                            ignoreCase = true
                        )
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = false
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
        ) {

            Text(
                "New conversation",
                fontSize = 21.sp,
                fontWeight =
                    FontWeight.Black
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Text(
                "Find a student, seller or campus contact.",
                fontSize = 10.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        100.dp
                    ),
                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
            ) {

                TextField(
                    value = search,
                    onValueChange = {
                        search = it
                    },
                    placeholder = {
                        Text(
                            "Name or username..."
                        )
                    },
                    leadingIcon = {

                        Icon(
                            Icons.Default.Search,
                            contentDescription =
                                null
                        )
                    },
                    singleLine = true,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor =
                                Color.Transparent,
                            unfocusedContainerColor =
                                Color.Transparent,
                            focusedIndicatorColor =
                                Color.Transparent,
                            unfocusedIndicatorColor =
                                Color.Transparent
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max = 480.dp
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        5.dp
                    )
            ) {

                items(
                    candidates,
                    key = {
                        "new_${it.id}"
                    }
                ) { conversation ->

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenConversation(
                                        conversation.partnerUsername
                                    )
                                }
                                .padding(
                                    vertical = 8.dp
                                ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        AsyncImage(
                            model =
                                conversation.partnerAvatar,
                            contentDescription =
                                conversation.partnerName,
                            contentScale =
                                ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(45.dp)
                                    .clip(
                                        CircleShape
                                    )
                                    .clickable {
                                        onProfileClick(
                                            conversation.partnerUsername
                                        )
                                    }
                        )

                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                conversation.partnerName,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            Text(
                                "@${conversation.partnerUsername}",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                fontSize = 9.sp
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription =
                                "Open chat"
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }
    }
}

// ============================================================================
// SETTINGS SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesSettingsSheet(
    onDismiss: () -> Unit
) {

    var notifications by rememberSaveable {
        mutableStateOf(true)
    }

    var readReceipts by rememberSaveable {
        mutableStateOf(true)
    }

    var typingIndicators by rememberSaveable {
        mutableStateOf(true)
    }

    var mediaAutoPlay by rememberSaveable {
        mutableStateOf(true)
    }

    var compactPreviews by rememberSaveable {
        mutableStateOf(false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 10.dp
                    )
        ) {

            Text(
                "Messages settings",
                fontSize = 21.sp,
                fontWeight =
                    FontWeight.Black
            )

            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )

            ChatSettingRow(
                icon =
                    Icons.Default.Notifications,
                title =
                    "Message notifications",
                description =
                    "Show notifications for new messages",
                enabled =
                    notifications,
                onClick = {
                    notifications =
                        !notifications
                }
            )

            ChatSettingRow(
                icon =
                    Icons.Default.DoneAll,
                title =
                    "Read receipts",
                description =
                    "Show message read status",
                enabled =
                    readReceipts,
                onClick = {
                    readReceipts =
                        !readReceipts
                }
            )

            ChatSettingRow(
                icon =
                    Icons.Default.MoreHoriz,
                title =
                    "Typing indicators",
                description =
                    "Show when someone is typing",
                enabled =
                    typingIndicators,
                onClick = {
                    typingIndicators =
                        !typingIndicators
                }
            )

            ChatSettingRow(
                icon =
                    Icons.Default.PlayCircle,
                title =
                    "Auto preview media",
                description =
                    "Preview shared media automatically",
                enabled =
                    mediaAutoPlay,
                onClick = {
                    mediaAutoPlay =
                        !mediaAutoPlay
                }
            )

            ChatSettingRow(
                icon =
                    Icons.Default.ViewCompact,
                title =
                    "Compact conversations",
                description =
                    "Use tighter inbox spacing",
                enabled =
                    compactPreviews,
                onClick = {
                    compactPreviews =
                        !compactPreviews
                }
            )

            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )

            SectionAction(
                icon =
                    Icons.Default.Security,
                title =
                    "Privacy & security",
                subtitle =
                    "Manage your messaging privacy"
            )

            SectionAction(
                icon =
                    Icons.Default.Storage,
                title =
                    "Storage & media",
                subtitle =
                    "Manage conversation media"
            )

            SectionAction(
                icon =
                    Icons.Default.Archive,
                title =
                    "Archived chats",
                subtitle =
                    "View hidden conversations"
            )

            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )
        }
    }
}

// ============================================================================
// SETTING ROW
// ============================================================================

@Composable
private fun ChatSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color =
                if (enabled)
                    BlinkPink.copy(
                        alpha = 0.10f
                    )
                else
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint =
                    if (enabled)
                        BlinkPink
                    else
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        10.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.width(11.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontSize = 12.5.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                description,
                fontSize = 9.5.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        Surface(
            shape =
                RoundedCornerShape(
                    100.dp
                ),
            color =
                if (enabled)
                    BlinkPink
                else
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
        ) {

            Text(
                if (enabled)
                    "ON"
                else
                    "OFF",
                color =
                    if (enabled)
                        Color.White
                    else
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                fontSize = 8.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
        }
    }
}

// ============================================================================
// SECTION ACTION
// ============================================================================

@Composable
private fun SectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            icon,
            contentDescription = title,
            tint =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.size(
                    20.dp
                )
        )

        Spacer(
            modifier =
                Modifier.width(11.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontSize = 12.5.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                subtitle,
                fontSize = 9.5.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier =
                Modifier.size(
                    17.dp
                )
        )
    }
}

// ============================================================================
// CHAT CONVERSATION
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationView(
    convo: ChatConversation,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendVideo: (Uri) -> Unit = {},
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    isConnected: Boolean = true,
    onRetryMessage: ((ChatMessage) -> Unit)? = null
) {

    var messageText by rememberSaveable {
        mutableStateOf("")
    }

    var showChatSearch by rememberSaveable {
        mutableStateOf(false)
    }

    var chatSearchQuery by rememberSaveable {
        mutableStateOf("")
    }

    var showAttachmentSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showChatMoreSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showReactionSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showEmojiPanel by rememberSaveable {
        mutableStateOf(false)
    }

    var showQuickReplies by rememberSaveable {
        mutableStateOf(true)
    }

    var isVoiceRecording by rememberSaveable {
        mutableStateOf(false)
    }

    var replyingTo by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var editingMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val listState =
        rememberLazyListState()

    val scope =
        rememberCoroutineScope()

    val clipboard =
        LocalClipboardManager.current

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSendVideo(uri)
    }

    val filteredMessages =
        remember(
            convo.messages,
            chatSearchQuery
        ) {

            if (
                chatSearchQuery.isBlank()
            ) {
                convo.messages
            } else {
                convo.messages.filter {
                    it.text.contains(
                        chatSearchQuery,
                        ignoreCase = true
                    )
                }
            }
        }

    val sendEnabled =
        messageText.isNotBlank()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "chat_conversation"
                ),
        topBar = {

            ChatTopBar(
                convo = convo,
                isConnected = isConnected,
                searchActive =
                    showChatSearch,
                onBack = onBack,
                onProfileClick = {
                    onProfileClick(
                        convo.partnerUsername
                    )
                },
                onSearch = {
                    showChatSearch =
                        !showChatSearch

                    if (!showChatSearch) {
                        chatSearchQuery = ""
                    }
                },
                onMore = {
                    showChatMoreSheet = true
                }
            )
        },
        bottomBar = {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
            ) {

                AnimatedVisibility(
                    visible =
                        replyingTo != null ||
                                editingMessage != null,
                    enter =
                        fadeIn() +
                                expandVertically(),
                    exit =
                        fadeOut() +
                                slideOutVertically()
                ) {

                    ReplyEditBanner(
                        replyTarget =
                            replyingTo,
                        editTarget =
                            editingMessage,
                        onClose = {
                            replyingTo = null
                            editingMessage = null
                        }
                    )
                }

                if (!isConnected) {
                    ChatConnectionNotice()
                }

                AnimatedVisibility(
                    visible =
                        showQuickReplies &&
                                messageText.isBlank(),
                    enter =
                        fadeIn(),
                    exit =
                        fadeOut()
                ) {

                    QuickReplyRail(
                        onReply = {
                            messageText = it
                        }
                    )
                }

                ChatComposer(
                    value = messageText,
                    isConnected = isConnected,
                    onValueChange = {
                        messageText = it
                    },
                    enabled =
                        !isVoiceRecording,
                    isVoiceRecording =
                        isVoiceRecording,
                    onAttachment = {
                        showAttachmentSheet = true
                    },
                    onEmoji = {
                        showEmojiPanel =
                            !showEmojiPanel
                    },
                    onVoice = {
                        isVoiceRecording =
                            !isVoiceRecording
                    },
                    onSend = {

                        if (
                            messageText.isNotBlank()
                        ) {

                            onSendMessage(
                                messageText.trim()
                            )

                            messageText = ""
                            replyingTo = null
                            editingMessage = null

                            scope.launch {

                                delay(100)

                                if (
                                    convo.messages
                                        .isNotEmpty()
                                ) {

                                    listState.animateScrollToItem(
                                        filteredMessages
                                            .lastIndex
                                            .coerceAtLeast(
                                                0
                                            )
                                    )
                                }
                            }
                        }
                    },
                    sendEnabled =
                        sendEnabled
                )

                AnimatedVisibility(
                    visible = showEmojiPanel,
                    enter =
                        fadeIn() +
                                slideInVertically(
                                    initialOffsetY =
                                        { it }
                                ),
                    exit =
                        fadeOut() +
                                slideOutVertically()
                ) {

                    EmojiPanel(
                        onEmoji = {
                            messageText += it
                        }
                    )
                }
            }
        }
    ) { padding ->

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
        ) {

            Column(
                modifier =
                    Modifier.fillMaxSize()
            ) {

                if (showChatSearch) {

                    ChatSearchBar(
                        value =
                            chatSearchQuery,
                        onValueChange = {
                            chatSearchQuery = it
                        },
                        onClose = {
                            showChatSearch = false
                            chatSearchQuery = ""
                        }
                    )
                }

                ConversationSecurityBanner()

                if (
                    filteredMessages.isEmpty() &&
                    chatSearchQuery.isNotBlank()
                ) {

                    MessageSearchEmptyState(
                        query =
                            chatSearchQuery
                    )

                } else {

                    LazyColumn(
                        state =
                            listState,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .fillMaxWidth(),
                        contentPadding =
                            PaddingValues(
                                horizontal = 13.dp,
                                vertical = 10.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                5.dp
                            )
                    ) {

                        itemsIndexed(
                            filteredMessages,
                            key = {
                                    index,
                                    message ->
                                message.id
                            }
                        ) { index, message ->

                            MessageRow(
                                message =
                                    message,
                                isDark =
                                    isDark,
                                isHighlighted =
                                    chatSearchQuery
                                        .isNotBlank(),
                                onReply = {
                                    replyingTo =
                                        message.text
                                },
                                onEdit = {

                                    if (
                                        message.isFromMe
                                    ) {
                                        editingMessage =
                                            message.id
                                        messageText =
                                            message.text
                                    }
                                },
                                onCopy = {

                                    clipboard
                                        .setText(
                                            AnnotatedString(
                                                message.text
                                            )
                                        )
                                },
                                onReaction = {
                                    showReactionSheet =
                                        true
                                },
                                onMore = {
                                    showChatMoreSheet =
                                        true
                                },
                                onRetry = {
                                    onRetryMessage?.invoke(message)
                                }
                            )

                            if (
                                index ==
                                filteredMessages.lastIndex
                            ) {

                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            3.dp
                                        )
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible =
                    listState.firstVisibleItemIndex >
                            4,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomEnd
                        )
                        .padding(
                            end = 12.dp,
                            bottom = 74.dp
                        ),
                enter =
                    fadeIn() +
                            scaleIn(),
                exit =
                    fadeOut() +
                            scaleOut()
            ) {

                Surface(
                    shape =
                        CircleShape,
                    color =
                        BlinkPink,
                    shadowElevation =
                        7.dp,
                    modifier =
                        Modifier.clickable {

                            scope.launch {

                                listState.animateScrollToItem(
                                    0
                                )
                            }
                        }
                ) {

                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription =
                            "Scroll to latest messages",
                        tint =
                            Color.White,
                        modifier =
                            Modifier.padding(
                                9.dp
                            )
                    )
                }
            }
        }
    }

    if (showAttachmentSheet) {

        AttachmentSheet(
            onDismiss = {
                showAttachmentSheet = false
            },
            onVideo = {
                showAttachmentSheet = false
                videoPicker.launch("video/*")
            }
        )
    }

    if (showChatMoreSheet) {

        ChatMoreSheet(
            conversation =
                convo,
            onDismiss = {
                showChatMoreSheet = false
            }
        )
    }

    if (showReactionSheet) {

        ReactionSheet(
            onDismiss = {
                showReactionSheet = false
            },
            onReaction = {
                showReactionSheet = false
            }
        )
    }
}

// ============================================================================
// CHAT TOP BAR
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    convo: ChatConversation,
    isConnected: Boolean,
    searchActive: Boolean,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit
) {

    val callContext = LocalContext.current
    val callRoom = "https://meet.jit.si/Blink-${convo.id}"

    TopAppBar(
        title = {

            Row(
                modifier =
                    Modifier.clickable {
                        onProfileClick()
                    },
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier.size(
                            40.dp
                        )
                ) {

                    AsyncImage(
                        model =
                            convo.partnerAvatar,
                        contentDescription =
                            convo.partnerName,
                        contentScale =
                            ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(
                                    CircleShape
                                )
                    )

                    if (convo.isOnline) {

                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .align(
                                        Alignment.BottomEnd
                                    )
                                    .background(
                                        BlinkOnlineGreen,
                                        CircleShape
                                    )
                                    .border(
                                        2.dp,
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                        CircleShape
                                    )
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.width(9.dp)
                )

                Column {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            convo.partnerName,
                            fontSize = 14.sp,
                            fontWeight =
                                FontWeight.Bold,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )

                        if (
                            convo.verificationBadge !=
                            VerificationBadge.NONE
                        ) {

                            Spacer(
                                modifier =
                                    Modifier.width(4.dp)
                            )

                            VerifiedMark(
                                badge =
                                    convo.verificationBadge,
                                size =
                                    12.dp
                            )

                        } else if (
                            convo.isVerified
                        ) {

                            Spacer(
                                modifier =
                                    Modifier.width(4.dp)
                            )

                            VerifiedMark(
                                badge =
                                    VerificationBadge.BLUE,
                                size =
                                    12.dp
                            )
                        }
                    }

                    Text(
                        when {
                            !isConnected -> "Offline"
                            convo.isOnline -> "Active now"
                            else -> "Last seen ${convo.lastSeen}"
                        },
                        fontSize = 10.sp,
                        fontWeight = if (!isConnected) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            when {
                                !isConnected -> MaterialTheme.colorScheme.error
                                convo.isOnline -> BlinkOnlineGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                    )
                }
            }
        },
        navigationIcon = {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription =
                        "Back"
                )
            }
        },
        actions = {

            IconButton(
                onClick = {
                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$callRoom#config.startWithVideoMuted=true")))
                }
            ) {

                Icon(
                    Icons.Default.Phone,
                    contentDescription =
                        "Audio call"
                )
            }

            IconButton(
                onClick = {
                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(callRoom)))
                }
            ) {

                Icon(
                    Icons.Default.Videocam,
                    contentDescription =
                        "Video call"
                )
            }

            IconButton(
                onClick = onSearch
            ) {

                Icon(
                    if (searchActive)
                        Icons.Default.Close
                    else
                        Icons.Default.Search,
                    contentDescription =
                        "Search conversation"
                )
            }

            IconButton(
                onClick = onMore
            ) {

                Icon(
                    Icons.Default.MoreVert,
                    contentDescription =
                        "Chat options"
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            )
    )
}

// ============================================================================
// SEARCH BAR
// ============================================================================

@Composable
private fun ChatSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit
) {

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = 5.dp
                ),
        shape =
            RoundedCornerShape(
                100.dp
            ),
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.Search,
                contentDescription =
                    null,
                modifier =
                    Modifier.padding(
                        start = 12.dp
                    )
            )

            TextField(
                value = value,
                onValueChange =
                    onValueChange,
                placeholder = {
                    Text(
                        "Search this chat..."
                    )
                },
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor =
                            Color.Transparent,
                        unfocusedContainerColor =
                            Color.Transparent,
                        focusedIndicatorColor =
                            Color.Transparent,
                        unfocusedIndicatorColor =
                            Color.Transparent
                    ),
                modifier =
                    Modifier.weight(1f)
            )

            IconButton(
                onClick = onClose
            ) {

                Icon(
                    Icons.Default.Close,
                    contentDescription =
                        "Close chat search"
                )
            }
        }
    }
}

// ============================================================================
// SECURITY BANNER
// ============================================================================

@Composable
private fun ConversationSecurityBanner() {

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 13.dp,
                    vertical = 4.dp
                ),
        shape =
            RoundedCornerShape(
                12.dp
            ),
        color =
            Color(0xFF22C55E).copy(
                alpha = 0.065f
            )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 9.dp,
                    vertical = 6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.Lock,
                contentDescription =
                    "Private conversation",
                tint =
                    Color(0xFF22C55E),
                modifier =
                    Modifier.size(
                        13.dp
                    )
            )

            Spacer(
                modifier =
                    Modifier.width(5.dp)
            )

            Text(
                "Private campus conversation",
                color =
                    Color(0xFF22C55E),
                fontSize = 8.5.sp,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

// ============================================================================
// MESSAGE ROW
// ============================================================================

@Composable
private fun MessageRow(
    message: ChatMessage,
    isDark: Boolean,
    isHighlighted: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onReaction: () -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit = {}
) {

    var pressed by rememberSaveable(
        message.id
    ) {
        mutableStateOf(false)
    }

    val scale by animateFloatAsState(
        targetValue =
            if (pressed)
                0.98f
            else
                1f,
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy
            ),
        label = "message_scale"
    )

    LaunchedEffect(pressed) {

        if (pressed) {
            delay(150)
            pressed = false
        }
    }

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalAlignment =
            if (message.isFromMe)
                Alignment.End
            else
                Alignment.Start
    ) {

        Surface(
            modifier =
                Modifier
                    .scale(scale)
                    .pointerInput(
                        message.id
                    ) {

                        detectTapGestures(
                            onLongPress = {

                                pressed = true
                                onMore()
                            }
                        )
                    },
            shape =
                RoundedCornerShape(
                    topStart =
                        if (message.isFromMe)
                            17.dp
                        else
                            5.dp,
                    topEnd =
                        if (message.isFromMe)
                            5.dp
                        else
                            17.dp,
                    bottomStart =
                        17.dp,
                    bottomEnd =
                        17.dp
                ),
            color =
                if (isHighlighted)
                    BlinkPurple.copy(
                        alpha = 0.14f
                    )
                else if (message.isFromMe)
                    BlinkPink
                else if (isDark)
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                else
                    Color(0xFFE9ECEF)
        ) {

            Column(
                modifier =
                    Modifier
                        .widthIn(
                            max = 305.dp
                        )
                        .padding(
                            horizontal = 13.dp,
                            vertical = 9.dp
                        )
            ) {
                if (!message.attachedVideoUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.attachedVideoUrl))) } }) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Play video", tint = if (message.isFromMe) Color.White else BlinkPink, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text("Video message", fontWeight = FontWeight.Bold, color = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.onSurface)
                                Text("Tap to play", fontSize = 10.sp, color = if (message.isFromMe) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }

                Text(
                    text =
                        message.text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color =
                        if (message.isFromMe)
                            Color.White
                        else
                            MaterialTheme
                                .colorScheme
                                .onSurface
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            message.timestamp,
                        fontSize = 8.5.sp,
                        color =
                            if (message.isFromMe)
                                Color.White.copy(
                                    alpha = 0.70f
                                )
                            else
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                    )

                    if (message.isFromMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Sending",
                                    tint = Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed. Tap to retry",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRetry() }
                                )
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = if (message.isRead) "Read" else "Sent",
                                    tint = if (message.isRead) Color(0xFF40C4FF) else Color.White.copy(alpha = 0.80f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 4.dp,
                    vertical = 1.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    3.dp
                )
        ) {

            if (message.text.length > 80) {

                SmallMessageAction(
                    icon =
                        Icons.Outlined.Reply,
                    onClick = onReply
                )
            }

            SmallMessageAction(
                icon =
                    Icons.Default.FavoriteBorder,
                onClick = onReaction
            )

            if (message.isFromMe) {

                SmallMessageAction(
                    icon =
                        Icons.Default.Edit,
                    onClick = onEdit
                )
            }

            SmallMessageAction(
                icon =
                    Icons.Default.ContentCopy,
                onClick = onCopy
            )
        }
    }
}

// ============================================================================
// SMALL MESSAGE ACTION
// ============================================================================

@Composable
private fun SmallMessageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {

    Surface(
        modifier =
            Modifier
                .size(28.dp)
                .clickable {
                    onClick()
                },
        shape = CircleShape,
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant
                .copy(alpha = 0.65f)
    ) {

        Icon(
            icon,
            contentDescription = null,
            modifier =
                Modifier.padding(
                    7.dp
                ),
            tint =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}

// ============================================================================
// QUICK REPLIES
// ============================================================================

@Composable
private fun QuickReplyRail(
    onReply: (String) -> Unit
) {

    val replies =
        listOf(
            "Okay 👍",
            "Thanks!",
            "On my way",
            "I'll check",
            "Nice!",
            "Sure",
            "Noted"
        )

    LazyRow(
        contentPadding =
            PaddingValues(
                horizontal = 11.dp,
                vertical = 4.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {

        items(replies) { reply ->

            Surface(
                shape =
                    RoundedCornerShape(
                        100.dp
                    ),
                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.55f),
                modifier =
                    Modifier.clickable {
                        onReply(reply)
                    }
            ) {

                Text(
                    reply,
                    fontSize = 9.5.sp,
                    modifier =
                        Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        )
                )
            }
        }
    }
}

// ============================================================================
// REPLY / EDIT BANNER
// ============================================================================

@Composable
private fun ReplyEditBanner(
    replyTarget: String?,
    editTarget: String?,
    onClose: () -> Unit
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        color =
            BlinkPink.copy(
                alpha = 0.07f
            )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 11.dp,
                    vertical = 7.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                if (editTarget != null)
                    Icons.Default.Edit
                else
                    Icons.Default.Reply,
                contentDescription =
                    null,
                tint = BlinkPink,
                modifier =
                    Modifier.size(
                        16.dp
                    )
            )

            Spacer(
                modifier =
                    Modifier.width(7.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    if (editTarget != null)
                        "Editing message"
                    else
                        "Replying to message",
                    fontSize = 9.sp,
                    color = BlinkPink,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    replyTarget
                        ?: editTarget
                        ?: "",
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onClose,
                modifier =
                    Modifier.size(
                        28.dp
                    )
            ) {

                Icon(
                    Icons.Default.Close,
                    contentDescription =
                        "Cancel"
                )
            }
        }
    }
}

// ============================================================================
// COMPOSER
// ============================================================================

@Composable
private fun ChatComposer(
    value: String,
    isConnected: Boolean,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isVoiceRecording: Boolean,
    onAttachment: () -> Unit,
    onEmoji: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        color =
            MaterialTheme
                .colorScheme
                .surface,
        shadowElevation = 8.dp
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick =
                    onAttachment,
                modifier =
                    Modifier.size(
                        37.dp
                    )
            ) {

                Icon(
                    Icons.Default.AttachFile,
                    contentDescription =
                        "Attach file",
                    tint = BlinkPink
                )
            }

            Surface(
                modifier =
                    Modifier.weight(1f),
                shape =
                    RoundedCornerShape(
                        24.dp
                    ),
                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.55f)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    TextField(
                        value = value,
                        onValueChange =
                            onValueChange,
                        enabled =
                            enabled &&
                                    !isVoiceRecording,
                        placeholder = {

                            Text(
                                when {
                                    isVoiceRecording -> "Recording voice note..."
                                    !isConnected -> "You can type while offline"
                                    else -> "Type a message..."
                                }
                            )
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization =
                                    KeyboardCapitalization
                                        .Sentences,
                                imeAction =
                                    ImeAction.Send,
                                autoCorrectEnabled =
                                    true
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onSend = {
                                    onSend()
                                }
                            ),
                        maxLines = 5,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor =
                                    Color.Transparent,
                                unfocusedContainerColor =
                                    Color.Transparent,
                                disabledContainerColor =
                                    Color.Transparent,
                                focusedIndicatorColor =
                                    Color.Transparent,
                                unfocusedIndicatorColor =
                                    Color.Transparent
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(
                                    "chat_input_field"
                                )
                    )

                    IconButton(
                        onClick = onEmoji,
                        enabled =
                            !isVoiceRecording,
                        modifier =
                            Modifier.size(
                                35.dp
                            )
                    ) {

                        Icon(
                            Icons.Default.EmojiEmotions,
                            contentDescription =
                                "Emoji",
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.width(5.dp)
            )

            AnimatedContent(
                targetState =
                    (sendEnabled && isConnected) ||
                            isVoiceRecording,
                label = "composer_action"
            ) { active ->

                if (isVoiceRecording) {

                    Surface(
                        shape = CircleShape,
                        color =
                            Color(0xFFE53935),
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clickable {
                                    onVoice()
                                }
                    ) {

                        Icon(
                            Icons.Default.Stop,
                            contentDescription =
                                "Stop recording",
                            tint = Color.White,
                            modifier =
                                Modifier.padding(
                                    11.dp
                                )
                        )
                    }

                } else if (active) {

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clickable {
                                    onSend()
                                }
                                .testTag(
                                    "send_message_button"
                                )
                    ) {

                        Icon(
                            Icons.Default.Send,
                            contentDescription =
                                "Send message",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier =
                                Modifier.padding(
                                    11.dp
                                )
                        )
                    }

                } else {

                    Surface(
                        shape = CircleShape,
                        color =
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant,
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clickable {
                                    onVoice()
                                }
                    ) {

                        Icon(
                            Icons.Default.Mic,
                            contentDescription =
                                "Voice message",
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            modifier =
                                Modifier.padding(
                                    11.dp
                                )
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// EMOJI PANEL
// ============================================================================

@Composable
private fun EmojiPanel(
    onEmoji: (String) -> Unit
) {

    val emojis =
        listOf(
            "😂", "😭", "❤️", "🔥",
            "😍", "😎", "👏", "💯",
            "🎉", "🎓", "🙌", "🤣",
            "👀", "😮", "🥳", "🤝",
            "🚀", "✨", "💜", "👍"
        )

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant
                .copy(alpha = 0.70f)
    ) {

        Row(
            modifier =
                Modifier
                    .horizontalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 11.dp,
                        vertical = 10.dp
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            emojis.forEach { emoji ->

                Text(
                    emoji,
                    fontSize = 22.sp,
                    modifier =
                        Modifier.clickable {
                            onEmoji(emoji)
                        }
                )
            }
        }
    }
}

// ============================================================================
// ATTACHMENT SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onVideo: () -> Unit = {}
) {

    ModalBottomSheet(
        onDismissRequest =
            onDismiss
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 9.dp
                    )
        ) {

            Text(
                "Send something",
                fontSize = 21.sp,
                fontWeight =
                    FontWeight.Black
            )

            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )

            AttachmentGridItem(
                icon = Icons.Default.VideoLibrary,
                title = "Video",
                subtitle = "Choose a video from your gallery",
                tint = BlinkPink,
                onClick = onVideo
            )

            AttachmentGridItem(
                icon =
                    Icons.Default.PhotoLibrary,
                title = "Photos",
                subtitle =
                    "Choose images from gallery",
                tint = Color(0xFFAB47BC)
            )

            AttachmentGridItem(
                icon =
                    Icons.Default.CameraAlt,
                title = "Camera",
                subtitle =
                    "Take a photo",
                tint = BlinkPink
            )

            AttachmentGridItem(
                icon =
                    Icons.Default.Description,
                title = "Document",
                subtitle =
                    "Share a file or PDF",
                tint = Color(0xFF4285F4)
            )

            AttachmentGridItem(
                icon =
                    Icons.Default.LocationOn,
                title = "Location",
                subtitle =
                    "Share a campus location",
                tint = Color(0xFF22C55E)
            )

            AttachmentGridItem(
                icon =
                    Icons.Default.ContactPage,
                title = "Contact",
                subtitle =
                    "Share a student contact",
                tint = BlinkPurple
            )

            AttachmentGridItem(
                icon =
                    Icons.Default.Poll,
                title = "Poll",
                subtitle =
                    "Ask your conversation a question",
                tint = BlinkGold
            )

            AttachmentGridItem(
                icon =
                    Icons.Default.GifBox,
                title = "GIF",
                subtitle =
                    "Choose an animated reaction",
                tint = Color(0xFF00ACC1)
            )

            Spacer(
                modifier =
                    Modifier.height(
                        15.dp
                    )
            )
        }
    }
}

// ============================================================================
// ATTACHMENT ITEM
// ============================================================================

@Composable
private fun AttachmentGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit = {}
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color =
                tint.copy(
                    alpha = 0.10f
                )
        ) {

            Icon(
                icon,
                contentDescription = title,
                tint = tint,
                modifier =
                    Modifier.padding(
                        11.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.width(
                    11.dp
                )
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 13.sp
            )

            Text(
                subtitle,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                fontSize = 9.5.sp
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null
        )
    }
}

// ============================================================================
// CHAT MORE SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatMoreSheet(
    conversation: ChatConversation,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest =
            onDismiss
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 9.dp
                    )
        ) {

            Text(
                "Conversation options",
                fontSize = 21.sp,
                fontWeight =
                    FontWeight.Black
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            ChatOption(
                icon =
                    Icons.Default.Person,
                title =
                    "View profile",
                subtitle =
                    "@${conversation.partnerUsername}"
            )

            ChatOption(
                icon =
                    Icons.Default.Search,
                title =
                    "Search messages",
                subtitle =
                    "Find text in this conversation"
            )

            ChatOption(
                icon =
                    Icons.Default.PhotoLibrary,
                title =
                    "Media, links and files",
                subtitle =
                    "Browse shared content"
            )

            ChatOption(
                icon =
                    Icons.Default.NotificationsOff,
                title =
                    "Mute notifications",
                subtitle =
                    "Silence this conversation"
            )

            ChatOption(
                icon =
                    Icons.Default.Timer,
                title =
                    "Disappearing messages",
                subtitle =
                    "Choose a message lifetime"
            )

            ChatOption(
                icon =
                    Icons.Default.Palette,
                title =
                    "Chat theme",
                subtitle =
                    "Customize this conversation"
            )

            ChatOption(
                icon =
                    Icons.Default.Lock,
                title =
                    "Security info",
                subtitle =
                    "Privacy and conversation details"
            )

            ChatOption(
                icon =
                    Icons.Default.DeleteSweep,
                title =
                    "Clear conversation",
                subtitle =
                    "Remove chat history from this device"
            )

            ChatOption(
                icon =
                    Icons.Default.Report,
                title =
                    "Report conversation",
                subtitle =
                    "Send a moderation report"
            )

            Spacer(
                modifier =
                    Modifier.height(
                        15.dp
                    )
            )
        }
    }
}

// ============================================================================
// CHAT OPTION
// ============================================================================

@Composable
private fun ChatOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant
        ) {

            Icon(
                icon,
                contentDescription =
                    title,
                modifier =
                    Modifier.padding(
                        11.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.width(
                    11.dp
                )
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                subtitle,
                fontSize = 9.5.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    17.dp
                )
        )
    }
}

// ============================================================================
// REACTION SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionSheet(
    onDismiss: () -> Unit,
    onReaction: (String) -> Unit
) {

    val reactions =
        listOf(
            "❤️", "😂", "🔥",
            "👍", "😍", "😮",
            "👏", "💯", "😭"
        )

    ModalBottomSheet(
        onDismissRequest =
            onDismiss
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                "React to message",
                fontSize = 19.sp,
                fontWeight =
                    FontWeight.Black
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            Row(
                modifier =
                    Modifier
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        11.dp
                    )
            ) {

                reactions.forEach { reaction ->

                    Surface(
                        shape = CircleShape,
                        color =
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant,
                        modifier =
                            Modifier.clickable {
                                onReaction(
                                    reaction
                                )
                            }
                    ) {

                        Text(
                            reaction,
                            fontSize = 28.sp,
                            modifier =
                                Modifier.padding(
                                    8.dp
                                )
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
                )
        }
    }
}

// ============================================================================
// MESSAGE SEARCH EMPTY
// ============================================================================

@Composable
private fun MessageSearchEmptyState(
    query: String
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    35.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Surface(
            shape = CircleShape,
            color =
                BlinkPink.copy(
                    alpha = 0.10f
                )
        ) {

            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                tint = BlinkPink,
                modifier =
                    Modifier.padding(
                        16.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    13.dp
                )
        )

        Text(
            "No message found",
            fontWeight =
                FontWeight.Bold,
            fontSize = 16.sp
        )

        Text(
            "Nothing in this conversation matches \"$query\".",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            fontSize = 10.5.sp,
            textAlign =
                TextAlign.Center
        )
    }
}