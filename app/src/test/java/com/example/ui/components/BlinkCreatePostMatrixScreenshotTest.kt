package com.example.ui.components

import android.app.Application
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the existing device fixtures locally; never a device parity certificate. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkCreatePostMatrixScreenshotTest : BlinkCreatePostVisualAuditFixture() {
    override fun capture(name: String, tag: String) {
        rule.waitForIdle()
        rule.onNodeWithTag(tag).captureRoboImage("build/outputs/reference-design/matrix/$name")
    }
}
