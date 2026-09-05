from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MESSAGES = ROOT / "app/src/main/java/com/example/ui/screens/MessagesScreen.kt"


def add_import(text: str, anchor: str, new_import: str) -> str:
    if new_import in text:
        return text
    if anchor not in text:
        raise RuntimeError(f"Import anchor not found for {new_import}: {anchor}")
    return text.replace(anchor, anchor + "\n" + new_import, 1)


NEW_MESSAGES_SECTION = r'''// ============================================================================
// FULL-SCREEN MESSAGES -> CHAT TRANSITION
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
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
    val selectedChat = activePartner?.takeIf { it.isNotBlank() }
    val paneOpen = selectedChat != null
    val selectedConversation = remember(conversations, selectedChat) {
        conversations.firstOrNull {
            it.partnerUsername.equals(selectedChat, ignoreCase = true)
        }
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("messages_fullscreen_chat")
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val swipeOffset = remember(selectedChat, widthPx) {
            Animatable(if (paneOpen) widthPx else 0f)
        }

        LaunchedEffect(selectedChat, widthPx) {
            if (paneOpen) {
                // A chat always arrives as a real full-screen page. It never first
                // compresses the inbox into an avatar-only rail.
                swipeOffset.snapTo(widthPx)
                swipeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 270,
                        easing = FastOutSlowInEasing
                    )
                )
            } else {
                swipeOffset.snapTo(0f)
            }
        }

        val revealFraction = (swipeOffset.value / widthPx).coerceIn(0f, 1f)
        val inboxBlur = if (paneOpen) ((1f - revealFraction) * 13f).dp else 0.dp

        // Messages stays mounted underneath the conversation. Keeping it alive avoids
        // a flash/reload and gives the right-swipe transition a real destination.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(inboxBlur)
                .graphicsLayer {
                    alpha = if (paneOpen) 0.78f + (0.22f * revealFraction) else 1f
                    scaleX = if (paneOpen) 0.985f + (0.015f * revealFraction) else 1f
                    scaleY = if (paneOpen) 0.985f + (0.015f * revealFraction) else 1f
                }
        ) {
            MessagesInboxContent(
                conversations = conversations,
                activePartner = null,
                onOpenConversation = onOpenConversation,
                onCloseConversation = onCloseConversation,
                onSendMessage = onSendMessage,
                onProfileClick = onProfileClick,
                isDark = isDark,
                isConnected = isConnected,
                isLoading = isLoading
            )
        }

        fun closeChatAnimated() {
            if (!paneOpen) return
            scope.launch {
                swipeOffset.animateTo(
                    targetValue = widthPx,
                    animationSpec = tween(
                        durationMillis = 205,
                        easing = FastOutSlowInEasing
                    )
                )
                onCloseConversation()
            }
        }

        BackHandler(enabled = paneOpen) {
            closeChatAnimated()
        }

        if (paneOpen) {
            // This scrim is deliberately above the inbox but below the chat. As the
            // user drags the chat right, the exposed Messages area remains blurred and
            // tapping that revealed area exits directly to Messages.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(
                            alpha = 0.20f * (1f - revealFraction)
                        )
                    )
                    .clickable(onClick = { closeChatAnimated() })
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = swipeOffset.value
                    }
                    .clip(
                        RoundedCornerShape(
                            topStart = (18f * revealFraction).dp,
                            bottomStart = (18f * revealFraction).dp
                        )
                    )
                    .pointerInput(selectedChat, widthPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                val next = (swipeOffset.value + dragAmount)
                                    .coerceIn(0f, widthPx)
                                if (dragAmount > 0f || swipeOffset.value > 0f) {
                                    change.consume()
                                    scope.launch {
                                        swipeOffset.snapTo(next)
                                    }
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (swipeOffset.value >= widthPx * 0.20f) {
                                        swipeOffset.animateTo(
                                            targetValue = widthPx,
                                            animationSpec = tween(
                                                durationMillis = 185,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                        onCloseConversation()
                                    } else {
                                        swipeOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    swipeOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        )
                    }
            ) {
                val convo = selectedConversation
                if (convo != null) {
                    androidx.compose.runtime.key(convo.id) {
                        ChatConversationView(
                            convo = convo,
                            onBack = { closeChatAnimated() },
                            onSendMessage = { text ->
                                onSendMessage(convo.partnerUsername, text)
                            },
                            onSendVideo = { uri ->
                                onSendVideo(convo.partnerUsername, uri)
                            },
                            onProfileClick = onProfileClick,
                            isDark = isDark,
                            isConnected = isConnected,
                            onRetryMessage = onRetryMessage?.let { retry ->
                                { message -> retry(convo.partnerUsername, message) }
                            },
                            hasMoreMessages = hasMoreMessages(convo.id),
                            isLoadingOlder = isLoadingOlder(convo.id),
                            onLoadOlder = { onLoadOlder(convo.partnerUsername) },
                            isLoadingMessages = isLoadingMessages(convo.id)
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
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

    text = add_import(
        text,
        "import androidx.compose.animation.core.animateFloat",
        "import androidx.compose.animation.core.Animatable",
    )
    text = add_import(
        text,
        "import androidx.compose.foundation.gestures.detectTapGestures",
        "import androidx.compose.foundation.gestures.detectHorizontalDragGestures",
    )
    text = add_import(
        text,
        "import androidx.compose.ui.draw.clipToBounds",
        "import androidx.compose.ui.draw.blur",
    )
    text = add_import(
        text,
        "import androidx.compose.ui.platform.LocalContext",
        "import androidx.compose.ui.platform.LocalDensity",
    )

    section_marker = "// ============================================================================\n// RESPONSIVE MASTER-DETAIL MESSAGES\n// ============================================================================"
    rail_marker = "@Composable\nprivate fun ConversationAvatarRail("
    start = text.find(section_marker)
    if start < 0:
        # Idempotence: an already-applied file uses the new marker.
        current_marker = "// ============================================================================\n// FULL-SCREEN MESSAGES -> CHAT TRANSITION\n// ============================================================================"
        start = text.find(current_marker)
    rail = text.find(rail_marker, start if start >= 0 else 0)
    if start < 0 or rail < 0:
        raise RuntimeError("Messages wrapper markers changed; refusing an unsafe patch")

    text = text[:start] + NEW_MESSAGES_SECTION + text[rail:]

    nav_pattern = re.compile(
        r"\n\s*navigationIcon\s*=\s*\{\s*"
        r"IconButton\(\s*onClick\s*=\s*onBack\s*\)\s*\{\s*"
        r"Icon\(\s*Icons\.Default\.ArrowBack\s*,\s*"
        r"contentDescription\s*=\s*\"Back\"\s*\)\s*"
        r"\}\s*\}\s*,",
        re.MULTILINE,
    )
    text, removed = nav_pattern.subn("\n        navigationIcon = {},", text, count=1)
    if removed == 0 and "Icons.Default.ArrowBack" in text:
        raise RuntimeError("Chat back-arrow block changed; refusing to leave the old arrow behind")

    MESSAGES.write_text(text)


if __name__ == "__main__":
    patch_messages()
    print("Applied full-screen Messages -> Chat navigation and removed the chat back arrow.")
