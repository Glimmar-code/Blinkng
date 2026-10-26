package com.example

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.BlinkElasticSwipeDirection
import com.example.ui.components.blinkElasticSwipeAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gesture regression: incomplete drags bounce; committed swipes fire exactly once. */
@RunWith(AndroidJUnit4::class)
class BlinkElasticSwipeInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun incompleteSlowSwipeDoesNotNavigate() {
        rule.setContent {
            var navigations by remember { mutableIntStateOf(0) }
            Box(Modifier.size(320.dp).blinkElasticSwipeAction(
                direction = BlinkElasticSwipeDirection.RIGHT,
                onAction = { navigations++ }
            ).testTag("surface")) {
                Text("$navigations", modifier = Modifier.testTag("count"))
            }
        }
        rule.onNodeWithTag("surface").performTouchInput {
            down(center)
            moveBy(Offset(18f, 0f), delayMillis = 280L)
            up()
        }
        rule.onNodeWithTag("count").assertTextEquals("0")
    }

    @Test fun deliberateLongSwipeNavigatesOnlyOnce() {
        rule.setContent {
            var navigations by remember { mutableIntStateOf(0) }
            Box(Modifier.size(320.dp).blinkElasticSwipeAction(
                direction = BlinkElasticSwipeDirection.RIGHT,
                onAction = { navigations++ }
            ).testTag("surface")) {
                Text("$navigations", modifier = Modifier.testTag("count"))
            }
        }
        rule.onNodeWithTag("surface").performTouchInput {
            down(center)
            moveBy(Offset(center.x * .84f, 0f), delayMillis = 120L)
            up()
        }
        rule.onNodeWithTag("count").assertTextEquals("1")
    }

    @Test fun horizontalCarouselOwnsConsumedSwipe() {
        rule.setContent {
            var navigations by remember { mutableIntStateOf(0) }
            Box(Modifier.size(320.dp).blinkElasticSwipeAction(
                direction = BlinkElasticSwipeDirection.RIGHT,
                onAction = { navigations++ }
            ).testTag("surface")) {
                Box(Modifier.size(280.dp).pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> change.consume() }
                }.testTag("child"))
                Text("$navigations", modifier = Modifier.testTag("count"))
            }
        }
        rule.onNodeWithTag("child").performTouchInput {
            down(center)
            moveBy(Offset(center.x * .84f, 0f), delayMillis = 120L)
            up()
        }
        rule.onNodeWithTag("count").assertTextEquals("0")
    }
}
