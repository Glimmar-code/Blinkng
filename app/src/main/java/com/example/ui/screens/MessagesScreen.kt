package com.example.ui.screens

import android.net.Uri
import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.BlinkChatImageQuality
import com.blinkng.shared.BlinkChatPhotoAlbum
import androidx.compose.runtime.Composable
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage

/**
 * Compatibility entry point for callers that still reference the original MessagesScreen.
 *
 * The legacy duplicate UI implementation was removed. All rendering now flows through
 * PremiumMessagesScreen so Blink has one canonical Messages experience.
 */
@Composable
fun MessagesScreen(
    conversations: List<ChatConversation>,
    activePartner: String?,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onSendVideo: (String, Uri) -> Unit = { _, _ -> },
    onSendAttachment: (String, Uri, BlinkChatAttachmentKind, Long, String, BlinkChatImageQuality, BlinkChatPhotoAlbum) -> Unit = { _, _, _, _, _, _, _ -> },
    onSendViewOncePicture: (String, Uri, String, BlinkChatImageQuality) -> Unit = { _, _, _, _ -> },
    onRetryMessage: ((String, ChatMessage) -> Unit)? = null,
    onCancelAttachment: ((String, ChatMessage) -> Unit)? = null,
    onForwardPicture: (String, ChatMessage) -> Unit = { _, _ -> },
    hasMoreMessages: (String) -> Boolean = { false },
    isLoadingOlder: (String) -> Boolean = { false },
    onLoadOlder: (String) -> Unit = {},
    isLoadingMessages: (String) -> Boolean = { false },
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    isConnected: Boolean = true,
    isLoading: Boolean = false
) {
    PremiumMessagesScreen(
        conversations = conversations,
        stories = emptyList(),
        activities = emptyList(),
        myAvatar = "",
        myName = "",
        activePartner = activePartner,
        isConversationFullScreen = activePartner != null,
        onConversationFullScreenChange = {},
        onOpenConversation = onOpenConversation,
        onCloseConversation = onCloseConversation,
        onSendMessage = { partner, text, _ -> onSendMessage(partner, text) },
        onSendVideo = onSendVideo,
        onSendAttachment = { partner, uri, kind ->
            val attachment = BlinkChatAttachmentKind.fromWireType(kind)
                ?: if (kind == "audio") BlinkChatAttachmentKind.VOICE else null
            if (attachment != null) onSendAttachment(
                partner, uri, attachment, 0L, "", BlinkChatImageQuality.STANDARD,
                BlinkChatPhotoAlbum.single()
            )
        },
        onSendViewOncePicture = onSendViewOncePicture,
        onSendPhotoAlbum = { partner, uris, quality ->
            val photos = uris.take(10)
            val albumId = if (photos.size > 1) java.util.UUID.randomUUID().toString() else null
            photos.forEachIndexed { index, uri ->
                onSendAttachment(
                    partner, uri, BlinkChatAttachmentKind.IMAGE, 0L, "", quality,
                    if (albumId == null) BlinkChatPhotoAlbum.single()
                    else BlinkChatPhotoAlbum.of(albumId, index, photos.size)
                )
            }
        },
        onRetryMessage = onRetryMessage,
        onCancelAttachment = onCancelAttachment,
        hasMoreMessages = hasMoreMessages,
        isLoadingOlder = isLoadingOlder,
        onLoadOlder = onLoadOlder,
        isLoadingMessages = isLoadingMessages,
        onProfileClick = onProfileClick,
        onStoryClick = {},
        onAddStoryClick = {},
        onOpenActivity = {},
        interactionActions = ChatInteractionActions(),
        isConnected = isConnected,
        isLoading = isLoading,
        isDark = isDark
    )
}

/**
 * Compatibility route for direct-chat overlays that previously depended on the legacy
 * ChatConversationView implementation. Rendering is delegated to the canonical premium
 * messaging screen with a single active conversation.
 */
@Composable
fun ChatConversationView(
    convo: ChatConversation,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendVideo: (Uri) -> Unit = {},
    onSendAttachment: (Uri, BlinkChatAttachmentKind, Long, String, BlinkChatImageQuality, BlinkChatPhotoAlbum) -> Unit = { _, _, _, _, _, _ -> },
    onSendViewOncePicture: (Uri, String, BlinkChatImageQuality) -> Unit = { _, _, _ -> },
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    isConnected: Boolean = true,
    onRetryMessage: ((ChatMessage) -> Unit)? = null,
    onCancelAttachment: ((ChatMessage) -> Unit)? = null,
    onForwardPicture: (String, ChatMessage) -> Unit = { _, _ -> },
    hasMoreMessages: Boolean = false,
    isLoadingOlder: Boolean = false,
    onLoadOlder: () -> Unit = {}
) {
    PremiumMessagesScreen(
        conversations = listOf(convo),
        stories = emptyList(),
        activities = emptyList(),
        myAvatar = "",
        myName = "",
        activePartner = convo.partnerUsername,
        isConversationFullScreen = true,
        onConversationFullScreenChange = {},
        onOpenConversation = {},
        onCloseConversation = onBack,
        onSendMessage = { _, text, _ -> onSendMessage(text) },
        onSendVideo = { _, uri -> onSendVideo(uri) },
        onSendAttachment = { _, uri, kind ->
            val attachment = BlinkChatAttachmentKind.fromWireType(kind)
                ?: if (kind == "audio") BlinkChatAttachmentKind.VOICE else null
            if (attachment != null) onSendAttachment(
                uri, attachment, 0L, "", BlinkChatImageQuality.STANDARD,
                BlinkChatPhotoAlbum.single()
            )
        },
        onSendViewOncePicture = { _, uri, caption, quality ->
            onSendViewOncePicture(uri, caption, quality)
        },
        onSendPhotoAlbum = { _, uris, quality ->
            val photos = uris.take(10)
            val albumId = if (photos.size > 1) java.util.UUID.randomUUID().toString() else null
            photos.forEachIndexed { index, uri ->
                onSendAttachment(
                    uri, BlinkChatAttachmentKind.IMAGE, 0L, "", quality,
                    if (albumId == null) BlinkChatPhotoAlbum.single()
                    else BlinkChatPhotoAlbum.of(albumId, index, photos.size)
                )
            }
        },
        onRetryMessage = onRetryMessage?.let { retry -> { _, message -> retry(message) } },
        onCancelAttachment = onCancelAttachment?.let { cancel -> { _, message -> cancel(message) } },
        hasMoreMessages = { hasMoreMessages },
        isLoadingOlder = { isLoadingOlder },
        onLoadOlder = { onLoadOlder() },
        isLoadingMessages = { false },
        onProfileClick = onProfileClick,
        onStoryClick = {},
        onAddStoryClick = {},
        onOpenActivity = {},
        interactionActions = ChatInteractionActions(),
        isConnected = isConnected,
        isLoading = false,
        isDark = isDark
    )
}
