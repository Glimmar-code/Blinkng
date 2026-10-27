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
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot

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

    @Test
    fun platformSplashIconKeepsWholeWordmarkInAndroidTwelveSafeCircle() {
        // Android 12+ masks the platform icon to the central 192dp circle.
        // An off-centre 192x72 vector clipped the leading dots and final K:
        // on the actual device it read "beyond lin" BEFORE Compose started.
        val file = listOf(
            File("src/main/res/drawable/blink_intro_wordmark.xml"),
            File("app/src/main/res/drawable/blink_intro_wordmark.xml"),
        ).firstOrNull { it.isFile } ?: error("BLINK launch icon XML not found")
        val root = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file).documentElement
        assertEquals("288dp", root.getAttribute("android:width"))
        assertEquals("288dp", root.getAttribute("android:height"))
        assertEquals("288", root.getAttribute("android:viewportWidth"))
        assertEquals("288", root.getAttribute("android:viewportHeight"))

        val safeGroup = root.getElementsByTagName("group").item(0) as Element
        val sx = safeGroup.getAttribute("android:scaleX").toFloat()
        val sy = safeGroup.getAttribute("android:scaleY").toFloat()
        val tx = safeGroup.getAttribute("android:translateX").toFloat()
        val ty = safeGroup.getAttribute("android:translateY").toFloat()
        // Conservative corners of the complete vector (dots, B, eyond, LINK).
        for (x in listOf(12f, 234f)) {
            for (y in listOf(19f, 65f)) {
                val distance = hypot(tx + x * sx - 144f, ty + y * sy - 144f)
                assertTrue("Full Beyond LINK must fit within platform splash circle", distance < 96f)
            }
        }
    }

    private fun verifyFrame(snapshot: String) {
        var finished = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BlinkBeyondLinkIntro(onFinished = { finished = true }, matchPlatformSplash = true)
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
