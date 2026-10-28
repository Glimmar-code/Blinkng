package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.BlinkContestHeroCard
import com.example.ui.screens.BlinkContestPodium
import com.example.ui.screens.BlinkContestWinnerCard
import com.example.ui.theme.BlinkTheme
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated demo fixture for visual parity; fake data never reaches production. */
@RunWith(AndroidJUnit4::class)
class ContestReferenceVisualAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contestHeroPodiumAndWinnersAreVisible() {
        val contest = JSONObject()
            .put("id", "11111111-1111-4111-8111-111111111111")
            .put("title", "BLINK Campus Showcase")
            .put("description", "Vote for your campus favourites.")
            .put("prize_first", 30000).put("prize_second", 15000)
            .put("prize_third", 5000).put("contestants", 43)
            .put("voting_closes_at", "2026-12-01T10:00:00+01:00")
            .put("status", "published")
        val leaders = (1..3).map { index -> JSONObject()
            .put("rank", index).put("username", "example" + index)
            .put("votes", 200 - index * 20)
        }
        val winner = JSONObject().put("place", 1)
            .put("username", "example1")
            .put("prize_amount", 30000)
            .put("payment_status", "pending")
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                Column(Modifier.fillMaxWidth().height(1000.dp)) {
                    BlinkContestHeroCard(contest, onOpen = {})
                    Spacer(Modifier.height(8.dp))
                    BlinkContestPodium(leaders, onView = {})
                    Spacer(Modifier.height(8.dp))
                    BlinkContestWinnerCard(winner)
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("BLINK Campus Showcase").assertExists()
        rule.onNodeWithText("Open contest →").assertExists()
        rule.onNodeWithText("Leaderboard leaders").assertExists()
        rule.onNodeWithText("Prize ₦30,000").assertExists()
    }
}
