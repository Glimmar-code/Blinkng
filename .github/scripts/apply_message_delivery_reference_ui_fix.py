from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        print(f"{label}: already applied")
        return text
    if old not in text:
        raise SystemExit(f"{label}: expected source block not found")
    print(f"{label}: applied")
    return text.replace(old, new, 1)


vm = VM.read_text()

vm = replace_once(
    vm,
    "    private val syncMutex = Mutex()\n    private val cacheWriteMutex = Mutex()",
    "    private val syncMutex = Mutex()\n    private val cacheWriteMutex = Mutex()\n    private val messageOutboxMutex = Mutex()",
    "message outbox mutex",
)

vm = replace_once(
    vm,
    '''        val optimistic = ChatMessage(
            id = tempId,
            senderId = uid,
            senderUsername = currentUsername,
            receiverUsername = cleanPartner,
            text = cleanText,
            timestamp = if (_uiState.value.isOnline) "Sending..." else "Queued",
            rawTimestamp = java.time.Instant.now().toString(),
            isFromMe = true,
            isRead = false,
            status = MessageStatus.SENDING
        )
        appendMessageToState(cleanPartner, optimistic)
        persistConversations()

        viewModelScope.launch(Dispatchers.IO) {
            offlineContentStore.enqueueMessage(tempId, cleanPartner, cleanText)
            if (_uiState.value.isOnline) {
                drainMessageOutbox()
            } else {
                withContext(Dispatchers.Main) {
                    showToast("Message queued. Blink will send it when you're back online.")
                }
            }
        }''',
    '''        val optimistic = ChatMessage(
            id = tempId,
            senderId = uid,
            senderUsername = currentUsername,
            receiverUsername = cleanPartner,
            text = cleanText,
            timestamp = "Sending...",
            rawTimestamp = java.time.Instant.now().toString(),
            isFromMe = true,
            isRead = false,
            status = MessageStatus.SENDING
        )
        appendMessageToState(cleanPartner, optimistic)
        persistConversations()

        // Always attempt delivery immediately. The old path trusted a cached connectivity
        // boolean before touching Supabase, which could leave a perfectly online device stuck
        // on "Sending..." forever. The durable outbox still protects offline sends.
        viewModelScope.launch(Dispatchers.IO) {
            offlineContentStore.enqueueMessage(tempId, cleanPartner, cleanText)
            drainMessageOutbox()
        }''',
    "immediate message delivery",
)

vm = replace_once(
    vm,
    '''            if (_uiState.value.isOnline) drainMessageOutbox()
        }
    }

    private suspend fun drainMessageOutbox() {
        if (!_uiState.value.isOnline || supabaseService.getCurrentUserId().isNullOrBlank()) return
        val pending = offlineContentStore.pendingOutbox(40)
        if (pending.isEmpty()) return

        for (item in pending) {
            chatRepository.sendMessage(item.receiverUsername, item.content).fold(
                onSuccess = { serverMsg ->
                    offlineContentStore.deleteOutbox(item.localId)
                    withContext(Dispatchers.Main) {
                        replaceMessageInState(
                            item.receiverUsername,
                            item.localId,
                            serverMsg.copy(
                                receiverUsername = item.receiverUsername,
                                status = MessageStatus.SENT
                            )
                        )
                        persistConversations()
                    }
                    runCatching {
                        supabaseService.recordActivity(
                            item.receiverUsername,
                            "sent you a direct message",
                            NotificationFilter.ALL,
                            targetUsername = supabaseService.getCurrentUsername().orEmpty(),
                            previewText = item.content,
                            targetType = "CHAT"
                        )
                    }
                    reconcileConversationSummary(item.receiverUsername)
                },
                onFailure = { error ->
                    offlineContentStore.markOutboxFailure(item, error.message ?: "Message send failed")
                    withContext(Dispatchers.Main) {
                        updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                        persistConversations()
                        showToast(error.message ?: "Message failed. Please try again.")
                    }
                }
            )
        }
    }''',
    '''            drainMessageOutbox()
        }
    }

    private suspend fun drainMessageOutbox() = messageOutboxMutex.withLock {
        if (supabaseService.getCurrentUserId().isNullOrBlank()) return@withLock
        val pending = offlineContentStore.pendingOutbox(40)
        if (pending.isEmpty()) return@withLock

        for (item in pending) {
            chatRepository.sendMessage(item.receiverUsername, item.content).fold(
                onSuccess = { serverMsg ->
                    offlineContentStore.deleteOutbox(item.localId)
                    withContext(Dispatchers.Main) {
                        replaceMessageInState(
                            item.receiverUsername,
                            item.localId,
                            serverMsg.copy(
                                receiverUsername = item.receiverUsername,
                                status = MessageStatus.SENT
                            )
                        )
                        persistConversations()
                    }
                    runCatching {
                        supabaseService.recordActivity(
                            item.receiverUsername,
                            "sent you a direct message",
                            NotificationFilter.ALL,
                            targetUsername = supabaseService.getCurrentUsername().orEmpty(),
                            previewText = item.content,
                            targetType = "CHAT"
                        )
                    }
                    reconcileConversationSummary(item.receiverUsername)
                },
                onFailure = { error ->
                    val detail = error.message.orEmpty()
                    val lower = detail.lowercase()
                    val retryable = lower.contains("timeout") ||
                        lower.contains("timed out") ||
                        lower.contains("network") ||
                        lower.contains("failed to connect") ||
                        lower.contains("unable to resolve host") ||
                        lower.contains("no route to host") ||
                        lower.contains("socket") ||
                        !_uiState.value.isOnline

                    if (retryable) {
                        // Keep it durable and immediately eligible for the network-restored drain.
                        offlineContentStore.resetOutbox(item.localId)
                        withContext(Dispatchers.Main) {
                            updateMessageStatusInState(
                                item.receiverUsername,
                                item.localId,
                                MessageStatus.SENDING,
                                pendingLabel = "Queued"
                            )
                            persistConversations()
                        }
                    } else {
                        offlineContentStore.markOutboxFailure(item, detail.ifBlank { "Message send failed" })
                        withContext(Dispatchers.Main) {
                            updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                            persistConversations()
                            showToast(detail.ifBlank { "Message failed. Please try again." })
                        }
                    }
                }
            )
        }
    }''',
    "outbox drain reliability",
)

