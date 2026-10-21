package com.example

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.models.FeedPost
import com.example.data.models.VerificationBadge
import com.example.ui.components.FeedBottomBar
import com.example.ui.components.CreatePostFab
import com.example.ui.components.FeedReferenceStatsRow
import com.example.ui.components.FeedTabs
import com.example.ui.components.FeedTopBar
import com.example.ui.components.PostCard
import com.example.ui.theme.BlinkTheme
import com.example.viewmodel.MainTab
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only layout audit. The sample author, rank and coin totals are intentionally
 * fixtures and NEVER flow into production user data or the shipped APK.
 *
 * Match screenshot composition and spacing with the supplied reference artwork.
 * This does not verify authenticated data, navigation wiring or production content.
 */
@RunWith(AndroidJUnit4::class)
class HomeReferenceVisualAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun captureLightThemeHomeChromeAndPostCard() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(ctx.cacheDir, "reference-only-4-by-3-media.png")
        // Build a non-personal test image with a 4:3 ratio to match the artwork's
        // media bounding box, without embedding or shipping anyone's photograph.
        val bitmapFixture = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapFixture)
        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawColor(AndroidColor.rgb(190, 218, 237))
        paint.color = AndroidColor.rgb(244, 238, 204)
        canvas.drawCircle(325f, 60f, 29f, paint)
        paint.color = AndroidColor.rgb(170, 173, 157)
        canvas.drawRect(0f, 150f, 400f, 300f, paint)
        paint.color = AndroidColor.rgb(125, 146, 109)
        canvas.drawRect(0f, 215f, 400f, 300f, paint)
        paint.color = AndroidColor.rgb(188, 160, 133)
        canvas.drawRect(45f, 75f, 240f, 222f, paint)
        paint.color = AndroidColor.rgb(245, 243, 222)
        for (x in 64..217 step 49) {
            for (y in 100..190 step 46) {
                canvas.drawRect(x.toFloat(), y.toFloat(), x + 25f, y + 27f, paint)
            }
        }
        FileOutputStream(fixture).use { check(bitmapFixture.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val fixtureUri = fixture.toURI().toString()

        val showFab = mutableStateOf(false)
        rule.setContent {
            BlinkTheme(darkTheme = false) {
                val context = LocalContext.current
                // Repository-owned icon, used as a stand-in photograph solely in test screenshots.
                val testImage = "android.resource://${context.packageName}/${R.drawable.app_icon}"
                Box(
                    Modifier.fillMaxWidth().height(752.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("home_reference_audit_root")
                ) {
                  Column(Modifier.fillMaxSize()) {
                    FeedTopBar(
                        userAvatar = testImage,
                        userIsOnline = true,
                        hasUnreadNotifications = true,
                        unreadNotificationCount = 1,
                        reserveStatusBar = false,
                        onSearchClick = {},
                        onItemsClick = {},
                        onNotificationClick = {},
                        onMenuClick = {},
                        onProfileClick = {}
                    )
                    FeedReferenceStatsRow(
                        worldRank = 248,
                        coins = 1250,
                        rankImprovement = 1, // test-only historical movement fixture
                        onRankClick = {},
                        onCoinsClick = {}
                    )
                    FeedTabs(
                        selectedIndex = 0,
                        onForYouClick = {},
                        onFollowingClick = {},
                        onFilterClick = {}
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PostCard(
                            post = FeedPost(
                                id = "visual-audit-only",
                                author = "Aisha Khan",
                                authorAvatar = testImage,
                                timeAgo = "2h",
                                text = "Beautiful day on campus ☀️\nGood people make great days.",
                                images = listOf(fixtureUri),
                                likes = 0,
                                commentsCount = 164,
                                sharesCount = 0,
                                repostsCount = 320,
                                viewsCount = 12400,
                                savesCount = 412,
                                authorUsername = "aisha",
                                authorProfileId = "visual-only"
                            ),
                            isDark = false,
                            onLike = {},
                            onComment = {},
                            onBookmark = {},
                            onRepost = {},
                            onShare = {},
                            onOptionsClick = {},
                            onProfileClick = {},
                            authorName = "Aisha Khan",
                            authorUniversity = "University of Lagos",
                            authorVerificationBadge = VerificationBadge.BLUE,
                            authorProfileId = "visual-only",
                            onFollowAuthor = {},
                            xFeedStyle = true,
                            trackExposure = false
                        )
                    }
                    FeedBottomBar(
                        currentTab = MainTab.HOME,
                        feedSubTab = 0,
                        onHomeClick = {},
                        onReelsClick = {},
                        onMarketClick = {},
                        onConnectClick = {},
                        onMessageClick = {},
                        isDark = false,
                        hasUnreadMessages = true
                    )
                  }
                  if (showFab.value) {
                      CreatePostFab(
                          expanded = false,
                          onClick = {},
                          modifier = Modifier.align(Alignment.BottomEnd)
                              .padding(end = 14.dp, bottom = 74.dp)
                      )
                  }
                }
            }
        }
        rule.waitForIdle()
        // The Media3/Coil image decoder needs an async frame after composition.
        Thread.sleep(900L)
        rule.waitForIdle()
        fun saveShot(filename: String) {
            val bitmap = rule.onNodeWithTag("home_reference_audit_root")
                .captureToImage().asAndroidBitmap()
            // MediaStore survives the test package uninstall.
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Audit")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val imageUri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            resolver.openOutputStream(imageUri).use { stream ->
                checkNotNull(stream)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(imageUri, values, null, null) == 1)
        }
        // Clean reference view for direct visual comparison.
        saveShot("home-reference-staging-light.png")
        // Separate capture proving that the preserved floating Create fits above navigation.
        rule.runOnIdle { showFab.value = true }
        rule.waitForIdle()
        saveShot("home-reference-with-create.png")
    }
}
