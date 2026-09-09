package com.example

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.models.ActivityItem
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.Story
import com.example.ui.screens.ChatInteractionActions

/**
 * Startup-hydration adapter for the canonical premium Messages experience.
 *
 * MainActivity lives in this package, so this overload is resolved before the
 * star-imported screen implementation. It keeps the inbox in a loading state
 * during the brief window where the ViewModel has not restored cached/cloud
 * conversations yet, instead of rendering a false "No conversations" state.
 *
 * The adapter never delays real data: cached conversations render immediately,
 * offline users can see a genuine empty inbox, and an online empty inbox becomes
 * final as soon as the existing conversation load starts and completes.
 */
@Composable
fun PremiumMessagesScreen(
    conversations: List<ChatConversation>,
    stories: List<Story>,
    activities: List<ActivityItem>,
    myAvatar: String,
    myName: String,
    activePartner: String?,
    isConversationFullScreen: Boolean,
    onConversationFullScreenChange: (Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, String?) -> Unit,
    interactionActions: ChatInteractionActions,
    onSendVideo: (String, Uri) -> Unit,
    onRetryMessage: ((String, ChatMessage) -> Unit)?,
    hasMoreMessages: (String) -> Boolean,
    isLoadingOlder: (String) -> Boolean,
    onLoadOlder: (String) -> Unit,
    isLoadingMessages: (String) -> Boolean,
    onProfileClick: (String) -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenActivity: () -> Unit,
    isDark: Boolean,
    isConnected: Boolean,
    isLoading: Boolean
) {
    var sawConversationLoad by remember { mutableStateOf(isLoading) }
    var initialHydrationResolved by remember {
        mutableStateOf(conversations.isNotEmpty() || !isConnected)
    }

    LaunchedEffect(isLoading, conversations.isNotEmpty(), isConnected) {
        if (isLoading) sawConversationLoad = true

        if (
            conversations.isNotEmpty() ||
            !isConnected ||
            (sawConversationLoad && !isLoading)
        ) {
            initialHydrationResolved = true
        }
    }

    val effectiveLoading = isLoading ||
        (conversations.isEmpty() && isConnected && !initialHydrationResolved)

    com.example.ui.screens.PremiumMessagesScreen(
        conversations = conversations,
        stories = stories,
        activities = activities,
        myAvatar = myAvatar,
        myName = myName,
        activePartner = activePartner,
        isConversationFullScreen = isConversationFullScreen,
        onConversationFullScreenChange = onConversationFullScreenChange,
        onOpenConversation = onOpenConversation,
        onCloseConversation = onCloseConversation,
        onSendMessage = onSendMessage,
        onSendVideo = onSendVideo,
        onRetryMessage = onRetryMessage,
        hasMoreMessages = hasMoreMessages,
        isLoadingOlder = isLoadingOlder,
        onLoadOlder = onLoadOlder,
        isLoadingMessages = isLoadingMessages,
        onProfileClick = onProfileClick,
        onStoryClick = onStoryClick,
        onAddStoryClick = onAddStoryClick,
        onOpenActivity = onOpenActivity,
        interactionActions = interactionActions,
        isConnected = isConnected,
        isLoading = effectiveLoading,
        isDark = isDark
    )
}
