package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.models.FeedPost
import com.example.ui.screens.SharedPostScreen
import com.example.ui.theme.BlinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full post destination keeps the comment composer in the same screen. */
@RunWith(AndroidJUnit4::class)
class PostDetailsInteractionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun postDetailsShowsContentAndSubmitsInlineComment() {
        var submitted: Pair<String, String?>? = null
        var backCount = 0
        composeRule.setContent {
            BlinkTheme(darkTheme = false) {
                SharedPostScreen(
                    post = FeedPost(
                        id = "post-details-test",
                        author = "Student",
                        authorUsername = "student",
                        authorAvatar = "",
                        timeAgo = "1h",
                        text = "BLINK full post details test body",
                        likes = 0,
                        commentsCount = 0,
                        sharesCount = 0,
                    ),
                    currentUsername = "reader",
                    currentUserId = "reader-id",
                    isDark = false,
                    comments = emptyList(),
                    isCommentsLoading = false,
                    isPostingComment = false,
                    onBack = { backCount++ },
                    onLike = {},
                    onBookmark = {},
                    onShare = {},
                    onOptions = {},
                    onDelete = {},
                    onProfileClick = {},
                    onVotePoll = { _, _ -> },
                    onSendComment = { text, parent -> submitted = text to parent },
                    onToggleCommentLike = {},
                    onReportComment = { _, _ -> }
                )
            }
        }
        composeRule.onNodeWithTag("post_details_list").assertIsDisplayed()
        composeRule.onNodeWithText("BLINK full post details test body").assertIsDisplayed()
        composeRule.onNodeWithTag("post_details_comment_input").performTextInput("Nice campus post!")
        composeRule.onNodeWithContentDescription("Send comment").performClick()
        composeRule.runOnIdle { assertEquals("Nice campus post!" to null, submitted) }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }
}
