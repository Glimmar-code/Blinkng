package com.example.ui.screens

import com.example.performance.rememberBlinkReduceMotion
import com.blinkng.shared.BlinkDesignTokens
import com.blinkng.shared.BlinkChatReferenceStyle
import com.blinkng.shared.BlinkReplySwipePhysics
import com.blinkng.shared.BlinkElasticGesturePhysics
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.blinkng.shared.BlinkChatMediaAccessPolicy

import com.example.ui.components.BlinkSkeletonChatHistory
import com.example.ui.components.blinkPressFeedback

import android.Manifest
import com.example.data.local.PersistentTextDraftStore
import com.example.data.local.rememberPersistentTextState
import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.supabase.MessageMediaService
import com.example.data.supabase.BlinkChatMediaPreferences
import com.example.data.supabase.BlinkChatNativePollService
import com.example.data.supabase.BlinkChatPhotoEditor
import com.blinkng.shared.BlinkChatImageQuality
import androidx.compose.ui.graphics.asImageBitmap
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.models.ActivityItem
import com.blinkng.shared.BlinkMessageDatePolicy
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.ChatPrivacySettings
import com.example.data.models.MessageStatus
import com.example.data.models.UserProfile
import com.example.ui.components.BlinkUserPickerSheet
import com.example.data.models.Story
import com.example.data.models.VerificationBadge
import com.example.ui.components.BlinkVipMarkForUsername
import com.example.util.safeString
import com.example.ui.theme.BlinkMessageTheme
import com.example.ui.theme.MessagePalette
import com.example.ui.theme.MessageThemeMode
import com.example.ui.theme.messagePalette
import com.example.ui.theme.referenceChatPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private const val MESSAGE_PREFERENCES = "blink_message_preferences"
private const val MESSAGE_THEME_KEY = "message_theme"

private data class PendingChatNativePoll(
    val draft: com.blinkng.shared.BlinkChatPollDraft,
    val existingIds: Set<String>,
    val createdAtMillis: Long = System.currentTimeMillis()
)
private data class ChatPhotoReviewItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val caption: String = ""
)

private enum class MessageCallKind { AUDIO, VIDEO }
private typealias MessageInboxTab = com.blinkng.shared.BlinkMessageInbox

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
    val verificationBadge: VerificationBadge = VerificationBadge.NONE,
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
    chatPrivacySettings: ChatPrivacySettings = ChatPrivacySettings(),
    myAvatar: String,
    myName: String,
    activePartner: String?,
    isConversationFullScreen: Boolean = false,
    onConversationFullScreenChange: (Boolean) -> Unit = {},
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, String?) -> Unit,
    onForwardMessage: (String, ChatMessage) -> Unit = { _, _ -> },
    onSendVideo: (String, Uri) -> Unit = { _, _ -> },
    onSendVideoAlbum: (String, List<Uri>) -> Unit = { _, _ -> },
    onSendAttachment: (String, Uri, String) -> Unit = { _, _, _ -> },
    onSendPhotoAlbum: (String, List<Pair<Uri, String>>, BlinkChatImageQuality) -> Unit = { _, _, _ -> },
    onSendViewOncePicture: (String, Uri, String, BlinkChatImageQuality) -> Unit = { _, _, _, _ -> },
    onPresenceChange: (String, String) -> Unit = { _, _ -> },
    onRetryMessage: ((String, ChatMessage) -> Unit)? = null,
    onCancelAttachment: ((String, ChatMessage) -> Unit)? = null,
    hasMoreMessages: (String) -> Boolean = { false },
    isLoadingOlder: (String) -> Boolean = { false },
    onLoadOlder: (String) -> Unit = {},
    isLoadingMessages: (String) -> Boolean = { false },
    onProfileClick: (String) -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenActivity: () -> Unit,
    onUpdateChatPrivacy: (ChatPrivacySettings) -> Unit = {},
    onComposeMessage: () -> Unit = {},
    onSearchUsers: suspend (String, Int, String?, String?) -> List<UserProfile> = { _, _, _, _ -> emptyList() },
    onCreateGroup: () -> Unit = {},
    onMyProfileClick: () -> Unit = {},
    onGameClick: () -> Unit = {},
    interactionActions: ChatInteractionActions = ChatInteractionActions(),
    isConnected: Boolean = true,
    isLoading: Boolean = false,
    isDark: Boolean = false,
    onVisibleInboxReceipt: (String, String) -> Unit = { _, _ -> },
    showInboxBehindChat: Boolean = true
) {
    val context = LocalContext.current
    val reduceRouteMotion = rememberBlinkReduceMotion()
    val preferences = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences(MESSAGE_PREFERENCES, Context.MODE_PRIVATE)
    }
    var storedTheme by rememberSaveable {
        mutableStateOf(preferences.safeString(MESSAGE_THEME_KEY, null))
    }
    val messageTheme = MessageThemeMode.fromStorage(storedTheme)
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showPrivacySheet by rememberSaveable { mutableStateOf(false) }
    var showUserPicker by rememberSaveable { mutableStateOf(false) }
    var activeCall by remember { mutableStateOf<MessageCallState?>(null) }

    val liveActiveConversation = activePartner?.let { partner ->
        conversations.firstOrNull { it.partnerUsername.equals(partner, ignoreCase = true) }
    }
    var retainedActiveConversation by remember {
        mutableStateOf<ChatConversation?>(liveActiveConversation)
    }
    LaunchedEffect(liveActiveConversation) {
        if (liveActiveConversation != null) retainedActiveConversation = liveActiveConversation
    }
    val activeConversation = activePartner?.let { partner ->
        liveActiveConversation
            ?: retainedActiveConversation?.takeIf { it.partnerUsername.equals(partner, ignoreCase = true) }
            ?: ChatConversation(
                id = "local_$partner",
                partnerUsername = partner,
                partnerName = partner.replace("_", " ").replace(".", " "),
                partnerAvatar = ""
            )
    }

    // Keep the old partner rendered until the closing slide completes.
    var chatSlideVisible by remember { mutableStateOf(false) }
    LaunchedEffect(activePartner) {
        if (activeConversation != null) retainedActiveConversation = activeConversation
        chatSlideVisible = activeConversation != null
    }

    BackHandler(enabled = activeCall != null) { activeCall = null }
    BackHandler(enabled = activeCall == null && activeConversation != null) {
        onCloseConversation()
    }

    BlinkMessageTheme(messageTheme) { palette ->
        AnimatedContent(
            targetState = activeCall,
            transitionSpec = {
                if (reduceRouteMotion) {
                    androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
                } else {
                    // Do not scale the live-call surface: native video renderers
                    // and chat text should never become soft or jittery.
                    fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                }
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
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Telegram-style interactive back needs the actual previous
                    // conversation list underneath the chat even at full screen.
                    // Do not blank it while the top screen follows the finger.
                    // Overlay chats from Feed/Profile/Connect reveal the actual prior screen.
                    if (showInboxBehindChat) {
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
                            onComposeMessage = { showUserPicker = true },
                            onMyProfileClick = onMyProfileClick,
                            onGameClick = onGameClick,
                            onNotificationsClick = onOpenActivity,
                            hasUnreadNotifications = activities.any { it.isUnread },
                            isConnected = isConnected,
                            isLoading = isLoading,
                            isDark = isDark,
                            // Hidden inbox rows must not mark conversations read.
                            onVisibleInboxReceipt = { conversationId, receiptId ->
                                if (!chatSlideVisible) onVisibleInboxReceipt(conversationId, receiptId)
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = chatSlideVisible && activeConversation != null,
                        enter = if (reduceRouteMotion) {
                            androidx.compose.animation.EnterTransition.None
                        } else slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ),
                        exit = if (reduceRouteMotion) {
                            androidx.compose.animation.ExitTransition.None
                        } else slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        )
                    ) {
                        val displayedConversation = activeConversation ?: retainedActiveConversation
                        if (displayedConversation != null) {
                            PremiumMessagesMasterDetail(
                                conversations = conversations,
                                activeConversation = displayedConversation,
                                palette = referenceChatPalette(palette),
                                isFullScreen = isConversationFullScreen,
                                onFullScreenChange = onConversationFullScreenChange,
                                onOpenConversation = onOpenConversation,
                                onCloseConversation = onCloseConversation,
                                onSendMessage = onSendMessage,
                                onForwardMessage = onForwardMessage,
                                onSendVideo = onSendVideo,
                                onSendVideoAlbum = onSendVideoAlbum,
                                onSendAttachment = onSendAttachment,
                                onSendPhotoAlbum = onSendPhotoAlbum,
                                onSendViewOncePicture = onSendViewOncePicture,
                                onPresenceChange = onPresenceChange,
                                allowLinkPreviews = chatPrivacySettings.allowLinkPreviews,
                                onRetryMessage = onRetryMessage,
                                onCancelAttachment = onCancelAttachment,
                                hasMoreMessages = hasMoreMessages,
                                isLoadingOlder = isLoadingOlder,
                                onLoadOlder = onLoadOlder,
                                isLoadingMessages = isLoadingMessages,
                                interactionActions = interactionActions,
                                onProfileClick = onProfileClick,
                                onStartCall = { conversation, kind ->
                                    launchSecureCall(context, conversation, kind)
                                },
                                isConnected = isConnected
                            )
                        }
                    }
                }
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
                onOpenPrivacy = {
                    showAppearanceSheet = false
                    showPrivacySheet = true
                },
                onOpenActivity = {
                    showAppearanceSheet = false
                    onOpenActivity()
                },
                onDismiss = { showAppearanceSheet = false }
            )
        }

        if (showPrivacySheet && activeCall == null) {
            ChatPrivacySettingsSheet(
                settings = chatPrivacySettings,
                palette = palette,
                onChange = onUpdateChatPrivacy,
                onDismiss = { showPrivacySheet = false }
            )
        }
    }
    if (showUserPicker) {
        BlinkUserPickerSheet(
            title = "New Message",
            onDismiss = { showUserPicker = false },
            searchProfiles = onSearchUsers,
            onSelectUser = { user ->
                showUserPicker = false
                onOpenConversation(user.username)
            },
            onCreateGroup = {
                showUserPicker = false
                onCreateGroup()
            }
        )
    }
}


/** Pure direction lock for Telegram-style chat back. Vertical scroll and zoom win. */
internal fun isTelegramChatBackIntent(
    deltaX: Float,
    deltaY: Float,
    touchSlopPx: Float
): Boolean = deltaX.isFinite() && deltaY.isFinite() && touchSlopPx.isFinite() &&
    touchSlopPx >= 0f && deltaX > touchSlopPx && deltaX > abs(deltaY) * 1.35f

private fun Modifier.observeRightSwipeToClose(
    thresholdPx: Float,
    onSwipeRight: () -> Unit,
    onDragProgress: (Float) -> Unit,
    onRelease: () -> Unit
): Modifier = pointerInput(thresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var committed = false
        var childHandled = false
        var visualDragActive = false
        val touchSlopPx = viewConfiguration.touchSlop
        while (true) {
            // Final pass lets a photo carousel or child swipe claim its own gesture.
            // This observer never consumes pointer changes from the conversation.
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (event.changes.size != 1) childHandled = true
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.pressed && change.isConsumed) childHandled = true
            tracker.addPosition(change.uptimeMillis, change.position)
            val horizontal = change.position.x - down.position.x
            val vertical = change.position.y - down.position.y
            val rightIntent = isTelegramChatBackIntent(horizontal, vertical, touchSlopPx)
            if (change.pressed) {
                if (!childHandled && rightIntent) {
                    visualDragActive = true
                    onDragProgress(horizontal - touchSlopPx)
                } else if (visualDragActive) {
                    visualDragActive = false
                    onDragProgress(0f)
                }
            } else {
                committed = !childHandled && rightIntent && BlinkElasticGesturePhysics.shouldCommit(
                    horizontal, BlinkElasticGesturePhysics.resolvedFlickVelocity(
                        horizontal, tracker.calculateVelocity().x, change.uptimeMillis - down.uptimeMillis
                    ), thresholdPx,
                    thresholdPx * 0.36f, thresholdPx * 14.5f
                )
                break
            }
        }
        onRelease()
        if (committed) onSwipeRight()
    }
}

@Composable
private fun PremiumMessagesMasterDetail(
    conversations: List<ChatConversation>,
    activeConversation: ChatConversation,
    palette: MessagePalette,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, String?) -> Unit,
    onForwardMessage: (String, ChatMessage) -> Unit,
    onSendVideo: (String, Uri) -> Unit,
    onSendVideoAlbum: (String, List<Uri>) -> Unit,
    onSendAttachment: (String, Uri, String) -> Unit,
    onSendPhotoAlbum: (String, List<Pair<Uri, String>>, BlinkChatImageQuality) -> Unit,
    onSendViewOncePicture: (String, Uri, String, BlinkChatImageQuality) -> Unit,
    onPresenceChange: (String, String) -> Unit,
    allowLinkPreviews: Boolean,
    onRetryMessage: ((String, ChatMessage) -> Unit)?,
    onCancelAttachment: ((String, ChatMessage) -> Unit)?,
    hasMoreMessages: (String) -> Boolean,
    isLoadingOlder: (String) -> Boolean,
    onLoadOlder: (String) -> Unit,
    isLoadingMessages: (String) -> Boolean,
    interactionActions: ChatInteractionActions,
    onProfileClick: (String) -> Unit,
    onStartCall: (ChatConversation, MessageCallKind) -> Unit,
    isConnected: Boolean
) {
    val density = LocalDensity.current
    val groupContext = LocalContext.current
    val chatReducedMotion = rememberBlinkReduceMotion()
    val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    var closeDragPx by remember(activeConversation.partnerUsername) { mutableStateOf(0f) }
    var closeDragging by remember(activeConversation.partnerUsername) { mutableStateOf(false) }
    val closeVisual by animateFloatAsState(
        // Follow the user's finger instead of the old capped rubber offset.
        // The inbox remains fully drawn underneath the chat during the drag.
        targetValue = if (chatReducedMotion) 0f else closeDragPx,
        animationSpec = if (closeDragging || chatReducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "chatElasticClose"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = closeVisual.coerceAtMost(size.width) }
            .semantics { contentDescription = "Chat" }
            .observeRightSwipeToClose(
                thresholdPx = swipeThresholdPx,
                onSwipeRight = onCloseConversation,
                onDragProgress = { closeDragging = true; closeDragPx = it },
                onRelease = { closeDragging = false; closeDragPx = 0f }
            )
    ) {
        AnimatedContent(
            targetState = activeConversation.partnerUsername,
            transitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { -it / 6 },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(
                        targetOffsetX = { -it / 6 },
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(120)))
            },
            label = "chatQuickSwitch"
        ) { partner ->
            val displayedConversation = conversations.firstOrNull {
                it.partnerUsername.equals(partner, ignoreCase = true)
            } ?: activeConversation

            PremiumChatDetail(
                conversation = displayedConversation,
                reducedMotion = chatReducedMotion,
                allConversations = conversations,
                palette = palette,
                onBack = onCloseConversation,
                hasMoreMessages = hasMoreMessages(displayedConversation.id),
                isLoadingOlder = isLoadingOlder(displayedConversation.id),
                onLoadOlder = { onLoadOlder(displayedConversation.partnerUsername) },
                isLoadingMessages = isLoadingMessages(displayedConversation.id),
                onSend = { content, replyTo ->
                    onSendMessage(displayedConversation.partnerUsername, content, replyTo)
                },
                onForward = { target, message ->
                    onForwardMessage(target, message)
                },
                interactionActions = interactionActions,
                onSendVideo = { onSendVideo(displayedConversation.partnerUsername, it) },
                onSendVideoAlbum = { onSendVideoAlbum(displayedConversation.partnerUsername, it) },
                onSendAttachment = { uri, kind ->
                    onSendAttachment(displayedConversation.partnerUsername, uri, kind)
                },
                onSendPhotoAlbum = { uris, quality ->
                    onSendPhotoAlbum(displayedConversation.partnerUsername, uris, quality)
                },
                onSendViewOncePicture = { uri ->
                    onSendViewOncePicture(
                        displayedConversation.partnerUsername, uri, "", BlinkChatImageQuality.STANDARD
                    )
                },
                onPresenceChange = { state ->
                    onPresenceChange(displayedConversation.partnerUsername, state)
                },
                allowLinkPreviews = allowLinkPreviews,
                onRetry = { message ->
                    onRetryMessage?.invoke(displayedConversation.partnerUsername, message)
                },
                onProfileClick = {
                    if (!displayedConversation.isGroup) {
                        onProfileClick(displayedConversation.partnerUsername)
                    }
                },
                onCancelAttachment = { message ->
                    onCancelAttachment?.invoke(displayedConversation.partnerUsername, message)
                },
                onAudioCall = {
                    if (!displayedConversation.isGroup) {
                        onStartCall(displayedConversation, MessageCallKind.AUDIO)
                    } else {
                        Toast.makeText(groupContext, "Group calls aren't available yet.", Toast.LENGTH_SHORT).show()
                    }
                },
                onVideoCall = {
                    if (!displayedConversation.isGroup) {
                        onStartCall(displayedConversation, MessageCallKind.VIDEO)
                    } else {
                        Toast.makeText(groupContext, "Group calls aren't available yet.", Toast.LENGTH_SHORT).show()
                    }
                },
                isConnected = isConnected,
                isFullScreen = true,
                onToggleFullScreen = {}
            )
        }
    }
}

