from pathlib import Path

PATH = Path("app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt")
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


# The live Messages tab renders PremiumMessagesScreen, so put the navigation gesture
# on that screen rather than on the legacy MessagesScreen implementation.
replace_once(
    "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n",
    "import androidx.compose.foundation.gestures.awaitEachGesture\n"
    "import androidx.compose.foundation.gestures.awaitFirstDown\n"
    "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n",
    "gesture imports",
)
replace_once(
    "import androidx.compose.ui.input.pointer.pointerInput\n",
    "import androidx.compose.ui.input.pointer.PointerEventPass\n"
    "import androidx.compose.ui.input.pointer.pointerInput\n"
    "import androidx.compose.ui.input.pointer.positionChange\n",
    "pointer imports",
)
replace_once(
    "import java.util.Locale\n",
    "import java.util.Locale\nimport kotlin.math.abs\n",
    "math import",
)

start = text.index("@Composable\nprivate fun PremiumMessagesMasterDetail(")
end = text.index("\n@Composable\nprivate fun ConversationSwitchRail(", start)

new_master_detail = r'''private fun Modifier.observeRightSwipeToClose(
    thresholdPx: Float,
    onSwipeRight: () -> Unit
): Modifier = pointerInput(thresholdPx, onSwipeRight) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var horizontal = 0f
        var vertical = 0f
        var fired = false

        while (true) {
            // Observe in the Initial pass so message-bubble gestures can still consume
            // their own left swipe later without blocking the screen-level right swipe.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.positionChange()
            horizontal += delta.x
            vertical += delta.y

            if (
                !fired &&
                horizontal >= thresholdPx &&
                horizontal > abs(vertical) * 1.25f
            ) {
                fired = true
                onSwipeRight()
                break
            }

            if (!change.pressed) break
        }
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
    onSendVideo: (String, Uri) -> Unit,
    onRetryMessage: ((String, ChatMessage) -> Unit)?,
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
    val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Chat" }
            .observeRightSwipeToClose(
                thresholdPx = swipeThresholdPx,
                onSwipeRight = onCloseConversation
            )
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
                    val forwarded = message.text.takeIf { it.isNotBlank() }
                        ?: message.attachedVideoUrl
                        ?: message.attachedImageUrl
                        ?: "Forwarded message"
                    onSendMessage(target, forwarded, null)
                },
                interactionActions = interactionActions,
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
                isFullScreen = true,
                onToggleFullScreen = {}
            )
        }
    }
}
'''
text = text[:start] + new_master_detail + text[end:]

# The visible Premium message bubbles previously replied on a right swipe. Flip that
# gesture so left = reply on every sent/received bubble, leaving right = close chat.
replace_once(
    """        if (displayOffset > 10f) {\n            Icon(\n                Icons.Default.Reply,\n                contentDescription = null,\n                tint = palette.accent.copy(alpha = (displayOffset / threshold).coerceIn(.25f, 1f)),\n                modifier = Modifier.align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 6.dp).size(20.dp)\n            )\n        }\n""",
    """        if (displayOffset < -10f) {\n            Icon(\n                Icons.Default.Reply,\n                contentDescription = null,\n                tint = palette.accent.copy(alpha = ((-displayOffset) / threshold).coerceIn(.25f, 1f)),\n                modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 6.dp).size(20.dp)\n            )\n        }\n""",
    "reply indicator direction",
)
replace_once(
    "dragOffset = (dragOffset + amount).coerceIn(0f, threshold * 1.35f)",
    "dragOffset = (dragOffset + amount).coerceIn(-threshold * 1.35f, 0f)",
    "reply drag direction",
)
replace_once(
    "if (dragOffset >= threshold) onReply()",
    "if (dragOffset <= -threshold) onReply()",
    "reply threshold direction",
)

# Chats are now intentionally full-screen; remove the old drawer/fullscreen toggle from
# the live header so there is no dead control or partial inbox reveal.
replace_once(
    """            GlassIconButton(\n                icon = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,\n                contentDescription = if (isFullScreen) \"Restore chat drawer\" else \"Expand chat fullscreen\",\n                palette = palette,\n                size = 34.dp,\n                onClick = onToggleFullScreen\n            )\n            Spacer(Modifier.width(4.dp))\n""",
    "",
    "fullscreen drawer control",
)

# Guardrails: fail the workflow if any required behavior was not produced.
required = [
    "observeRightSwipeToClose",
    "PointerEventPass.Initial",
    "horizontal >= thresholdPx",
    "dragOffset <= -threshold",
    "coerceIn(-threshold * 1.35f, 0f)",
    "isFullScreen = true",
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"required marker missing after patch: {marker}")

for forbidden in [
    "dragOffset >= threshold) onReply()",
    "Restore chat drawer",
    "contentDescription = \"Chat drawer\"",
]:
    if forbidden in text:
        raise SystemExit(f"old live-chat behavior still present: {forbidden}")

PATH.write_text(text, encoding="utf-8")
print("Patched live PremiumMessagesScreen gestures successfully.")