old_status = '''    private fun updateMessageStatusInState(partnerUsername: String, messageId: String, status: MessageStatus) { val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }; if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(messages = old.messages.map { if (it.id == messageId) it.copy(status = status, timestamp = if (status == MessageStatus.SENDING) "Sending..." else it.timestamp) else it }.toMutableList()); _uiState.value = _uiState.value.copy(conversations = conversations); persistConversations() } }'''
new_status = '''    private fun updateMessageStatusInState(
        partnerUsername: String,
        messageId: String,
        status: MessageStatus,
        pendingLabel: String = "Sending..."
    ) {
        val conversations = _uiState.value.conversations.toMutableList()
        val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
        if (index >= 0) {
            val old = conversations[index]
            conversations[index] = old.copy(
                messages = old.messages.map {
                    if (it.id == messageId) {
                        it.copy(
                            status = status,
                            timestamp = if (status == MessageStatus.SENDING) pendingLabel else it.timestamp
                        )
                    } else it
                }.toMutableList()
            )
            _uiState.value = _uiState.value.copy(conversations = conversations)
            persistConversations()
        }
    }'''
vm = replace_once(vm, old_status, new_status, "queued/sending status labels")

VM.write_text(vm)

main = MAIN.read_text()
old_main = '''                MainTab.MESSAGES -> {
                    MessagesScreen(
                        conversations = uiState.conversations,
                        activePartner = uiState.activeConversationPartner,
                        onOpenConversation = { partner ->
                            viewModel.openChatWithUser(partner)
                        },
                        onCloseConversation = { viewModel.closeConversation() },
                        onSendMessage = { partner, text -> viewModel.sendMessage(partner, text) },
                        onSendVideo = { partner, uri -> viewModel.sendVideoMessage(partner, uri) },
                        onRetryMessage = { partner, message ->
                            viewModel.retrySendMessage(partner, message)
                        },
                        hasMoreMessages = { conversationId ->
                            uiState.messageHistoryHasMore[conversationId] ?: true
                        },
                        isLoadingOlder = { conversationId ->
                            uiState.loadingOlderConversationId == conversationId
                        },
                        onLoadOlder = { partner -> viewModel.loadOlderMessages(partner) },
                        onProfileClick = { viewModel.openProfile(it) },
                        isDark = uiState.isDarkMode,
                        isConnected = uiState.isOnline
                    )
                }'''
new_main = '''                MainTab.MESSAGES -> {
                    PremiumMessagesScreen(
                        conversations = uiState.conversations,
                        stories = uiState.stories,
                        activities = uiState.activities,
                        myAvatar = uiState.myProfile.avatarUrl,
                        myName = uiState.myProfile.fullName.ifBlank { uiState.myProfile.username },
                        activePartner = uiState.activeConversationPartner,
                        onOpenConversation = { partner ->
                            viewModel.openChatWithUser(partner)
                        },
                        onCloseConversation = { viewModel.closeConversation() },
                        onSendMessage = { partner, text -> viewModel.sendMessage(partner, text) },
                        onRetryMessage = { partner, message ->
                            viewModel.retrySendMessage(partner, message)
                        },
                        onProfileClick = { viewModel.openProfile(it) },
                        onStoryClick = { story -> viewModel.openStory(story) },
                        onAddStoryClick = { viewModel.openCreateStory(true) },
                        onOpenActivity = { viewModel.openActivity(true) },
                        isDark = uiState.isDarkMode,
                        isConnected = uiState.isOnline
                    )
                }'''
main = replace_once(main, old_main, new_main, "reference-style messages screen")
MAIN.write_text(main)

print("Message delivery and reference UI patch complete.")
