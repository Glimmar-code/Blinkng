package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.models.ActivityItem
import com.example.ui.screens.ActivityScreen
import com.example.ui.theme.BlinkTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Test the actual notification screen at the font scales shown in the user report.
 * Fake in-memory notifications are disconnected from Supabase and personal data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkNotificationAdaptiveVisualTest {
    @get:Rule val rule = createComposeRule()

    @Test fun notification320dpAtNormalFont() = render(320,1f)
    @Test fun notification320dpAt130Percent() = render(320,1.3f)
    @Test fun notification320dpAt150Percent() = render(320,1.5f)
    @Test fun notification320dpAt200Percent() = render(320,2f)
    @Test fun notification412dpAt200Percent() = render(412,2f)

    private fun render(widthDp: Int,scale: Float) {
        val rows = listOf(
            ActivityItem(
                id="test-follow",
                user="",avatar="",action="started following you",
                time="1 hour ago",isUnread=true,previewText="null"
            ),
            ActivityItem(
                id="test-like",
                user="",avatar="",action="null",
                time="5 days ago",isUnread=true,previewText=null
            )
        )
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
                BlinkTheme(darkTheme = true) {
                    Box(Modifier.width(widthDp.dp).height(820.dp).testTag("notification_visual_frame")) {
                        ActivityScreen(
                            activities = rows,
                            accountId = "",
                            onBack = {},
                            onProfileClick = {},
                            onNotificationClick = {},
                            isDark = true,
                            // Deliberately offline: no profile lookups or network mutations.
                            isConnected = false
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("Notifications").assertExists()
        rule.onNodeWithContentDescription("More notification actions").assertExists()
        rule.onNodeWithText("New activity").assertExists()
        val scaleName=scale.toString().replace('.', '_')
        rule.onNodeWithTag("notification_visual_frame").captureRoboImage(
            filePath="build/test-visuals/notifications_w${widthDp}_scale${scaleName}.png"
        )
    }
}
