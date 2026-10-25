package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class BlinkChatPhotoEditorTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun image(width: Int, height: Int): File {
        val file = File.createTempFile("blink-photo-test-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(file).use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
            }
        } finally { bitmap.recycle() }
        return file
    }

    @Test fun centerCropProducesSquareWithoutOverwritingOriginal() = runBlocking {
        val original = image(120, 80)
        val before = original.length()
        val result = BlinkChatPhotoEditor.edit(
            context, Uri.fromFile(original), BlinkChatPhotoEditor.Edit.CENTER_SQUARE
        )
        val edited = File(requireNotNull(result.path))
        try {
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(edited.path, dimensions)
            assertEquals(80, dimensions.outWidth)
            assertEquals(80, dimensions.outHeight)
            assertEquals(before, original.length())
        } finally { edited.delete(); original.delete() }
    }

    @Test fun rotateRightSwapsDimensions() = runBlocking {
        val original = image(72, 120)
        val output = BlinkChatPhotoEditor.edit(
            context, Uri.fromFile(original), BlinkChatPhotoEditor.Edit.ROTATE_RIGHT
        )
        val edited = File(requireNotNull(output.path))
        try {
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(edited.path, dimensions)
            assertEquals(120, dimensions.outWidth)
            assertEquals(72, dimensions.outHeight)
            assertTrue(original.exists())
        } finally { edited.delete(); original.delete() }
    }
}
