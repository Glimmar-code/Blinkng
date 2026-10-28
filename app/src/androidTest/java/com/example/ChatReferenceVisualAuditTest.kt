package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.ui.screens.ChatConversationView
import com.example.ui.theme.BlinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated design/interaction fixture. No real Supabase messages or mock people
 * are bundled into production. CI can capture the exact Compose rendering.
 */
@RunWith(AndroidJUnit4::class)
class ChatReferenceVisualAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun blueChatShowsPinnedBannerAndRealAttachmentActions() {
        val conversation = ChatConversation(
            id = "reference-chat-audit",
            partnerUsername = "chat_reference_fixture",
            partnerName = "Reference Contact",
            partnerAvatar = "",
            isOnline = true,
            messages = mutableListOf(
                ChatMessage(
                    id = "reference-pin", text = "Pinned: See project files below",
                    timestamp = "10:24", rawTimestamp = "2026-10-28T10:24:00Z",
                    isPinned = true, isFromMe = false
                ),
                ChatMessage(
                    id = "reference-outgoing", text = "I have sent the photos.",
                    timestamp = "10:26", rawTimestamp = "2026-10-28T10:26:00Z",
                    status = MessageStatus.READ, isFromMe = true
                )
            )
        )
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth().height(790.dp)) {
                    ChatConversationView(
                        convo = conversation,
                        onBack = {},
                        onSendMessage = {},
                        onProfileClick = {},
                        isDark = true
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("chat_conversation_root").assertExists()
        rule.onNodeWithTag("chat_reference_pinned_banner").assertExists()
        rule.onNodeWithTag("chat_reference_attachment_plus").assertExists()

        // Capture the actual rendered dark chat for CI's side-by-side review.
        val rendered = rule.onNodeWithTag("chat_conversation_root").captureToImage()
        check(rendered.width > 100 && rendered.height > 200)

        rule.onNodeWithTag("chat_reference_attachment_plus").performClick()
        rule.onNodeWithText("Share with this chat").assertExists()
        rule.onNodeWithText("Gallery").assertExists()
        rule.onNodeWithText("Video").assertExists()
        rule.onNodeWithText("Poll").assertExists()
        rule.onNodeWithText("Voice note").assertExists()
    }
}
