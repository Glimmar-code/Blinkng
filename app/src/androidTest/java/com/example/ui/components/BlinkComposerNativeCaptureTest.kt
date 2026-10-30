package com.example.ui.components

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.models.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Full compositor captures include the real IME and Media3 SurfaceView, which
 * node-only Compose captures cannot establish. No account, network or publication. */
@RunWith(AndroidJUnit4::class)
class BlinkComposerNativeCaptureTest {
    @get:Rule val rule = createComposeRule()

    @Test @SdkSuppress(minSdkVersion = 33)
    fun permittedDeviceLibrarySupportsRealPhotoSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // This suite runs on a disposable emulator/test APK, never a user's library.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.READ_MEDIA_IMAGES)
        val resolver = context.contentResolver
        val fixtures = (0..2).map { index ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "blink-library-fixture-$index.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Fixtures")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(40 + index * 50, 90, 140))
            try { resolver.openOutputStream(uri).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, checkNotNull(it))) } }
            finally { bitmap.recycle() }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri.toString()
        }
        try {
            var selection by mutableStateOf(listOf(fixtures[0]))
            rule.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    BlinkReferenceMediaReview(images = selection, onBack = {}, onRemove = {}, onMove = { _, _ -> },
                        onEdit = {}, onAddMore = {}, onPickVideos = {}, onNext = {},
                        onSelectPhoto = { selection = selection + it })
                }
            }
            val tag = "blink_gallery_available_" + fixtures[1]
            rule.waitUntil(12_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag(tag).performClick()
            rule.runOnIdle { check(selection == listOf(fixtures[0], fixtures[1])) }
            captureDisplay("create-post-gallery-library-staging.png")
        } finally { fixtures.forEach { resolver.delete(android.net.Uri.parse(it), null, null) } }
    }

    @Test @SdkSuppress(minSdkVersion = 30)
    fun typingMentionsWithActualKeyboardVisible() {
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CreatePostSheet(
                    profile = UserProfile(id = "native-visual-composer", fullName = "BLINK Test Student",
                        username = "blink_visual_test", university = "FUTA"),
                    onDismiss = {},
                    onSubmitPost = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    isDark = true,
                    trendingTagsLoader = { _, _ -> listOf("FUTA", "Campus") },
                    searchMentions = { _, _, _, _ -> listOf(UserProfile(id = "native-audit-ayo", fullName = "Ayo", username = "ayo_futa")) }
                )
            }
        }
        rule.onNodeWithTag("blink_create_choice_text").performClick()
        rule.waitForIdle()
        try {
            rule.waitUntil(15_000) {
                var focused = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    focused = WindowInspector.getGlobalWindowViews().any {
                        it.hasWindowFocus() && (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION
                    }
                }
                focused
            }
        } catch (error: ComposeTimeoutException) {
            runCatching { captureDisplay("create-post-keyboard-failure-staging.png") }
            throw AssertionError("Composer dialog did not receive native window focus", error)
        }
        val field = rule.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick()
        try {
            rule.waitUntil(15_000) {
                var visible = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    visible = WindowInspector.getGlobalWindowViews().any {
                        it.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
                    }
                }
                visible
            }
        } catch (error: ComposeTimeoutException) {
            runCatching { captureDisplay("create-post-keyboard-failure-staging.png") }
            throw AssertionError("Actual app window does not report a visible IME", error)
        }
        field.performTextInput("@a")
        try {
            rule.waitUntil(15_000) { rule.onAllNodesWithText("Ayo").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            runCatching { captureDisplay("create-post-keyboard-failure-staging.png") }
            throw AssertionError("Mention results were not loaded", error)
        }
        captureDisplay("create-post-keyboard-mentions-staging.png")
        rule.runOnIdle {
            check(WindowInspector.getGlobalWindowViews().any { it.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }) {
                "Keyboard was hidden after typing the mention"
            }
        }
        rule.onNodeWithText("Ayo").assertIsDisplayed()
    }

    @Test fun actualVideoPreviewAndTrimTimelineAreCaptured() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // MPEG-4 Part 2, one second of blue video at 160x120/15fps; generated by
        // DesktopMediaSmoke's lavfi fixture command. Included only in the test APK.
        val source = File(instrumentation.targetContext.cacheDir, "blink-visual-synthetic.mp4")
        instrumentation.context.assets.open("blink-visual-synthetic.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
                    PremiumReelEditor(uri = source.toURI().toString(), draftScope = "native_visual_only",
                        onRemove = {}, onEdited = {}, onExporting = {}, onPendingEdits = {})
                }
            }
        }
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("/ 00:01", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        captureDisplay("create-post-video-preview-staging.png")
        rule.onNodeWithText("Trim video").performScrollTo()
        rule.onNodeWithContentDescription("Play or pause reel").performClick()
        captureDisplay("create-post-video-timeline-staging.png")
    }

    private fun captureDisplay(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Actual display capture failed" }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Audit")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = instrumentation.targetContext.contentResolver
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            resolver.openOutputStream(uri).use { check(image.compress(Bitmap.CompressFormat.PNG, 100, checkNotNull(it))) }
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        } finally { image.recycle() }
    }
}
