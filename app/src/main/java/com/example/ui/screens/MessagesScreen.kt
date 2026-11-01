package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ───────────────────────────── Palette ───────────────────────────── */

enum class OutgoingBubbleStyle { Light, Green }

/** Provide your own image loader (Coil etc.) once, high in the tree. */
val LocalBlinkImageLoader =
    staticCompositionLocalOf<(@Composable (url: String, modifier: Modifier) -> Unit)?> { null }

val LocalOutgoingBubbleStyle = compositionLocalOf { OutgoingBubbleStyle.Light }

object BlinkChatColors {
    val Background = Color(0xFF0B0B0D)
    val Panel = Color(0xFF1B1B1E)
    val PanelRaised = Color(0xFF26262A)
    val Incoming = Color(0xFF2A2A2D)
    val IncomingText = Color(0xFFF2F2F4)
    val Accent = Color(0xFF1F6FFF)
    val TickRead = Color(0xFF3B8BFF)
    val Muted = Color(0xFF8E8E93)
    val Online = Color(0xFF2F80FF)
    val Danger = Color(0xFFFF4D4F)
    val OutgoingLight = Color(0xFFE8E8EA)
    val OutgoingGreen = Color(0xFF005C4B)

    fun outgoing(s: OutgoingBubbleStyle) =
        if (s == OutgoingBubbleStyle.Light) OutgoingLight else OutgoingGreen

    fun outgoingText(s: OutgoingBubbleStyle) =
        if (s == OutgoingBubbleStyle.Light) Color(0xFF111114) else Color.White

    fun outgoingMeta(s: OutgoingBubbleStyle) =
        if (s == OutgoingBubbleStyle.Light) Color(0xFF6C6C72) else Color(0xB3FFFFFF)
}

/* ───────────────────────────── Models ───────────────────────────── */

enum class DeliveryStatus { Sending, Sent, Delivered, Read, Failed }

data class MediaRef(val url: String, val isVideo: Boolean = false, val durationLabel: String? = null)

data class PollOption(val label: String, val votes: Int)

sealed interface ChatContent {
    data class Text(val text: String) : ChatContent
    data class Media(val items: List<MediaRef>, val caption: String? = null) : ChatContent
    data class Voice(val durationMs: Long, val waveform: List<Float>) : ChatContent
    data class File(
        val name: String,
        val sizeLabel: String,
        val typeLabel: String,
        val downloaded: Boolean = false,
    ) : ChatContent
    data class Poll(
        val question: String,
        val options: List<PollOption>,
        val selected: Int? = null,
        val title: String = "Quick Poll",
    ) : ChatContent
}

data class ReplyPreview(val messageId: String, val sender: String, val snippet: String)

data class ChatMessage(
    val id: String,
    val outgoing: Boolean,
    val content: ChatContent,
    val timeLabel: String,
    val dateLabel: String,
    val status: DeliveryStatus = DeliveryStatus.Sent,
    val replyTo: ReplyPreview? = null,
    val reactions: List<String> = emptyList(),
    val starred: Boolean = false,
    val edited: Boolean = false,
    val upload: AlbumUploadState? = null,
)

data class BlinkConversation(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val lastMessage: String,
    val lastStatus: DeliveryStatus? = null,
    val timeLabel: String,
    val unread: Int = 0,
    val online: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val hasStory: Boolean = false,
    val statusText: String = "",
    val pinnedNote: String? = null,
)

data class VoicePlaybackState(
    val playingId: String? = null,
    val progress: Float = 0f,
    val speed: Float = 1f,
)

fun ChatMessage.snippet(): String = when (val c = content) {
    is ChatContent.Text -> c.text
    is ChatContent.Media ->
        c.caption?.takeIf { it.isNotBlank() }
            ?: if (c.items.all { it.isVideo }) "🎬 Video" else "📷 Photo"
    is ChatContent.Voice -> "🎤 Voice message"
    is ChatContent.File -> "📄 ${c.name}"
    is ChatContent.Poll -> "📊 ${c.question}"
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/* ───────────────────────────── Actions (wire to ChatRepository) ───────────────────────────── */

/**
 * Implement this in your ViewModel / adapter and forward each call to ChatRepository,
 * MessageMediaService, CallRepository etc. Every method has an empty default.
 */
interface BlinkMessagesActions {
    fun onOpenConversation(id: String) {}
    fun onMarkRead(conversationId: String) {}
    fun onNewMessage() {}
    fun onTogglePin(conversationId: String) {}
    fun onToggleMute(conversationId: String) {}

    fun onSendText(conversationId: String, text: String, replyToId: String?) {}
    fun onEdit(conversationId: String, messageId: String, newText: String) {}
    fun onReact(conversationId: String, messageId: String, emoji: String) {}
    fun onStar(conversationId: String, messageId: String) {}
    fun onForward(conversationId: String, messageId: String) {}
    fun onShare(conversationId: String, messageId: String) {}
    fun onReport(conversationId: String, messageId: String) {}
    fun onDelete(conversationId: String, messageId: String, forEveryone: Boolean) {}

    fun onAttachmentPicked(conversationId: String, kind: AttachmentKind) {}
    fun onOpenMedia(messageId: String, index: Int) {}
    fun onDownload(messageId: String) {}
    fun onVote(conversationId: String, messageId: String, optionIndex: Int) {}
    fun onRetryUpload(messageId: String) {}
    fun onCancelUpload(messageId: String) {}

    fun onVoiceRecorded(conversationId: String, file: java.io.File, durationMs: Long, waveform: List<Float>) {}
    fun onVoiceToggle(messageId: String) {}
    fun onVoiceSeek(messageId: String, fraction: Float) {}
    fun onVoiceSpeedChange() {}

    fun onVoiceCall(conversationId: String) {}
    fun onVideoCall(conversationId: String) {}
    fun onOpenProfile(conversationId: String) {}
    fun onChatExtra(conversationId: String, action: ChatExtraAction) {}
}

object NoOpBlinkMessagesActions : BlinkMessagesActions

/* ───────────────────────────── Shared building blocks ───────────────────────────── */

@Composable
fun BlinkMediaImage(url: String?, modifier: Modifier = Modifier) {
    val loader = LocalBlinkImageLoader.current
    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color(0xFF2E2E34), Color(0xFF1B1B1F)))
        )
    ) {
        if (url != null) loader?.invoke(url, Modifier.fillMaxSize())
    }
}

