package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.models.FeedPost
import com.example.ui.components.CreatePostFab
import com.example.ui.components.PostCard
import com.example.ui.theme.BlinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose touch tests, using test-only post data with no external network calls. */
@RunWith(AndroidJUnit4::class)
class HomeReferenceInteractionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun retainedFloatingCreateOpensWorkingPostAction() {
        var createCount = 0
        composeRule.setContent {
            BlinkTheme(darkTheme = false) {
                CreatePostFab(expanded = true, onClick = { createCount++ })
            }
        }
        // The Create button is a single action, never a speed-dial menu.
        composeRule.onNodeWithTag("create_post_fab").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, createCount) }
        composeRule.onNodeWithTag("create_post_action").assertDoesNotExist()
        composeRule.onNodeWithTag("blink_ai_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("create_post_fab").performClick()
        composeRule.runOnIdle { assertEquals(2, createCount) }
    }

    @Test fun imagePostKeepsLikeAndPostMetadataOpensDetails() {
        var liked = 0
        var opened = 0
        composeRule.setContent {
            BlinkTheme(darkTheme = false) {
                Box(Modifier.width(393.dp)) {
                    PostCard(
                        post = FeedPost(
                            id = "image-interaction-audit",
                            author = "Test Creator",
                            authorUsername = "testcreator",
                            authorAvatar = "",
                            timeAgo = "2h",
                            text = "An image post still needs an accessible Like action.",
                            images = listOf("android.resource://com.example/nonexistent_fixture"),
                            viewsCount = 21,
                            likes = 4
                        ),
                        isDark = false,
                        xFeedStyle = true,
                        trackExposure = false,
                        authorUniversity = "University of Lagos",
                        onLike = { liked++ },
                        onComment = {},
                        onBookmark = {},
                        onRepost = {},
                        onShare = {},
                        onOptionsClick = {},
                        onProfileClick = {},
                        onOpenPost = { opened++ }
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Like post").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("University of Lagos").performClick()
        composeRule.runOnIdle {
            assertEquals(1, liked)
            assertEquals(1, opened)
        }
    }

    @Test fun realPostActionsRemainClickableWithReferenceFooter() {
        var saved = 0
        var liked = 0
        var reposted = 0
        var commented = 0
        var shared = 0
        var followed = 0
        var options = 0

        composeRule.setContent {
            BlinkTheme(darkTheme = false) {
                Box(Modifier.width(393.dp)) {
                    PostCard(
                        post = FeedPost(
                            id = "audit-post",
                            author = "Test Creator",
                            authorUsername = "testcreator",
                            authorProfileId = "audit-author",
                            authorAvatar = "",
                            timeAgo = "2h",
                            text = "A real feed post interaction test",
                            likes = 1,
                            commentsCount = 2,
                            sharesCount = 0,
                            repostsCount = 1,
                            viewsCount = 32,
                            savesCount = 3
                        ),
                        isDark = false,
                        xFeedStyle = true,
                        trackExposure = false,
                        authorUniversity = "University",
                        authorProfileId = "audit-author",
                        onLike = { liked++ },
                        onBookmark = { saved++ },
                        onComment = { commented++ },
                        onRepost = { reposted++ },
                        onShare = { shared++ },
                        onOptionsClick = { options++ },
                        onProfileClick = {},
                        onFollowAuthor = { followed++ }
                    )
                }
            }
        }
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("Follow").performClick()
        composeRule.onNodeWithContentDescription("Repost").performClick()
        composeRule.onNodeWithContentDescription("Comments").performClick()
        composeRule.onNodeWithContentDescription("Save post").performClick()
        composeRule.onNodeWithContentDescription("Share post").performClick()
        composeRule.onNodeWithContentDescription("Like post").performClick()
        composeRule.onNodeWithContentDescription("Post options").performClick()
        composeRule.runOnIdle {
            assertEquals(1, followed)
            assertEquals(1, reposted)
            assertEquals(1, commented)
            assertEquals(1, saved)
            assertEquals(1, shared)
            assertEquals(1, liked)
            assertEquals(1, options)
        }
    }
}
