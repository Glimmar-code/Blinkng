#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("app/src/main/java/com/example/ui/screens/MessagesScreen.kt")
text = path.read_text(encoding="utf-8")

# Gesture helpers used by the full-screen right-swipe observer.
if "import androidx.compose.foundation.gestures.awaitEachGesture" not in text:
    text = text.replace(
        "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n",
        "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n"
        "import androidx.compose.foundation.gestures.awaitEachGesture\n"
        "import androidx.compose.foundation.gestures.awaitFirstDown\n",
        1,
    )
if "import kotlin.math.abs" not in text:
    text = text.replace(
        "import kotlinx.coroutines.launch\n",
        "import kotlinx.coroutines.launch\nimport kotlin.math.abs\n",
        1,
    )

new_messages_screen = r'''@OptIn(ExperimentalMaterial3Api::class)
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
    val selectedConversation = remember(conversations, selectedChat) {
        conversations.firstOrNull {
            it.partnerUsername.equals(selectedChat, ignoreCase = true)
        }
    }

    if (selectedChat == null) {
        // Inbox and chat are mutually exclusive full-screen destinations. The inbox is
        // never mounted underneath an open chat, so a back/right swipe cannot expose a
        // 70/30 split or partially reveal the Messages screen.
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
    } else {
        val density = LocalDensity.current
        val exitSwipeThresholdPx = with(density) { 72.dp.toPx() }

        BackHandler(enabled = true) {
            onCloseConversation()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("messages_fullscreen_chat")
                .pointerInput(selectedChat, exitSwipeThresholdPx) {
                    // Observe the whole chat without consuming leftward movement. Message
                    // bubbles therefore own swipe-left-to-reply, while a deliberate right
                    // swipe exits straight to Messages with no interactive pane reveal.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var navigated = false

                        while (!navigated) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (
                                dx >= exitSwipeThresholdPx &&
                                abs(dx) > abs(dy) * 1.15f
                            ) {
                                navigated = true
                                onCloseConversation()
                            }
                        }
                    }
                }
        ) {
            val convo = selectedConversation
            if (convo != null) {
                androidx.compose.runtime.key(convo.id) {
                    ChatConversationView(
                        convo = convo,
                        onBack = onCloseConversation,
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
}'''

screen_pattern = re.compile(
    r"@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun MessagesScreen\(.*?\n\}\n\n@Composable\nprivate fun ConversationAvatarRail\(",
    re.S,
)
match = screen_pattern.search(text)
if not match:
    raise SystemExit("MessagesScreen block not found")
text = text[: match.start()] + new_messages_screen + "\n\n@Composable\nprivate fun ConversationAvatarRail(" + text[match.end():]

# Add swipe-left state and thresholds to every message bubble.
row_anchor = '''    var pressed by rememberSaveable(
        message.id
    ) {
        mutableStateOf(false)
    }
'''
row_insert = row_anchor + '''
    // Both outgoing and incoming bubbles use the same left-swipe reply gesture.
    val replySwipeMaxPx = with(LocalDensity.current) { 72.dp.toPx() }
    val replySwipeTriggerPx = with(LocalDensity.current) { 44.dp.toPx() }
    var replySwipeOffset by remember(message.id) {
        mutableStateOf(0f)
    }
'''
if row_anchor not in text:
    raise SystemExit("MessageRow state anchor not found")
text = text.replace(row_anchor, row_insert, 1)

modifier_anchor = '''                Modifier
                    .scale(scale)
                    .animateContentSize(
                        animationSpec =
                            tween(
                                200,
                                easing = FastOutSlowInEasing
                            )
                    )
                    .pointerInput(
                        message.id
                    ) {

                        detectTapGestures(
                            onLongPress = {

                                pressed = true
                                onMore()
                            }
                        )
                    },'''
modifier_replacement = '''                Modifier
                    .scale(scale)
                    .graphicsLayer {
                        translationX = replySwipeOffset
                    }
                    .animateContentSize(
                        animationSpec =
                            tween(
                                200,
                                easing = FastOutSlowInEasing
                            )
                    )
                    .pointerInput(
                        message.id,
                        replySwipeMaxPx,
                        replySwipeTriggerPx
                    ) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                // Reply is intentionally left-only. A right swipe is left
                                // available to the full-screen chat navigation observer.
                                if (dragAmount < 0f || replySwipeOffset < 0f) {
                                    replySwipeOffset =
                                        (replySwipeOffset + dragAmount)
                                            .coerceIn(-replySwipeMaxPx, 0f)
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                val shouldReply =
                                    replySwipeOffset <= -replySwipeTriggerPx
                                replySwipeOffset = 0f
                                if (shouldReply) {
                                    onReply()
                                }
                            },
                            onDragCancel = {
                                replySwipeOffset = 0f
                            }
                        )
                    }
                    .pointerInput(
                        message.id
                    ) {

                        detectTapGestures(
                            onLongPress = {

                                pressed = true
                                onMore()
                            }
                        )
                    },'''
if modifier_anchor not in text:
    raise SystemExit("MessageRow modifier anchor not found")
text = text.replace(modifier_anchor, modifier_replacement, 1)

# Reply is gesture-first now; remove the old length-dependent reply shortcut button.
reply_button = re.compile(
    r'''\n\s*if \(message\.text\.length > 80\) \{\n\s*SmallMessageAction\(\n\s*icon =\n\s*Icons\.Outlined\.Reply,\n\s*onClick = onReply\n\s*\)\n\s*\}\n''',
    re.S,
)
text, count = reply_button.subn("\n", text, count=1)
if count != 1:
    raise SystemExit("Legacy reply action block not found")

path.write_text(text, encoding="utf-8")

ledger = Path("platform-parity/changes.md")
ledger_text = ledger.read_text(encoding="utf-8")
marker = "PARITY-EXCEPTION: android-touch-chat-swipe-navigation"
if marker not in ledger_text:
    ledger_text += f'''\n\n{marker}\nDate: 2026-09-08\nFeature: Touch gestures for direct chat navigation and reply\nAndroid behavior: Swipe left on any incoming or outgoing message bubble to reply. Swipe right across the open chat to return directly to Messages. The former interactive 70/30 inbox reveal is removed.\nWhy this is genuinely Android-only: This change is specifically a touchscreen gesture adapter implemented with Jetpack Compose pointer input.\nWindows equivalent or reason no equivalent is needed: Windows retains direct full-screen chat/inbox navigation through desktop pointer/keyboard controls; no touch-drag pane is required. Message reply semantics remain the same shared product behavior.\nBackend/shared behavior preserved: Message storage, reply state semantics, conversation identity, delivery/read receipts, notification routing, and backend APIs are unchanged.\nTests/validation: Android compile/unit/lint/APK quality gate plus Windows parity/build gate must pass before merge.\nOwner/reviewer note: Only the Android touch interaction is excepted; any change to message/reply business semantics still requires Windows parity.\n'''
    ledger.write_text(ledger_text, encoding="utf-8")

print("Applied direct chat swipe navigation and swipe-left reply fix")
