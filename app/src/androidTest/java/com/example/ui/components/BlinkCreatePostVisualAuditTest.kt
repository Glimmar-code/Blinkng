package com.example.ui.components

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import com.example.ui.screens.BlinkUniversalPhotoEditor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.models.PostDraft
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device captures of the same fixtures rendered by the local visual suite. */
@RunWith(AndroidJUnit4::class)
class BlinkCreatePostVisualAuditTest : BlinkCreatePostVisualAuditFixture() {
    override fun capture(name: String, tag: String) {
        rule.waitForIdle()
        val image = rule.onNodeWithTag(tag)
            .captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BLINK-Visual-Audit")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
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
