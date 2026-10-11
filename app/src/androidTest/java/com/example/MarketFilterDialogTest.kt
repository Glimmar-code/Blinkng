package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.MarketScreen
import com.example.ui.theme.BlinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the real dialog window and keyboard; the JVM dialog did not become idle. */
@RunWith(AndroidJUnit4::class)
class MarketFilterDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun invalidPriceRangeBlocksApplyUntilCorrected() {
        compose.setContent {
            BlinkTheme {
                MarketScreen(items = emptyList(), isSellerActive = true, onItemClick = {},
                    onOpenPostItem = {}, onOpenBecomeSeller = {}, isDark = true)
            }
        }
        compose.onNodeWithText("Filters").performScrollTo().performClick()
        compose.onNodeWithText("Min ₦").performTextInput("200")
        compose.onNodeWithText("Max ₦").performTextInput("100")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Max ₦").performTextReplacement("300")
        compose.onNodeWithText("Apply").assertIsEnabled().performClick()
        compose.onNodeWithText("Filters (2)").assertExists()
    }
}
