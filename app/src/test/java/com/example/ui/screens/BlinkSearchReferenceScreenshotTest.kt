package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Isolated visual QA: synthetic fixtures exist only here, not in production Search.
 * These capture the actual Compose landing on narrow layouts and enlarged fonts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkSearchReferenceScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val owner = UserProfile(
        id = "search-visual-owner", username = "owner", fullName = "BLINK User",
        university = "Federal University of Technology Akure", onlineNow = true,
    )
    private val friends = listOf(
        UserProfile(
            id = "search-visual-1", username = "student1", fullName = "Ada Okoro",
            university = "FUTA", department = "Engineering",
            verificationBadge = VerificationBadge.BLUE,
        ),
        UserProfile(
            id = "search-visual-2", username = "student2", fullName = "Tobi Ade",
            university = "FUTA", department = "Computer Science",
        ),
    )
    private val post = FeedPost(
        id = "search-visual-post",
        author = "Ada Okoro", authorAvatar = "", timeAgo = "3h",
        text = "Best study spots on campus? Share your favourites!",
        likes = 124, commentsCount = 36, sharesCount = 12,
    )
    private fun render(dark: Boolean, fontScale: Float, width: Dp? = null) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("blink_reference_search", Context.MODE_PRIVATE)
            .edit().putString("queries:${owner.id}", "engineering\u001Fmacbook\u001Fmusic club").commit()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BlinkTheme(darkTheme = dark) {
                    Box(
                        Modifier.then(
                            if (width == null) Modifier.fillMaxSize()
                            else Modifier.width(width).fillMaxHeight()
                        ).background(if (dark) Color.Black else Color.White)
                    ) {
                        BlinkReferenceSearchScreen(
                            myProfile = owner,
                            profiles = friends + owner,
                            posts = listOf(post),
                            currentUsername = owner.username,
                            serverProfiles = emptyList(),
                            serverPosts = emptyList(),
                            onProfileClick = {},
                            onPostClick = {},
                            onLikePost = {},
                            onCommentPost = {},
                            onSharePost = {},
                            onBookmarkPost = {},
                            onOptionsClick = {},
                            onGameClick = {},
                            onNotificationsClick = {},
                            hasUnreadNotifications = true,
                            onOpenAdvanced = {},
                            onBackToHome = {},
                            isDark = dark,
                        )
                    }
                }
            }
        }
    }

    @Test fun marketAndCommunitiesAreNotSearchCategories() {
        render(false, 1.0f)
        rule.onNodeWithText("Market").assertDoesNotExist()
        rule.onNodeWithText("Communities").assertDoesNotExist()
        rule.onNodeWithText("All").assertExists()
    }

    @Test fun searchReferenceLight() {
        render(false, 1.0f)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-reference-light.png")
    }

    @Test fun searchReferenceDark() {
        render(true, 1.0f)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-reference-dark.png")
    }

    @Test fun searchReference320Font200() {
        render(false, 2.0f, 320.dp)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-reference-320dp-font200.png")
    }

    @Test fun searchReference360Font130() {
        render(false, 1.3f, 360.dp)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-reference-360dp-font130.png")
    }
}
