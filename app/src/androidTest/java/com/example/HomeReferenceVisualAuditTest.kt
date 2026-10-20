package com.example

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.models.FeedPost
import com.example.data.models.VerificationBadge
import com.example.ui.components.FeedBottomBar
import com.example.ui.components.FeedReferenceStatsRow
import com.example.ui.components.FeedTabs
import com.example.ui.components.FeedTopBar
import com.example.ui.components.PostCard
import com.example.ui.theme.BlinkTheme
import com.example.viewmodel.MainTab
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only layout audit. The sample author, rank and coin totals are intentionally
 * fixtures and NEVER flow into production user data or the shipped APK.
 *
 * Match screenshot composition and spacing with the supplied reference artwork.
 * This does not verify authenticated data, navigation wiring or production content.
 */
@RunWith(AndroidJUnit4::class)
class HomeReferenceVisualAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun captureLightThemeHomeChromeAndPostCard() {
        rule.setContent {
            BlinkTheme(darkTheme = false) {
                val context = LocalContext.current
                // Repository-owned icon, used as a stand-in photograph solely in test screenshots.
                val testImage = "android.resource://${context.packageName}/${R.drawable.app_icon}"
                Column(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("home_reference_audit_root")
                ) {
                    FeedTopBar(
                        userAvatar = testImage,
                        userIsOnline = true,
                        hasUnreadNotifications = true,
                        unreadNotificationCount = 1,
                        reserveStatusBar = false,
                        onSearchClick = {},
                        onItemsClick = {},
                        onNotificationClick = {},
                        onMenuClick = {},
                        onProfileClick = {}
                    )
                    FeedReferenceStatsRow(
                        worldRank = 248,
                        coins = 1250,
                        onRankClick = {},
                        onCoinsClick = {}
                    )
                    FeedTabs(
                        selectedIndex = 0,
                        onForYouClick = {},
                        onFollowingClick = {},
                        onFilterClick = {}
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PostCard(
                            post = FeedPost(
                                id = "visual-audit-only",
                                author = "Aisha Khan",
                                authorAvatar = testImage,
                                timeAgo = "2h",
                                text = "Beautiful day on campus ☀️\nGood people make great days.",
                                images = listOf(testImage),
                                likes = 0,
                                commentsCount = 164,
                                sharesCount = 0,
                                repostsCount = 320,
                                viewsCount = 12400,
                                authorUsername = "aisha",
                                authorProfileId = "visual-only"
                            ),
                            isDark = false,
                            onLike = {},
                            onComment = {},
                            onBookmark = {},
                            onRepost = {},
                            onShare = {},
                            onOptionsClick = {},
                            onProfileClick = {},
                            authorName = "Aisha Khan",
                            authorUniversity = "University of Lagos",
                            authorVerificationBadge = VerificationBadge.BLUE,
                            authorProfileId = "visual-only",
                            onFollowAuthor = {},
                            xFeedStyle = true,
                            trackExposure = false
                        )
                    }
                    FeedBottomBar(
                        currentTab = MainTab.HOME,
                        feedSubTab = 0,
                        onHomeClick = {},
                        onReelsClick = {},
                        onMarketClick = {},
                        onConnectClick = {},
                        onMessageClick = {},
                        isDark = false,
                        hasUnreadMessages = true
                    )
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onNodeWithTag("home_reference_audit_root").captureToImage().asAndroidBitmap()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = requireNotNull(ctx.getExternalFilesDir(null))
        val file = File(folder, "home-reference-staging-light.png")
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        check(file.exists() && file.length() > 1_000)
    }
}
