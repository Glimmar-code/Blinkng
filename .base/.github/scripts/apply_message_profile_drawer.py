from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MESSAGES = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
VIEWMODEL = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"


def add_import(text: str, anchor: str, new_import: str) -> str:
    if new_import in text:
        return text
    if anchor not in text:
        raise RuntimeError(f"Import anchor not found: {anchor}")
    return text.replace(anchor, anchor + "\n" + new_import, 1)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"Patch anchor changed: {label}")
    return text.replace(old, new, 1)


MASTER_DETAIL = r'''
@Composable
private fun PremiumMessagesMasterDetail(
    conversations: List<ChatConversation>,
    activeConversation: ChatConversation,
    palette: MessagePalette,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onSendVideo: (String, Uri) -> Unit,
    onRetryMessage: ((String, ChatMessage) -> Unit)?,
    onProfileClick: (String) -> Unit,
    onStartCall: (ChatConversation, MessageCallKind) -> Unit,
    isConnected: Boolean
) {
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }

    MessageBackground(palette) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Chat drawer" }
        ) {
            val railWidth = (maxWidth * .20f).coerceIn(68.dp, 92.dp)
            val targetPanelWidth = if (isFullScreen) maxWidth else maxWidth - railWidth
            val panelWidth by animateDpAsState(
                targetValue = targetPanelWidth,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "chatDrawerWidth"
            )

            AnimatedVisibility(
                visible = !isFullScreen,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(railWidth)
                    .fillMaxHeight()
            ) {
                ConversationSwitchRail(
                    conversations = conversations,
                    selectedPartner = activeConversation.partnerUsername,
                    palette = palette,
                    onOpenConversation = onOpenConversation,
                    onCloseConversation = onCloseConversation
                )
            }

            val panelShape = if (isFullScreen) {
                RoundedCornerShape(0.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .pointerInput(isFullScreen, activeConversation.partnerUsername) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragCancel = { totalDrag = 0f },
                            onDragEnd = {
                                when {
                                    isFullScreen && totalDrag > swipeThresholdPx -> onFullScreenChange(false)
                                    !isFullScreen && totalDrag < -swipeThresholdPx -> onFullScreenChange(true)
                                }
                                totalDrag = 0f
                            }
                        )
                    },
                shape = panelShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = if (isFullScreen) 0.dp else 14.dp,
                border = if (isFullScreen) null else BorderStroke(1.dp, palette.border)
            ) {
                AnimatedContent(
                    targetState = activeConversation.partnerUsername,
                    transitionSpec = {
                        (slideInHorizontally(
                            initialOffsetX = { it / 6 },
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { -it / 8 },
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
                        palette = palette,
                        onBack = {
                            if (isFullScreen) onFullScreenChange(false) else onCloseConversation()
                        },
                        onSend = { onSendMessage(displayedConversation.partnerUsername, it) },
                        onSendVideo = { onSendVideo(displayedConversation.partnerUsername, it) },
                        onRetry = { message ->
                            onRetryMessage?.invoke(displayedConversation.partnerUsername, message)
                        },
                        onProfileClick = { onProfileClick(displayedConversation.partnerUsername) },
                        onAudioCall = {
                            onStartCall(displayedConversation, MessageCallKind.AUDIO)
                        },
                        onVideoCall = {
                            onStartCall(displayedConversation, MessageCallKind.VIDEO)
                        },
                        isConnected = isConnected,
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = { onFullScreenChange(!isFullScreen) }
                    )
                }
            }
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
    val remaining = remember(conversations, selectedPartner) {
        conversations.filterNot {
            it.partnerUsername.equals(selectedPartner, ignoreCase = true)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
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
            GlassIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back to chats",
                palette = palette,
                size = 40.dp,
                onClick = onCloseConversation
            )
            Text(
                text = "Chats",
                color = palette.textSecondary,
                fontSize = 9.sp,
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
                items(remaining, key = { "switch_${it.id}" }) { conversation ->
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clickable(role = Role.Button) {
                                onOpenConversation(conversation.partnerUsername)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        RingAvatar(
                            url = conversation.partnerAvatar,
                            name = conversation.partnerName,
                            palette = palette,
                            size = 48.dp,
                            online = conversation.isOnline,
                            emphasizeRing = conversation.unreadCount > 0
                        )
                        if (conversation.unreadCount > 0) {
                            Badge(
                                containerColor = palette.accent,
                                contentColor = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    conversation.unreadCount.coerceAtMost(99).toString(),
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

'''


