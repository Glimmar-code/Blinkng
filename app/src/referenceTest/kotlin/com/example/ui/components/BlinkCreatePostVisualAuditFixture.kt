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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
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

    @Test fun firstPhotoTapOpensInAppMediaReviewFixture() {
        rule.setContent {
            AuditSurface {
                CreatePostSheet(
                    profile = UserProfile(
                        id = "visual-audit-photo-entry",
                        fullName = "BLINK Test Student",
                        username = "blink_photo_test",
                        university = "FUTA"
                    ),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true,
                    trendingTagsLoader = { _, _ -> listOf("FUTA", "Campus") }
                )
            }
        }
        rule.onNodeWithTag("blink_create_choice_photo").performClick()
        rule.onNodeWithTag("blink_media_review").assertExists()
        capture("create-post-photo-first-entry-staging.png", "blink_create_post_full_screen")
    }

    @Test fun inlinePhotoOpensSelectMediaAndBackRestoresTextDraft() {
        rule.setContent {
            AuditSurface {
                CreatePostSheet(
                    profile = UserProfile(
                        id = "visual-audit-inline-gallery",
                        fullName = "BLINK Test Student",
                        username = "blink_inline_photo_test",
                        university = "FUTA"
                    ),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true,
                    trendingTagsLoader = { _, _ -> listOf("FUTA", "Campus") }
                )
            }
        }
        rule.onNodeWithTag("blink_create_choice_text").performClick()
        rule.onNodeWithTag("blink_create_post_caption_field")
            .performTextReplacement("Draft retained when reviewing photos")
        check(
            rule.onNodeWithTag("blink_create_post_caption_field")
                .fetchSemanticsNode().config
                [SemanticsProperties.EditableText].text ==
                "Draft retained when reviewing photos"
        ) { "The actual caption editor lost its typed draft." }
        rule.onNodeWithTag("blink_compose_action_0").performScrollTo().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_media_review").fetchSemanticsNodes().isNotEmpty()
        }
        capture("create-post-text-to-gallery-staging.png", "blink_create_post_full_screen")
        rule.onNodeWithTag("blink_media_review_back").performClick()
        // Wait for the editor to replace the outgoing gallery in AnimatedContent.
        // Assert the actual caption field, not the first transitioning input.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_create_post_caption_field")
                .fetchSemanticsNodes().isNotEmpty()
        }
        check(
            rule.onNodeWithTag("blink_create_post_caption_field")
                .fetchSemanticsNode().config
                [SemanticsProperties.EditableText].text ==
                "Draft retained when reviewing photos"
        ) { "The actual caption editor lost its typed draft." }
        rule.onNodeWithTag("blink_create_post_editor_header").assertExists()
    }

    @Test fun cancelledPhotoToPollSwitchKeepsExistingPhotoDraftUntilDone() {
        rule.setContent {
            AuditSurface {
                CreatePostSheet(
                    profile = UserProfile(
                        id = "visual-audit-photo-poll-switch",
                        fullName = "BLINK Test Student",
                        username = "blink_photo_poll_switch_test",
                        university = "FUTA"
                    ),
                    savedDrafts = listOf(PostDraft(
                        id = "photo-poll-switch-draft",
                        text = "Keep my campus photo",
                        imageUri = "content://sample/selected-photo"
                    )),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true
                )
            }
        }
        rule.onNodeWithText("Drafts").performClick()
        rule.onNodeWithTag("blink_create_saved_draft_photo-poll-switch-draft").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_selected_media_thumbnail_0")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("blink_compose_action_2").performScrollTo().performClick()
        rule.onNodeWithText("Change post type?").assertExists()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithTag("blink_poll_fullscreen").assertExists()
        rule.onNodeWithTag("blink_poll_back").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_selected_media_thumbnail_0")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // The same photo survives the cancelled conversion. Only a completed
        // valid Poll may replace it.
        rule.onNodeWithTag("blink_compose_action_2").performScrollTo().performClick()
        rule.onNodeWithText("Change post type?").assertExists()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithTag("blink_poll_question_input").performTextReplacement(
            "Which club should host the next event?"
        )
        rule.onNodeWithTag("blink_poll_option_0").performTextReplacement("Tech")
        rule.onNodeWithTag("blink_poll_option_1").performTextReplacement("Sports")
        rule.onNodeWithTag("blink_poll_done").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_poll_attached_card").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("blink_selected_media_thumbnail_0").assertDoesNotExist()
    }

    @Test fun changingPollToPhotosOpensSelectMediaAndBackPreservesPoll() {
        rule.setContent {
            AuditSurface {
                CreatePostSheet(
                    profile = UserProfile(
                        id = "visual-audit-poll-gallery",
                        fullName = "BLINK Test Student",
                        username = "blink_poll_gallery_test",
                        university = "FUTA"
                    ),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true
                )
            }
        }
        rule.onNodeWithTag("blink_create_choice_poll").performClick()
        rule.onNodeWithTag("blink_poll_question_input")
            .performTextReplacement("Which campus club should we support?")
        rule.onNodeWithTag("blink_poll_option_0").performTextReplacement("Coding")
        rule.onNodeWithTag("blink_poll_option_1").performTextReplacement("Robotics")
        rule.onNodeWithTag("blink_poll_done").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_poll_attached_card").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("blink_compose_action_0").performScrollTo().performClick()
        rule.onNodeWithText("Change post type?").assertExists()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithTag("blink_media_review").assertExists()
        capture("create-post-poll-switch-gallery-staging.png", "blink_create_post_full_screen")
        rule.onNodeWithTag("blink_media_review_back").performClick()
        // The old Poll must still be attached if no image was chosen.
        rule.onNodeWithTag("blink_poll_attached_card").assertExists()
        rule.onNodeWithTag("blink_edit_attached_poll").performClick()
        rule.onNodeWithTag("blink_poll_question_input").assertExists()
        check(rule.onNodeWithTag("blink_poll_question_input")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text ==
            "Which campus club should we support?") { "Canceling photo replacement lost the original Poll." }
    }

    @Test fun landingPhotoBackReturnsToCreateOptions() {
        rule.setContent {
            AuditSurface {
                CreatePostSheet(
                    profile = UserProfile(
                        id = "visual-audit-gallery-back",
                        fullName = "BLINK Test Student",
                        username = "blink_gallery_back_test",
                        university = "FUTA"
                    ),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true
                )
            }
        }
        rule.onNodeWithTag("blink_create_choice_photo").performClick()
        rule.onNodeWithTag("blink_media_review").assertExists()
        rule.onNodeWithTag("blink_media_review_back").performClick()
        rule.onNodeWithTag("blink_create_choice_photo").assertExists()
    }

    @Test fun emptyGalleryShowsUsableDevicePickerFixture() {
        rule.setContent {
            AuditSurface {
                BlinkMediaEmptyGallery(
                    canBrowse = false,
                    onPick = {},
                    onRequestLibrary = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        rule.onNodeWithTag("blink_gallery_browse_photos").assertExists()
        rule.onNodeWithTag("blink_gallery_allow_access").assertExists()
        capture("create-post-gallery-empty-staging.png")
    }

    @Test fun selectedPhotosUseNativeAlbumReviewFixture() {
        var edited: String? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resource = "android.resource://${context.packageName}/${com.example.R.drawable.app_icon}"
        rule.setContent {
            AuditSurface {
                BlinkReferenceMediaReview(
                    images = listOf(resource, resource + "?two"),
                    onBack = {}, onRemove = {}, onMove = { _, _ -> },
                    onEdit = { edited = it }, onAddMore = {}, onPickVideos = {}, onNext = {}
                )
            }
        }
        rule.onNodeWithTag("blink_gallery_selected_strip").assertExists()
        rule.onNodeWithTag("blink_gallery_remove_selected_0").assertExists()
        rule.onNodeWithTag("blink_gallery_add_more").assertExists()
        rule.onNodeWithTag("blink_gallery_next").assertExists()
        // One footer Next; the header must not offer a duplicate advance.
        rule.onNodeWithText("Next").assertDoesNotExist()
        capture("create-post-gallery-staging.png")
        rule.onNodeWithTag("blink_gallery_photo_0").performClick()
        rule.onNodeWithTag("blink_gallery_edit_preview").performClick()
        rule.runOnIdle { check(edited == resource) { "Preview edit must keep the selected original photo" } }
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
                    trendingTagsLoader = { _, _ -> listOf("FUTA", "SchoolLife", "Exam", "Campus") },
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

    @Test fun pollOptionControlsAreVisibleAndRespectTwoAnswerMinimum() {
        rule.setContent {
            AuditSurface {
                var options by remember { mutableStateOf(listOf("Sports", "Technology")) }
                PollComposer(
                    question = "Which club?",
                    onQuestionChanged = {},
                    options = options,
                    onOptionsChanged = { options = it },
                    durationHours = 24,
                    onDurationChanged = {},
                    enabled = true,
                    standalone = true
                )
            }
        }
        rule.onNodeWithTag("blink_poll_remove_0").assertExists().assertIsNotEnabled()
        rule.onNodeWithTag("blink_poll_remove_1").assertExists().assertIsNotEnabled()
        rule.onNodeWithText("Add option (up to 4)").performClick()
        rule.onNodeWithTag("blink_poll_remove_2").assertIsEnabled()
        rule.onNodeWithTag("blink_poll_remove_2").performClick()
        rule.onNodeWithTag("blink_poll_remove_0").assertIsNotEnabled()
    }

    @Test fun pollTileOpensFullScreenAndDoneKeepsEditablePoll() {
        rule.setContent {
            AuditSurface {
                CreatePostSheet(
                    profile = UserProfile(
                        id = "visual-audit-poll-fullscreen",
                        fullName = "BLINK Test Student",
                        username = "blink_poll_screen_test",
                        university = "FUTA"
                    ),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true
                )
            }
        }
        rule.onNodeWithTag("blink_create_choice_poll").performClick()
        rule.onNodeWithTag("blink_poll_fullscreen").assertExists()
        rule.onNodeWithTag("blink_poll_question_input")
            .performTextReplacement("Which FUTA event should we host?")
        rule.onNodeWithTag("blink_poll_option_0")
            .performTextReplacement("Tech meetup")
        rule.onNodeWithTag("blink_poll_option_1")
            .performTextReplacement("Sports day")
        rule.onNodeWithTag("blink_poll_duration").performScrollTo().performClick()
        rule.onNodeWithText("3 days").performScrollTo().performClick()
        capture("create-post-poll-fullscreen-staging.png", "blink_create_post_full_screen")
        rule.onNodeWithTag("blink_poll_done").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_poll_attached_card").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("blink_edit_attached_poll").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("blink_poll_fullscreen").fetchSemanticsNodes().isNotEmpty()
        }
        // Material TextField merges character counters and labels into Text;
        // assert only its real editable value after the Done → Edit roundtrip.
        check(rule.onNodeWithTag("blink_poll_question_input")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text ==
            "Which FUTA event should we host?") { "Poll question was lost after Edit." }
        check(rule.onNodeWithTag("blink_poll_option_0")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text ==
            "Tech meetup") { "First poll option was lost after Edit." }
        check(rule.onNodeWithTag("blink_poll_option_1")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text ==
            "Sports day") { "Second poll option was lost after Edit." }
        rule.onNodeWithText("3 days").assertExists()
        rule.onNodeWithTag("blink_poll_back").performClick()
        rule.onNodeWithTag("blink_poll_attached_card").assertExists()
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
                    onPreview = {}, onShareWhatsApp = {},
                    onShareOtherApps = {}, onCopyCaption = {}, canCopyCaption = true,
                    isSubmitting = false, canPublish = true, uploadProgress = null
                )
            }
        }
        capture("create-post-options-staging.png")
    }

    @Test fun previewUsesActualFeedCardProportionsOnAndroid() {
        var dismissed = false
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
                    onDismiss = { dismissed = true }
                )
            }
        }
        rule.onNodeWithTag("blink_post_preview_back").assertExists()
        rule.onAllNodesWithTag("blink_post_preview_photo", useUnmergedTree = true)
            .onFirst().assertExists()
        rule.onNodeWithText("Preview only · Not published").assertExists()
        rule.onNodeWithText("Back to editor").assertDoesNotExist()
        capture("create-post-preview-staging.png", "blink_post_preview_stage")
        rule.onNodeWithTag("blink_post_preview_back").performClick()
        rule.runOnIdle { check(dismissed) { "Preview must allow returning to composer" } }
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
