package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test complements the five-tab and Reel gesture tests: rapid state changes
 * must settle on the final state, not leave phantom outgoing views visible.
 */
@RunWith(AndroidJUnit4::class)
class BlinkFadeThroughInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rapidStateChangesKeepTheLatestContent() {
        val selected = mutableStateOf("Loading")
        rule.setContent {
            MaterialTheme {
                BlinkFadeThrough(targetState = selected.value, label = "motionRegression") {
                    Text(it)
                }
            }
        }
        rule.runOnIdle { selected.value = "Ready" }
        rule.runOnIdle { selected.value = "Final" }
        rule.waitForIdle()
        rule.onNodeWithText("Final").assertExists()
        rule.onNodeWithText("Loading").assertDoesNotExist()
        rule.onNodeWithText("Ready").assertDoesNotExist()
    }
}
