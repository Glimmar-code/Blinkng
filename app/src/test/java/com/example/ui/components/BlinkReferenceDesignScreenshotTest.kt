package com.example.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.data.models.PostDraft
import com.example.ui.screens.ChatConversationView
import com.example.ui.theme.BlinkTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose rendering under Robolectric; this is not Android device evidence. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkReferenceDesignScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun landingRendersAndEachChoiceUsesItsRealCallback() {
        val chosen = mutableListOf<String>()
        rule.setContent { BlinkTheme(darkTheme = true) {
            BlinkCreatePostLanding(emptyList(), {}, { chosen += "text" }, { chosen += "photo" },
                { chosen += "video" }, { chosen += "poll" }, {}, {}, {})
        } }
        rule.onRoot().captureRoboImage("build/outputs/reference-design/create-post-landing-robolectric.png")
        listOf("Text", "Photo", "Video", "Poll").forEach { rule.onNodeWithText(it).performClick() }
        rule.runOnIdle { assertEquals(listOf("text", "photo", "video", "poll"), chosen) }
    }

    @Test fun savedDraftResumesByIdentity() {
        var selected = ""
        rule.setContent { BlinkTheme(darkTheme = true) {
            BlinkCreatePostLanding(listOf(PostDraft(id = "local-fixture", text = "My campus draft")),
                {}, {}, {}, {}, {}, {}, {}, { selected = it.id })
        } }
        // The reference shows a compact Drafts row; saved drafts become
        // selectable only after that row is expanded.
        rule.onNodeWithTag("blink_create_saved_drafts").assertDoesNotExist()
        rule.onNodeWithText("Drafts").performClick()
        rule.onNodeWithTag("blink_create_saved_drafts").assertExists()
        rule.onNodeWithTag("blink_create_saved_draft_local-fixture").performClick()
        rule.runOnIdle { assertEquals("local-fixture", selected) }
    }

    @Test fun charcoalChatRendersPinnedHeaderAndComposer() {
        val chat = ChatConversation(id = "isolated-reference", partnerUsername = "fixture", partnerName = "Reference Contact", partnerAvatar = "", isOnline = true,
            messages = mutableListOf(
                ChatMessage(id = "pin", text = "See the project files below", timestamp = "9:12 AM", isPinned = true, isFromMe = false),
                ChatMessage(id = "reply", text = "Yes, I’m available.", timestamp = "9:13 AM", status = MessageStatus.READ, isFromMe = true)))
        rule.setContent { BlinkTheme(darkTheme = true) { Box(Modifier.fillMaxSize()) {
            ChatConversationView(convo = chat, onBack = {}, onSendMessage = {}, onProfileClick = {}, isDark = true)
        } } }
        rule.onNodeWithTag("chat_reference_header").assertExists()
        rule.onNodeWithTag("chat_reference_pinned_banner").assertExists()
        rule.onNodeWithTag("chat_reference_attachment_plus").assertExists()
        rule.onRoot().captureRoboImage("build/outputs/reference-design/chat-charcoal-robolectric.png")
    }
}
