package com.example

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.MarketScreen
import com.example.ui.theme.BlinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp", sdk = [35])
class MarketUiRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyLoadedPageStillOffersPagination() {
        var loads = 0
        compose.setContent {
            BlinkTheme {
                Surface {
                    MarketScreen(items = emptyList(), isSellerActive = true, onItemClick = {},
                        onOpenPostItem = {}, onOpenBecomeSeller = {}, isDark = true,
                        hasMore = true, onLoadMore = { loads++ })
                }
            }
        }
        compose.onNodeWithText("Load more listings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, loads) }
    }

}
