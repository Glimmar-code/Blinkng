package com.example

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.BlinkContestHeroCard
import com.example.ui.screens.BlinkContestApplicantMediaCard
import com.example.ui.screens.BlinkContestReviewHistoryDialog
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
        capture("contest-hero-podium-winners-staging.png")
    }

    @Test fun adminApplicantMediaCardShowsThumbnailAndOpensPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A genuine PNG file lets Coil exercise the same file decoding path that
        // real, selected photos use; XML launcher icons are not reliable fixtures.
        val fixture = java.io.File(context.cacheDir, "contest-visual-fixture.png")
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(20, 93, 192))
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(245, 199, 89)
        }
        canvas.drawCircle(80f, 80f, 46f, paint)
        fixture.outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
        val imageUri = android.net.Uri.fromFile(fixture).toString()
        var opened = false
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    BlinkContestApplicantMediaCard(imageUri, onPreview = { opened = true })
                }
            }
        }
        rule.onNodeWithText("View application photo").assertExists()
        rule.waitUntil(12_000) {
            rule.onAllNodesWithContentDescription("Loaded application photo")
                .fetchSemanticsNodes().isNotEmpty()
        }
        capture("contest-applicant-card-staging.png")
        rule.onNodeWithText("View application photo").performClick()
        rule.runOnIdle { org.junit.Assert.assertTrue(opened) }
    }

    @Test fun contestModerationHistoryShowsAuditedReasons() {
        val timeline = listOf(
            JSONObject().put("previous_status", "rejected")
                .put("next_status", "approved")
                .put("reason", "Appeal accepted after verification of original entry")
                .put("actor_username", "contestadmin")
                .put("created_at", "2026-10-31T07:15:00+00:00"),
            JSONObject().put("previous_status", "pending")
                .put("next_status", "rejected")
                .put("reason", "Image did not meet the published competition rules")
                .put("actor_username", "contestadmin")
                .put("created_at", "2026-10-30T15:45:00+00:00")
        )
        var dismissed = false
        rule.setContent {
            BlinkTheme(darkTheme = true) {
                BlinkContestReviewHistoryDialog(
                    username = "samplecreator", history = timeline,
                    total = 2, busy = false, error = null,
                    onDismiss = { dismissed = true }, onRetry = {}
                )
            }
        }
        rule.onNodeWithText("Review history · @samplecreator").assertExists()
        rule.onNodeWithText("Appeal accepted after verification of original entry").assertExists()
        rule.onNodeWithText("Image did not meet the published competition rules").assertExists()
        capture("contest-review-history-staging.png", dialog = true)
        rule.onNodeWithText("Close").performClick()
        rule.runOnIdle { org.junit.Assert.assertTrue(dismissed) }
    }

    private fun containsRenderedContent(bitmap: Bitmap): Boolean {
        // Subsample the full screenshot to detect a false-positive all-black
        // artifact without relying on any fixture's exact text or colors.
        val xStep = (bitmap.width / 48).coerceAtLeast(1)
        val yStep = (bitmap.height / 48).coerceAtLeast(1)
        var visible = 0
        for (y in 0 until bitmap.height step yStep) {
            for (x in 0 until bitmap.width step xStep) {
                val color = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(color) > 25 ||
                    android.graphics.Color.green(color) > 25 ||
                    android.graphics.Color.blue(color) > 25) {
                    if (++visible >= 6) return true
                }
            }
        }
        return false
    }

    private fun capture(name: String, dialog: Boolean = false) {
        rule.waitForIdle()
        // A Compose semantics root can capture as *pure black* for a separate
        // Android Dialog window while the UI assertion still passes. Use a real
        // compositor screenshot for dialogs and reject blank captures.
        val image = if (dialog) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            var visible: Bitmap? = null
            for (attempt in 1..3) {
                val frame = instrumentation.uiAutomation.takeScreenshot()
                if (frame != null && containsRenderedContent(frame)) {
                    visible = frame
                    break
                }
                frame?.recycle()
                if (attempt < 3) android.os.SystemClock.sleep(250)
            }
            checkNotNull(visible) { "Dialog visual audit captured no rendered pixels: $name" }
        } else {
            rule.onRoot().captureToImage().asAndroidBitmap()
        }
        check(containsRenderedContent(image)) {
            "Visual audit must not accept a blank screen: $name"
        }
        val resolver = InstrumentationRegistry.getInstrumentation()
            .targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Audit")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri).use { stream ->
            checkNotNull(stream)
            check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        check(resolver.update(uri, values, null, null) == 1)
    }
}
