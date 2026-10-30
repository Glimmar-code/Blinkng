package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.models.PostDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reference controls must call real host actions; existing draft data is preserved. */
@RunWith(AndroidJUnit4::class)
class BlinkCreatePostReferenceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun launchTilesOpenCorrectExistingComposerModes() {
        val selected = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                BlinkCreatePostLanding(
                    drafts = emptyList(),
                    onClose = { selected += "close" },
                    onText = { selected += "text" },
                    onPhoto = { selected += "photo" },
                    onVideo = { selected += "video" },
                    onPoll = { selected += "poll" },
                    onClassic = { selected += "classic" },
                    onCamera = { selected += "camera" },
                    onDraft = { selected += "draft" }
                )
            }
        }
        composeRule.onNodeWithTag("blink_create_choice_text").performClick()
        composeRule.onNodeWithTag("blink_create_choice_photo").performClick()
        composeRule.onNodeWithTag("blink_create_choice_video").performClick()
        composeRule.onNodeWithTag("blink_create_choice_poll").performClick()
        composeRule.runOnIdle { assertEquals(listOf("text", "photo", "video", "poll"), selected) }
    }

    @Test
    fun savedDraftIsRealAndSelectableWithoutFakeGalleryRows() {
        var openedId = ""
        composeRule.setContent {
            MaterialTheme {
                BlinkCreatePostLanding(
                    drafts = listOf(PostDraft(id = "draft123", text = "My campus update")),
                    onClose = {}, onText = {}, onPhoto = {}, onVideo = {}, onPoll = {},
                    onClassic = {}, onCamera = {}, onDraft = { openedId = it.id }
                )
            }
        }
        composeRule.onNodeWithText("My campus update").performClick()
        composeRule.runOnIdle { assertEquals("draft123", openedId) }
    }

    @Test
    fun mediaReviewSupportsRemovalAndNextWithoutAutoPosting() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                BlinkReferenceMediaReview(
                    images = listOf("content://sample/1", "content://sample/2"),
                    onBack = { actions += "back" },
                    onRemove = { actions += "remove:" + it },
                    onMove = { from, to -> actions += "move:" + from + "->" + to },
                    onEdit = { actions += "edit:" + it },
                    onAddMore = { actions += "add" },
                    onPickVideos = { actions += "video" },
                    onNext = { actions += "next" }
                )
            }
        }
        composeRule.onNodeWithTag("blink_media_review").assertExists()
        composeRule.onNodeWithText("2 / 10").assertExists()
        composeRule.onNodeWithTag("blink_gallery_photo_0").performClick()
        composeRule.onNodeWithTag("blink_gallery_edit_preview").performClick()
        composeRule.onNodeWithText("Add more photos").performClick()
        composeRule.onNodeWithTag("blink_media_tab_videos").performClick()
        composeRule.onNodeWithText("Next (2)").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("edit:content://sample/1", "add", "video", "next"), actions)
        }
    }

    @Test
    fun detailsUseSupportedAudienceAndKeepNextSeparateFromPublishing() {
        val selected = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                BlinkReferencePostDetails(
                    text = "A campus update",
                    mediaUri = null,
                    tags = listOf("FUTA"),
                    audience = "Everyone",
                    location = "",
                    category = "Campus Life",
                    allowComments = true,
                    hideLikes = false,
                    temporary = false,
                    onBack = { selected += "back" },
                    onEditCaption = { selected += "edit" },
                    onAudienceChange = { selected += "audience:" + it },
                    onLocationChange = { selected += "location:" + it },
                    onCategoryChange = { selected += "category:" + it },
                    onCommentsChange = { selected += "comments:" + it },
                    onHideLikesChange = { selected += "likes:" + it },
                    onTemporaryChange = { selected += "temporary:" + it },
                    onNext = { selected += "next" }
                )
            }
        }
        composeRule.onNodeWithTag("blink_post_details_stage").assertExists()
        composeRule.onNodeWithText("Public").performClick()
        composeRule.onNodeWithText("Followers").performClick()
        composeRule.onNodeWithText("Next: Post options").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("audience:Followers", "next"), selected)
        }
    }

    @Test
    fun finalOptionsAreSeparateActionsAndNeverAutoPublish() {
        val selected = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                BlinkCreatePostPublishOptions(
                    onBack = { selected += "back" },
                    onPost = { selected += "post" },
                    onSaveDraft = { selected += "draft" },
                    onPreview = { selected += "preview" },
                    onShareWhatsApp = { selected += "whatsapp" },
                    onShareOtherApps = { selected += "other" },
                    onCopyCaption = { selected += "copy" },
                    canCopyCaption = true,
                    isSubmitting = false,
                    canPublish = true,
                    uploadProgress = null
                )
            }
        }
        composeRule.onNodeWithTag("blink_create_post_publish_options").assertExists()
        composeRule.onNodeWithText("Preview Post").performClick()
        composeRule.onNodeWithText("Save as Draft").performClick()
        composeRule.onNodeWithText("WhatsApp").performScrollTo().performClick()
        composeRule.onNodeWithText("Copy caption").performScrollTo().performClick()
        composeRule.onNodeWithText("Other apps").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("preview", "draft", "whatsapp", "copy", "other"), selected)
        }
        composeRule.onNodeWithText("Post Now").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("preview", "draft", "whatsapp", "copy", "other", "post"), selected)
        }
    }
}