@Composable
private fun ConversationSwitchRail(
    conversations: List<ChatConversation>,
    selectedPartner: String,
    palette: MessagePalette,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit
) {
    val railConversations = remember(conversations, selectedPartner) {
        val selected = conversations.firstOrNull {
            it.partnerUsername.equals(selectedPartner, ignoreCase = true)
        }
        buildList {
            selected?.let(::add)
            addAll(
                conversations.filterNot {
                    it.partnerUsername.equals(selectedPartner, ignoreCase = true)
                }
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onCloseConversation),
        color = palette.glass.copy(alpha = if (palette.isLight) .90f else .72f),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Chats",
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            HorizontalDivider(color = palette.border.copy(alpha = .55f))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(railConversations, key = { "switch_${it.id}" }) { conversation ->
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clickable(role = Role.Button) {
                                onOpenConversation(conversation.partnerUsername)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val selected = conversation.partnerUsername.equals(
                            selectedPartner,
                            ignoreCase = true
                        )
                        RingAvatar(
                            url = conversation.partnerAvatar,
                            name = conversation.partnerName,
                            palette = palette,
                            size = 48.dp,
                            online = conversation.isOnline,
                            emphasizeRing = selected || conversation.unreadCount > 0,
                            modifier = if (selected) Modifier else Modifier.blur(1.8.dp)
                        )
                        if (conversation.unreadCount > 0) {
                            Badge(
                                containerColor = palette.accent,
                                contentColor = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    conversation.unreadCount.coerceAtMost(99).toString(),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
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
    onMyProfileClick: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenAppearance: () -> Unit,
    onComposeMessage: () -> Unit,
    onGameClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    hasUnreadNotifications: Boolean,
    isConnected: Boolean,
    isLoading: Boolean,
    isDark: Boolean,
    onVisibleInboxReceipt: (String, String) -> Unit
) {
    val channelContext = androidx.compose.ui.platform.LocalContext.current
    var query by rememberPersistentTextState(key = "message_home_search")
    val startVoiceSearch = rememberSpeechInput { result -> query = result }
    var inboxTab by rememberSaveable { mutableStateOf(MessageInboxTab.ALL) }
    var showSearch by rememberSaveable { mutableStateOf(query.isNotBlank()) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val unreadCount = conversations.count {
        !it.isArchived && it.inboxCategory != "requests" && (it.unreadCount > 0 || it.isMarkedUnread)
    }
    val requestCount = conversations.count { it.inboxCategory == "requests" && !it.isArchived }
    val archivedCount = conversations.count { it.isArchived }
    val sortedConversations = remember(conversations, inboxTab) {
        conversations.filter { conversation ->
            com.blinkng.shared.BlinkChatInboxPolicy.matches(
                inboxTab, conversation.isArchived, conversation.inboxCategory,
                conversation.unreadCount > 0 || conversation.isMarkedUnread
            )
        }.sortedWith(
            compareByDescending<ChatConversation> { it.isConversationPinned }
                .thenByDescending { it.lastMessageRawTime }
                .thenByDescending { it.id }
        )
    }
    // Inbox search covers all conversation folders, not only the selected filter.
    val filteredConversations = remember(conversations, sortedConversations, query) {
        if (query.isBlank()) sortedConversations
        else conversations.filter {
            com.blinkng.shared.BlinkConversationSearchPolicy.matches(
                query, it.partnerName, it.partnerUsername, it.lastMessage
            )
        }.sortedWith(
            compareByDescending<ChatConversation> { it.isConversationPinned }
                .thenByDescending { it.lastMessageRawTime }
                .thenByDescending { it.id }
        )
    }
    BlinkReferenceInbox(
        conversations = filteredConversations,
        stories = stories,
        myAvatar = myAvatar,
        myName = myName,
        isDark = !palette.isLight,
        query = query,
        onQueryChange = { query = it },
        onVoiceSearch = startVoiceSearch,
        showSearch = showSearch,
        onToggleSearch = { showSearch = !showSearch; if (!showSearch) query = "" },
        showAll = showAll,
        onToggleAll = { showAll = !showAll },
        onOpenConversation = onOpenConversation,
        onProfileClick = onProfileClick,
        onMyProfileClick = onMyProfileClick,
        onStoryClick = onStoryClick,
        onAddStoryClick = onAddStoryClick,
        onGameClick = onGameClick,
        onNotificationsClick = onNotificationsClick,
        hasUnreadNotifications = hasUnreadNotifications,
        onComposeMessage = onComposeMessage,
        onOpenSettings = onOpenAppearance,
        isConnected = isConnected,
        isLoading = isLoading && conversations.isEmpty() && query.isBlank(),
        onVisibleInboxReceipt = onVisibleInboxReceipt,
        filterContent = {
            MessageInboxTabs(
                selected = inboxTab,
                requestCount = requestCount,
                unreadCount = unreadCount,
                archivedCount = archivedCount,
                palette = messagePalette(if (isDark) MessageThemeMode.DARK else MessageThemeMode.LIGHT),
                onSelect = { inboxTab = it },
                onChannelsClick = {
                    channelContext.startActivity(
                        Intent(channelContext, com.example.ProfessionalCenterActivity::class.java)
                            .putExtra("section", "communities")
                    )
                }
            )
        }
    )
}

@Composable
private fun MessageInboxTabs(
    selected: MessageInboxTab,
    requestCount: Int,
    unreadCount: Int,
    archivedCount: Int,
    palette: MessagePalette,
    onSelect: (MessageInboxTab) -> Unit,
    onChannelsClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MessageInboxTabChip(
                label = "Channels",
                selected = false,
                count = null,
                palette = palette,
                onClick = onChannelsClick
            )
        }
        item {
            MessageInboxTabChip(
                label = "All",
                selected = selected == MessageInboxTab.ALL,
                count = null,
                palette = palette,
                onClick = { onSelect(MessageInboxTab.ALL) }
            )
        }
        item {
            MessageInboxTabChip(
                label = "Primary",
                selected = selected == MessageInboxTab.PRIMARY,
                count = null,
                palette = palette,
                onClick = { onSelect(MessageInboxTab.PRIMARY) }
            )
        }
        item {
            MessageInboxTabChip(
                label = "Unread",
                selected = selected == MessageInboxTab.UNREAD,
                count = unreadCount.takeIf { it > 0 },
                palette = palette,
                onClick = { onSelect(MessageInboxTab.UNREAD) }
            )
        }
        item {
            MessageInboxTabChip(
                label = "Requests",
                selected = selected == MessageInboxTab.REQUESTS,
                count = requestCount.takeIf { it > 0 },
                palette = palette,
                onClick = { onSelect(MessageInboxTab.REQUESTS) }
            )
        }
        item {
            MessageInboxTabChip(
                label = "Archived",
                selected = selected == MessageInboxTab.ARCHIVED,
                count = archivedCount.takeIf { it > 0 },
                palette = palette,
                onClick = { onSelect(MessageInboxTab.ARCHIVED) }
            )
        }
    }
}

@Composable
private fun MessageInboxTabChip(
    label: String,
    selected: Boolean,
    count: Int?,
    palette: MessagePalette,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) palette.textPrimary else palette.glassElevated,
        contentColor = if (selected) palette.backgroundMiddle else palette.textPrimary,
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, if (selected) palette.textPrimary else palette.border),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            if (count != null) {
                Badge(
                    containerColor = if (selected) palette.backgroundMiddle else palette.accent,
                    contentColor = if (selected) palette.textPrimary else Color.White
                ) {
                    Text(count.coerceAtMost(99).toString(), fontSize = 11.sp)
                }
            }
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
        if (palette.mode == MessageThemeMode.DARK &&
            palette.incomingBubble == Color(0xFF202C33)) {
            // Subtle original doodle wallpaper in the WhatsApp-reference conversation.
            // Canvas vectors stay device-resolution independent and require no assets.
            Canvas(Modifier.fillMaxSize()) {
                val spacing = 98.dp.toPx()
                val ink = Color(0xFF22323B).copy(alpha = .5f)
                val line = 1.dp.toPx()
                var col = 0
                var x = 18.dp.toPx()
                while (x < size.width) {
                    var row = 0
                    var y = 22.dp.toPx()
                    while (y < size.height) {
                        val shift = if (row % 2 == 0) 0f else 32.dp.toPx()
                        val center = Offset(x + shift, y)
                        val radius = if ((row + col) % 3 == 0) 17.dp.toPx()
                            else 11.dp.toPx()
                        drawCircle(ink, radius, center,
                            style = Stroke(width = line))
                        if ((row + col) % 2 == 0) {
                            drawLine(ink, center - Offset(radius * .5f, 0f),
                                center + Offset(radius * .5f, 0f), strokeWidth = line)
                        } else {
                            drawCircle(ink, 3.dp.toPx(), center,
                                style = Stroke(width = line))
                        }
                        row++
                        y += spacing
                    }
                    col++
                    x += spacing
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(.42f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (palette.isLight) .09f else .17f),
                            Color.Transparent
                        ),
                        center = Offset(120f, 90f),
                        radius = 720f
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth()
                .fillMaxHeight(.30f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.accentSecondary.copy(alpha = if (palette.isLight) .05f else .09f),
                            Color.Transparent
                        ),
                        center = Offset(820f, 420f),
                        radius = 760f
                    )
                )
        )
        content()
    }
}

@Composable
private fun MessagesHeader(
    palette: MessagePalette,
    onMenu: () -> Unit,
    onCompose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 18.dp)
    ) {
        GlassIconButton(
            icon = Icons.Default.Menu,
            contentDescription = "Messages menu",
            palette = palette,
            size = 42.dp,
            onClick = onMenu,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = "Messages",
            color = palette.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
        GlassIconButton(
            icon = Icons.Default.Edit,
            contentDescription = "New message",
            palette = palette,
            size = 42.dp,
            onClick = onCompose,
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
            Text("Search by name, text etc", color = palette.textSecondary, fontSize = 12.sp)
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
private fun ImportantContactsRail(
    conversations: List<ChatConversation>,
    palette: MessagePalette,
    onOpenConversation: (String) -> Unit
) {
    val important = remember(conversations) {
        conversations
            .sortedWith(
                compareByDescending<ChatConversation> { it.unreadCount > 0 }
                    .thenByDescending { it.isOnline }
                    .thenByDescending { it.lastMessageRawTime }
            )
            .take(10)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Important",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (important.isNotEmpty()) {
                Text(
                    "Quick access",
                    color = palette.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (important.isEmpty()) {
            Text(
                "Your recent and active conversations will appear here.",
                color = palette.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(important, key = { "important_${it.id}" }) { conversation ->
                    MatchItem(
                        person = MatchPerson(
                            id = "important_${conversation.id}",
                            name = conversation.partnerName,
                            username = conversation.partnerUsername,
                            avatar = conversation.partnerAvatar,
                            isOnline = conversation.isOnline,
                            hasUnseenStory = false,
                            verificationBadge = when {
                                conversation.verificationBadge != VerificationBadge.NONE -> conversation.verificationBadge
                                conversation.isVerified -> VerificationBadge.BLUE
                                else -> VerificationBadge.NONE
                            },
                            conversation = conversation
                        ),
                        palette = palette,
                        onClick = { onOpenConversation(conversation.partnerUsername) }
                    )
                }
            }
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
            Canvas(Modifier.fillMaxSize()) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = person.name.substringBefore(" ").take(10),
                color = palette.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (person.verificationBadge != VerificationBadge.NONE) {
                VerificationDot(person.verificationBadge)
            }
        }
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
private fun PremiumConversationListSkeleton(
    palette: MessagePalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(7) {
            Surface(
                color = palette.glassElevated.copy(alpha = if (palette.isLight) .70f else .48f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, palette.border.copy(alpha = .70f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(palette.glass.copy(alpha = .88f))
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth(.46f).height(13.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(palette.glass.copy(alpha = .90f))
                        )
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier.fillMaxWidth(.78f).height(10.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(palette.glass.copy(alpha = .66f))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.width(38.dp).height(9.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.glass.copy(alpha = .62f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<ChatConversation>,
    palette: MessagePalette,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        PremiumConversationListSkeleton(palette = palette, modifier = modifier)
        return
    }
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

    val conversationsListState = rememberLazyListState()
    LazyColumn(
        state = conversationsListState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(conversations, key = { conversation -> "conversation:${conversation.id}" }) { conversation ->
            ConversationCard(
                conversation = conversation,
                palette = palette,
                onOpen = { onOpenConversation(conversation.partnerUsername) },
                onAvatarClick = {
                    if (conversation.isGroup) onOpenConversation(conversation.partnerUsername)
                    else onProfileClick(conversation.partnerUsername)
                }
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
    val context = LocalContext.current
    val draftScope = conversation.id.ifBlank { conversation.partnerUsername.lowercase() }
    val draft = remember(conversation.id, conversation.partnerUsername, conversation.lastMessageRawTime) {
        PersistentTextDraftStore.readDraft(
            context = context,
            key = "chat_composer_text",
            scope = draftScope
        )
    }

    val pressInteraction = remember { MutableInteractionSource() }
    Surface(
        color = palette.glassElevated.copy(alpha = if (palette.isLight) .88f else .66f),
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = if (palette.isLight) 3.dp else 5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .blinkPressFeedback(pressInteraction)
            .clickable(
                interactionSource = pressInteraction,
                indication = null,
                onClick = onOpen
            )
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
                    BlinkVipMarkForUsername(
                        username = conversation.partnerUsername,
                        knownVip = if (conversation.partnerIsVip) true else null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        conversation.presenceLabel.isNotBlank() -> conversation.presenceLabel
                        !draft.isNullOrBlank() -> "Draft: $draft"
                        else -> conversation.lastMessage.ifBlank { "Start the conversation" }
                    },
                    color = when {
                        conversation.presenceLabel.isNotBlank() -> palette.accent
                        !draft.isNullOrBlank() -> palette.accent
                        conversation.unreadCount > 0 || conversation.isMarkedUnread -> palette.textSecondary
                        else -> palette.textMuted
                    },
                    fontSize = 11.sp,
                    fontWeight = if (
                        conversation.presenceLabel.isNotBlank() ||
                        !draft.isNullOrBlank() ||
                        conversation.unreadCount > 0 ||
                        conversation.isMarkedUnread
                    ) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    conversation.lastMessageTime,
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                if (conversation.unreadCount > 0 || conversation.isMarkedUnread) {
                    Spacer(Modifier.height(7.dp))
                    Badge(containerColor = palette.accent, contentColor = Color.White) {
                        Text(
                            if (conversation.unreadCount > 0) {
                                conversation.unreadCount.coerceAtMost(99).toString()
                            } else {
                                "•"
                            },
                            fontSize = 11.sp
                        )
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
    reducedMotion: Boolean,
    allConversations: List<ChatConversation>,
    palette: MessagePalette,
    onBack: () -> Unit,
    hasMoreMessages: Boolean,
    isLoadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    isLoadingMessages: Boolean,
    onSend: (String, String?) -> Unit,
    onForward: (String, ChatMessage) -> Unit,
    interactionActions: ChatInteractionActions,
    onSendVideo: (Uri) -> Unit,
    onSendVideoAlbum: (List<Uri>) -> Unit,
    onSendAttachment: (Uri, String) -> Unit,
    onSendPhotoAlbum: (List<Pair<Uri, String>>, BlinkChatImageQuality) -> Unit,
    onSendViewOncePicture: (Uri) -> Unit,
    onPresenceChange: (String) -> Unit,
    allowLinkPreviews: Boolean,
    onRetry: (ChatMessage) -> Unit,
    onCancelAttachment: (ChatMessage) -> Unit,
    onProfileClick: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    isConnected: Boolean,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    val context = LocalContext.current
    val voiceRecorder = remember(conversation.id) { ChatVoiceRecorder(context.applicationContext) }
    var isRecordingVoice by remember(conversation.id) { mutableStateOf(false) }
    var recordingSeconds by remember(conversation.id) { mutableIntStateOf(0) }

    fun startVoiceRecordingNow() {
        voiceRecorder.start()
            .onSuccess {
                isRecordingVoice = true
                recordingSeconds = 0
                onPresenceChange("recording")
            }
            .onFailure {
                Toast.makeText(context, "Unable to start voice recording.", Toast.LENGTH_SHORT).show()
            }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecordingNow()
        else Toast.makeText(context, "Microphone permission is needed for voice notes.", Toast.LENGTH_SHORT).show()
    }

    fun beginVoiceRecording() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startVoiceRecordingNow()
        else voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun finishVoiceRecording(send: Boolean) {
        if (!isRecordingVoice) return
        if (send) {
            voiceRecorder.stopAndKeep()
                .onSuccess { (uri, _) -> onSendAttachment(uri, "voice") }
                .onFailure {
                    Toast.makeText(context, "Voice recording could not be saved.", Toast.LENGTH_SHORT).show()
                }
        } else {
            voiceRecorder.cancel()
        }
        isRecordingVoice = false
        recordingSeconds = 0
        onPresenceChange("online")
    }

    LaunchedEffect(isRecordingVoice) {
        while (isRecordingVoice) {
            recordingSeconds = voiceRecorder.elapsedSeconds()
            delay(500)
        }
    }
    DisposableEffect(conversation.id) {
        onDispose { voiceRecorder.cancel() }
    }

    var text by rememberPersistentTextState(
        key = "chat_composer_text",
        scope = conversation.id.ifBlank { conversation.partnerUsername.lowercase() }
    )
    var showEmojiRail by rememberSaveable(conversation.partnerUsername) { mutableStateOf(false) }
    var showAttachmentSheet by rememberSaveable(conversation.partnerUsername) { mutableStateOf(false) }
    var chatExtraMode by remember(conversation.id) { mutableStateOf<String?>(null) }
    val nativePollService = remember(conversation.id) { BlinkChatNativePollService() }
    var nativePollEnabled by remember(conversation.id) { mutableStateOf(false) }
    var pendingNativePoll by remember(conversation.id) {
        mutableStateOf<PendingChatNativePoll?>(null)
    }
    LaunchedEffect(chatExtraMode) {
        if (chatExtraMode == "poll") {
            nativePollEnabled = nativePollService.isSupported()
        }
    }
    var selectedContact by remember(conversation.id) { mutableStateOf<Pair<String,String>?>(null) }
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            runCatching {
                context.contentResolver.query(uri,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0).orEmpty().take(100)
                        val number = cursor.getString(1).orEmpty().take(60)
                        if (name.isNotBlank() && number.isNotBlank()) name to number else null
                    } else null
                }
            }.onSuccess { info ->
                if (info != null) {
                    selectedContact = info
                    chatExtraMode = "contact"
                } else Toast.makeText(context, "No phone number selected.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Contact unavailable.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var showStickers by remember(conversation.id) { mutableStateOf(false) }
    var aiPhotoUri by remember(conversation.id) { mutableStateOf<Uri?>(null) }
    var aiAudioUri by remember(conversation.id) { mutableStateOf<Uri?>(null) }
    var selectedMessage by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var viewedImageMessage by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var historicalViewerPhotos by remember(conversation.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var forwardingMessage by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var replyingTo by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember(conversation.partnerUsername) { mutableStateOf<ChatMessage?>(null) }
    var showOverflow by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var showMuteOptions by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var showNotificationSettings by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var showSharedContent by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var showContactProfile by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var confirmClearChat by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var searchVisible by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var searchQuery by rememberPersistentTextState(
        key = "chat_search_query",
        scope = conversation.id.ifBlank { conversation.partnerUsername.lowercase() }
    )
    var pinnedOnly by remember(conversation.partnerUsername) { mutableStateOf(false) }
    var showReferencePinBar by remember(conversation.id) { mutableStateOf(true) }
    var starredOnly by remember(conversation.partnerUsername) { mutableStateOf(false) }
    val listState = remember(conversation.id) { LazyListState() }
    val scrollScope = rememberCoroutineScope()
    // A quoted reply can point beyond the currently loaded history window.
    var pendingReplyTargetId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var lastRequestedReplyOldestId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var highlightedReplyId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var activeVideoMessageId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var newMessagesBelow by remember(conversation.id) { mutableIntStateOf(0) }
    val isNearLatest by remember(conversation.id) {
        derivedStateOf {
            val layout = listState.layoutInfo
            if (layout.totalItemsCount == 0) true
            else (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 2
        }
    }
    BackHandler(enabled = showContactProfile) { showContactProfile = false }
    // MESSAGING_RELIABILITY_AUDIT_V3: older pages load only after a real user scroll reaches the top.
    var userHasScrolled by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) userHasScrolled = true
    }
    LaunchedEffect(listState.firstVisibleItemIndex, userHasScrolled, hasMoreMessages, isLoadingOlder) {
        if (userHasScrolled && listState.firstVisibleItemIndex <= 2 && hasMoreMessages && !isLoadingOlder) {
            onLoadOlder()
        }
    }
    var selectedPhotoQuality by remember(conversation.id) {
        mutableStateOf(BlinkChatImageQuality.STANDARD)
    }
    var pendingPhotoReview by remember(conversation.id) {
        mutableStateOf<List<ChatPhotoReviewItem>>(emptyList())
    }
    var photoEditBusy by remember(conversation.id) { mutableStateOf(false) }
    var gallerySelectedPhotoId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var photoAdjustmentItem by remember(conversation.id) { mutableStateOf<ChatPhotoReviewItem?>(null) }

    // Only delete files produced by BLINK's non-destructive editor. Never touch
    // files returned by the Android Photo Picker or another application.
    fun cleanupReviewEdit(uri: Uri) {
        if (uri.scheme != "file") return
        val file = java.io.File(uri.path.orEmpty())
        val isEditorCopy = file.parentFile?.canonicalFile ==
            context.cacheDir.canonicalFile && file.name.startsWith("blink-edited-")
        val isCameraCapture = file.parentFile?.name == "blink-camera" &&
            file.parentFile?.parentFile?.canonicalFile == context.cacheDir.canonicalFile &&
            file.name.startsWith("blink-camera-")
        if (isEditorCopy || isCameraCapture) {
            runCatching { file.delete() }
        }
    }

    fun dismissPhotoReview() {
        pendingPhotoReview.forEach { cleanupReviewEdit(it.uri) }
        pendingPhotoReview = emptyList()
        selectedPhotoQuality = BlinkChatImageQuality.STANDARD
        onPresenceChange("online")
    }

    var galleryPermissionVersion by remember { mutableIntStateOf(0) }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        galleryPermissionVersion++
    }
    fun askForGalleryPermission() {
        galleryPermissionLauncher.launch(
            when {
                android.os.Build.VERSION.SDK_INT >= 34 -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
                android.os.Build.VERSION.SDK_INT >= 33 -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES
                )
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        )
    }
    val photoAlbumPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingPhotoReview = uris.take(10).map { ChatPhotoReviewItem(uri = it) }
        } else {
            selectedPhotoQuality = BlinkChatImageQuality.STANDARD
        }
        onPresenceChange("online")
    }
    var cameraOutputFile by remember(conversation.id) {
        mutableStateOf<java.io.File?>(null)
    }
    val captureChatPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = cameraOutputFile
        if (success && file != null && file.isFile && file.length() > 0L) {
            pendingPhotoReview = listOf(ChatPhotoReviewItem(uri = Uri.fromFile(file)))
        } else {
            file?.delete()
        }
        cameraOutputFile = null
        onPresenceChange("online")
    }
    fun launchChatCamera() {
        runCatching {
            val directory = java.io.File(context.cacheDir, "blink-camera").apply {
                mkdirs()
            }
            val file = java.io.File.createTempFile(
                "blink-camera-", ".jpg", directory
            )
            cameraOutputFile = file
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.chatfiles", file
            )
            captureChatPhoto.launch(uri)
        }.onFailure { failure ->
            cameraOutputFile?.delete()
            cameraOutputFile = null
            Toast.makeText(
                context, failure.message ?: "Camera is unavailable.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchChatCamera()
        else Toast.makeText(context, "Camera permission is required.", Toast.LENGTH_SHORT).show()
    }
    fun requestChatCamera() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) launchChatCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var viewOncePictureChoice by remember(conversation.id) { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (viewOncePictureChoice) onSendViewOncePicture(uri)
            else onSendAttachment(uri, "image")
        }
        viewOncePictureChoice = false
        onPresenceChange("online")
    }
    var pendingVideoReview by remember(conversation.id) { mutableStateOf<Uri?>(null) }
    // Android's media picker allows 1–10 videos without broad storage access.
    // Every selected video must pass the existing explicit edit/review dialog.
    var pendingVideoBatch by remember(conversation.id) { mutableStateOf<List<Uri>>(emptyList()) }
    var videoBatchPreview by remember(conversation.id) { mutableStateOf(false) }
    var editedVideoBatch by remember(conversation.id) { mutableStateOf<List<Uri>>(emptyList()) }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        pendingVideoBatch = uris.take(10)
        editedVideoBatch = emptyList()
        pendingVideoReview = if (pendingVideoBatch.size == 1) pendingVideoBatch.first() else null
        videoBatchPreview = pendingVideoBatch.size > 1
        onPresenceChange("online")
    }
    var pendingFileReview by remember(conversation.id) { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    var scanningDocument by remember(conversation.id) { mutableStateOf(false) }
    val documentScanPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { pages ->
        if (pages.isNotEmpty() && !scanningDocument) {
            scanningDocument = true
            scrollScope.launch {
                runCatching {
                    com.example.media.BlinkChatDocumentScanner.fromPhotos(
                        context, pages.take(10)
                    )
                }.onSuccess { pdf ->
                    pendingFileReview = pdf to false
                }.onFailure {
                    Toast.makeText(context,
                        it.message ?: "Cannot scan these pages.", Toast.LENGTH_LONG).show()
                }
                scanningDocument = false
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingFileReview = uri to true
        onPresenceChange("online")
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingFileReview = uri to false
        onPresenceChange("online")
    }
    val startDictation = rememberSpeechInput { spoken ->
        text = listOf(text.trim(), spoken.trim()).filter { it.isNotBlank() }.joinToString(" ")
    }

    LaunchedEffect(conversation.id, text) {
        if (text.isBlank()) {
            onPresenceChange("online")
        } else {
            onPresenceChange("typing")
            delay(1800)
            onPresenceChange("online")
        }
    }

    // Avoid repeatedly filtering and linearly scanning the entire history for every row.
    val matchedMessages = remember(conversation.messages, searchQuery, pinnedOnly, starredOnly) {
        conversation.messages.filter { message ->
            val matchesSearch = searchQuery.isBlank() || message.text.contains(searchQuery, ignoreCase = true)
            val matchesPinned = !pinnedOnly || message.isPinned
            val matchesStarred = !starredOnly || message.isStarred
            matchesSearch && matchesPinned && matchesStarred
        }
    }
    val albumGroups = remember(matchedMessages) {
        matchedMessages.filter {
            !it.imageViewOnce && !it.imageAlbumId.isNullOrBlank() &&
                it.imageAlbumSize > 1 && !it.attachedImageUrl.isNullOrBlank()
        }.groupBy { it.imageAlbumId.orEmpty() }
    }
    val videoAlbumGroups = remember(matchedMessages) {
        matchedMessages.filter {
            !it.imageAlbumId.isNullOrBlank() && it.imageAlbumSize > 1 &&
                !it.attachedVideoUrl.isNullOrBlank() && !it.deletedForEveryone
        }.groupBy { it.imageAlbumId.orEmpty() }
    }
    val visibleMessages = remember(matchedMessages, albumGroups, videoAlbumGroups) {
        val displayedAlbumIds = hashSetOf<String>()
        matchedMessages.filter { item ->
            val id = item.imageAlbumId
            val group = if (item.messageType == "video") videoAlbumGroups[id]
                else albumGroups[id]
            if (id.isNullOrBlank() || group.orEmpty().size < 2) true
            else displayedAlbumIds.add(item.messageType + id)
        }
    }
    val messagesById = remember(conversation.messages) {
        conversation.messages.associateBy { it.id }
    }
    val visibleMessageIndexes = remember(visibleMessages) {
        visibleMessages.withIndex().associate { (index, message) -> message.id to index }
    }
    val messageDateLabels = remember(visibleMessages) {
        BlinkMessageDatePolicy.labels(visibleMessages.map {
            BlinkMessageDatePolicy.Entry(it.id, it.rawTimestamp)
        })
    }
    val historyHeaderCount = if (isLoadingOlder) 1 else 0

    LaunchedEffect(
        pendingReplyTargetId, conversation.messages.firstOrNull()?.id,
        visibleMessages, hasMoreMessages, isLoadingOlder
    ) {
        val targetId = pendingReplyTargetId ?: return@LaunchedEffect
        val original = messagesById[targetId]
        val position = visibleMessages.indexOfFirst { visible ->
            visible.id == targetId || (
                !original?.imageAlbumId.isNullOrBlank() &&
                    visible.imageAlbumId == original?.imageAlbumId
            )
        }
        if (position >= 0) {
            listState.animateScrollToItem(position + historyHeaderCount)
            highlightedReplyId = targetId
            pendingReplyTargetId = null
            lastRequestedReplyOldestId = null
        } else if (!hasMoreMessages) {
            pendingReplyTargetId = null
            lastRequestedReplyOldestId = null
            Toast.makeText(context, "Original message is no longer available.", Toast.LENGTH_SHORT).show()
        } else if (!isLoadingOlder) {
            val oldestId = conversation.messages.firstOrNull()?.id ?: "empty"
            if (lastRequestedReplyOldestId != oldestId) {
                lastRequestedReplyOldestId = oldestId
                onLoadOlder()
            }
        }
    }
    LaunchedEffect(highlightedReplyId) {
        if (highlightedReplyId != null) {
            delay(1800)
            highlightedReplyId = null
        }
    }

    fun submitMessage(value: String = text) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val edit = editingMessage
        if (edit != null) {
            interactionActions.onEdit(conversation.partnerUsername, edit, clean)
            editingMessage = null
        } else {
            onSend(clean, replyingTo?.id)
            replyingTo = null
        }
        if (value == text) text = ""
        showEmojiRail = false
    }

    val latestVisibleMessageId = conversation.messages.lastOrNull()?.id
    LaunchedEffect(latestVisibleMessageId, pendingNativePoll) {
        val pending = pendingNativePoll ?: return@LaunchedEffect
        val match = conversation.messages.lastOrNull {
            it.isFromMe && it.id !in pending.existingIds &&
                it.text == pending.draft.question &&
                it.status != MessageStatus.SENDING &&
                runCatching { java.util.UUID.fromString(it.id) }.isSuccess
        }
        if (match != null) {
            pendingNativePoll = null // Never attach twice, including on recompose.
            nativePollService.attach(match.id, pending.draft.question,
                pending.draft.options).onFailure {
                Toast.makeText(context,
                    "Question sent, but native poll setup failed. Retry after server verification.",
                    Toast.LENGTH_LONG).show()
            }
        } else if (System.currentTimeMillis() - pending.createdAtMillis >= 120_000L) {
            pendingNativePoll = null
            Toast.makeText(context, "Poll delivery was not confirmed.", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(pendingNativePoll) {
        val pending = pendingNativePoll ?: return@LaunchedEffect
        delay(120_000L)
        if (pendingNativePoll === pending) pendingNativePoll = null
    }
    var previousLatestVisibleMessageId by remember(conversation.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(conversation.id, latestVisibleMessageId, searchQuery, pinnedOnly, starredOnly) {
        val previous = previousLatestVisibleMessageId
        val action = com.blinkng.shared.BlinkMessageScrollPolicy.decide(
            hasMessages = visibleMessages.isNotEmpty(),
            filtering = searchQuery.isNotBlank() || pinnedOnly || starredOnly,
            hasSeenLatest = previous != null,
            userHasScrolled = userHasScrolled,
            nearLatest = isNearLatest,
            latestChanged = previous != null && previous != latestVisibleMessageId
        )
        val lastIndex = visibleMessages.lastIndex + historyHeaderCount
        when (action) {
            com.blinkng.shared.BlinkMessageScrollAction.JUMP_TO_LATEST -> {
                // Large initial histories must jump directly; animating thousands of rows stutters.
                listState.scrollToItem(lastIndex)
                newMessagesBelow = 0
            }
            com.blinkng.shared.BlinkMessageScrollAction.ANIMATE_TO_LATEST -> {
                listState.animateScrollToItem(lastIndex)
                newMessagesBelow = 0
            }
            com.blinkng.shared.BlinkMessageScrollAction.SHOW_NEW_MESSAGE ->
                newMessagesBelow = (newMessagesBelow + 1).coerceAtMost(99)
            com.blinkng.shared.BlinkMessageScrollAction.KEEP -> Unit
        }
        if (visibleMessages.isNotEmpty() && searchQuery.isBlank() && !pinnedOnly && !starredOnly) {
            previousLatestVisibleMessageId = latestVisibleMessageId
        }
    }
    LaunchedEffect(isNearLatest) {
        if (isNearLatest) newMessagesBelow = 0
    }

    MessageBackground(palette) {
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("chat_conversation_root")
        ) {
            ChatHeader(
                conversation = conversation,
                palette = palette,
                onBack = onBack,
                onProfileClick = { showContactProfile = true },
                onAudioCall = onAudioCall,
                onVideoCall = onVideoCall,
                onMore = { showOverflow = true },
                isFullScreen = isFullScreen,
                onToggleFullScreen = onToggleFullScreen
            )

            // A single-row reference banner: jump to the actual pinned message.
            val pinnedPreview = conversation.messages.lastOrNull { it.isPinned }
            if (showReferencePinBar && pinnedPreview != null && !pinnedOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(palette.glassElevated)
                        .heightIn(min = 44.dp)
                        .testTag("chat_reference_pinned_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).clickable {
                            pendingReplyTargetId = pinnedPreview.id
                            lastRequestedReplyOldestId = null
                        }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(Modifier.size(17.dp)) {
                            val c = palette.accent
                            drawLine(c, Offset(size.width * .27f, size.height * .22f),
                                Offset(size.width * .76f, size.height * .22f), 3.dp.toPx(), StrokeCap.Round)
                            drawLine(c, Offset(size.width * .40f, size.height * .22f),
                                Offset(size.width * .40f, size.height * .52f), 3.dp.toPx(), StrokeCap.Round)
                            drawLine(c, Offset(size.width * .22f, size.height * .54f),
                                Offset(size.width * .79f, size.height * .54f), 3.dp.toPx(), StrokeCap.Round)
                            drawLine(c, Offset(size.width * .53f, size.height * .54f),
                                Offset(size.width * .46f, size.height * .92f), 2.dp.toPx(), StrokeCap.Round)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Pinned: " + pinnedPreview.text.ifBlank { "Pinned media" }
                                .replace(Regex("^Pinned:\\s*", RegexOption.IGNORE_CASE), ""),
                            color = palette.textPrimary, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("›", color = palette.textPrimary, fontSize = 23.sp,
                            modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    IconButton(onClick = { showReferencePinBar = false },
                        modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Hide pinned banner",
                            tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (conversation.inboxCategory == "requests") {
                Surface(
                    color = palette.glassElevated,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Message request",
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Accept to move this chat into your main inbox.",
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = palette.accent,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.clickable {
                                    interactionActions.onRespondMessageRequest(conversation, true)
                                }
                            ) {
                                Text(
                                    "Accept",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                            Surface(
                                color = palette.glass,
                                contentColor = palette.textPrimary,
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.clickable {
                                    interactionActions.onRespondMessageRequest(conversation, false)
                                    onBack()
                                }
                            ) {
                                Text(
                                    "Delete",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                            Surface(
                                color = palette.danger.copy(alpha = .10f),
                                contentColor = palette.danger,
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, palette.danger.copy(alpha = .30f)),
                                modifier = Modifier.clickable {
                                    interactionActions.onBlockConversation(conversation)
                                    onBack()
                                }
                            ) {
                                Text(
                                    "Block",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!isConnected) {
                Surface(color = palette.danger.copy(alpha = .18f)) {
                    Text(
                        "Offline — your messages stay in the outbox and send automatically when connection returns.",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            if (searchVisible) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search this chat", color = palette.textMuted) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.textSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { searchVisible = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", tint = palette.textSecondary)
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.glass,
                        unfocusedContainerColor = palette.glass,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (searchVisible && searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${matchedMessages.size} loaded matches",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasMoreMessages) {
                        androidx.compose.material3.TextButton(
                            enabled = !isLoadingOlder && isConnected,
                            onClick = { onLoadOlder() },
                            modifier = Modifier.testTag("chat_search_older")
                        ) {
                            Text(
                                if (isLoadingOlder) "Searching older…" else "Search older messages",
                                color = palette.textPrimary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text("Entire history loaded", color = palette.textMuted, fontSize = 11.sp)
                    }
                }
            }

            if (pinnedOnly || starredOnly) {
                Surface(color = palette.glassElevated, border = BorderStroke(1.dp, palette.border)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (pinnedOnly) "Pinned messages" else "Starred messages",
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Show all",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { pinnedOnly = false; starredOnly = false }
                        )
                    }
                }
            }

            if (visibleMessages.isEmpty()) {
                if (isLoadingMessages) {
                    BlinkSkeletonChatHistory(modifier = Modifier.weight(1f), rows = 6)
                } else if (conversation.messages.isNotEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No messages match your filters", color = palette.textSecondary, fontSize = 12.sp)
                    }
                } else {
                    EmptyConversation(
                        conversation = conversation,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    if (isLoadingOlder) {
                        item(key = "message_history_loading") {
                            BlinkSkeletonChatHistory(modifier = Modifier.fillMaxWidth(), rows = 2)
                        }
                    }

                    items(visibleMessages, key = { it.id }) { message ->
                        val replyTarget = message.replyToMessageId?.let(messagesById::get)
                        val highlightedAlbumId = highlightedReplyId?.let { messagesById[it]?.imageAlbumId }
                        val showReplyHighlight = highlightedReplyId == message.id ||
                            (!highlightedAlbumId.isNullOrBlank() && highlightedAlbumId == message.imageAlbumId)
                        Column(
                            modifier = Modifier
                                .background(
                                    if (showReplyHighlight) palette.accent.copy(alpha = .12f)
                                    else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            messageDateLabels[message.id]?.let { label ->
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = palette.glassElevated,
                                        shape = RoundedCornerShape(100.dp),
                                        border = BorderStroke(1.dp, palette.border)
                                    ) {
                                        Text(
                                            label,
                                            color = palette.textSecondary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                            if (!message.imageAlbumId.isNullOrBlank() &&
                                videoAlbumGroups[message.imageAlbumId].orEmpty().size > 1) {
                                BlinkChatVideoAlbumBubble(videoAlbumGroups[message.imageAlbumId].orEmpty())
                            } else if (!message.imageAlbumId.isNullOrBlank() &&
                                albumGroups[message.imageAlbumId].orEmpty().size > 1) {
                                BlinkChatPhotoAlbumBubble(
                                    messages = albumGroups[message.imageAlbumId].orEmpty(),
                                    palette = palette,
                                    onOpen = { viewedImageMessage = it },
                                    onReply = { replyingTo = message; editingMessage = null },
                                    onActions = { selectedMessage = message }
                                )
                            } else MessageBubble(
                            message = message,
                            reducedMotion = reducedMotion,
                            replyTarget = replyTarget,
                            partnerAvatar = conversation.partnerAvatar,
                            partnerName = conversation.partnerName,
                            palette = palette,
                            onReply = {
                                replyingTo = message
                                editingMessage = null
                            },
                            onReplyTargetClick = {
                                message.replyToMessageId?.let { targetId ->
                                    // Clear local filters before resolving the target. If it is in
                                    // older history, load additional pages until found or exhausted.
                                    searchQuery = ""
                                    pinnedOnly = false
                                    starredOnly = false
                                    lastRequestedReplyOldestId = null
                                    pendingReplyTargetId = targetId
                                }
                            },
                            onActions = { selectedMessage = message },
                            onOpenImage = { viewedImageMessage = it },
                            onRetry = { onRetry(message) },
                            onCancelAttachment = { onCancelAttachment(message) },
                            allowLinkPreviews = allowLinkPreviews,
                            activeVideoMessageId = activeVideoMessageId,
                            onVideoActivate = { activeVideoMessageId = message.id },
                            onVideoDeactivate = {
                                if (activeVideoMessageId == message.id) activeVideoMessageId = null
                            },
                            onLegacyPollChoice = { index ->
                                if (text.isBlank() && editingMessage == null) {
                                    replyingTo = message
                                    text = (index + 1).toString()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Finish your current draft before choosing a poll reply.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = newMessagesBelow > 0,
                enter = fadeIn(tween(140)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(200)),
                exit = fadeOut(tween(110)) + slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(150))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        color = palette.glassElevated,
                        contentColor = palette.textPrimary,
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, palette.border),
                        shadowElevation = 6.dp,
                        modifier = Modifier.clickable {
                            scrollScope.launch {
                                if (visibleMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(visibleMessages.lastIndex + historyHeaderCount)
                                    newMessagesBelow = 0
                                }
                            }
                        }
                    ) {
                        Text(
                            "↓ $newMessagesBelow new",
                            color = palette.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (replyingTo != null || editingMessage != null) {
                val target = editingMessage ?: replyingTo
                Surface(color = palette.glassElevated, border = BorderStroke(1.dp, palette.border)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (editingMessage != null) Icons.Default.Edit else Icons.Default.Reply,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (editingMessage != null) "Editing message" else "Replying to ${if (target?.isFromMe == true) "yourself" else conversation.partnerName}",
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                target?.text?.ifBlank { "Media message" }.orEmpty(),
                                color = palette.textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyingTo = null; editingMessage = null; if (text == target?.text) text = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = palette.textSecondary)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showEmojiRail) {
                EmojiRail(palette = palette, onEmoji = { text += it })
            }

            // Shared @username completion is used by referral, mention and invite flows.
            com.blinkng.shared.discovery.BlinkUsernameSelectionPolicy.trailingMention(text)?.let { needle ->
                BlinkUsernameSearchSuggestions(
                    query = needle,
                    onSelected = { person ->
                        text = com.blinkng.shared.discovery.BlinkUsernameSelectionPolicy
                            .insertMention(text, person.username)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MessageComposer(
                value = text,
                onValueChange = { text = it },
                palette = palette,
                onAttachment = { showAttachmentSheet = true },
                onCamera = { requestChatCamera() },
                onDictation = startDictation,
                onEmoji = { showEmojiRail = !showEmojiRail },
                onSubmit = { submitMessage() },
                onQuickLike = { submitMessage("👍") },
                isRecording = isRecordingVoice,
                recordingSeconds = recordingSeconds,
                onVoiceStart = { beginVoiceRecording() },
                onVoiceStop = { finishVoiceRecording(true) },
                onVoiceCancel = { finishVoiceRecording(false) }
            )
        }
    }

    viewedImageMessage?.let { requestedImage ->
        val message = conversation.messages.firstOrNull { it.id == requestedImage.id } ?: requestedImage
        val normalPictures = remember(conversation.messages, historicalViewerPhotos) {
            // Include both paginated history and current messages, without duplicate rows.
            // Sorted ascending for an intuitive left-to-right conversation gallery.
            (conversation.messages + historicalViewerPhotos)
                .distinctBy { it.id }
                .filter {
                    BlinkChatMediaAccessPolicy.mayBrowse(
                        it.imageViewOnce, it.deletedForEveryone,
                        !it.attachedImageUrl.isNullOrBlank()
                    )
                }
                .sortedWith(compareBy<ChatMessage> { it.rawTimestamp }
                    .thenBy { it.imageAlbumPosition })
        }
        // A shared 2/3-photo album opens its own sequence just like the reference.
        // All media remains reachable through the viewer's existing overflow action.
        val albumPictures = message.imageAlbumId?.let { albumId ->
            normalPictures.filter { it.imageAlbumId == albumId }
        }.orEmpty()
        val viewerPictures = if (albumPictures.size > 1) albumPictures else normalPictures
        val pictureIndex = viewerPictures.indexOfFirst { it.id == message.id }
        if (!message.imageViewOnce && !message.attachedImageUrl.isNullOrBlank()) {
            BlinkChatPictureViewer(
                message = message,
                senderName = if (message.isFromMe) "You" else conversation.partnerName,
                onDismiss = { viewedImageMessage = null; historicalViewerPhotos = emptyList() },
                onShowInChat = {
                    viewedImageMessage = null
                    searchQuery = ""
                    pinnedOnly = false
                    starredOnly = false
                    val position = visibleMessages.indexOfFirst {
                        it.id == message.id ||
                            (!message.imageAlbumId.isNullOrBlank() &&
                                it.imageAlbumId == message.imageAlbumId)
                    }
                    if (position >= 0) scrollScope.launch {
                        delay(100)
                        listState.animateScrollToItem(position + if (isLoadingOlder) 1 else 0)
                    }
                },
                onForward = {
                    forwardingMessage = message
                    viewedImageMessage = null
                },
                onReply = {
                    replyingTo = message
                    editingMessage = null
                    viewedImageMessage = null
                },
                onStar = {
                    interactionActions.onToggleStar(conversation.partnerUsername, message)
                    viewedImageMessage = null
                },
                onDeleteForMe = {
                    interactionActions.onDeleteForMe(conversation.partnerUsername, message)
                    viewedImageMessage = null
                },
                onOpenAllMedia = {
                    viewedImageMessage = null
                    showSharedContent = true
                },
                onReact = { emoji ->
                    interactionActions.onReact(conversation.partnerUsername, message, emoji)
                    viewedImageMessage = null
                },
                groupConversationId = conversation.id.takeIf { conversation.isGroup },
                onAskBlinkAi = { selected ->
                    aiPhotoUri = selected
                    viewedImageMessage = null
                },
                hasPrevious = pictureIndex > 0,
                hasNext = pictureIndex >= 0 && pictureIndex < viewerPictures.lastIndex,
                photoIndex = pictureIndex + 1,
                totalPhotos = viewerPictures.size,
                galleryPhotos = viewerPictures,
                onPickPhoto = { selected -> viewedImageMessage = selected },
                onPrevious = {
                    if (pictureIndex > 0) viewedImageMessage = viewerPictures[pictureIndex - 1]
                },
                onNext = {
                    if (pictureIndex in 0 until viewerPictures.lastIndex)
                        viewedImageMessage = viewerPictures[pictureIndex + 1]
                }
            )
        }
    }

    selectedMessage?.let { message ->
        MessageActionsPopover(
            message = message,
            palette = palette,
            onReaction = { emoji ->
                interactionActions.onReact(conversation.partnerUsername, message, emoji)
                selectedMessage = null
            },
            onReply = {
                replyingTo = message
                editingMessage = null
                selectedMessage = null
            },
            onForward = {
                if (message.imageViewOnce) {
                    Toast.makeText(context, "View-once photos cannot be forwarded.", Toast.LENGTH_SHORT).show()
                } else {
                    forwardingMessage = message
                }
                selectedMessage = null
            },
            onEdit = {
                editingMessage = message
                replyingTo = null
                text = message.text
                selectedMessage = null
            },
            onDeleteForMe = {
                interactionActions.onDeleteForMe(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onDeleteForEveryone = {
                interactionActions.onDeleteForEveryone(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onToggleStar = {
                interactionActions.onToggleStar(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onShare = {
                shareChatMessage(context, message)
                selectedMessage = null
            },
            onTogglePin = {
                interactionActions.onTogglePin(conversation.partnerUsername, message)
                selectedMessage = null
            },
            onReport = {
                interactionActions.onReportMessage(message, "Reported from message actions")
                selectedMessage = null
            },
            onDismiss = { selectedMessage = null }
        )
    }

    forwardingMessage?.let { message ->
        ForwardMessageSheet(
            sourcePartner = conversation.partnerUsername,
            conversations = allConversations,
            palette = palette,
            onForward = { targets ->
                targets.take(10).forEach { target -> onForward(target.partnerUsername, message) }
                forwardingMessage = null
            },
            onDismiss = { forwardingMessage = null }
        )
    }

    if (showOverflow) {
        ChatOverflowSheet(
            conversation = conversation,
            palette = palette,
            pinnedOnly = pinnedOnly,
            starredOnly = starredOnly,
            onProfile = { showOverflow = false; onProfileClick() },
            onSearch = { showOverflow = false; searchVisible = true },
            onSharedMedia = {
                showOverflow = false
                showSharedContent = true
            },
            onPinned = { showOverflow = false; pinnedOnly = !pinnedOnly; starredOnly = false },
            onStarred = { showOverflow = false; starredOnly = !starredOnly; pinnedOnly = false },
            onMute = {
                showOverflow = false
                if (conversation.isMuted) {
                    interactionActions.onMuteConversation(conversation, false)
                } else {
                    showMuteOptions = true
                }
            },
            onNotificationSettings = {
                showOverflow = false
                showNotificationSettings = true
            },
            onPinConversation = {
                showOverflow = false
                interactionActions.onPinConversation(conversation, !conversation.isConversationPinned)
            },
            onMarkUnread = {
                showOverflow = false
                interactionActions.onMarkConversationUnread(conversation, !conversation.isMarkedUnread)
            },
            onArchive = {
                showOverflow = false
                interactionActions.onArchiveConversation(conversation, !conversation.isArchived)
                if (!conversation.isArchived) onBack()
            },
            onBlock = {
                showOverflow = false
                interactionActions.onBlockConversation(conversation)
                onBack()
            },
            onDelete = {
                showOverflow = false
                confirmClearChat = true
            },
            onReport = {
                showOverflow = false
                interactionActions.onReportConversation(conversation, "Reported from conversation menu")
            },
            onDismiss = { showOverflow = false }
        )
    }

    if (showMuteOptions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMuteOptions = false },
            title = { Text("Mute notifications", color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Choose how long to mute alerts from " + conversation.partnerName + ". Messages will still arrive in the chat.",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    listOf(
                        "1 hour" to 60L * 60L * 1_000L,
                        "8 hours" to 8L * 60L * 60L * 1_000L,
                        "24 hours" to 24L * 60L * 60L * 1_000L
                    ).forEach { (label, duration) ->
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showMuteOptions = false
                                interactionActions.onMuteConversationFor(conversation, duration)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, color = palette.textPrimary)
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showMuteOptions = false
                            interactionActions.onMuteConversationFor(conversation, null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Until I turn it back on", color = palette.textPrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMuteOptions = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }

    if (confirmClearChat) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("Clear this chat?", color = palette.textPrimary) },
            text = {
                Text(
                    "This hides the current history only for your account. New messages will make the conversation appear again.",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        confirmClearChat = false
                        interactionActions.onClearConversation(conversation)
                    }
                ) { Text("Clear chat", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClearChat = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }

    if (showSharedContent) {
        SharedChatContentSheet(
            conversation = conversation,
            palette = palette,
            onDismiss = { showSharedContent = false },
            onOpenPhoto = { photo, history ->
                historicalViewerPhotos = history
                viewedImageMessage = photo
                showSharedContent = false
            },
            onJumpToMessage = { messageId ->
                showSharedContent = false
                searchQuery = ""
                pinnedOnly = false
                starredOnly = false
                val targetMessage = conversation.messages.firstOrNull { it.id == messageId }
                val targetIndex = visibleMessages.indexOfFirst {
                    it.id == messageId ||
                        (!targetMessage?.imageAlbumId.isNullOrBlank() &&
                            it.imageAlbumId == targetMessage?.imageAlbumId)
                }
                if (targetIndex >= 0) {
                    scrollScope.launch {
                        delay(120)
                        listState.animateScrollToItem(targetIndex + if (isLoadingOlder) 1 else 0)
                    }
                }
            }
        )
    }

    if (showNotificationSettings) {
        ChatNotificationSettingsSheet(
            conversation = conversation,
            palette = palette,
            onSelect = { mode, muteUntil ->
                interactionActions.onNotificationSettings(conversation, mode, muteUntil)
                showNotificationSettings = false
            },
            onDismiss = { showNotificationSettings = false }
        )
    }


    // Review before sending: selections and captions stay aligned while reordering,
    // removing, rotating or cropping individual images. Originals are not modified.
    if (pendingPhotoReview.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!photoEditBusy) dismissPhotoReview() },
            title = {
                Text("Review ${pendingPhotoReview.size} photo(s)", color = palette.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // A private, app-designed post-selection gallery; the Android
                    // Photo Picker retains its own privacy-safe system appearance.
                    val focused = pendingPhotoReview.firstOrNull {
                        it.id == gallerySelectedPhotoId
                    } ?: pendingPhotoReview.first()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0D0D16),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(Modifier.fillMaxWidth().height(228.dp),
                            contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = focused.uri,
                                contentDescription = "Large preview of selected image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier=Modifier.align(Alignment.TopStart).padding(7.dp),
                                color=Color.Black.copy(alpha=.68f),
                                shape=RoundedCornerShape(12.dp)
                            ) {
                                Text("${pendingPhotoReview.indexOfFirst { it.id == focused.id } + 1} / ${pendingPhotoReview.size}",
                                    fontSize=11.sp,color=Color.White,
                                    modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
                            }
                            androidx.compose.material3.TextButton(
                                enabled=!photoEditBusy,
                                onClick = { photoAdjustmentItem = focused },
                                modifier=Modifier.align(Alignment.BottomEnd).padding(5.dp)
                            ) {
                                Text("✎ Edit photo",fontSize=11.sp,color=Color.White)
                            }
                        }
                    }
                    LazyRow(
                        contentPadding=PaddingValues(vertical=3.dp),
                        horizontalArrangement=Arrangement.spacedBy(5.dp)
                    ) {
                        itemsIndexed(pendingPhotoReview, key = { _, item -> "thumb:${item.id}" }) { index,item ->
                            Surface(
                                shape=RoundedCornerShape(8.dp),
                                color=Color(0xFF171724),
                                border=BorderStroke(if(item.id==focused.id) 2.dp else 1.dp,
                                    if(item.id==focused.id) palette.accent else palette.border),
                                modifier=Modifier.size(54.dp)
                                    .clickable { gallerySelectedPhotoId = item.id }
                            ) {
                                Box(contentAlignment=Alignment.BottomEnd) {
                                    AsyncImage(model=item.uri,
                                        contentDescription="View selected photo ${index+1}",
                                        contentScale=ContentScale.Crop,
                                        modifier=Modifier.fillMaxSize())
                                    Text("${index+1}",fontSize=10.sp,color=Color.White,
                                        modifier=Modifier.background(Color.Black.copy(alpha=.63f))
                                            .padding(horizontal=4.dp))
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { selectedPhotoQuality = BlinkChatImageQuality.STANDARD },
                            enabled = !photoEditBusy
                        ) {
                            Text(
                                if (selectedPhotoQuality == BlinkChatImageQuality.STANDARD)
                                    "✓ Standard" else "Standard",
                                color = palette.textPrimary
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { selectedPhotoQuality = BlinkChatImageQuality.ORIGINAL },
                            enabled = !photoEditBusy
                        ) {
                            Text(
                                if (selectedPhotoQuality == BlinkChatImageQuality.ORIGINAL)
                                    "✓ Original / HD" else "Original / HD",
                                color = palette.textPrimary
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(pendingPhotoReview, key = { _, item -> item.id }) { index, item ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = "Photo ${index + 1} preview",
                                        modifier = Modifier.size(76.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Photo ${index + 1}",
                                            color = palette.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row {
                                            androidx.compose.material3.TextButton(
                                                enabled = !photoEditBusy && index > 0,
                                                onClick = {
                                                    pendingPhotoReview = pendingPhotoReview.toMutableList()
                                                        .apply { add(index - 1, removeAt(index)) }
                                                }
                                            ) { Text("↑ Up") }
                                            androidx.compose.material3.TextButton(
                                                enabled = !photoEditBusy &&
                                                    index < pendingPhotoReview.lastIndex,
                                                onClick = {
                                                    pendingPhotoReview = pendingPhotoReview.toMutableList()
                                                        .apply { add(index + 1, removeAt(index)) }
                                                }
                                            ) { Text("↓ Down") }
                                        }
                                    }
                                    androidx.compose.material3.TextButton(
                                        enabled = !photoEditBusy,
                                        onClick = {
                                            cleanupReviewEdit(item.uri)
                                            pendingPhotoReview =
                                                pendingPhotoReview.filterNot { it.id == item.id }
                                            if (pendingPhotoReview.isEmpty()) {
                                                selectedPhotoQuality = BlinkChatImageQuality.STANDARD
                                            }
                                        }
                                    ) { Text("Remove", color = palette.danger) }
                                }
                                TextField(
                                    value = item.caption,
                                    onValueChange = { value ->
                                        pendingPhotoReview = pendingPhotoReview.map {
                                            if (it.id == item.id) it.copy(caption = value.take(1024))
                                            else it
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Caption (optional)") },
                                    maxLines = 3,
                                    enabled = !photoEditBusy
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    androidx.compose.material3.TextButton(
                                        enabled = !photoEditBusy,
                                        onClick = { photoAdjustmentItem = item }
                                    ) {
                                        Text("Adjust", color = palette.textSecondary)
                                    }
                                    listOf(
                                        "Rotate" to BlinkChatPhotoEditor.Edit.ROTATE_RIGHT,
                                        "Square crop" to BlinkChatPhotoEditor.Edit.CENTER_SQUARE
                                    ).forEach { (label, operation) ->
                                        androidx.compose.material3.TextButton(
                                            enabled = !photoEditBusy,
                                            onClick = {
                                                photoEditBusy = true
                                                scrollScope.launch {
                                                    runCatching {
                                                        BlinkChatPhotoEditor.edit(
                                                            context, item.uri, operation
                                                        )
                                                    }.onSuccess { editedUri ->
                                                        pendingPhotoReview = pendingPhotoReview.map {
                                                            if (it.id == item.id) it.copy(uri = editedUri)
                                                            else it
                                                        }
                                                        cleanupReviewEdit(item.uri)
                                                    }.onFailure { failure ->
                                                        Toast.makeText(
                                                            context,
                                                            failure.message ?: "This photo cannot be edited.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    photoEditBusy = false
                                                }
                                            }
                                        ) { Text(label, color = palette.textSecondary) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !photoEditBusy && pendingPhotoReview.isNotEmpty(),
                    onClick = {
                        val photos = pendingPhotoReview.map {
                            it.uri to it.caption.trim().take(1024)
                        }
                        val quality = selectedPhotoQuality
                        // Do not delete edited sources: the durable upload queue needs
                        // them until staging has completed.
                        pendingPhotoReview = emptyList()
                        selectedPhotoQuality = BlinkChatImageQuality.STANDARD
                        onPresenceChange("uploading")
                        onSendPhotoAlbum(photos, quality)
                    }
                ) { Text("Send ${pendingPhotoReview.size}", color = palette.accent) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !photoEditBusy,
                    onClick = { dismissPhotoReview() }
                ) { Text("Cancel", color = palette.textSecondary) }
            }
        )
    }

    pendingFileReview?.let { (selected, audio) ->
        BlinkChatAttachmentReviewDialog(
            uri = selected,
            isAudio = audio,
            onSend = {
                pendingFileReview = null
                onPresenceChange("uploading")
                onSendAttachment(it, if (audio) "audio" else "document")
            },
            onOpenBlinkAi = { selectedAudio ->
                pendingFileReview = null
                aiAudioUri = selectedAudio
                onPresenceChange("online")
            },
            onDismiss = {
                pendingFileReview = null
                onPresenceChange("online")
            }
        )
    }
    if (videoBatchPreview && pendingVideoBatch.size > 1) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                videoBatchPreview = false
                pendingVideoBatch = emptyList()
            },
            title = { Text("Review selected videos", color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Choose 2–10 videos. Each video is reviewed and uploaded individually.",
                        fontSize = 12.sp, color = palette.textSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("chat_reference_multi_video_preview")) {
                        itemsIndexed(pendingVideoBatch) { index, uri ->
                            Column(Modifier.width(126.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(126.dp).height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))) {
                                    AsyncImage(
                                        model = remember(uri) {
                                            ImageRequest.Builder(context).data(uri)
                                                .videoFrameMillis(1000L).build()
                                        },
                                        contentDescription = "Video " + (index + 1),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Text("▶ " + (index + 1),
                                        modifier = Modifier.align(Alignment.BottomStart)
                                            .background(Color(0xAA06111C)).padding(6.dp),
                                        color = Color.White, fontSize = 11.sp)
                                }
                                androidx.compose.material3.TextButton(onClick = {
                                    pendingVideoBatch = pendingVideoBatch.filterIndexed {
                                        i, _ -> i != index
                                    }
                                    if (pendingVideoBatch.size == 1) {
                                        videoBatchPreview = false
                                        pendingVideoReview = pendingVideoBatch.first()
                                    }
                                }) { Text("Remove", color = palette.textSecondary) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    videoBatchPreview = false
                    pendingVideoReview = pendingVideoBatch.firstOrNull()
                }) { Text("Review & send " + pendingVideoBatch.size, color = palette.accent) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    videoBatchPreview = false
                    pendingVideoBatch = emptyList()
                }) { Text("Cancel") }
            }
        )
    }
    pendingVideoReview?.let { selected ->
        BlinkChatVideoEditorDialog(
            sourceUri = selected,
            onSend = { finalVideo ->
                val approved = editedVideoBatch + finalVideo
                val remaining = pendingVideoBatch.drop(1)
                pendingVideoBatch = remaining
                pendingVideoReview = remaining.firstOrNull()
                if (remaining.isEmpty()) {
                    editedVideoBatch = emptyList()
                    onPresenceChange("uploading")
                    if (approved.size > 1) onSendVideoAlbum(approved)
                    else approved.firstOrNull()?.let(onSendVideo)
                } else {
                    editedVideoBatch = approved
                    onPresenceChange("online")
                }
            },
            onDismiss = {
                // A cancellation never silently uploads any remaining selected video.
                pendingVideoBatch = emptyList()
                editedVideoBatch = emptyList()
                pendingVideoReview = null
                onPresenceChange("online")
            }
        )
    }

    photoAdjustmentItem?.let { item ->
        BlinkUniversalPhotoEditor(
            originalUri = item.uri,
            onEdited = { editedUri ->
                pendingPhotoReview = pendingPhotoReview.map {
                    if (it.id == item.id) it.copy(uri = editedUri) else it
                }
                cleanupReviewEdit(item.uri)
                photoAdjustmentItem = null
            },
            onDismiss = { photoAdjustmentItem = null }
        )
    }

    aiAudioUri?.let { selected ->
        com.example.ui.components.BlinkAiSheet(
            onDismiss = { aiAudioUri = null },
            initialAudioUri = selected
        )
    }
    aiPhotoUri?.let { selected ->
        com.example.ui.components.BlinkAiSheet(
            onDismiss = { aiPhotoUri = null },
            initialImageUri = selected
        )
    }
    if (showStickers) {
        BlinkStickerPicker(
            onSend = { uri ->
                showStickers = false
                onSendAttachment(uri, "image")
            },
            onDismiss = { showStickers = false }
        )
    }
    if (showAttachmentSheet) {
        BlinkChatMediaGallerySheet(
            palette = palette,
            permissionVersion = galleryPermissionVersion,
            onRequestPermission = { askForGalleryPermission() },
            onSystemPhotoPicker = {
                selectedPhotoQuality = BlinkChatImageQuality.STANDARD
                showAttachmentSheet = false
                photoAlbumPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPhotosSelected = { uris ->
                selectedPhotoQuality = BlinkChatImageQuality.STANDARD
                pendingPhotoReview = uris.take(10).map { ChatPhotoReviewItem(uri = it) }
                gallerySelectedPhotoId = pendingPhotoReview.firstOrNull()?.id
                showAttachmentSheet = false
            },
            onCamera = {
                showAttachmentSheet = false
                requestChatCamera()
            },
            onDocument = {
                showAttachmentSheet = false
                documentPicker.launch(arrayOf("*/*"))
            },
            onDocumentScan = {
                showAttachmentSheet = false
                if (!scanningDocument) documentScanPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onAudio = {
                showAttachmentSheet = false
                audioPicker.launch("audio/*")
            },
            onVoiceNote = {
                showAttachmentSheet = false
                beginVoiceRecording()
            },
            onVideo = {
                showAttachmentSheet = false
                videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onSticker = {
                showAttachmentSheet = false
                showStickers = true
            },
            onViewOnce = {
                viewOncePictureChoice = true
                showAttachmentSheet = false
                imagePicker.launch("image/*")
            },
            onShareLocation = {
                showAttachmentSheet = false
                chatExtraMode = "location"
            },
            onPickContact = {
                showAttachmentSheet = false
                contactPicker.launch(
                    Intent(Intent.ACTION_PICK,
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                )
            },
            onShareArticle = {
                showAttachmentSheet = false
                chatExtraMode = "article"
            },
            onCreatePoll = {
                showAttachmentSheet = false
                chatExtraMode = "poll"
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    chatExtraMode?.let { mode ->
        BlinkChatExtrasDialog(
            mode = mode,
            contact = selectedContact,
            nativePollEnabled = nativePollEnabled,
            onNativePoll = { draft ->
                pendingNativePoll = PendingChatNativePoll(
                    draft = draft,
                    existingIds = conversation.messages.map { it.id }.toSet()
                )
                chatExtraMode = null
                selectedContact = null
                onSend(draft.question, null)
            },
            onSend = { content ->
                chatExtraMode = null
                selectedContact = null
                onSend(content, null)
            },
            onDismiss = {
                chatExtraMode = null
                selectedContact = null
            }
        )
    }
    if (showContactProfile) {
        ChatContactProfileOverlay(
            conversation = conversation,
            palette = palette,
            onDismiss = { showContactProfile = false },
            onViewFullProfile = {
                showContactProfile = false
                onProfileClick()
            },
            onAudioCall = {
                showContactProfile = false
                onAudioCall()
            },
            onVideoCall = {
                showContactProfile = false
                onVideoCall()
            },
            onMute = {
                if (conversation.isMuted) {
                    interactionActions.onMuteConversation(conversation, false)
                } else {
                    showContactProfile = false
                    showMuteOptions = true
                }
            },
            onSearch = {
                showContactProfile = false
                searchVisible = true
            }
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
    onVideoCall: () -> Unit,
    onMore: () -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    val referenceDark = palette.mode == MessageThemeMode.DARK
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (referenceDark) Color(BlinkChatReferenceStyle.Header) else palette.glass)
            .heightIn(min = 70.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("chat_reference_header"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                tint = palette.textPrimary, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(5.dp))
        RingAvatar(
            url = conversation.partnerAvatar,
            name = conversation.partnerName,
            palette = palette,
            size = 44.dp,
            emphasizeRing = false,
            modifier = Modifier.clickable(onClick = onProfileClick)
        )
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onProfileClick),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.partnerName,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                VerificationDot(conversation.verificationBadge, conversation.isVerified)
                BlinkVipMarkForUsername(
                    username = conversation.partnerUsername,
                    knownVip = if (conversation.partnerIsVip) true else null,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.isOnline) {
                    Box(Modifier.size(7.dp).background(palette.online, CircleShape))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = when {
                        conversation.presenceLabel.isNotBlank() &&
                            !conversation.presenceLabel.equals("Active now", ignoreCase = true) ->
                            conversation.presenceLabel
                        conversation.isOnline -> "Online"
                        else -> conversation.lastSeen
                    },
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onVideoCall, modifier = Modifier.size(37.dp)) {
            Icon(Icons.Default.Videocam, contentDescription = "Video call",
                tint = palette.textPrimary, modifier = Modifier.size(25.dp))
        }
        IconButton(onClick = onAudioCall, modifier = Modifier.size(37.dp)) {
            Icon(Icons.Default.Call, contentDescription = "Voice call",
                tint = palette.textPrimary, modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onMore, modifier = Modifier.size(35.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More chat options",
                tint = palette.textPrimary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ChatContactProfileOverlay(
    conversation: ChatConversation,
    palette: MessagePalette,
    onDismiss: () -> Unit,
    onViewFullProfile: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onMute: () -> Unit,
    onSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "Chat profile for ${conversation.partnerName}" }
    ) {
        if (conversation.partnerAvatar.isNotBlank()) {
            AsyncImage(
                model = conversation.partnerAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = .72f }
                    .blur(2.dp)
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.accent.copy(alpha = .42f),
                                palette.backgroundBottom,
                                Color.Black
                            )
                        )
                    )
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .12f),
                            Color.Black.copy(alpha = .28f),
                            Color.Black.copy(alpha = .82f)
                        )
                    )
                )
        )

        GlassIconButton(
            icon = Icons.Default.ArrowBack,
            contentDescription = "Back to chat",
            palette = palette,
            size = 42.dp,
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
        )

        Surface(
            color = palette.glass.copy(alpha = if (palette.isLight) .94f else .84f),
            contentColor = palette.textPrimary,
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = .85f)),
            shadowElevation = 18.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                conversation.partnerName,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VerificationDot(conversation.verificationBadge, conversation.isVerified)
                            BlinkVipMarkForUsername(
                                username = conversation.partnerUsername,
                                knownVip = if (conversation.partnerIsVip) true else null,
                                modifier = Modifier.padding(start = 5.dp)
                            )
                        }
                        Text(
                            "@${conversation.partnerUsername.removePrefix("@")}",
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                        if (conversation.faculty.isNotBlank()) {
                            Text(
                                conversation.faculty,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    RingAvatar(
                        url = conversation.partnerAvatar,
                        name = conversation.partnerName,
                        palette = palette,
                        size = 58.dp,
                        online = conversation.isOnline
                    )
                }

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatContactAction(
                        label = "Audio",
                        icon = Icons.Default.Call,
                        palette = palette,
                        onClick = onAudioCall,
                        modifier = Modifier.weight(1f)
                    )
                    ChatContactAction(
                        label = "Video",
                        icon = Icons.Default.Videocam,
                        palette = palette,
                        onClick = onVideoCall,
                        modifier = Modifier.weight(1f)
                    )
                    ChatContactAction(
                        label = if (conversation.isMuted) "Unmute" else "Mute",
                        icon = Icons.Default.VolumeOff,
                        palette = palette,
                        onClick = onMute,
                        modifier = Modifier.weight(1f)
                    )
                    ChatContactAction(
                        label = "Search",
                        icon = Icons.Default.Search,
                        palette = palette,
                        onClick = onSearch,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = palette.border.copy(alpha = .65f))
                Spacer(Modifier.height(12.dp))

                Surface(
                    color = palette.glassElevated.copy(alpha = .72f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onViewFullProfile)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "View full BLINK profile",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                conversation.presenceLabel.ifBlank {
                    if (conversation.isOnline) "Active now" else conversation.lastSeen
                },
                                color = palette.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Text("›", color = palette.accent, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatContactAction(
    label: String,
    icon: ImageVector,
    palette: MessagePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = palette.glassElevated.copy(alpha = .78f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = palette.accent, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                color = palette.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    reducedMotion: Boolean,
    replyTarget: ChatMessage?,
    partnerAvatar: String,
    partnerName: String,
    palette: MessagePalette,
    onReply: () -> Unit,
    onReplyTargetClick: () -> Unit,
    onActions: () -> Unit,
    onOpenImage: (ChatMessage) -> Unit,
    onRetry: () -> Unit,
    onCancelAttachment: () -> Unit,
    allowLinkPreviews: Boolean,
    activeVideoMessageId: String?,
    onVideoActivate: () -> Unit,
    onVideoDeactivate: () -> Unit,
    onLegacyPollChoice: (Int) -> Unit
) {
    val isMine = message.isFromMe
    val density = LocalDensity.current
    val threshold = with(density) { BlinkReplySwipePhysics.activationDp.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var dragOffset by remember(message.id) { mutableStateOf(0f) }
    var isDragging by remember(message.id) { mutableStateOf(false) }
    var replyArmed by remember(message.id) { mutableStateOf(false) }
    var replyVelocity by remember(message.id) { mutableStateOf(VelocityTracker()) }
    val displayOffset by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else BlinkReplySwipePhysics.visualOffset(dragOffset, threshold),
        animationSpec = if (isDragging || reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "messageSwipeReply"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        if (displayOffset > 10f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                tint = palette.accent.copy(alpha = (displayOffset / threshold).coerceIn(.25f, 1f)),
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp).size(20.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = displayOffset }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            replyVelocity = VelocityTracker()
                            isDragging = true
                        },
                        onHorizontalDrag = { change, amount ->
                            replyVelocity.addPosition(change.uptimeMillis, change.position)
                            dragOffset = (dragOffset + amount).coerceIn(0f, threshold * 2f)
                            val active = BlinkReplySwipePhysics.shouldReply(
                                dragOffset, threshold, replyVelocity.calculateVelocity().x
                            )
                            if (active && !replyArmed) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            replyArmed = active
                        },
                        onDragEnd = {
                            if (BlinkReplySwipePhysics.shouldReply(
                                    dragOffset, threshold, replyVelocity.calculateVelocity().x
                                )) onReply()
                            isDragging = false
                            replyArmed = false
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            replyArmed = false
                            dragOffset = 0f
                        }
                    )
                }
                .pointerInput(message.id, message.status) {
                    detectTapGestures(
                        onDoubleTap = { onActions() },
                        onLongPress = { onActions() },
                        onTap = { if (message.status == MessageStatus.FAILED) onRetry() }
                    )
                },
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMine && palette.mode != MessageThemeMode.DARK) {
                RingAvatar(url = partnerAvatar, name = partnerName, palette = palette, size = 31.dp)
                Spacer(Modifier.width(8.dp))
            }

            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                val bubbleShape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                )
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(bubbleShape)
                        .background(
                            brush = if (isMine) palette.outgoingBrush() else Brush.linearGradient(listOf(palette.incomingBubble, palette.incomingBubble)),
                            shape = bubbleShape
                        )
                        .border(
                            1.dp,
                            if (palette.mode == MessageThemeMode.DARK) Color.Transparent
                            else palette.border.copy(alpha = .55f),
                            bubbleShape
                        )
                ) {
                    if (message.isForwarded) {
                        Text(
                            "Forwarded",
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp)
                        )
                    }
                    if (message.replyToMessageId != null) {
                        Surface(
                            color = if (isMine && palette.mode == MessageThemeMode.DARK)
                                Color(0xFFE1E4E9) else palette.glass.copy(alpha = .45f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.border.copy(alpha = .55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 5.dp, end = 5.dp, top = 5.dp)
                                .clickable(onClick = onReplyTargetClick)
                        ) {
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                                Text(
                                    if (replyTarget?.isFromMe == true) "You" else partnerName,
                                    color = palette.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    replyTarget?.text?.ifBlank { "Media message" } ?: "Original message unavailable",
                                    color = if (isMine && palette.mode == MessageThemeMode.DARK)
                                        palette.outgoingText.copy(alpha = .65f) else palette.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    MessageContent(
                        message = message,
                        isMine = isMine,
                        palette = palette,
                        allowLinkPreviews = allowLinkPreviews,
                        onCancelAttachment = onCancelAttachment,
                        onOpenImage = onOpenImage,
                        videoActive = activeVideoMessageId == message.id,
                        onVideoActivate = onVideoActivate,
                        onVideoDeactivate = onVideoDeactivate,
                        onLegacyPollChoice = onLegacyPollChoice
                    )
                    // Compact inside-bubble timestamp and one overlapping double-check glyph.
                    val metadata = when (message.status) {
                        MessageStatus.SENDING -> "Sending…"
                        MessageStatus.FAILED -> "Waiting to retry"
                        MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ ->
                            message.timestamp + if (!message.editedAt.isNullOrBlank()) " • edited" else ""
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 9.dp, end = 8.dp, bottom = 5.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isPinned) Text("📌", fontSize = 10.sp)
                        if (message.isStarred) Text("★", color = palette.accent, fontSize = 10.sp)
                        Text(
                            metadata,
                            color = if (message.status == MessageStatus.FAILED)
                                palette.danger else if (isMine && palette.mode == MessageThemeMode.DARK)
                                palette.outgoingText.copy(alpha = .52f) else palette.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isMine && message.status in setOf(
                                MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ
                            )) {
                            Spacer(Modifier.width(3.dp))
                            AnimatedContent(
                                targetState = message.status,
                                transitionSpec = {
                                    if (reducedMotion) {
                                        androidx.compose.animation.EnterTransition.None togetherWith
                                            androidx.compose.animation.ExitTransition.None
                                    } else fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                                },
                                label = "chatAuthoritativeReceipt"
                            ) { confirmedStatus ->
                                Icon(
                                    imageVector = if (confirmedStatus == MessageStatus.SENT)
                                        Icons.Default.Check else Icons.Default.DoneAll,
                                    contentDescription = when (confirmedStatus) {
                                        MessageStatus.READ -> "Read"
                                        MessageStatus.DELIVERED -> "Delivered"
                                        else -> "Sent"
                                    },
                                    tint = if (confirmedStatus == MessageStatus.READ)
                                        Color(0xFF1685FF) else if (palette.mode == MessageThemeMode.DARK)
                                        palette.outgoingText.copy(alpha = .55f) else palette.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                if (message.reactionCounts.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 3.dp).widthIn(max = 260.dp)
                    ) {
                        items(message.reactionCounts.entries.sortedByDescending { it.value }.take(8)) { entry ->
                            Surface(
                                color = if (entry.key in message.myReactions) palette.accent.copy(alpha = .18f) else palette.glassElevated,
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, if (entry.key in message.myReactions) palette.accent.copy(alpha = .55f) else palette.border)
                            ) {
                                Text(
                                    "${entry.key} ${entry.value}",
                                    fontSize = 11.sp,
                                    color = palette.textSecondary,
                                    modifier = Modifier.clickable(
                                        role = androidx.compose.ui.semantics.Role.Button,
                                        onClickLabel = "Manage message reactions",
                                        onClick = onActions
                                    ).padding(horizontal = 10.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }


            }
        }
    }
}

@Composable
private fun MessageContent(
    message: ChatMessage,
    isMine: Boolean,
    palette: MessagePalette,
    allowLinkPreviews: Boolean,
    onCancelAttachment: () -> Unit,
    onOpenImage: (ChatMessage) -> Unit,
    videoActive: Boolean,
    onVideoActivate: () -> Unit,
    onVideoDeactivate: () -> Unit,
    onLegacyPollChoice: (Int) -> Unit
) {
    val context = LocalContext.current
    val contentColor = if (isMine) palette.outgoingText else palette.textPrimary
    Column(modifier = Modifier.padding(5.dp)) {
        if (message.deletedForEveryone) {
            Text(
                "This message was deleted",
                color = contentColor.copy(alpha = .70f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
            )
        } else {
            if (isMine && !message.imageViewOnce &&
                message.id.startsWith("temp_attachment_") &&
                message.status == MessageStatus.SENDING &&
                message.uploadProgress != null) {
                Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text(
                        "Uploading photo · ${message.uploadProgress.coerceIn(0, 100)}%",
                        color = contentColor,
                        fontSize = 11.sp
                    )
                    androidx.compose.material3.TextButton(onClick = onCancelAttachment) {
                        Text("Cancel upload", color = palette.danger)
                    }
                }
            }
            if (message.imageViewOnce) {
                var opening by remember(message.id) { mutableStateOf(false) }
                var alreadyOpened by remember(message.id) { mutableStateOf(false) }
                var oneTimeBitmap by remember(message.id) {
                    mutableStateOf<android.graphics.Bitmap?>(null)
                }
                val viewScope = rememberCoroutineScope()
                Surface(
                    color = contentColor.copy(alpha = .08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (isMine) "🔒 View-once photo sent"
                            else if (alreadyOpened) "This photo has been opened"
                            else "🔒 Private view-once photo",
                            color = contentColor,
                            fontSize = 12.sp
                        )
                        if (!isMine && !opening && !alreadyOpened &&
                            message.status != MessageStatus.FAILED &&
                            message.status != MessageStatus.SENDING) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    opening = true
                                    viewScope.launch {
                                        MessageMediaService.openViewOncePicture(message.id).fold(
                                            onSuccess = {
                                                oneTimeBitmap = it
                                                alreadyOpened = true
                                            },
                                            onFailure = {
                                                if (it.message?.contains("already", true) == true) {
                                                    alreadyOpened = true
                                                }
                                                Toast.makeText(
                                                    context, it.message ?: "Photo unavailable",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                        opening = false
                                    }
                                }
                            ) { Text("Open once", color = palette.accent) }
                        } else if (opening) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                oneTimeBitmap?.let { bitmap ->
                    DisposableEffect(bitmap) {
                        onDispose { if (!bitmap.isRecycled) bitmap.recycle() }
                    }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { oneTimeBitmap = null },
                        title = { Text("View-once photo") },
                        text = {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "One-time private photo",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                                contentScale = ContentScale.Fit
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { oneTimeBitmap = null }
                            ) { Text("Close") }
                        }
                    )
                }
            } else if (!message.attachedImageUrl.isNullOrBlank()) {
                var imageRetry by remember(message.id, message.attachedImageUrl) { mutableIntStateOf(0) }
                var imageFailed by remember(message.id, message.attachedImageUrl) { mutableStateOf(false) }
                Box(
                    modifier = Modifier.width(220.dp).height(170.dp)
                        .clip(RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = remember(message.attachedImageUrl, imageRetry) {
                            coil.request.ImageRequest.Builder(context)
                                .data(message.attachedImageUrl)
                                .memoryCacheKey("${message.id}:$imageRetry")
                                .build()
                        },
                        contentDescription = "Shared image",
                        contentScale = ContentScale.Crop,
                        onSuccess = { imageFailed = false },
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize().clickable { if (!imageFailed) onOpenImage(message) }
                    )
                    if (imageFailed) {
                        Column(
                            modifier = Modifier.fillMaxSize().background(contentColor.copy(alpha = .10f)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Photo unavailable", color = contentColor, fontSize = 12.sp)
                            androidx.compose.material3.TextButton(onClick = {
                                imageFailed = false
                                imageRetry++
                            }) {
                                Text("Retry", color = contentColor, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            if (!message.attachedVideoUrl.isNullOrBlank()) {
                BlinkChatInlineVideo(
                    url = message.attachedVideoUrl,
                    isActive = videoActive,
                    onActivate = onVideoActivate,
                    onDeactivate = onVideoDeactivate,
                    modifier = Modifier.width(240.dp),
                )
            }
            if (!message.attachedAudioUrl.isNullOrBlank()) {
                BlinkChatVoicePlayer(
                    url = message.attachedAudioUrl,
                    durationLabel = if (message.isVoiceNote) {
                        message.voiceDuration.ifBlank { "Voice note" }
                    } else "Audio",
                    tint = contentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            if (!message.attachedDocumentUrl.isNullOrBlank()) {
                val pdf = message.attachmentMimeType == "application/pdf" ||
                    message.attachmentName.orEmpty().endsWith(".pdf", ignoreCase = true)
                var pdfOpen by remember(message.id) { mutableStateOf(false) }
                if (pdf && pdfOpen) BlinkChatPdfViewer(
                    url = message.attachedDocumentUrl,
                    filename = message.attachmentName ?: "Document.pdf",
                    onDismiss = { pdfOpen = false }
                )
                Surface(
                    color = contentColor.copy(alpha = .08f),
                    contentColor = contentColor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = .16f)),
                    modifier = Modifier
                        .widthIn(min = 190.dp, max = 240.dp)
                        .clickable {
                            if (pdf) pdfOpen = true else
                                openExternalUri(context, Uri.parse(message.attachedDocumentUrl))
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                message.attachmentName ?: message.text.ifBlank { "Document" },
                                color = contentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(if (pdf) "Preview PDF" else "Open document", color = contentColor.copy(alpha = .72f), fontSize = 11.sp)
                        }
                    }
                }
            }
            val legacyPoll = com.blinkng.shared.BlinkChatPollPolicy.parseTextFallback(message.text)
            if (legacyPoll != null || message.messageType == "poll") {
                BlinkChatNativePollCard(
                    message = message,
                    fallback = legacyPoll,
                    palette = palette,
                    textColor = contentColor,
                    onLegacyChoice = onLegacyPollChoice
                )
            }
            val sharedContact = com.blinkng.shared.BlinkChatContactCardPolicy.parse(message.text)
            if (sharedContact != null) {
                BlinkChatContactMessageCard(
                    contact = sharedContact,
                    palette = palette,
                    textColor = contentColor,
                )
            }
            val placeholderOnly =
                (!message.attachedVideoUrl.isNullOrBlank() && message.text.equals("Video", true)) ||
                    (!message.attachedImageUrl.isNullOrBlank() &&
                        (message.text.equals("Image", true) || message.text.equals("Photo", true))) ||
                    (!message.attachedAudioUrl.isNullOrBlank() &&
                        (message.text.equals("Audio", true) || message.text.equals("Voice message", true))) ||
                    !message.attachedDocumentUrl.isNullOrBlank()
            if (message.text.isNotBlank() && !placeholderOnly &&
                !message.isVoiceNote && sharedContact == null &&
                legacyPoll == null && message.messageType != "poll") {
                Text(
                    message.text,
                    color = contentColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
                if (allowLinkPreviews && !message.imageViewOnce && !message.deletedForEveryone) {
                    com.example.ui.components.BlinkRichLinkPreview(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        compact = false
                    )
                }
            }
        }
    }
}


@Composable
private fun MessageActionsPopover(
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
    val context = LocalContext.current
    val attachmentUrl = message.attachedVideoUrl
        ?.takeIf { it.isNotBlank() }
        ?: message.attachedImageUrl?.takeIf { it.isNotBlank() }
        ?: message.attachedAudioUrl?.takeIf { it.isNotBlank() }
        ?: message.attachedDocumentUrl?.takeIf { it.isNotBlank() }

    Popup(
        alignment = Alignment.CenterEnd,
        offset = IntOffset(-12, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 220.dp, max = 286.dp)
                .padding(end = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                color = if (palette.isLight) {
                    Color.White.copy(alpha = .98f)
                } else {
                    palette.glassElevated.copy(alpha = .98f)
                },
                contentColor = palette.textPrimary,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, palette.border.copy(alpha = .75f)),
                shadowElevation = 18.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onReaction(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Surface(
                color = if (palette.isLight) {
                    Color.White.copy(alpha = .99f)
                } else {
                    palette.glassElevated.copy(alpha = .99f)
                },
                contentColor = palette.textPrimary,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, palette.border.copy(alpha = .75f)),
                shadowElevation = 18.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                    MessageActionRow("↩", "Reply", palette, onClick = onReply)

                    if (message.text.isNotBlank() && !message.deletedForEveryone) {
                        MessageActionRow("⧉", "Copy", palette) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("BLINK message", message.text)
                            )
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }

                    MessageActionRow("➤", "Forward", palette, onClick = onForward)
                    MessageActionRow(
                        symbol = "⌖",
                        label = if (message.isPinned) "Unpin" else "Pin",
                        palette = palette,
                        onClick = onTogglePin
                    )
                    MessageActionRow(
                        symbol = "☆",
                        label = if (message.isStarred) "Remove from Keep" else "Keep",
                        palette = palette,
                        onClick = onToggleStar
                    )

                    if (message.isFromMe && message.text.isNotBlank() && !message.deletedForEveryone) {
                        MessageActionRow("✎", "Edit", palette, onClick = onEdit)
                    }

                    if (attachmentUrl != null) {
                        MessageActionRow("↗", "Open with", palette) {
                            openExternalUri(context, Uri.parse(attachmentUrl), "Open attachment with")
                            onDismiss()
                        }
                    }

                    MessageActionRow("⇧", "Share", palette, onClick = onShare)

                    if (!message.isFromMe) {
                        MessageActionRow("⚑", "Report", palette, onClick = onReport)
                    }

                    HorizontalDivider(
                        color = palette.border.copy(alpha = .65f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    if (message.isFromMe && !message.deletedForEveryone) {
                        MessageActionRow(
                            symbol = "⌫",
                            label = "Delete for everyone",
                            palette = palette,
                            danger = true,
                            onClick = onDeleteForEveryone
                        )
                    }
                    MessageActionRow(
                        symbol = "⌫",
                        label = "Delete for me",
                        palette = palette,
                        danger = true,
                        onClick = onDeleteForMe
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    symbol: String,
    label: String,
    palette: MessagePalette,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val foreground = when {
        !enabled -> palette.textMuted.copy(alpha = .55f)
        danger -> palette.danger
        else -> palette.textPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = symbol,
            color = foreground,
            fontSize = 17.sp,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
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
    onCamera: () -> Unit,
    onDictation: () -> Unit,
    onEmoji: () -> Unit,
    onSubmit: () -> Unit,
    onQuickLike: () -> Unit,
    isRecording: Boolean,
    recordingSeconds: Int,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceCancel: () -> Unit
) {
    // Four separate controls match the supplied chat: +, emoji, input with
    // camera/microphone, and persistent blue send. All actions remain wired.
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("chat_message_composer"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape, color = palette.glass,
            modifier = Modifier.size(40.dp).clickable(
                enabled = !isRecording, onClick = onAttachment
            ).testTag("chat_reference_attachment_plus")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "Open attachments",
                    tint = palette.textPrimary, modifier = Modifier.size(27.dp))
            }
        }
        Surface(
            shape = CircleShape, color = palette.glass,
            modifier = Modifier.size(38.dp).clickable(
                enabled = !isRecording, onClick = onEmoji
            ).testTag("chat_reference_emoji")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.EmojiEmotions, contentDescription = "Choose emoji",
                    tint = palette.textPrimary, modifier = Modifier.size(23.dp))
            }
        }
        Surface(
            color = palette.glass,
            contentColor = palette.textPrimary,
            shape = RoundedCornerShape(27.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.heightIn(min = 44.dp)
                    .padding(start = 13.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    Text("Recording · ${formatCallDuration(recordingSeconds)}",
                        color = palette.textPrimary, fontSize = 12.sp,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = onVoiceCancel, modifier = Modifier.size(31.dp)) {
                        Icon(Icons.Default.Close, "Cancel voice note",
                            tint = palette.textSecondary, modifier = Modifier.size(21.dp))
                    }
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(palette.accent),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { if (value.isNotBlank()) onSubmit() }
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 22.dp, max = 94.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isBlank()) Text(
                                    "Message", color = palette.textMuted, fontSize = 14.sp
                                )
                                innerTextField()
                            }
                        }
                    )
                    IconButton(onClick = onCamera, modifier = Modifier.size(33.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Open camera",
                            tint = palette.textPrimary, modifier = Modifier.size(21.dp))
                    }
                    Box(
                        modifier = Modifier.size(33.dp).pointerInput(onDictation, onVoiceStart) {
                            detectTapGestures(
                                onTap = { onDictation() },
                                onLongPress = { onVoiceStart() }
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic,
                            contentDescription = "Tap for voice typing, hold for voice note",
                            tint = palette.textPrimary, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
        Surface(
            shape = CircleShape,
            color = palette.accent,
            contentColor = Color.White,
            modifier = Modifier.size(46.dp)
                .clickable(enabled = isRecording || value.isNotBlank()) {
                    if (isRecording) onVoiceStop() else onSubmit()
                }
                .testTag("chat_reference_send")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Send,
                    contentDescription = if (isRecording) "Send voice note" else "Send message",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun firstHttpUrl(text: String): String? =
    Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        .find(text)
        ?.value
        ?.trimEnd('.', ',', ')', ']', '}', ';')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedChatContentSheet(
    conversation: ChatConversation,
    palette: MessagePalette,
    onDismiss: () -> Unit,
    onJumpToMessage: (String) -> Unit,
    onOpenPhoto: (ChatMessage, List<ChatMessage>) -> Unit
) {
    val context = LocalContext.current
    var tab by rememberSaveable(conversation.id) { mutableStateOf("media") }
    val media = remember(conversation.messages) {
        conversation.messages.filter {
            BlinkChatMediaAccessPolicy.mayBrowse(
                it.imageViewOnce, it.deletedForEveryone,
                !it.attachedImageUrl.isNullOrBlank() ||
                    !it.attachedVideoUrl.isNullOrBlank() ||
                    !it.attachedAudioUrl.isNullOrBlank()
            )
        }
    }
    val links = remember(conversation.messages) {
        conversation.messages.mapNotNull { message ->
            firstHttpUrl(message.text)?.let { url -> message to url }
        }
    }
    val files = remember(conversation.messages) {
        conversation.messages.filter {
            !it.deletedForEveryone && !it.attachedDocumentUrl.isNullOrBlank()
        }
    }
    val starred = remember(conversation.messages) {
        conversation.messages.filter {
            BlinkChatMediaAccessPolicy.mayKeepInSavedMedia(
                it.imageViewOnce, it.deletedForEveryone, it.isStarred
            )
        }
    }
    var historicalPictures by remember(conversation.id) {
        mutableStateOf<List<ChatMessage>>(emptyList())
    }
    var photoOffset by remember(conversation.id) { mutableIntStateOf(0) }
    var photoHasMore by remember(conversation.id) { mutableStateOf(true) }
    var photoLoading by remember(conversation.id) { mutableStateOf(false) }
    var photoError by remember(conversation.id) { mutableStateOf<String?>(null) }
    val photoScope = rememberCoroutineScope()

    suspend fun loadNextPhotoPage() {
        if (photoLoading || !photoHasMore ||
            conversation.id.isBlank() || conversation.id.startsWith("local_")) return
        photoLoading = true
        MessageMediaService.fetchConversationPhotoPage(
            conversation.id, photoOffset, 24
        ).fold(
            onSuccess = { photos ->
                historicalPictures = (historicalPictures + photos).distinctBy { it.id }
                photoOffset += 24
                photoHasMore = photos.size >= 24
                photoError = null
            },
            onFailure = { photoError = it.message ?: "Could not load shared photos." }
        )
        photoLoading = false
    }

    LaunchedEffect(conversation.id) {
        loadNextPhotoPage()
    }
    val galleryPictures = historicalPictures.ifEmpty {
        if (photoError != null) media.filter { !it.attachedImageUrl.isNullOrBlank() }
        else emptyList()
    }
    val galleryOtherMedia = remember(media) {
        media.filter { it.attachedImageUrl.isNullOrBlank() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Text(
            "Shared content",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                "media" to "Media",
                "links" to "Links",
                "files" to "Files",
                "starred" to "Starred"
            ).forEach { (key, label) ->
                item(key = key) {
                    Surface(
                        color = if (tab == key) palette.textPrimary else palette.glassElevated,
                        contentColor = if (tab == key) palette.backgroundMiddle else palette.textPrimary,
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, if (tab == key) palette.textPrimary else palette.border),
                        modifier = Modifier.clickable { tab = key }
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (tab) {
                "media" -> {
                    if (galleryPictures.isEmpty() && galleryOtherMedia.isEmpty() &&
                        !photoLoading && !photoHasMore) {
                        item { SharedContentEmpty("No shared media yet", palette) }
                    }
                    items(galleryPictures, key = { "photo:${it.id}" }) { picture ->
                        Surface(
                            color = palette.glassElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onOpenPhoto(picture, galleryPictures) }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = picture.imagePreviewUrl ?: picture.attachedImageUrl,
                                    contentDescription = "Private shared picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(76.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Photo", color = palette.textPrimary,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(
                                        picture.text.takeIf { it != "Image" }.orEmpty()
                                            .ifBlank { picture.timestamp },
                                        color = palette.textSecondary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    items(galleryOtherMedia, key = { "other:${it.id}" }) { message ->
                        val url = message.attachedVideoUrl ?: message.attachedAudioUrl
                        SharedContentRow(
                            title = when {
                                !message.attachedVideoUrl.isNullOrBlank() -> "Video"
                                message.isVoiceNote -> "Voice message"
                                else -> "Audio"
                            },
                            subtitle = message.timestamp,
                            palette = palette,
                            onClick = { url?.let { openExternalUri(context, Uri.parse(it)) } }
                        )
                    }
                    if (photoLoading) {
                        item {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    photoError?.let { error ->
                        item {
                            Column {
                                Text(error, color = palette.danger, fontSize = 11.sp)
                                androidx.compose.material3.TextButton(
                                    enabled = !photoLoading,
                                    onClick = { photoScope.launch { loadNextPhotoPage() } }
                                ) { Text("Retry") }
                            }
                        }
                    }
                    if (photoHasMore && !photoLoading && photoError == null) {
                        item {
                            androidx.compose.material3.TextButton(
                                onClick = { photoScope.launch { loadNextPhotoPage() } }
                            ) { Text("Load older photos") }
                        }
                    }
                }
                "links" -> {
                    if (links.isEmpty()) {
                        item { SharedContentEmpty("No shared links yet", palette) }
                    } else {
                        items(links, key = { "link:${it.first.id}:${it.second}" }) { (_, url) ->
                            val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
                            SharedContentRow(
                                title = host.ifBlank { "Link" },
                                subtitle = url,
                                palette = palette,
                                onClick = { openExternalUri(context, Uri.parse(url)) }
                            )
                        }
                    }
                }
                "files" -> {
                    if (files.isEmpty()) {
                        item { SharedContentEmpty("No shared files yet", palette) }
                    } else {
                        items(files, key = { "file:${it.id}" }) { message ->
                            SharedContentRow(
                                title = message.attachmentName ?: message.text.ifBlank { "Document" },
                                subtitle = message.timestamp,
                                palette = palette,
                                onClick = {
                                    message.attachedDocumentUrl?.let {
                                        openExternalUri(context, Uri.parse(it))
                                    }
                                }
                            )
                        }
                    }
                }
                else -> {
                    if (starred.isEmpty()) {
                        item { SharedContentEmpty("No starred messages yet", palette) }
                    } else {
                        items(starred, key = { "starred:${it.id}" }) { message ->
                            SharedContentRow(
                                title = message.text.ifBlank {
                                    when {
                                        !message.attachedImageUrl.isNullOrBlank() -> "Photo"
                                        !message.attachedVideoUrl.isNullOrBlank() -> "Video"
                                        !message.attachedDocumentUrl.isNullOrBlank() -> "Document"
                                        else -> "Media message"
                                    }
                                },
                                subtitle = message.timestamp,
                                palette = palette,
                                onClick = { onJumpToMessage(message.id) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp).navigationBarsPadding())
    }


}

@Composable
private fun SharedContentRow(
    title: String,
    subtitle: String,
    palette: MessagePalette,
    onClick: () -> Unit
) {
    Surface(
        color = palette.glassElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = palette.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SharedContentEmpty(
    text: String,
    palette: MessagePalette
) {
    Text(
        text,
        color = palette.textSecondary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatNotificationSettingsSheet(
    conversation: ChatConversation,
    palette: MessagePalette,
    onSelect: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Text(
            "Chat notifications",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            "Choose how this conversation can notify you.",
            color = palette.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        val options = listOf(
            Triple("All messages", "all", null),
            Triple("Mentions only", "mentions", null),
            Triple("Mute for 1 hour", "none", java.time.Instant.now().plusSeconds(3600).toString()),
            Triple("Mute for 8 hours", "none", java.time.Instant.now().plusSeconds(8 * 3600L).toString()),
            Triple("Mute for 1 week", "none", java.time.Instant.now().plusSeconds(7 * 24 * 3600L).toString()),
            Triple("Mute until I turn it back on", "none", null)
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            options.forEach { (label, mode, until) ->
                val selected = when {
                    mode == "all" -> conversation.notificationMode == "all"
                    mode == "mentions" -> conversation.notificationMode == "mentions"
                    label.startsWith("Mute until") ->
                        conversation.notificationMode == "none" && conversation.muteUntil == null
                    else -> false
                }
                Surface(
                    color = if (selected) palette.accent.copy(alpha = .14f) else palette.glassElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) palette.accent.copy(alpha = .55f) else palette.border
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(mode, until) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp).navigationBarsPadding())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    palette: MessagePalette,
    onImage: () -> Unit,
    onCamera: () -> Unit,
    onViewOnce: () -> Unit,
    onOriginalPhotos: () -> Unit,
    onSticker: () -> Unit,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onDocument: () -> Unit,
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
        Text(
            "Media stays private to conversation members.",
            color = palette.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttachmentOption(
                label = "Photo",
                description = "Choose an image from your device",
                icon = Icons.Default.Image,
                palette = palette,
                onClick = onImage
            )
            AttachmentOption(
                label = "Camera",
                description = "Take a picture and review before sending",
                icon = Icons.Default.Image,
                palette = palette,
                onClick = onCamera
            )
            AttachmentOption(
                label = "Original photos",
                description = "Send up to 10 photos without resizing",
                icon = Icons.Default.Image,
                palette = palette,
                onClick = onOriginalPhotos
            )
            AttachmentOption(
                label = "View-once photo",
                description = "Recipient can open this photo only once",
                icon = Icons.Default.Image,
                palette = palette,
                onClick = onViewOnce
            )
            AttachmentOption(
                label = "Stickers",
                description = "Create and reuse saved image stickers",
                icon = Icons.Default.EmojiEmotions,
                palette = palette,
                onClick = onSticker
            )
            AttachmentOption(
                label = "Video",
                description = "Choose a video from your device",
                icon = Icons.Default.VideoLibrary,
                palette = palette,
                onClick = onVideo
            )
            AttachmentOption(
                label = "Audio",
                description = "Share an audio file",
                icon = Icons.Default.MusicNote,
                palette = palette,
                onClick = onAudio
            )
            AttachmentOption(
                label = "Document",
                description = "PDF, slides, notes and other files",
                icon = Icons.Default.Description,
                palette = palette,
                onClick = onDocument
            )
        }
        Spacer(Modifier.height(18.dp).navigationBarsPadding())
    }
}

@Composable
private fun AttachmentOption(
    label: String,
    description: String,
    icon: ImageVector,
    palette: MessagePalette,
    onClick: () -> Unit
) {
    Surface(
        color = palette.glassElevated.copy(alpha = .75f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = palette.accent.copy(alpha = .16f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, color = palette.textSecondary, fontSize = 11.sp)
            }
        }
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
                error = painterResource(R.drawable.ic_default_profile),
                fallback = painterResource(R.drawable.ic_default_profile),
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
                palette = palette,
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
private fun ChatPrivacySettingsSheet(
    settings: ChatPrivacySettings,
    palette: MessagePalette,
    onChange: (ChatPrivacySettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var autoWifi by remember { mutableStateOf(BlinkChatMediaPreferences.wifiAuto(context)) }
    var autoMobile by remember { mutableStateOf(BlinkChatMediaPreferences.mobileAuto(context)) }
    var dataSaver by remember { mutableStateOf(BlinkChatMediaPreferences.dataSaver(context)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.glass,
        contentColor = palette.textPrimary
    ) {
        Text(
            "Privacy & messaging",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            "Control who can reach you and what activity other people can see.",
            color = palette.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ChatPrivacyScopeSection(
                    title = "Who can message me",
                    value = settings.whoCanMessage,
                    palette = palette,
                    onSelect = { onChange(settings.copy(whoCanMessage = it)) }
                )
            }
            item {
                ChatPrivacyScopeSection(
                    title = "Who can call me",
                    value = settings.whoCanCall,
                    palette = palette,
                    onSelect = { onChange(settings.copy(whoCanCall = it)) }
                )
            }
            item {
                ChatPrivacyScopeSection(
                    title = "Who can add me to groups",
                    value = settings.whoCanGroupInvite,
                    palette = palette,
                    onSelect = { onChange(settings.copy(whoCanGroupInvite = it)) }
                )
            }

            item {
                Text(
                    "Activity",
                    color = palette.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            item {
                ChatPrivacyToggleRow(
                    "Show online status",
                    "Allow people to see when you are active.",
                    settings.showOnline,
                    palette
                ) { onChange(settings.copy(showOnline = it)) }
            }
            item {
                ChatPrivacyToggleRow(
                    "Show last seen",
                    "Show your most recent activity time.",
                    settings.showLastSeen,
                    palette
                ) { onChange(settings.copy(showLastSeen = it)) }
            }
            item {
                ChatPrivacyToggleRow(
                    "Read receipts",
                    "Send read status when you open messages.",
                    settings.sendReadReceipts,
                    palette
                ) { onChange(settings.copy(sendReadReceipts = it)) }
            }
            item {
                ChatPrivacyToggleRow(
                    "Typing status",
                    "Let people see when you are typing.",
                    settings.showTyping,
                    palette
                ) { onChange(settings.copy(showTyping = it)) }
            }
            item {
                ChatPrivacyToggleRow(
                    "Recording status",
                    "Let people see when you are recording a voice note.",
                    settings.showRecording,
                    palette
                ) { onChange(settings.copy(showRecording = it)) }
            }
            item {
                ChatPrivacyToggleRow(
                    "Profile photo in chat",
                    "Allow your profile photo to appear in conversations.",
                    settings.showProfilePhotoInChat,
                    palette
                ) { onChange(settings.copy(showProfilePhotoInChat = it)) }
            }
            item {
                ChatPrivacyToggleRow(
                    "Link previews",
                    "Show compact previews for web links in messages.",
                    settings.allowLinkPreviews,
                    palette
                ) { onChange(settings.copy(allowLinkPreviews = it)) }
            }

            item {
                Text(
                    "Photo downloads",
                    color = palette.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            item {
                ChatPrivacyToggleRow(
                    "Automatically download on Wi-Fi",
                    "Load normal-quality private photos over Wi-Fi.",
                    autoWifi,
                    palette
                ) {
                    autoWifi = it
                    BlinkChatMediaPreferences.update(context, wifi = it)
                }
            }
            item {
                ChatPrivacyToggleRow(
                    "Automatically download on mobile data",
                    "Off by default to save mobile data.",
                    autoMobile,
                    palette
                ) {
                    autoMobile = it
                    BlinkChatMediaPreferences.update(context, mobile = it)
                }
            }
            item {
                ChatPrivacyToggleRow(
                    "Data Saver",
                    "Show small picture previews until you choose to open more.",
                    dataSaver,
                    palette
                ) {
                    dataSaver = it
                    BlinkChatMediaPreferences.update(context, saver = it)
                }
            }

            item {
                Text(
                    "Notification preview",
                    color = palette.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            item {
                ChatPrivacyChoiceRow(
                    label = "Full preview",
                    selected = settings.notificationPreview == "full",
                    palette = palette,
                    onClick = { onChange(settings.copy(notificationPreview = "full")) }
                )
            }
            item {
                ChatPrivacyChoiceRow(
                    label = "Sender only",
                    selected = settings.notificationPreview == "sender_only",
                    palette = palette,
                    onClick = { onChange(settings.copy(notificationPreview = "sender_only")) }
                )
            }
            item {
                ChatPrivacyChoiceRow(
                    label = "Hide message preview",
                    selected = settings.notificationPreview == "hidden",
                    palette = palette,
                    onClick = { onChange(settings.copy(notificationPreview = "hidden")) }
                )
            }
        }
        Spacer(Modifier.height(16.dp).navigationBarsPadding())
    }
}

@Composable
private fun ChatPrivacyScopeSection(
    title: String,
    value: String,
    palette: MessagePalette,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.glassElevated)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            title,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            contentPadding = PaddingValues(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val choices = listOf(
                "everyone" to "Everyone",
                "followers" to "Followers",
                "mutuals" to "Mutuals",
                "connections" to "Connections",
                "nobody" to "Nobody"
            )
            items(choices, key = { it.first }) { (key, label) ->
                val selected = value == key
                Surface(
                    color = if (selected) palette.textPrimary else palette.glass,
                    contentColor = if (selected) palette.backgroundMiddle else palette.textPrimary,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) palette.textPrimary else palette.border
                    ),
                    modifier = Modifier.clickable { onSelect(key) }
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatPrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    palette: MessagePalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = palette.glassElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun ChatPrivacyChoiceRow(
    label: String,
    selected: Boolean,
    palette: MessagePalette,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) palette.accent.copy(alpha = .12f) else palette.glassElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (selected) palette.accent.copy(alpha = .50f) else palette.border
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = palette.textPrimary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageAppearanceSheet(
    selected: MessageThemeMode,
    palette: MessagePalette,
    unreadActivityCount: Int,
    onSelect: (MessageThemeMode) -> Unit,
    onOpenPrivacy: () -> Unit,
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
                Text("Black is the default theme", color = palette.textSecondary, fontSize = 11.sp)
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
                .clickable(onClick = onOpenPrivacy)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = palette.glassElevated) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.padding(10.dp).size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Privacy & messaging", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Who can message, call and see your activity", color = palette.textSecondary, fontSize = 11.sp)
            }
            Text("›", color = palette.accent, fontSize = 24.sp)
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
            if (mode == MessageThemeMode.DARK) {
                Text("Default", color = preview.textSecondary, fontSize = 11.sp)
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
    online: Boolean? = null,
    emphasizeRing: Boolean = true
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (emphasizeRing) {
                        Brush.linearGradient(listOf(palette.accent, palette.accentSecondary, palette.accent))
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
        if (online == true) {
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
            error = painterResource(R.drawable.ic_default_profile),
            fallback = painterResource(R.drawable.ic_default_profile),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier.clip(CircleShape).background(palette.glass),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_default_profile),
                contentDescription = "$name default profile picture",
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize()
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
    com.example.call.BlinkCallLauncher.startOutgoing(
        context = context,
        conversationId = conversation.id,
        calleeId = conversation.partnerId,
        calleeUsername = conversation.partnerUsername,
        calleeName = conversation.partnerName,
        calleeAvatar = conversation.partnerAvatar,
        type = if (kind == MessageCallKind.AUDIO) {
            com.example.call.CallType.AUDIO
        } else {
            com.example.call.CallType.VIDEO
        }
    )
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
