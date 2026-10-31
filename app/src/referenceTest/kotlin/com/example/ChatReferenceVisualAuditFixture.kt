package com.example

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.ui.test.performScrollToIndex
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.ui.screens.ChatConversationView
import com.example.ui.screens.MessageComposer
import com.example.ui.screens.BlinkChatPollResult
import com.example.ui.screens.BlinkChatPollResultOption
import com.example.ui.screens.BlinkChatPollResultsCard
import com.example.ui.screens.BlinkChatPollRecoveryControls
import com.example.ui.theme.MessageThemeMode
import com.example.ui.theme.messagePalette
import com.example.ui.theme.referenceChatPalette
import com.example.ui.theme.BlinkTheme
import org.junit.Rule
import org.junit.Test

/**
 * Isolated design/interaction fixture. No real Supabase messages or mock people
 * are bundled into production. CI can capture the exact Compose rendering.
 */
abstract class ChatReferenceVisualAuditFixture {
    protected abstract fun capture(name: String)
    @get:Rule val rule = createComposeRule()

    @Test fun chatReferenceComposerHasAccessibleMicrophoneAndOutlinedCamera() {
        var voiceTyping = 0
        var voiceRecording = 0
        var cameraOpens = 0
        var messagesSent = 0
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth().height(790.dp)
                    .testTag("chat_conversation_root")) {
                    MessageComposer(
                        value = "Sample message",
                        onValueChange = {},
                        palette = referenceChatPalette(messagePalette(MessageThemeMode.DARK)),
                        onAttachment = {},
                        onCamera = { cameraOpens++ },
                        onDictation = { voiceTyping++ },
                        onEmoji = {},
                        onSubmit = { messagesSent++ },
                        onQuickLike = {},
                        isRecording = false,
                        recordingSeconds = 0,
                        onVoiceStart = { voiceRecording++ },
                        onVoiceStop = {},
                        onVoiceCancel = {}
                    )
                }
            }
        }
        rule.onNodeWithTag("chat_reference_message_input").assertExists()
        rule.onNodeWithTag("chat_reference_camera").assertExists()
        rule.onNodeWithTag("chat_reference_microphone").assertExists()
        rule.onNodeWithTag("chat_reference_send").assertExists()
        rule.onNodeWithTag("chat_reference_send_plane",
            useUnmergedTree = true).assertExists()
        capture("chat-reference-composer-controls.png")
        rule.onNodeWithTag("chat_reference_camera").performClick()
        rule.onNodeWithTag("chat_reference_microphone").performClick()
        rule.onNodeWithTag("chat_reference_microphone")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithTag("chat_reference_send").performClick()
        rule.runOnIdle {
            check(cameraOpens == 1)
            check(voiceTyping == 1)
            check(voiceRecording == 1)
            check(messagesSent == 1)
        }
    }

    @Test fun outgoingDocumentIsVisibleInDarkAndLightThemes() {
        var dark by mutableStateOf(true)
        val conversation = ChatConversation(id = "reference-file-theme", partnerName = "Reference Contact",
            partnerUsername = "reference_contact", partnerAvatar = "",
            messages = mutableListOf(ChatMessage(id = "reference-outgoing-file", text = "Document",
                timestamp = "09:30", isFromMe = true, status = MessageStatus.READ,
                attachedDocumentUrl = "https://example.invalid/fixture-report.pdf",
                attachmentName = "Report.pdf", attachmentMimeType = "application/pdf", messageType = "document")))
        val preferences = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("blink_message_preferences", android.content.Context.MODE_PRIVATE)
        val originalTheme = preferences.getString("message_theme", null)
        try {
        check(preferences.edit().putString("message_theme", "dark").commit())
        rule.setContent {
            androidx.compose.runtime.key(dark) {
            BlinkTheme(darkTheme = dark) {
                Box(Modifier.fillMaxWidth().height(790.dp)) {
                    ChatConversationView(convo = conversation, onBack = {}, onSendMessage = {},
                        onProfileClick = {}, isDark = dark)
                }
            }
        }
        }
        rule.onNodeWithText("Report.pdf").assertExists()
        rule.onNodeWithTag("chat_reference_document_download_circle").assertExists()
        capture("chat-reference-outgoing-document-dark.png")
        rule.runOnIdle {
            check(preferences.edit().putString("message_theme", "light").commit())
            dark = false
        }
        rule.onNodeWithText("Report.pdf").assertExists()
        rule.onNodeWithTag("chat_reference_document_download_circle").assertExists()
        capture("chat-reference-outgoing-document-light.png")
        } finally {
            val editor = preferences.edit()
            if (originalTheme == null) editor.remove("message_theme") else editor.putString("message_theme", originalTheme)
            check(editor.commit())
        }
    }

    @Test fun nativePollFailureHasRealRetryAndNoInventedResultsFixture() {
        var retries = 0
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth().height(790.dp)
                    .testTag("chat_conversation_root")) {
                    BlinkChatPollRecoveryControls(
                        loading = false,
                        message = "Couldn't load poll results. Check your connection.",
                        allowRetry = true,
                        palette = referenceChatPalette(messagePalette(MessageThemeMode.DARK)),
                        onRetry = { retries++ }
                    )
                }
            }
        }
        rule.onNodeWithTag("chat_poll_recovery").assertExists()
        rule.onNodeWithText("Try again").assertExists()
        rule.onNodeWithTag("chat_reference_native_poll").assertDoesNotExist()
        capture("chat-native-poll-retry-fixture.png")
        rule.onNodeWithTag("chat_poll_retry").performClick()
        rule.runOnIdle { check(retries == 1) }
    }

    @Test fun nativePollLoadingCannotTriggerDuplicateRetriesFixture() {
        var retries = 0
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                BlinkChatPollRecoveryControls(
                    loading = true,
                    message = null,
                    allowRetry = true,
                    palette = referenceChatPalette(messagePalette(MessageThemeMode.DARK)),
                    onRetry = { retries++ }
                )
            }
        }
        rule.onNodeWithText("Loading poll results…").assertExists()
        rule.onNodeWithTag("chat_poll_retry").assertDoesNotExist()
        rule.runOnIdle { check(retries == 0) }
    }

    @Test fun nativeResultsShowServerStylePercentagesAndClickableChoicesFixture() {
        // Synthetic numbers are a screenshot-test fixture ONLY, never live chat
        // results. The production card requires an authenticated, validated RPC.
        val sample = BlinkChatPollResult(
            question = "Which design do you prefer?",
            options = listOf(
                BlinkChatPollResultOption("Design 1", 7),
                BlinkChatPollResultOption("Design 2", 15),
                BlinkChatPollResultOption("Design 3", 3)
            ),
            totalVotes = 25,
            myVote = 1,
            closed = false
        )
        var selected = -1
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth().height(790.dp)
                    .testTag("chat_conversation_root")) {
                    BlinkChatPollResultsCard(
                        result = sample,
                        palette = referenceChatPalette(messagePalette(MessageThemeMode.DARK)),
                        textColor = Color.White,
                        sending = false,
                        error = null,
                        onChoice = { selected = it }
                    )
                }
            }
        }
        rule.onNodeWithTag("chat_reference_native_poll").assertExists()
        // Native poll choice rows are clickable and merge descendant semantics.
        // Inspect their unmerged children to verify the actual percentage and
        // progress widgets without weakening the user-action assertion below.
        rule.onNodeWithTag("chat_reference_native_poll_percent_0",
            useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("chat_reference_native_poll_bar_1",
            useUnmergedTree = true).assertExists()
        capture("chat-native-poll-synthetic-fixture.png")
        rule.onNodeWithTag("chat_reference_native_poll_choice_2").performClick()
        rule.runOnIdle { check(selected == 2) }
    }

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
        rule.onNodeWithTag("chat_reference_header").assertExists()
        rule.onNodeWithTag("chat_reference_pinned_banner").assertExists()
        rule.onNodeWithTag("chat_reference_pinned_jump").assertExists()
        rule.onNodeWithTag("chat_reference_attachment_plus").assertExists()
        rule.onNodeWithTag("chat_reference_emoji").assertExists()
        rule.onNodeWithTag("chat_reference_send").assertExists()
        // The reference has an outward-pointing pale tail on outgoing text,
        // while attachment and album containers retain their own shapes.
        rule.onNodeWithTag("chat_reference_text_tail_outgoing").assertExists()

        capture("chat-reference-staging-dark.png")

        // Keep the real pinned-message jump action, then verify dismiss is local
        // to the banner rather than deleting or unpinning the message.
        rule.onNodeWithTag("chat_reference_pinned_jump").performClick()
        rule.onNodeWithTag("chat_reference_pinned_banner").assertExists()
        rule.onNodeWithContentDescription("Hide pinned banner").performClick()
        rule.onNodeWithTag("chat_reference_pinned_banner").assertDoesNotExist()
        rule.onNodeWithTag("chat_reference_attachment_plus").performClick()
        rule.onNodeWithText("Share with this chat").assertExists()
        rule.onNodeWithText("Gallery").assertExists()
        rule.onNodeWithText("Video").assertExists()
        rule.onNodeWithText("Poll").assertExists()
        rule.onNodeWithText("Voice note").assertExists()
    }
    /**
     * Full-thread reference fixture. Uses the actual ChatConversationView with
     * synthetic locally generated media, never user photographs or production
     * messages. Three scroll positions make photo/video/voice/document/reply
     * styling visible in a stable emulator artifact. This is *not* an exact
     * pixel-match certificate for the owner's reference art.
     */
    @Test fun captureEntireReferenceConversationAtThreeScrollPositions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photoUris = (0 until 3).map { index ->
            val file = File(context.cacheDir, "chat-reference-fixture-$index.png")
            val bitmap = Bitmap.createBitmap(300, 230, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(
                when (index) {
                    0 -> AndroidColor.rgb(58, 93, 122)
                    1 -> AndroidColor.rgb(54, 80, 106)
                    else -> AndroidColor.rgb(102, 123, 104)
                }
            )
            paint.color = AndroidColor.rgb(226, 224, 207)
            canvas.drawRoundRect(28f, 40f, 272f, 194f, 12f, 12f, paint)
            paint.color = AndroidColor.rgb(71, 109, 137)
            for (column in 0..3) {
                for (row in 0..1) {
                    canvas.drawRect(
                        48f + 52f * column,
                        68f + 55f * row,
                        73f + 52f * column,
                        94f + 55f * row,
                        paint
                    )
                }
            }
            FileOutputStream(file).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
            file.toURI().toString()
        }
        // Use an actual playable MP4 for video-album verification. The fixture
        // asset is decoded by Coil's video-frame fetcher and MediaMetadataRetriever;
        // photos remain separate PNG assets and are never passed as videos.
        val videoFile = File(context.cacheDir, "chat-reference-fixture-video.mp4")
        // referenceTest is a separate APK from androidTest. Keep its fixture
        // self-contained instead of reading an asset from the other test APK.
        val encodedVideo = javaClass.classLoader
            ?.getResourceAsStream("blink-visual-synthetic.mp4.b64")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: InstrumentationRegistry.getInstrumentation().context.assets
                .open("blink-visual-synthetic.mp4.b64").bufferedReader().use { it.readText() }
        val videoBytes = android.util.Base64.decode(encodedVideo, android.util.Base64.DEFAULT)
        videoFile.writeBytes(videoBytes)
        val videoUri = videoFile.toURI().toString()
        val base = "2026-10-29T"
        fun msg(
            id: String, text: String, time: String, fromMe: Boolean = false
        ) = ChatMessage(
            id = id, text = text, timestamp = time,
            rawTimestamp = base + time + ":00Z", isFromMe = fromMe,
            status = if (fromMe) MessageStatus.READ else MessageStatus.SENT
        )
        val reference = mutableListOf(
            msg("reference-pinned", "See the project files below", "09:11")
                .copy(isPinned = true),
            msg("reference-intro", "Hey! Are you free for the meeting today?", "09:12"),
            msg("reference-yes", "Yes, I'm available. What time works for you?", "09:13", true),
            msg("reference-gallery-label", "Great! Let me send you some photos from the event.", "09:15")
        )
        photoUris.forEachIndexed { index, uri ->
            reference += msg("reference-photo-$index", "Image", "09:15").copy(
                attachedImageUrl = uri,
                imageAlbumId = "reference-photo-group",
                imageAlbumPosition = index,
                imageAlbumSize = 3,
                messageType = "image"
            )
        }
        reference += msg("reference-reaction", "These look amazing! 🔥", "09:16", true)
        reference += msg("reference-voice", "Voice message", "09:17").copy(
            attachedAudioUrl = "https://example.invalid/fixture-voice.m4a",
            isVoiceNote = true, voiceDuration = "0:24", messageType = "audio"
        )
        reference += msg("reference-videos-label", "Check these videos from the event.", "09:20", true)
        // Actual MP4 source exercises video-frame extraction and duration parsing.
        // No still-image fixture is masqueraded as a video attachment.
        for (index in 0..1) {
            reference += msg("reference-video-$index", "Video", "09:20", true).copy(
                attachedVideoUrl = videoUri,
                imageAlbumId = "reference-video-group",
                imageAlbumSize = 2,
                imageAlbumPosition = index,
                messageType = "video"
            )
        }
        reference += msg("reference-document", "Document", "09:22").copy(
            attachedDocumentUrl = "https://example.invalid/fixture-project.pdf",
            attachmentName = "Project_Proposal.pdf",
            attachmentMimeType = "application/pdf",
            messageType = "document"
        )
        val pollText = com.blinkng.shared.BlinkChatPollPolicy.textFallback(
            com.blinkng.shared.BlinkChatPollDraft(
                "Which design do you prefer?",
                listOf("Design 1", "Design 2", "Design 3")
            )
        )
        reference += msg("reference-poll", pollText, "09:25").copy(messageType = "poll")
        reference += msg("reference-reply", "Design 2 looks best! 👍", "09:26", true)
            .copy(replyToMessageId = "reference-poll", reactionCounts = mapOf("❤️" to 1))

        val conversation = ChatConversation(
            id = "reference-full-thread-audit",
            partnerUsername = "reference_amaraf",
            partnerName = "Amara",
            partnerAvatar = "",
            isOnline = true,
            messages = reference
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
        rule.onNodeWithTag("chat_reference_header").assertExists()
        rule.onNodeWithTag("chat_reference_pinned_banner").assertExists()
        val list = rule.onNodeWithTag("chat_reference_history_list")
        list.assertExists()
        fun saveFrame(filename: String) = capture(filename)
        // Lazy list collapses album rows; these indexes select three distinct
        // real Compose slices instead of pretending all media fits in one view.
        list.performScrollToIndex(1)
        // Several messages can carry tails; inspect a visible first match.
        rule.onAllNodesWithTag("chat_reference_text_tail_incoming").onFirst().assertExists()
        rule.onAllNodesWithTag("chat_reference_text_tail_outgoing").onFirst().assertExists()
        saveFrame("chat-reference-full-top.png")
        list.performScrollToIndex(4)
        rule.onNodeWithTag("chat_reference_album_photo_0").assertExists()
        saveFrame("chat-reference-full-photos.png")
        list.performScrollToIndex(6)
        rule.onNodeWithTag("chat_reference_voice_waveform").assertExists()
        rule.onNodeWithTag("chat_reference_voice_duration").assertExists()
        rule.onNodeWithTag("chat_reference_voice_speed").assertExists()
        saveFrame("chat-reference-full-media.png")
        list.performScrollToIndex(8)
        rule.onNodeWithTag("chat_reference_album_video_0").assertExists()
        // A valid local video may not expose duration metadata on every Android
        // emulator/codec combination. Production correctly leaves unknown duration
        // blank; keep this visual fixture deterministic and verify duration parsing
        // independently in BlinkSelectedClipDurationTest.
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("chat_video_frame_loaded_0")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("chat_video_frame_loaded_0").assertExists()
        saveFrame("chat-reference-full-videos.png")
        list.performScrollToIndex(9)
        rule.onNodeWithTag("chat_reference_document_card").assertExists()
        list.performScrollToIndex(10)
        rule.onNodeWithTag("chat_reference_legacy_poll").assertExists()
        // The short quoted reply must carry text, time and authoritative blue
        // receipt in one compact content row, not an oversized separate footer.
        rule.onNodeWithTag("chat_reference_reply_inline_text").assertExists()
        rule.onNodeWithTag("chat_reference_reaction_chip").assertExists()
        saveFrame("chat-reference-full-bottom.png")
    }

}
