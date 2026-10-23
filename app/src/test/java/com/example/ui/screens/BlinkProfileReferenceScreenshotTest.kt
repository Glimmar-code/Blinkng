package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.models.AchievementBadge
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
 * Staging screenshot probe for the BLINK full-screen reference profile.
 * Fake data is limited to the isolated JVM screenshot test, never production.
 * Rendering at larger font scales catches clipping without consuming device data.
 */
@RunWith(RobolectricTestRunner::class)
// LEGACY captures show Compose's colored semantics/debug rectangles, not the
// actual BLINK UI. NATIVE draws real text, surfaces and images for visual QA.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkProfileReferenceScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val profile = UserProfile(
        id = "screenshot-only",
        fullName = "Aisha Khan",
        username = "aisha",
        university = "University of Lagos",
        bio = "Good people make great days.",
        verificationBadge = VerificationBadge.BLUE,
        worldRank = 248,
        campusRank = 12,
        totalXp = 620L,
        xpLevel = 4,
        followerCount = 876,
        followingCount = 134,
        badges = listOf(
            AchievementBadge("verified_student", "Verified Student", "Academic verification"),
            AchievementBadge("social_connector", "Social Connector", "Connect with students")
        ),
    )

    private fun render(dark: Boolean, fontScale: Float, viewportWidth: Dp? = null) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BlinkTheme(darkTheme = dark) {
                    Box(
                        Modifier
                            .then(if (viewportWidth == null) Modifier.fillMaxSize() else Modifier.width(viewportWidth).fillMaxHeight())
                            .background(if (dark) Color.Black else Color.White)
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                BlinkReferenceProfileDashboard(
                                    profile = profile,
                                    isMe = true,
                                    blinkCoinBalance = 1250L,
                                    isDark = dark,
                                    isFollowing = false,
                                    followBusy = false,
                                    postCount = 24,
                                    profileCompletion = 82,
                                    onAvatarClick = {}, onBack = {}, onEdit = {},
                                    onMessage = {}, onFollow = {}, onVerify = {},
                                    onEarnCoins = {}, onShowBadges = {}, onShare = {},
                                    onCopyUsername = {}, onVisitors = {}, onTopAction = {}
                                )
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            BlinkReferenceProfileBottomNav(dark, "home") {}
                        }
                    }
                }
            }
        }
    }

    @Test fun profileReferenceLight() {
        render(dark = false, fontScale = 1.0f)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/profile-reference-light.png")
    }

    @Test fun profileReferenceLargeFonts() {
        render(dark = false, fontScale = 1.5f)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/profile-reference-large-fonts.png")
    }

    @Test fun profileReference320DpFont200() {
        render(dark = false, fontScale = 2.0f, viewportWidth = 320.dp)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/profile-reference-320dp-font200.png")
    }

    @Test fun profileReference360DpFont130() {
        render(dark = true, fontScale = 1.3f, viewportWidth = 360.dp)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/profile-reference-360dp-font130.png")
    }

    @Test fun profileReferenceDark() {
        render(dark = true, fontScale = 1.0f)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/profile-reference-dark.png")
    }
}
