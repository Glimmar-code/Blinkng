package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ActivityItem
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.data.models.Story
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkMessageTheme
import com.example.ui.theme.MessagePalette
import com.example.ui.theme.MessageThemeMode
import com.example.ui.theme.messagePalette
import kotlinx.coroutines.delay
import java.util.Locale

private const val MESSAGE_PREFERENCES = "blink_message_preferences"
private const val MESSAGE_THEME_KEY = "message_theme"

private enum class MessageCallKind { AUDIO, VIDEO }

private data class MessageCallState(
    val conversation: ChatConversation,
    val kind: MessageCallKind,
    val startedAtMillis: Long = System.currentTimeMillis()
)

private data class MatchPerson(
    val id: String,
    val name: String,
    val username: String,
    val avatar: String,
    val isOnline: Boolean,
    val hasUnseenStory: Boolean,
    val conversation: ChatConversation? = null,
    val story: Story? = null
)

/**
 * Reference-inspired messaging UI. Message transport, realtime updates, retries, profiles,
 * stories, activity and media continue to be owned by the existing ViewModel/Supabase layer.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumMessagesScreen(
    conversations: List<ChatConversation>,
    stories: List<Story>,
    activities: List<ActivityItem>,
    myAvatar: String,
    myName: String,
    activePartner: String?,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onSendVideo: (String, Uri) -> Unit = { _, _ -> },
    onRetryMessage: ((String, ChatMessage) -> Unit)? = null,
    onProfileClick: (String) -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenActivity: () -> Unit,
    isConnected: Boolean = true,
    isDark: Boolean = false
) {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences(MESSAGE_PREFERENCES, Context.MODE_PRIVATE)
    }
    var storedTheme by rememberSaveable {
        mutableStateOf(preferences.getString(MESSAGE_THEME_KEY, null))
    }
    val messageTheme = MessageThemeMode.fromStorage(storedTheme)
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var activeCall by remember { mutableStateOf<MessageCallState?>(null) }

    val activeConversation = activePartner?.let { partner ->
        conversations.firstOrNull { it.partnerUsername.equals(partner, ignoreCase = true) }
            ?: ChatConversation(
                id = "local_$partner",
                partnerUsername = partner,
                partnerName = partner.replace("_", " ").replace(".", " "),
                partnerAvatar = ""
            )
    }

    BackHandler(enabled = activeCall != null) { activeCall = null }
    BackHandler(enabled = activeCall == null && activeConversation != null) { onCloseConversation() }

    BlinkMessageTheme(messageTheme) { palette ->
        AnimatedContent(
            targetState = activeCall,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(initialScale = .985f)) togetherWith
                    (fadeOut(tween(180)) + scaleOut(targetScale = 1.015f))
            },
            label = "message_call_transition"
        ) { call ->
            if (call != null) {
                PremiumCallScreen(
                    call = call,
                    palette = palette,
                    onEndCall = { activeCall = null },
                    onReopenCall = {
                        launchSecureCall(context, call.conversation, call.kind)
                    }
                )
            } else if (activeConversation != null) {
                PremiumChatDetail(
                    conversation = activeConversation,
                    palette = palette,
                    onBack = onCloseConversation,
                    onSend = { onSendMessage(activeConversation.partnerUsername, it) },
                    onSendVideo = { onSendVideo(activeConversation.partnerUsername, it) },
                    onRetry = { message ->
                        onRetryMessage?.invoke(activeConversation.partnerUsername, message)
                    },
                    onProfileClick = { onProfileClick(activeConversation.partnerUsername) },
                    onAudioCall = {
                        activeCall = MessageCallState(activeConversation, MessageCallKind.AUDIO)
                        launchSecureCall(context, activeConversation, MessageCallKind.AUDIO)
                    },
                    onVideoCall = {
                        activeCall = MessageCallState(activeConversation, MessageCallKind.VIDEO)
                        launchSecureCall(context, activeConversation, MessageCallKind.VIDEO)
                    },
                    isConnected = isConnected
                )
            } else {
                PremiumMessagesHome(
                    conversations = conversations,
                    stories = stories,
                    myAvatar = myAvatar,
                    myName = myName,
                    palette = palette,
                    onOpenConversation = onOpenConversation,
                    onProfileClick = onProfileClick,
                    onStoryClick = onStoryClick,
                    onAddStoryClick = onAddStoryClick,
                    onOpenAppearance = { showAppearanceSheet = true },
                    isConnected = isConnected
                )
            }
        }

        if (showAppearanceSheet && activeCall == null) {
            MessageAppearanceSheet(
                selected = messageTheme,
                palette = palette,
                unreadActivityCount = activities.count { it.isUnread },
                onSelect = { selected ->
                    storedTheme = selected.storageValue
                    preferences.edit().putString(MESSAGE_THEME_KEY, selected.storageValue).apply()
                    showAppearanceSheet = false
                },
                onOpenActivity = {
                    showAppearanceSheet = false
                    onOpenActivity()
                },
                onDismiss = { showAppearanceSheet = false }
            )
        }
    }
}

@Composable
private fun PremiumMessagesHome(
    conversations: List<ChatConversation>,
    stories: List<Story>,
    myAvatar: String,
    myName: String,
    palette: MessagePalette,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenAppearance: () -> Unit,
    isConnected: Boolean
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredConversations = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter {
            it.partnerName.contains(query, ignoreCase = true) ||
                it.partnerUsername.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
        }
    }
    val startVoiceSearch = rememberSpeechInput { result -> query = result }

    MessageBackground(palette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            MessagesHeader(
                myAvatar = myAvatar,
                myName = myName,
                palette = palette,
                onMore = onOpenAppearance
            )

            SearchMatchesField(
                value = query,
                onValueChange = { query = it },
                onVoiceSearch = startVoiceSearch,
                palette = palette
            )

            MatchesRail(
                conversations = conversations,
                stories = stories,
                palette = palette,
                onAddStoryClick = onAddStoryClick,
                onOpenConversation = onOpenConversation,
                onStoryClick = onStoryClick
            )

            Text(
                text = "Chats",
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 8.dp)
            )

            if (!isConnected) {
                OfflineNotice(palette)
            }

            ConversationList(
                conversations = filteredConversations,
                palette = palette,
                onOpenConversation = onOpenConversation,
                onProfileClick = onProfileClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MessageBackground(
    palette: MessagePalette,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.backgroundBrush())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(.42f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (palette.isLight) .09f else .20f),
                            Color.Transparent
                        ),
                        center = Offset(120f, 90f),
                        radius = 720f
                    )
                )
        )
        content()
    }
}

@Composable
private fun MessagesHeader(
    myAvatar: String,
    myName: String,
    palette: MessagePalette,
    onMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 18.dp)
    ) {
        RingAvatar(
            url = myAvatar,
            name = myName,
            palette = palette,
            size = 38.dp,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = "Chats",
            color = palette.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
        GlassIconButton(
            icon = Icons.Default.MoreVert,
            contentDescription = "Message appearance and activity",
            palette = palette,
            size = 42.dp,
            onClick = onMore,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun SearchMatchesField(
    value: String,
    onValueChange: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    palette: MessagePalette
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text("Search Matches", color = palette.textSecondary, fontSize = 12.sp)
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(19.dp)
            )
        },
        trailingIcon = {
            IconButton(onClick = onVoiceSearch) {
                Surface(
                    shape = CircleShape,
                    color = palette.glassElevated.copy(alpha = if (palette.isLight) .75f else .90f),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Search by voice",
                        tint = palette.textPrimary,
                        modifier = Modifier.padding(8.dp).size(17.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.glassElevated.copy(alpha = if (palette.isLight) .88f else .72f),
            unfocusedContainerColor = palette.glassElevated.copy(alpha = if (palette.isLight) .88f else .72f),
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
            cursorColor = palette.accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(50.dp)
            .border(1.dp, palette.border, RoundedCornerShape(26.dp))
    )
}

@Composable
private fun MatchesRail(
    conversations: List<ChatConversation>,
    stories: List<Story>,
    palette: MessagePalette,
    onAddStoryClick: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onStoryClick: (Story) -> Unit
) {
    val matches = remember(conversations, stories) {
        val people = mutableListOf<MatchPerson>()
        conversations.forEach { conversation ->
            people += MatchPerson(
                id = "conversation_${conversation.id}",
                name = conversation.partnerName,
                username = conversation.partnerUsername,
                avatar = conversation.partnerAvatar,
                isOnline = conversation.isOnline,
                hasUnseenStory = false,
                conversation = conversation
            )
        }
        val existingUsers = people.map { it.username.lowercase(Locale.ROOT) }.toMutableSet()
        stories.filterNot { it.isUser }.forEach { story ->
            val normalized = story.username.removePrefix("@").lowercase(Locale.ROOT)
            if (normalized !in existingUsers) {
                existingUsers += normalized
                people += MatchPerson(
                    id = "story_${story.id}",
                    name = story.username.removePrefix("@"),
                    username = normalized,
                    avatar = story.avatar,
                    isOnline = false,
                    hasUnseenStory = story.hasUnseen,
                    story = story
                )
            }
        }
        people.take(12)
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "add_story") {
            AddMatchItem(palette = palette, onClick = onAddStoryClick)
        }
        items(matches, key = { it.id }) { person ->
            MatchItem(
                person = person,
                palette = palette,
                onClick = {
                    person.conversation?.let { onOpenConversation(it.partnerUsername) }
                        ?: person.story?.let(onStoryClick)
                }
            )
        }
    }
}

@Composable
private fun AddMatchItem(palette: MessagePalette, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = palette.accent.copy(alpha = .80f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))
                    )
                )
            }
            Icon(
                Icons.Default.Add,
                contentDescription = "Add story",
                tint = palette.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text("Me", color = palette.textSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun MatchItem(person: MatchPerson, palette: MessagePalette, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .clickable(onClick = onClick)
    ) {
        RingAvatar(
            url = person.avatar,
            name = person.name,
            palette = palette,
            size = 54.dp,
            online = person.isOnline,
            emphasizeRing = person.isOnline || person.hasUnseenStory
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = person.name.substringBefore(" ").take(10),
            color = palette.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OfflineNotice(palette: MessagePalette) {
    Surface(
        color = palette.danger.copy(alpha = if (palette.isLight) .10f else .16f),
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.danger.copy(alpha = .35f)),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        Text(
            "Offline — new messages will queue and send when your connection returns.",
            color = palette.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun ConversationList(
    conversations: List<ChatConversation>,
    palette: MessagePalette,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (conversations.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = palette.glass.copy(alpha = .70f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Icon(
                        Icons.Default.EmojiEmotions,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.padding(16.dp).size(26.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("No conversations found", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Start a match or search for someone to message.",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(conversations, key = { it.id }) { conversation ->
            ConversationCard(
                conversation = conversation,
                palette = palette,
                onOpen = { onOpenConversation(conversation.partnerUsername) },
                onAvatarClick = { onProfileClick(conversation.partnerUsername) }
            )
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: ChatConversation,
    palette: MessagePalette,
    onOpen: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Surface(
        color = palette.glassElevated.copy(alpha = if (palette.isLight) .88f else .66f),
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = if (palette.isLight) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RingAvatar(
                url = conversation.partnerAvatar,
                name = conversation.partnerName,
                palette = palette,
                size = 52.dp,
                online = conversation.isOnline,
                modifier = Modifier.clickable(onClick = onAvatarClick)
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation.partnerName,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    VerificationDot(conversation.verificationBadge, conversation.isVerified)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    conversation.lastMessage.ifBlank { "Start the conversation" },
                    color = if (conversation.unreadCount > 0) palette.textSecondary else palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    conversation.lastMessageTime,
                    color = palette.textMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.height(7.dp))
                    Badge(containerColor = palette.accent, contentColor = Color.White) {
                        Text(conversation.unreadCount.coerceAtMost(99).toString(), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumChatDetail(
    conversation: ChatConversation,
    palette: MessagePalette,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onSendVideo: (Uri) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onProfileClick: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    isConnected: Boolean
) {
    var text by rememberSaveable(conversation.partnerUsername) { mutableStateOf("") }
    var showEmojiRail by rememberSaveable(conversation.partnerUsername) { mutableStateOf(false) }
    var showAttachmentSheet by rememberSaveable(conversation.partnerUsername) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSendVideo(uri)
    }
    val startDictation = rememberSpeechInput { spoken ->
        text = listOf(text.trim(), spoken.trim()).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun submitMessage(value: String = text) {
        val clean = value.trim()
        if (clean.isNotEmpty()) {
            onSend(clean)
            if (value == text) text = ""
            showEmojiRail = false
        }
    }

    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.size)
        }
    }

    MessageBackground(palette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ChatHeader(
                conversation = conversation,
                palette = palette,
                onBack = onBack,
                onProfileClick = onProfileClick,
                onAudioCall = onAudioCall,
                onVideoCall = onVideoCall
            )

            if (!isConnected) {
                Surface(color = palette.danger.copy(alpha = .18f)) {
                    Text(
                        "Offline — your message will stay queued and retry automatically.",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            if (conversation.messages.isEmpty()) {
                EmptyConversation(
                    conversation = conversation,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    item(key = "today_divider") {
                        DayDivider("Today", palette)
                    }
                    items(conversation.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            partnerAvatar = conversation.partnerAvatar,
                            partnerName = conversation.partnerName,
                            palette = palette,
                            onRetry = { onRetry(message) }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showEmojiRail) {
                EmojiRail(
                    palette = palette,
                    onEmoji = { text += it }
                )
            }

            MessageComposer(
                value = text,
                onValueChange = { text = it },
                palette = palette,
                onAttachment = { showAttachmentSheet = true },
                onDictation = startDictation,
                onEmoji = { showEmojiRail = !showEmojiRail },
                onSubmit = { submitMessage() },
                onQuickLike = { submitMessage("👍") }
            )
        }
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            palette = palette,
            onVideo = {
                showAttachmentSheet = false
                videoPicker.launch("video/*")
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }
}

@Composable
private fun ChatHeader(
    conversation: ChatConversation,
    palette: MessagePalette,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.headerBrush())
            .border(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                palette = palette,
                size = 40.dp,
                onClick = onBack
            )
            Spacer(Modifier.width(7.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onProfileClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RingAvatar(
                    url = conversation.partnerAvatar,
                    name = conversation.partnerName,
                    palette = palette,
                    size = 40.dp,
                    online = conversation.isOnline
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            conversation.partnerName,
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        VerificationDot(conversation.verificationBadge, conversation.isVerified)
                    }
                    Text(
                        if (conversation.isOnline) "Active now" else conversation.lastSeen,
                        color = if (conversation.isOnline) palette.online else palette.textSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GlassIconButton(
                icon = Icons.Default.Call,
                contentDescription = "Audio call",
                palette = palette,
                size = 40.dp,
                onClick = onAudioCall
            )
            Spacer(Modifier.width(7.dp))
            GlassIconButton(
                icon = Icons.Default.Videocam,
                contentDescription = "Video call",
                palette = palette,
                size = 40.dp,
                onClick = onVideoCall
            )
        }
    }
}

@Composable
private fun EmptyConversation(
    conversation: ChatConversation,
    palette: MessagePalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RingAvatar(
            url = conversation.partnerAvatar,
            name = conversation.partnerName,
            palette = palette,
            size = 86.dp,
            online = conversation.isOnline
        )
        Spacer(Modifier.height(14.dp))
        Text(
            conversation.partnerName,
            color = palette.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Text(
            "Start the conversation with a message 👋",
            color = palette.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun DayDivider(label: String, palette: MessagePalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = palette.glass.copy(alpha = .40f),
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = .55f))
        ) {
            Text(
                label,
                color = palette.textSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    partnerAvatar: String,
    partnerName: String,
    palette: MessagePalette,
    onRetry: () -> Unit
) {
    val isMine = message.isFromMe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            RingAvatar(
                url = partnerAvatar,
                name = partnerName,
                palette = palette,
                size = 31.dp
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            val bubbleShape = RoundedCornerShape(17.dp)
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(bubbleShape)
                    .background(
                        brush = if (isMine) palette.outgoingBrush()
                        else Brush.linearGradient(listOf(palette.incomingBubble, palette.incomingBubble)),
                        shape = bubbleShape
                    )
                    .border(1.dp, palette.border.copy(alpha = .55f), bubbleShape)
                    .clickable(enabled = message.status == MessageStatus.FAILED, onClick = onRetry)
            ) {
                MessageContent(message = message, isMine = isMine, palette = palette)
            }
            Spacer(Modifier.height(3.dp))
            val metadata = when (message.status) {
                MessageStatus.SENDING -> "Sending…"
                MessageStatus.FAILED -> "Failed • tap to retry"
                MessageStatus.SENT -> message.timestamp
            }
            Text(
                metadata,
                color = if (message.status == MessageStatus.FAILED) palette.danger else palette.textSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MessageContent(message: ChatMessage, isMine: Boolean, palette: MessagePalette) {
    val context = LocalContext.current
    val contentColor = if (isMine) palette.outgoingText else palette.textPrimary
    Column(modifier = Modifier.padding(5.dp)) {
        if (!message.attachedImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = message.attachedImageUrl,
                contentDescription = "Shared image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(220.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(13.dp))
            )
        }
        if (!message.attachedVideoUrl.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = .20f),
                contentColor = contentColor,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .width(220.dp)
                    .height(96.dp)
                    .clickable {
                        openExternalUri(context, Uri.parse(message.attachedVideoUrl))
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Play video", modifier = Modifier.size(34.dp))
                    Text("Video message", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (message.isVoiceNote) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .width(104.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = .55f))
                )
                Spacer(Modifier.width(8.dp))
                Text(message.voiceDuration.ifBlank { "0:00" }, color = contentColor, fontSize = 9.sp)
            }
        }
        val placeholderOnly =
            (!message.attachedVideoUrl.isNullOrBlank() && message.text.equals("Video", true)) ||
                (!message.attachedImageUrl.isNullOrBlank() && message.text.equals("Image", true))
        if (message.text.isNotBlank() && !placeholderOnly && !message.isVoiceNote) {
            Text(
                message.text,
                color = contentColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun EmojiRail(palette: MessagePalette, onEmoji: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.glass.copy(alpha = .94f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("😊", "😂", "😍", "🔥", "👏", "💜", "👍", "🎉").forEach { emoji ->
            Surface(
                color = palette.glassElevated.copy(alpha = .75f),
                shape = CircleShape,
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.size(38.dp).clickable { onEmoji(emoji) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    palette: MessagePalette,
    onAttachment: () -> Unit,
    onDictation: () -> Unit,
    onEmoji: () -> Unit,
    onSubmit: () -> Unit,
    onQuickLike: () -> Unit
) {
    Surface(
        color = palette.glass.copy(alpha = if (palette.isLight) .96f else .90f),
        contentColor = palette.textPrimary,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add attachment",
                palette = palette,
                size = 40.dp,
                onClick = onAttachment
            )
            Spacer(Modifier.width(6.dp))
            GlassIconButton(
                icon = Icons.Default.Mic,
                contentDescription = "Dictate message",
                palette = palette,
                size = 40.dp,
                onClick = onDictation
            )
            Spacer(Modifier.width(7.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Message", color = palette.textMuted, fontSize = 12.sp) },
                trailingIcon = {
                    IconButton(onClick = onEmoji) {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            contentDescription = "Choose emoji",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.backgroundBottom.copy(alpha = if (palette.isLight) .08f else .36f),
                    unfocusedContainerColor = palette.backgroundBottom.copy(alpha = if (palette.isLight) .08f else .36f),
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    cursorColor = palette.accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 112.dp)
                    .border(1.dp, palette.border, RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = if (value.isBlank()) onQuickLike else onSubmit) {
                AnimatedContent(
                    targetState = value.isNotBlank(),
                    transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                    label = "composer_action"
                ) { hasText ->
                    Icon(
                        if (hasText) Icons.Default.Send else Icons.Default.ThumbUp,
                        contentDescription = if (hasText) "Send message" else "Send like",
                        tint = palette.accent,
                        modifier = Modifier.size(if (hasText) 25.dp else 28.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    palette: MessagePalette,
    onVideo: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 11.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(palette.textMuted.copy(alpha = .55f))
            )
        }
    ) {
        Text(
            "Share with this chat",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Surface(
            color = palette.glassElevated.copy(alpha = .75f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .clickable(onClick = onVideo)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = palette.accent.copy(alpha = .18f)) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Video", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Choose a video from your device", color = palette.textSecondary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp).navigationBarsPadding())
    }
}

@Composable
private fun PremiumCallScreen(
    call: MessageCallState,
    palette: MessagePalette,
    onEndCall: () -> Unit,
    onReopenCall: () -> Unit
) {
    var elapsedSeconds by remember(call.startedAtMillis) { mutableIntStateOf(0) }
    var muted by rememberSaveable(call.conversation.id) { mutableStateOf(false) }
    var speakerEnabled by rememberSaveable(call.conversation.id) { mutableStateOf(true) }
    val pulse by animateFloatAsState(
        targetValue = if (muted) .96f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "call_avatar_pulse"
    )

    LaunchedEffect(call.startedAtMillis) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - call.startedAtMillis) / 1_000L).toInt().coerceAtLeast(0)
            delay(1_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF7B164F), Color(0xFF2D153D), Color(0xFF160F1B))
                )
            )
    ) {
        if (call.conversation.partnerAvatar.isNotBlank()) {
            AsyncImage(
                model = call.conversation.partnerAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.22f)
                    .blur(42.dp)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x66160024),
                            Color(0x55331A57),
                            Color(0xCC120B19)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(.32f))
            RingAvatar(
                url = call.conversation.partnerAvatar,
                name = call.conversation.partnerName,
                palette = messagePalette(MessageThemeMode.PINK),
                size = 108.dp,
                emphasizeRing = true,
                modifier = Modifier
                    .scale(pulse)
                    .clickable(onClick = onReopenCall)
                    .semantics { contentDescription = "Reopen secure call" }
            )
            Spacer(Modifier.weight(.30f))
            Text(
                call.conversation.partnerName,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                formatCallDuration(elapsedSeconds),
                color = Color.White.copy(alpha = .86f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControl(
                    icon = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    description = if (speakerEnabled) "Turn speaker off" else "Turn speaker on",
                    active = speakerEnabled,
                    onClick = { speakerEnabled = !speakerEnabled }
                )
                CallControl(
                    icon = Icons.Default.CallEnd,
                    description = "End call",
                    containerColor = palette.danger,
                    size = 56.dp,
                    onClick = onEndCall
                )
                CallControl(
                    icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    description = if (muted) "Unmute" else "Mute",
                    active = !muted,
                    onClick = { muted = !muted }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CallControl(
    icon: ImageVector,
    description: String,
    containerColor: Color = Color(0x8843304B),
    active: Boolean = true,
    size: Dp = 50.dp,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) containerColor else containerColor.copy(alpha = .58f),
        contentColor = Color.White,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
        shadowElevation = 7.dp,
        modifier = Modifier
            .size(size)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.padding(size * .28f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageAppearanceSheet(
    selected: MessageThemeMode,
    palette: MessagePalette,
    unreadActivityCount: Int,
    onSelect: (MessageThemeMode) -> Unit,
    onOpenActivity: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 11.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(palette.textMuted.copy(alpha = .55f))
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = palette.accent.copy(alpha = .18f)) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Message appearance", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Pink is the default theme", color = palette.textSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MessageThemeMode.entries.forEach { mode ->
                ThemePreviewCard(
                    mode = mode,
                    selected = mode == selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(mode) }
                )
            }
        }

        HorizontalDivider(color = palette.border)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenActivity)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = palette.glassElevated) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.padding(10.dp).size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Activity", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Open message-related notifications", color = palette.textSecondary, fontSize = 11.sp)
            }
            if (unreadActivityCount > 0) {
                Badge(containerColor = palette.accent, contentColor = Color.White) {
                    Text(unreadActivityCount.coerceAtMost(99).toString())
                }
            }
        }
        Spacer(Modifier.height(18.dp).navigationBarsPadding())
    }
}

@Composable
private fun ThemePreviewCard(
    mode: MessageThemeMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val preview = messagePalette(mode)
    Surface(
        color = preview.glassElevated,
        contentColor = preview.textPrimary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) preview.accent else preview.border),
        shadowElevation = if (selected) 5.dp else 0.dp,
        modifier = modifier
            .height(112.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.role = Role.RadioButton }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(preview.backgroundBrush())
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 7.dp)
                        .size(width = 34.dp, height = 12.dp)
                        .clip(CircleShape)
                        .background(preview.incomingBubble)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 7.dp, bottom = 5.dp)
                        .size(width = 28.dp, height = 10.dp)
                        .clip(CircleShape)
                        .background(preview.outgoingBubble)
                )
                if (selected) {
                    Surface(
                        color = preview.accent,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(3.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(mode.displayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (mode == MessageThemeMode.PINK) {
                Text("Default", color = preview.textSecondary, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: MessagePalette,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = palette.glassElevated.copy(alpha = if (palette.isLight) .90f else .72f),
        contentColor = palette.textPrimary,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .size(size)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = palette.textPrimary,
            modifier = Modifier.padding(size * .25f)
        )
    }
}

@Composable
private fun RingAvatar(
    url: String,
    name: String,
    palette: MessagePalette,
    size: Dp,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    emphasizeRing: Boolean = true
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (emphasizeRing) {
                        Brush.linearGradient(listOf(palette.accent, palette.accentSecondary, Color(0xFF8B5CF6)))
                    } else {
                        Brush.linearGradient(listOf(palette.border, palette.border))
                    },
                    shape = CircleShape
                )
                .padding(if (emphasizeRing) 2.dp else 1.dp)
        ) {
            AvatarImage(
                url = url,
                name = name,
                palette = palette,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (online) {
            Box(
                modifier = Modifier
                    .size(size * .25f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(palette.online)
                    .border(2.dp, palette.glass, CircleShape)
            )
        }
    }
}

@Composable
private fun AvatarImage(
    url: String,
    name: String,
    palette: MessagePalette,
    modifier: Modifier
) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier.clip(CircleShape).background(palette.glass),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials(name),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun VerificationDot(badge: VerificationBadge, legacyVerified: Boolean = false) {
    val effective = if (badge == VerificationBadge.NONE && legacyVerified) VerificationBadge.BLUE else badge
    if (effective == VerificationBadge.NONE) return
    Text(
        "✓",
        color = if (effective == VerificationBadge.GOLD) Color(0xFFFFB000) else Color(0xFF55A7FF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun rememberSpeechInput(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(latestOnResult)
        }
    }
    return remember(context, launcher) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
            }
            runCatching { launcher.launch(intent) }
                .onFailure {
                    Toast.makeText(context, "Voice input is not available on this device.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}

private fun launchSecureCall(
    context: Context,
    conversation: ChatConversation,
    kind: MessageCallKind
) {
    val roomId = conversation.id.ifBlank { conversation.partnerUsername }
        .replace(Regex("[^A-Za-z0-9_-]"), "-")
    val base = "https://meet.jit.si/Blink-$roomId"
    val target = if (kind == MessageCallKind.AUDIO) {
        "$base#config.startWithVideoMuted=true"
    } else {
        base
    }
    openExternalUri(context, Uri.parse(target), "Open secure call")
}

private fun openExternalUri(context: Context, uri: Uri, chooserTitle: String? = null) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val launchIntent = chooserTitle?.let { Intent.createChooser(intent, it) } ?: intent
    runCatching { context.startActivity(launchIntent) }
        .onFailure {
            Toast.makeText(context, "No compatible app is available.", Toast.LENGTH_SHORT).show()
        }
}

private fun formatCallDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase(Locale.ROOT)
        else -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.ROOT)
    }
}
