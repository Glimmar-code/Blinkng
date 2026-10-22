package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.example.data.models.FeedPost
import com.example.data.models.StudyCircleSummary
import com.example.data.models.UserProfile
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
 * Captures the actual Compose Connect layout for reference-image and font-scale
 * comparisons. Fixtures exist only in test source; no fake users reach production.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class ConnectDiscoveryLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val students = listOf(
        "Aisha", "Rohan", "Meera", "Arjun", "Sana"
    ).mapIndexed { index, name ->
        UserProfile(
            id = "campus-visitor-${index + 1}",
            username = name.lowercase(),
            fullName = name,
            avatarUrl = "",
            university = "Federal University of Technology, Akure",
            department = listOf("Science", "Computer Sci", "Business", "Engineering", "Design")[index],
            onlineNow = true
        )
    }

    private val groups = listOf("Music Club", "Sports", "Study Buddies", "Photography")
        .mapIndexed { index, title ->
            StudyCircleSummary(
                id = "circle-$index",
                ownerId = students[index].id,
                name = title,
                description = "Layout fixture",
                faculty = "",
                course = "",
                maxMembers = 50,
                isPrivate = false,
                memberCount = listOf(12, 24, 31, 9)[index],
                isMember = false,
                isOwner = false
            )
        }

    private val post = FeedPost(
        id = "campus-layout-fixture",
        author = "Aisha",
        authorAvatar = "",
        timeAgo = "3h",
        text = "Campus event update for screenshot layout.",
        likes = 8,
        commentsCount = 4,
        sharesCount = 2,
        authorUsername = "aisha"
    )

    private fun capture(fontScale: Float, filename: String) {
        rule.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(original.density, fontScale)
            ) {
                BlinkTheme(darkTheme = false) {
                    ConnectDiscoveryLanding(
                        profiles = students,
                        currentUsername = "",
                        myCampus = "FUTA",
                        userAvatar = "",
                        onSearchClick = {},
                        onOpenGame = {},
                        onOpenNotifications = {},
                        onProfileClick = {},
                        onOpenHub = {},
                        onOpenCampusFeed = {},
                        onRefresh = {},
                        isRefreshing = false,
                        posts = listOf(post),
                        previewCircles = groups,
                        showIllustrativePhotos = false
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$filename")
    }

    @Test fun referenceSizeLayout() = capture(1f, "connect-reference-100.png")

    @Test fun accessibilityLargeFontLayout() = capture(2f, "connect-reference-200.png")
}
