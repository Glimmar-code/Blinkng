package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.blinkHorizontalPageSwipe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Android gesture smoke: normal vertical feed scrolling must not switch pages. */
@RunWith(AndroidJUnit4::class)
class BlinkHorizontalPageSwipeInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun leftRightPagesAndVerticalScrollingRemainsUnaffected() {
        rule.setContent {
            var left by remember { mutableIntStateOf(0) }
            var right by remember { mutableIntStateOf(0) }
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .testTag("swipe_surface")
                    .blinkHorizontalPageSwipe(
                        enabled = true,
                        onSwipeLeft = { left += 1 },
                        onSwipeRight = { right += 1 },
                    )
            ) {
                Text("${left}/${right}", modifier = Modifier.testTag("swipe_result"))
            }
        }
        rule.onNodeWithTag("swipe_surface").performTouchInput { swipeUp() }
        rule.onNodeWithTag("swipe_result").assertTextEquals("0/0")
        rule.onNodeWithTag("swipe_surface").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("swipe_result").assertTextEquals("1/0")
        rule.onNodeWithTag("swipe_surface").performTouchInput { swipeRight() }
        rule.onNodeWithTag("swipe_result").assertTextEquals("1/1")
    }
}
