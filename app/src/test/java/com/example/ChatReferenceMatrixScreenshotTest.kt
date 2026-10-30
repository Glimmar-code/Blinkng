package com.example

import android.app.Application
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The exact device fixtures, rendered locally for review; no real accounts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class ChatReferenceMatrixScreenshotTest : ChatReferenceVisualAuditFixture() {
    override fun capture(name: String) {
        rule.waitForIdle()
        rule.onNodeWithTag("chat_conversation_root").captureRoboImage("build/outputs/reference-design/chat/$name")
    }
}
