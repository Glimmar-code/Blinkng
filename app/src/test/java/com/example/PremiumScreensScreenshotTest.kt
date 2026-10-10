package com.example

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.ui.screens.PremiumFeedScreen
import com.example.ui.screens.ProfileScreen
import com.example.data.models.UserProfile
import com.example.data.models.BlinkStoreCatalog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.screens.MarketScreen
import com.example.ui.screens.PremiumMessagesScreen
import com.example.ui.theme.BlinkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real screens, including a narrow display and enlarged accessibility text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp", sdk = [35])
class PremiumScreensScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun market(dark: Boolean, name: String, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BlinkTheme(darkTheme = dark) {
                    Surface {
                        MarketScreen(items = emptyList(), isSellerActive = true, onItemClick = {},
                            onOpenPostItem = {}, onOpenBecomeSeller = {}, isDark = dark, hasMore = true)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/ui-audit/$name.png")
    }

    @Test fun marketDark() = market(true, "market-dark")
    @Test fun marketLight() = market(false, "market-light")
    @Test fun marketLargeText() = market(false, "market-large-text", 1.5f)

    @Test fun feedLight() {
        compose.setContent {
            BlinkTheme(darkTheme = false) {
                PremiumFeedScreen(posts = emptyList(), reels = emptyList(), stories = emptyList(),
                    profiles = emptyList(), leaderboardUsers = emptyList(), currentUsername = "",
                    userAvatar = "", currentSubTab = 0, onSubTabChanged = {}, isDark = false,
                    onLikePost = {}, onCommentPost = {}, onBookmarkPost = {}, onRepostPost = {},
                    onSharePost = {}, onOptionsClick = {}, onProfileClick = {}, onAddStoryClick = {},
                    onStoryClick = {}, onOpenCreatePost = {}, onOpenActivity = {}, onOpenMenu = {},
                    onToggleTheme = {}, isLoading = true)
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/ui-audit/feed-light-loading.png")
    }

    @Test fun profileDark() {
        compose.setContent {
            BlinkTheme {
                ProfileScreen(profile = UserProfile(), isMe = true, userPosts = emptyList(),
                    likedPosts = emptyList(), savedPosts = emptyList(), userMarketItems = emptyList(),
                    onBack = {}, onEditProfileClick = {}, onDirectMessage = {}, onEndorseSkill = {},
                    onLikePost = {}, onCommentPost = {}, onBookmarkPost = {}, onSharePost = {},
                    onOptionsClick = {}, onProfileClick = {}, onMarketItemClick = {}, isDark = true)
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/ui-audit/profile-dark.png")
    }

    @Test fun storeCard() {
        compose.setContent {
            BlinkTheme {
                Surface {
                    StoreItemCard(item = BlinkStoreCatalog.items.first(), owned = false,
                        active = false, equipped = false, vipLocked = false, vipActive = false,
                        onPreview = {}, onBuy = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/ui-audit/store-card.png")
    }

    @Test fun messageInbox() {
        compose.setContent {
            BlinkTheme {
                PremiumMessagesScreen(conversations = emptyList(), stories = emptyList(),
                    activities = emptyList(), myAvatar = "", myName = "BLINK", activePartner = null,
                    onOpenConversation = {}, onCloseConversation = {}, onSendMessage = { _, _, _ -> },
                    onProfileClick = {}, onStoryClick = {}, onAddStoryClick = {}, onOpenActivity = {},
                    isDark = true)
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/ui-audit/messages-dark.png")
    }
}
