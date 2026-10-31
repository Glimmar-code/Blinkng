package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BlinkTheme
import com.example.data.supabase.BlinkDropDiscovery
import com.example.ui.components.BlinkDropDiscoveryCard
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.NumberFormat
import java.util.Locale

/** Isolated, real Compose captures of BLINK Giveaway and growth cards used in the app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkGrowthVisualCaptureTest {
    @get:Rule val rule = createComposeRule()
    private val formatter = NumberFormat.getIntegerInstance(Locale.US)
    private fun scene(content: @Composable () -> Unit) {
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { content() }
            }
        }
    }

    @Test fun mostGiversWithCommittedGiveawayTotals() {
        scene {
            TopGiverCard(TopGiver("u1","first","First Creator",1200,3,20,false),
                rank = 1, formatter = formatter, following = false, followBusy = false, onFollow = {})
            TopGiverCard(TopGiver("u2","second","Second Creator",760,2,12,true),
                rank = 2, formatter = formatter, following = true, followBusy = false, onFollow = {})
            TopGiverCard(TopGiver("u3","third","Third Creator",300,1,8,false),
                rank = 3, formatter = formatter, following = false, followBusy = false, onFollow = {})
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/giveaway-most-givers-podium.png")
    }

    @Test fun mostGiversGenuineEmptyState() {
        scene { TopGiversEmptyState(onBrowse = {}, onCreate = {}) }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/giveaway-most-givers-empty.png")
    }

    @Test fun giveawayFeedLiveDiscoveryCard() {
        val item = BlinkDropDiscovery(
            dropId = "giveaway-live",
            creatorId = "creator-1",
            creatorUsername = "glimmar",
            creatorName = "Glimmar",
            creatorAvatar = "",
            totalCoins = 10000,
            rewardPerUser = 500,
            winnerCount = 20,
            claimedCount = 5,
            status = "ACTIVE",
            action = "LIKE",
            createdAt = "2026-10-31T18:00:00Z",
            endsAt = "2026-10-31T20:00:00Z",
        )
        scene {
            BlinkDropDiscoveryCard(item, onFollow = {}, onOpenDrops = {}, following = false)
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/giveaway-feed-live.png")
    }

    @Test fun giveawayFeedMissedDiscoveryCard() {
        val item = BlinkDropDiscovery(
            dropId = "giveaway-ended",
            creatorId = "creator-2",
            creatorUsername = "campusstar",
            creatorName = "Campus Star",
            creatorAvatar = "",
            totalCoins = 5000,
            rewardPerUser = 500,
            winnerCount = 10,
            claimedCount = 10,
            status = "COMPLETED",
            action = "LIKE",
            createdAt = "2026-10-30T18:00:00Z",
            endsAt = "2026-10-30T20:00:00Z",
        )
        scene {
            BlinkDropDiscoveryCard(item, onFollow = {}, onOpenDrops = {}, following = false)
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/giveaway-feed-missed.png")
    }

    @Test fun boostQuoteAndAnalytics() {
        val quote = JSONObject().apply {
            put("coin_cost", 500); put("estimated_reach_low", 800)
            put("estimated_reach_high", 1300); put("estimated_daily_reach_low", 250)
            put("estimated_daily_reach_high", 420); put("audience_user_count", 12000)
            put("remaining_balance_after_reserve", 250)
        }
        val analytics = JSONObject().apply {
            put("campaigns", 3); put("spent", 1100); put("refunded", 50)
            put("impressions", 2040); put("opens", 180); put("conversion_rate", 8.8)
        }
        scene {
            CampaignQuoteCard(quote, quoteLoading = false, balance = 750, formatter = formatter)
            BoostAnalyticsCard(analytics, formatter)
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/growth-reference-boost-quote-analytics.png")
    }

    @Test fun boostActiveCampaignProgress() {
        val campaign = JSONObject().apply {
            put("target_type", "REEL"); put("boost_power", 30)
            put("coin_budget", 1200); put("coin_spent", 480)
            put("coin_refunded", 0); put("promoted_impressions", 1065)
            put("promoted_opens", 94); put("ends_at", "2026-11-02T12:00:00Z")
        }
        scene { ActiveCampaignCard(campaign, formatter, working = false, onCancel = {}) }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/growth-reference-boost-active.png")
    }
}