@Composable
fun BlinkAvatar(
    name: String,
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    storyRing: Boolean = false,
    online: Boolean = false,
) {
    val loader = LocalBlinkImageLoader.current
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (storyRing) Modifier.border(
                        2.dp,
                        Brush.sweepGradient(
                            listOf(BlinkChatColors.Accent, Color(0xFF8B5CF6), BlinkChatColors.Accent)
                        ),
                        CircleShape
                    ) else Modifier
                )
                .padding(if (storyRing) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(BlinkChatColors.PanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null && loader != null) {
                loader(url, Modifier.fillMaxSize())
            } else {
                Text(
                    initials,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.36f).sp,
                )
            }
        }
        if (online) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(BlinkChatColors.Background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(BlinkChatColors.Online)
            )
        }
    }
}

@Composable
fun BlinkCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = BlinkChatColors.Panel,
    tint: Color = Color.White,
    size: Dp = 48.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun DeliveryTicks(
    status: DeliveryStatus,
    idleTint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    Crossfade(targetState = status, animationSpec = tween(220), label = "ticks") { s ->
        val icon: ImageVector
        val tint: Color
        when (s) {
            DeliveryStatus.Sending -> { icon = Icons.Filled.Schedule; tint = idleTint }
            DeliveryStatus.Sent -> { icon = Icons.Filled.Done; tint = idleTint }
            DeliveryStatus.Delivered -> { icon = Icons.Filled.DoneAll; tint = idleTint }
            DeliveryStatus.Read -> { icon = Icons.Filled.DoneAll; tint = BlinkChatColors.TickRead }
            DeliveryStatus.Failed -> { icon = Icons.Filled.ErrorOutline; tint = BlinkChatColors.Danger }
        }
        Icon(icon, contentDescription = s.name, tint = tint, modifier = modifier.size(size))
    }
}

/* ───────────────────────────── Entry point ───────────────────────────── */

@Composable
fun MessagesScreen(
    conversations: List<BlinkConversation>,
    messages: Map<String, List<ChatMessage>>,
    modifier: Modifier = Modifier,
    actions: BlinkMessagesActions = NoOpBlinkMessagesActions,
    voice: VoicePlaybackState = VoicePlaybackState(),
    outgoingStyle: OutgoingBubbleStyle = OutgoingBubbleStyle.Light,
    initialConversationId: String? = null,
    /** Hide your bottom navigation while a chat is open (chat must be full-screen). */
    onChatOpenChanged: (Boolean) -> Unit = {},
) {
    var openId by rememberSaveable { mutableStateOf(initialConversationId) }

    BackHandler(enabled = openId != null) { openId = null }
    LaunchedEffect(openId) { onChatOpenChanged(openId != null) }

    CompositionLocalProvider(LocalOutgoingBubbleStyle provides outgoingStyle) {
        Box(modifier.fillMaxSize().background(BlinkChatColors.Background)) {
            AnimatedContent(
                targetState = openId,
                transitionSpec = {
                    if (targetState != null && initialState == null) {
                        (slideInHorizontally(tween(320)) { it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(220)))
                    } else {
                        (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(220)))
                    }
                },
                label = "messagesNavigation",
            ) { id ->
                val conversation = conversations.firstOrNull { it.id == id }
                if (id == null || conversation == null) {
                    BlinkReferenceInbox(
                        conversations = conversations,
                        actions = actions,
                        onOpen = { actions.onOpenConversation(it); openId = it },
                    )
                } else {
                    PremiumMessagesScreen(
                        conversation = conversation,
                        messages = messages[conversation.id].orEmpty(),
                        actions = actions,
                        voice = voice,
                        onBack = { openId = null },
                    )
                }
            }
        }
    }
}
