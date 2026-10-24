package com.example

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import com.blinkng.shared.brand.BlinkBeyondLinkIntro
import com.blinkng.shared.brand.BLINK_INTRO_DURATION_MS
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots at different viewport sizes are CI artifacts for comparing with the
 * approved Beyond LINK transition. They are NOT a substitute for on-device visual signoff.
 * The timing check also prevents the intro from trapping navigation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class BlinkBeyondLinkIntroTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h720dp")
    fun compactPhoneSnapshotAndCompletion() = verifyFrame(
        "src/test/screenshots/intro-320dp.png"
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun largePhoneSnapshotAndCompletion() = verifyFrame(
        "src/test/screenshots/intro-412dp.png"
    )

    @Test
    @Config(qualifiers = "w600dp-h960dp")
    fun tabletSnapshotAndCompletion() = verifyFrame(
        "src/test/screenshots/intro-600dp.png"
    )

    private fun verifyFrame(snapshot: String) {
        var finished = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BlinkBeyondLinkIntro(onFinished = { finished = true })
        }
        // This exact B is the ONLY brand mark throughout the transition.
        // Replacing it with another logo would break this invariant.
        composeRule.onAllNodesWithTag("blink_intro_brand_b", useUnmergedTree = true)
            .assertCountEquals(1)
        // Capture the FIRST frame: no half-second blank black intro.
        composeRule.onRoot().captureRoboImage(filePath = snapshot.removeSuffix(".png") + "-start.png")
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onAllNodesWithTag("blink_intro_brand_b", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onRoot().captureRoboImage(filePath = snapshot.removeSuffix(".png") + "-transition.png")
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onAllNodesWithTag("blink_intro_brand_b", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onRoot().captureRoboImage(filePath = snapshot)
        composeRule.mainClock.advanceTimeBy(350)
        composeRule.runOnIdle { assertTrue("Launch must continue after animation", finished) }
        assertEquals(3_200, BLINK_INTRO_DURATION_MS)
    }
}
