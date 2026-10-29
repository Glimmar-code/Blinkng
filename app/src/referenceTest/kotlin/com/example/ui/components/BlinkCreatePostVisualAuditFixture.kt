package com.example.ui.components

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import com.example.ui.screens.BlinkUniversalPhotoEditor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.models.PostDraft
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import org.junit.Rule
import org.junit.Test

/** Shared Android Compose fixtures for device and Robolectric visual comparison with the user's
 * ten-panel reference. No screenshots or personal data ship with the APK.
 */
abstract class BlinkCreatePostVisualAuditFixture {
    @get:Rule val rule = createComposeRule()

    protected abstract fun capture(name: String, tag: String = "create_post_visual_audit")

    @Composable
    private fun AuditSurface(darkHost: Boolean = true, content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = if (darkHost) darkColorScheme() else lightColorScheme()) {
            Box(Modifier.fillMaxSize().background(Color.Black)
                .testTag("create_post_visual_audit")) { content() }
        }
    }

    @Test fun landingMatchesReferenceLayoutFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = "android.resource://${context.packageName}/${com.example.R.drawable.app_icon}"
        rule.setContent {
            AuditSurface {
                BlinkCreatePostLanding(
                    drafts = listOf(PostDraft(
                        id = "audit-draft", text = "Campus news", imageUri = icon
                    )),
                    onClose = {}, onText = {}, onPhoto = {}, onVideo = {},
                    onPoll = {}, onClassic = {}, onCamera = {}, onDraft = {}
                )
            }
        }
        capture("create-post-landing-staging.png")
    }

    @Test fun landingWorksInsideLightThemeHost() {
        rule.setContent {
            AuditSurface(darkHost = false) {
                BlinkCreatePostLanding(
                    drafts = emptyList(),
                    onClose = {}, onText = {}, onPhoto = {}, onVideo = {},
                    onPoll = {}, onClassic = {}, onCamera = {}, onDraft = {}
                )
            }
        }
        capture("create-post-landing-light-host-staging.png")
    }

    @Test fun selectedPhotosUseNativeAlbumReviewFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resource = "android.resource://${context.packageName}/${com.example.R.drawable.app_icon}"
        rule.setContent {
            AuditSurface {
                BlinkReferenceMediaReview(
                    images = listOf(resource, resource + "?two"),
                    onBack = {}, onRemove = {}, onMove = { _, _ -> },
                    onEdit = {}, onAddMore = {}, onPickVideos = {}, onNext = {}
                )
            }
        }
        capture("create-post-gallery-staging.png")
    }

    @Test fun postDetailsAreScrollableOnMobileFixture() {
        rule.setContent {
            AuditSurface {
                BlinkReferencePostDetails(
                    text = "FUTA campus update", mediaUri = null,
                    tags = listOf("Campus"), audience = "Everyone",
                    location = "Akure", category = "Campus Life",
                    allowComments = true, hideLikes = false, temporary = false,
                    onBack = {}, onEditCaption = {}, onAudienceChange = {},
                    onLocationChange = {}, onCategoryChange = {},
                    onCommentsChange = {}, onHideLikesChange = {},
                    onTemporaryChange = {}, onNext = {}
                )
            }
        }
        capture("create-post-details-staging.png")
    }

    @Test fun textComposerToolbarFixture() {
        rule.setContent {
            AuditSurface {
                BlinkReferenceComposerToolbar(
                    enabled = true,
                    onPhoto = {}, onVideo = {}, onPoll = {},
                    onEmoji = {}, onTag = {}, onMention = {}, onLocation = {}
                )
            }
        }
        capture("create-post-text-toolbar-staging.png")
    }


    @Test fun fullTextComposerAndMentionResultsAreCapturedFromActualCreatePostScreen() {
        val avatar = "android.resource://" +
            InstrumentationRegistry.getInstrumentation().targetContext.packageName +
            "/${com.example.R.drawable.app_icon}"
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CreatePostSheet(
                    profile = UserProfile(
                        id = "visual-audit-composer",
                        fullName = "BLINK Test Student",
                        username = "blink_visual_test",
                        avatarUrl = avatar,
                        university = "FUTA"
                    ),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true,
                    searchMentions = { _, _, _, _ ->
                        listOf(
                            UserProfile(id = "audit-ayo", fullName = "Ayo", username = "ayo_futa", avatarUrl = avatar),
                            UserProfile(id = "audit-abby", fullName = "Abby", username = "abby_official", avatarUrl = avatar),
                            UserProfile(id = "audit-adeola", fullName = "Adeola", username = "adeola_futa", avatarUrl = avatar)
                        )
                    }
                )
            }
        }
        rule.onNodeWithTag("blink_create_choice_text").performClick()
        rule.onNodeWithTag("blink_create_post_full_screen").assertExists()
        val header = rule.onNodeWithTag("blink_create_post_editor_header").fetchSemanticsNode().boundsInRoot
        val field = rule.onAllNodes(hasSetTextAction()).onFirst().fetchSemanticsNode().boundsInRoot
        check(header.bottom <= field.top) { "Editor header overlaps the text field" }
        capture("create-post-full-text-staging.png", "blink_create_post_full_screen")

        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("@a")
        rule.waitUntil(12_000) {
            rule.onAllNodesWithText("Ayo").fetchSemanticsNodes().isNotEmpty()
        }
        capture("create-post-full-mentions-staging.png", "blink_create_post_full_screen")
    }

    @Test fun realPollOptionsAndDurationsFixture() {
        rule.setContent {
            AuditSurface {
                PollComposer(
                    question = "Which campus event?",
                    onQuestionChanged = {},
                    options = listOf("Sports", "Tech", "Arts"),
                    onOptionsChanged = {},
                    durationHours = 24,
                    onDurationChanged = {},
                    enabled = true
                )
            }
        }
        capture("create-post-poll-staging.png")
    }

    @Test fun multipleVideoClipReviewFixture() {
        rule.setContent {
            AuditSurface {
                BlinkReelClipReview(
                    clips = listOf("content://fixture/video/a", "content://fixture/video/b"),
                    exporting = false, progress = 0, error = null,
                    onMove = { _, _ -> }, onRemove = {}, onAdd = {},
                    onExport = {}, onBack = {}
                )
            }
        }
        capture("create-post-video-clips-staging.png")
    }

    @Test fun finalOptionsAreAccessibleFixture() {
        rule.setContent {
            AuditSurface {
                BlinkCreatePostPublishOptions(
                    onBack = {}, onPost = {}, onSaveDraft = {},
                    onPreview = {}, onShareCaption = {},
                    isSubmitting = false, canPublish = true, uploadProgress = null
                )
            }
        }
        capture("create-post-options-staging.png")
    }

    @Test fun previewUsesActualFeedCardProportionsOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resource = "android.resource://${context.packageName}/${com.example.R.drawable.app_icon}"
        rule.setContent {
            AuditSurface {
                PostPreviewDialog(
                    post = FeedPost(
                        id = "visual-audit-preview",
                        author = "BLINK User",
                        authorAvatar = resource,
                        timeAgo = "Now",
                        text = "Campus update: welcome to BLINK! #FUTA",
                        images = listOf(resource, resource),
                        likes = 0,
                        commentsCount = 0,
                        sharesCount = 0,
                        audience = "Everyone",
                        category = "Campus Life"
                    ),
                    profile = UserProfile(
                        fullName = "BLINK User",
                        username = "blink_preview",
                        avatarUrl = resource,
                        university = "FUTA"
                    ),
                    onDismiss = {}
                )
            }
        }
        capture("create-post-preview-staging.png", "blink_post_preview_stage")
    }

    @Test fun photoEditorUsesActualSelectedPhotoAndNativeTools() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse(
            "android.resource://${context.packageName}/${com.example.R.drawable.app_icon}"
        )
        rule.setContent {
            AuditSurface {
                BlinkUniversalPhotoEditor(
                    originalUri = uri,
                    onEdited = {},
                    onDismiss = {}
                )
            }
        }
        capture("create-post-photo-editor-staging.png", "blink_photo_editor_stage")
    }

    @Test fun mentionResultsUseRealSuggestionComponentWithOfflineTestData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val avatar = "android.resource://${context.packageName}/${com.example.R.drawable.app_icon}"
        rule.setContent {
            AuditSurface {
                BlinkPostMentionSuggestions(
                    text = "@a",
                    onTextChanged = {},
                    searchProfiles = { _, _, _, _ ->
                        listOf(
                            UserProfile(id = "mention-1", fullName = "Ayo", username = "ayo_futa", avatarUrl = avatar),
                            UserProfile(id = "mention-2", fullName = "Abby", username = "abby_official", avatarUrl = avatar),
                            UserProfile(id = "mention-3", fullName = "Adeola", username = "adeola_futa", avatarUrl = avatar),
                            UserProfile(id = "mention-4", fullName = "Aisha", username = "aisha01", avatarUrl = avatar)
                        )
                    }
                )
            }
        }
        rule.waitUntil(12_000) {
            rule.onAllNodesWithText("Ayo").fetchSemanticsNodes().isNotEmpty()
        }
        capture("create-post-mentions-staging.png")
    }

}