def patch_messages() -> None:
    text = MESSAGES.read_text()

    for anchor, new_import in [
        ("import androidx.compose.animation.core.animateFloatAsState", "import androidx.compose.animation.core.animateDpAsState"),
        ("import androidx.compose.animation.core.tween", "import androidx.compose.animation.core.Spring"),
        ("import androidx.compose.animation.core.tween", "import androidx.compose.animation.core.spring"),
        ("import androidx.compose.animation.fadeOut", "import androidx.compose.animation.slideInHorizontally"),
        ("import androidx.compose.animation.fadeOut", "import androidx.compose.animation.slideOutHorizontally"),
        ("import androidx.compose.foundation.clickable", "import androidx.compose.foundation.gestures.detectHorizontalDragGestures"),
        ("import androidx.compose.foundation.layout.Box", "import androidx.compose.foundation.layout.BoxWithConstraints"),
        ("import androidx.compose.material.icons.filled.DarkMode", "import androidx.compose.material.icons.filled.Fullscreen"),
        ("import androidx.compose.material.icons.filled.Fullscreen", "import androidx.compose.material.icons.filled.FullscreenExit"),
        ("import androidx.compose.ui.graphics.vector.ImageVector", "import androidx.compose.ui.input.pointer.pointerInput"),
        ("import androidx.compose.ui.platform.LocalContext", "import androidx.compose.ui.platform.LocalDensity"),
    ]:
        text = add_import(text, anchor, new_import)

    text = replace_once(
        text,
        """    activePartner: String?,\n    onOpenConversation: (String) -> Unit,""",
        """    activePartner: String?,\n    isConversationFullScreen: Boolean = false,\n    onConversationFullScreenChange: (Boolean) -> Unit = {},\n    onOpenConversation: (String) -> Unit,""",
        "messages signature",
    )

    text = replace_once(
        text,
        """    BackHandler(enabled = activeCall != null) { activeCall = null }\n    BackHandler(enabled = activeCall == null && activeConversation != null) { onCloseConversation() }""",
        """    BackHandler(enabled = activeCall != null) { activeCall = null }\n    BackHandler(enabled = activeCall == null && activeConversation != null) {\n        if (isConversationFullScreen) onConversationFullScreenChange(false) else onCloseConversation()\n    }""",
        "messages back behavior",
    )

    old_active = """            } else if (activeConversation != null) {\n                PremiumChatDetail(\n                    conversation = activeConversation,\n                    palette = palette,\n                    onBack = onCloseConversation,\n                    onSend = { onSendMessage(activeConversation.partnerUsername, it) },\n                    onSendVideo = { onSendVideo(activeConversation.partnerUsername, it) },\n                    onRetry = { message ->\n                        onRetryMessage?.invoke(activeConversation.partnerUsername, message)\n                    },\n                    onProfileClick = { onProfileClick(activeConversation.partnerUsername) },\n                    onAudioCall = {\n                        activeCall = MessageCallState(activeConversation, MessageCallKind.AUDIO)\n                        launchSecureCall(context, activeConversation, MessageCallKind.AUDIO)\n                    },\n                    onVideoCall = {\n                        activeCall = MessageCallState(activeConversation, MessageCallKind.VIDEO)\n                        launchSecureCall(context, activeConversation, MessageCallKind.VIDEO)\n                    },\n                    isConnected = isConnected\n                )\n            } else {"""
    new_active = """            } else if (activeConversation != null) {\n                PremiumMessagesMasterDetail(\n                    conversations = conversations,\n                    activeConversation = activeConversation,\n                    palette = palette,\n                    isFullScreen = isConversationFullScreen,\n                    onFullScreenChange = onConversationFullScreenChange,\n                    onOpenConversation = onOpenConversation,\n                    onCloseConversation = onCloseConversation,\n                    onSendMessage = onSendMessage,\n                    onSendVideo = onSendVideo,\n                    onRetryMessage = onRetryMessage,\n                    onProfileClick = onProfileClick,\n                    onStartCall = { conversation, kind ->\n                        activeCall = MessageCallState(conversation, kind)\n                        launchSecureCall(context, conversation, kind)\n                    },\n                    isConnected = isConnected\n                )\n            } else {"""
    text = replace_once(text, old_active, new_active, "active chat content")

    if "private fun PremiumMessagesMasterDetail(" not in text:
        marker = "@Composable\nprivate fun PremiumMessagesHome("
        if marker not in text:
            raise RuntimeError("PremiumMessagesHome marker changed")
        text = text.replace(marker, MASTER_DETAIL + marker, 1)

    text = replace_once(
        text,
        """    onAudioCall: () -> Unit,\n    onVideoCall: () -> Unit,\n    isConnected: Boolean\n) {""",
        """    onAudioCall: () -> Unit,\n    onVideoCall: () -> Unit,\n    isConnected: Boolean,\n    isFullScreen: Boolean,\n    onToggleFullScreen: () -> Unit\n) {""",
        "chat detail signature",
    )

    text = replace_once(
        text,
        """                onProfileClick = onProfileClick,\n                onAudioCall = onAudioCall,\n                onVideoCall = onVideoCall\n            )""",
        """                onProfileClick = onProfileClick,\n                onAudioCall = onAudioCall,\n                onVideoCall = onVideoCall,\n                isFullScreen = isFullScreen,\n                onToggleFullScreen = onToggleFullScreen\n            )""",
        "chat header call",
    )

    text = replace_once(
        text,
        """    onProfileClick: () -> Unit,\n    onAudioCall: () -> Unit,\n    onVideoCall: () -> Unit\n) {""",
        """    onProfileClick: () -> Unit,\n    onAudioCall: () -> Unit,\n    onVideoCall: () -> Unit,\n    isFullScreen: Boolean,\n    onToggleFullScreen: () -> Unit\n) {""",
        "chat header signature",
    )

    expand_anchor = """            GlassIconButton(\n                icon = Icons.Default.Call,\n                contentDescription = \"Audio call\","""
    expand_block = """            GlassIconButton(\n                icon = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,\n                contentDescription = if (isFullScreen) \"Restore chat drawer\" else \"Expand chat fullscreen\",\n                palette = palette,\n                size = 36.dp,\n                onClick = onToggleFullScreen\n            )\n            Spacer(Modifier.width(5.dp))\n            GlassIconButton(\n                icon = Icons.Default.Call,\n                contentDescription = \"Audio call\","""
    text = replace_once(text, expand_anchor, expand_block, "chat fullscreen button")

    MESSAGES.write_text(text)


