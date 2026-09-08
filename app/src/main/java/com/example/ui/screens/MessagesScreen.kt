package com.example.ui.screens

import android.net.Uri
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
    onRetryMessage: ((String, ChatMessage) -> Unit)? = null,
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
        onRetryMessage = onRetryMessage,
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
