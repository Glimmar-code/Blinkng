package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                    onClassic = {}, onDraft = { openedId = it.id }
                )
            }
        }
        composeRule.onNodeWithText("My campus update").performClick()
        composeRule.runOnIdle { assertEquals("draft123", openedId) }
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
                    onShareCaption = { selected += "share" },
                    isSubmitting = false,
                    uploadProgress = null
                )
            }
        }
        composeRule.onNodeWithTag("blink_create_post_publish_options").assertExists()
        composeRule.onNodeWithText("Preview Post").performClick()
        composeRule.onNodeWithText("Save as Draft").performClick()
        composeRule.onNodeWithText("WhatsApp / other apps").performClick()
        composeRule.runOnIdle { assertEquals(listOf("preview", "draft", "share"), selected) }
        composeRule.onNodeWithText("Post Now").performClick()
        composeRule.runOnIdle { assertEquals(listOf("preview", "draft", "share", "post"), selected) }
    }
}
