package com.example

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.models.ChatConversation
import com.example.data.models.Story
import com.example.data.models.VerificationBadge
import com.example.ui.components.FeedBottomBar
import com.example.ui.screens.PremiumMessagesScreen
import com.example.ui.theme.BlinkTheme
import com.example.viewmodel.MainTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One focused Android 15 visual/interaction fixture for the approved Message inbox artwork.
 * Fixture people and messages never enter the production APK, Supabase, or real accounts.
 * Compare layout geometry; stock reference photos are not bundled in the app.
 */
@RunWith(AndroidJUnit4::class)
class MessageInboxReferenceVisualAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun captureInboxAndVerifyReferenceControls() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val storiesOpened = mutableIntStateOf(0)
        val chatsOpened = mutableIntStateOf(0)
        val notificationsOpened = mutableIntStateOf(0)
        val gamesOpened = mutableIntStateOf(0)
        val rows = listOf(
            ChatConversation("audit_a", "aisha", partnerName = "Aisha Khan", partnerAvatar = "",
                lastMessage = "Hey! Are you coming to the event tonight? 👀", lastMessageTime = "2m", lastMessageRawTime = "2026-10-21T14:00:00Z",
                unreadCount = 3, isOnline = true, verificationBadge = VerificationBadge.BLUE),
            ChatConversation("audit_b", "noah", partnerName = "Noah Parker", partnerAvatar = "",
                lastMessage = "That was such a fun class today", lastMessageTime = "14m", lastMessageRawTime = "2026-10-21T13:45:00Z",
                unreadCount = 1, isOnline = true),
            ChatConversation("audit_c", "maya", partnerName = "Maya Singh", partnerAvatar = "",
                lastMessage = "Let's grab coffee tomorrow ☕", lastMessageTime = "1h", lastMessageRawTime = "2026-10-21T12:00:00Z", isOnline = true),
            ChatConversation("audit_d", "ethan", partnerName = "Ethan Brooks", partnerAvatar = "",
                lastMessage = "Sent a photo", lastMessageTime = "2h"),
            ChatConversation("audit_e", lastMessageRawTime = "2026-10-21T11:00:00Z", "sophie", partnerName = "Sophie Chen", partnerAvatar = "",
                lastMessage = "Same! See you then 💛", lastMessageTime = "3h"),
            ChatConversation("audit_f", lastMessageRawTime = "2026-10-21T10:00:00Z", "campus-crew", partnerName = "Campus Crew", partnerAvatar = "",
                lastMessage = "Liam: Who's down for the game tomorrow?", lastMessageTime = "4h"),
            ChatConversation("audit_g", lastMessageRawTime = "2026-10-21T09:00:00Z", "james", partnerName = "James Wilson", partnerAvatar = "",
                lastMessage = "Sounds good!", lastMessageTime = "1d")
        )
        val stories = listOf("Aisha" to "aisha", lastMessageRawTime = "2026-10-21T08:00:00Z", "Noah" to "noah", "Maya" to "maya", "Leo" to "leo")
            .mapIndexed { index, (name, user) ->
                Story(id = "audit_story_$index", username = user, avatar = "", displayName = name,
                    storyImage = "audit-local-fixture", hasUnseen = true)
            }
        rule.setContent {
            BlinkTheme(darkTheme = false) {
                val context = LocalContext.current
                val avatar = "android.resource://${context.packageName}/${R.drawable.app_icon}"
                Box(Modifier.fillMaxWidth().height(790.dp)
                    .background(MaterialTheme.colorScheme.surface).testTag("message_inbox_audit_root")) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            PremiumMessagesScreen(
                                conversations = rows,
                                stories = stories,
                                activities = emptyList(),
                                myAvatar = avatar,
                                myName = "BLINK user",
                                activePartner = null,
                                onOpenConversation = { chatsOpened.intValue += 1 },
                                onCloseConversation = {},
                                onSendMessage = { _, _, _ -> },
                                onProfileClick = {},
                                onStoryClick = { storiesOpened.intValue += 1 },
                                onAddStoryClick = {},
                                onOpenActivity = { notificationsOpened.intValue += 1 },
                                onComposeMessage = {},
                                onGameClick = { gamesOpened.intValue += 1 },
                                isDark = false
                            )
                        }
                        FeedBottomBar(
                            currentTab = MainTab.MESSAGES,
                            feedSubTab = 0,
                            onHomeClick = {},
                            onReelsClick = {},
                            onMarketClick = {},
                            onConnectClick = {},
                            onMessageClick = {},
                            isDark = false,
                            hasUnreadMessages = true
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("inbox_my_avatar").assertIsDisplayed()
        rule.onNodeWithTag("inbox_stories").assertIsDisplayed()
        rule.onNodeWithTag("inbox_chat_audit_a").assertIsDisplayed()
        rule.onNodeWithTag("inbox_see_all").assertIsDisplayed()

        // Export exactly one light-theme screen to CI's artifact; no APK install needed.
        val bitmap = rule.onNodeWithTag("message_inbox_audit_root").captureToImage().asAndroidBitmap()
        val name = "inbox-reference-staging-light.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Audit")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output)
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        check(resolver.update(uri, values, null, null) == 1)

        rule.onNodeWithTag("inbox_search").performClick()
        rule.onNodeWithTag("inbox_search_field").assertIsDisplayed()
        rule.onNodeWithTag("inbox_see_all").performClick()
        rule.onNodeWithTag("inbox_game").performClick()
        rule.onNodeWithTag("inbox_notifications").performClick()
        rule.onNodeWithTag("inbox_chat_audit_a").performClick()
        rule.runOnIdle {
            check(gamesOpened.intValue == 1)
            check(notificationsOpened.intValue == 1)
            check(chatsOpened.intValue == 1)
        }
    }
}