def patch_main() -> None:
    text = MAIN.read_text()
    text = replace_once(
        text,
        """                        activePartner = uiState.activeConversationPartner,\n                        onOpenConversation = { partner ->""",
        """                        activePartner = uiState.activeConversationPartner,\n                        isConversationFullScreen = uiState.isConversationFullScreen,\n                        onConversationFullScreenChange = { viewModel.setConversationFullScreen(it) },\n                        onOpenConversation = { partner ->""",
        "messages state wiring",
    )
    text = replace_once(
        text,
        """                        onProfileClick = { viewModel.openProfile(it) },\n                        onStoryClick = { story -> viewModel.openStory(story) },""",
        """                        onProfileClick = { viewModel.openProfileFromChat(it) },\n                        onStoryClick = { story -> viewModel.openStory(story) },""",
        "message profile overlay wiring",
    )
    text = replace_once(
        text,
        """                !uiState.isEditProfileOpen &&\n                !uiState.isConversationFullScreen &&\n                !uiState.isActivityOpen &&""",
        """                !uiState.isEditProfileOpen &&\n                uiState.activeConversationPartner == null &&\n                !uiState.isActivityOpen &&""",
        "hide bottom bar for chat drawer",
    )
    MAIN.write_text(text)


def patch_viewmodel() -> None:
    text = VIEWMODEL.read_text()

    old_existing = """            _uiState.value = state.copy(\n                conversations = state.conversations.map {\n                    if (it.partnerUsername.equals(clean, true)) it.copy(unreadCount = 0) else it\n                },\n                activeConversationPartner = clean,\n                isConversationFullScreen = true\n            )\n            viewModelScope.launch {"""
    new_existing = """            _uiState.value = state.copy(\n                selectedTab = MainTab.MESSAGES,\n                viewingProfile = null,\n                viewingProduct = null,\n                conversations = state.conversations.map {\n                    if (it.partnerUsername.equals(clean, true)) it.copy(unreadCount = 0) else it\n                },\n                activeConversationPartner = clean,\n                isConversationFullScreen = false\n            )\n            persistUiPreferences()\n            viewModelScope.launch {"""
    text = replace_once(text, old_existing, new_existing, "existing conversation open state")

    launch_anchor = """        viewModelScope.launch {\n            val profile = supabaseService.fetchProfileByUsername(clean)"""
    launch_replacement = """        _uiState.value = state.copy(\n            selectedTab = MainTab.MESSAGES,\n            viewingProfile = null,\n            viewingProduct = null,\n            activeConversationPartner = clean,\n            isConversationFullScreen = false\n        )\n        persistUiPreferences()\n\n        viewModelScope.launch {\n            val profile = supabaseService.fetchProfileByUsername(clean)"""
    text = replace_once(text, launch_anchor, launch_replacement, "new conversation immediate drawer")

    text = replace_once(
        text,
        """            if (profile == null) {\n                showToast(\"User @$clean wasn't found.\")\n                return@launch\n            }""",
        """            if (profile == null) {\n                if (_uiState.value.activeConversationPartner.equals(clean, true)) {\n                    _uiState.value = _uiState.value.copy(\n                        activeConversationPartner = null,\n                        isConversationFullScreen = false\n                    )\n                }\n                showToast(\"User @$clean wasn't found.\")\n                return@launch\n            }""",
        "missing message profile cleanup",
    )

    text = replace_once(
        text,
        """                conversations = listOf(convo) + latest.conversations,\n                activeConversationPartner = profile.username,\n                isConversationFullScreen = true\n            )""",
        """                selectedTab = MainTab.MESSAGES,\n                viewingProfile = null,\n                viewingProduct = null,\n                conversations = listOf(convo) + latest.conversations.filterNot {\n                    it.partnerUsername.equals(profile.username, true)\n                },\n                activeConversationPartner = profile.username,\n                isConversationFullScreen = false\n            )\n            persistUiPreferences()""",
        "new conversation resolved state",
    )

    text = replace_once(
        text,
        """    fun closeConversation() { _uiState.value = _uiState.value.copy(activeConversationPartner = null, isConversationFullScreen = false) }""",
        """    fun setConversationFullScreen(fullScreen: Boolean) {\n        val state = _uiState.value\n        if (state.activeConversationPartner == null && fullScreen) return\n        _uiState.value = state.copy(isConversationFullScreen = fullScreen)\n    }\n\n    fun closeConversation() { _uiState.value = _uiState.value.copy(activeConversationPartner = null, isConversationFullScreen = false) }""",
        "conversation fullscreen setter",
    )

    VIEWMODEL.write_text(text)


patch_messages()
patch_main()
patch_viewmodel()
print("Applied profile-to-chat 80% drawer, quick chat rail, fullscreen expansion and swipe restore.")
