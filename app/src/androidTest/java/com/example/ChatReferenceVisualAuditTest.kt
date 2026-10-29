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
        rule.onNodeWithTag("chat_reference_header").assertExists()
        rule.onNodeWithTag("chat_reference_pinned_banner").assertExists()
        rule.onNodeWithTag("chat_reference_attachment_plus").assertExists()
        rule.onNodeWithTag("chat_reference_emoji").assertExists()
        rule.onNodeWithTag("chat_reference_send").assertExists()

        // Capture the actual rendered dark chat for CI's side-by-side review.
        val rendered = rule.onNodeWithTag("chat_conversation_root").captureToImage()
        check(rendered.width > 100 && rendered.height > 200)
        // Export a real emulator-rendered screenshot, not a fabricated mockup.
        // This isolated fixture verifies the chrome and contrast; comparing a
        // full media-rich conversation requires matching content on a device.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "chat-reference-staging-dark.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Audit")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        )
        resolver.openOutputStream(uri).use { stream ->
            checkNotNull(stream)
            check(rendered.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        check(resolver.update(uri, values, null, null) == 1)

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
        // Local URI thumbnails exercise the real video-album composable
        // without fetching untrusted/private content or playing a video.
        for (index in 0..1) {
            reference += msg("reference-video-$index", "Video", "09:20", true).copy(
                attachedVideoUrl = photoUris[index],
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
        fun saveFrame(filename: String) {
            rule.waitForIdle()
            val bitmap = rule.onNodeWithTag("chat_conversation_root")
                .captureToImage().asAndroidBitmap()
            check(bitmap.width > 100 && bitmap.height > 200)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Audit")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = requireNotNull(
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            )
            resolver.openOutputStream(uri).use { output ->
                checkNotNull(output)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) == 1)
        }
        // Lazy list collapses album rows; these indexes select three distinct
        // real Compose slices instead of pretending all media fits in one view.
        list.performScrollToIndex(1)
        saveFrame("chat-reference-full-top.png")
        list.performScrollToIndex(6)
        saveFrame("chat-reference-full-media.png")
        list.performScrollToIndex(10)
        saveFrame("chat-reference-full-bottom.png")
    }

}
