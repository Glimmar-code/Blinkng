package com.example.data.supabase

import android.app.Application
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
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
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

    @Test fun landscapeAndPortraitCropKeepOriginalAndRespectAspectRatio() = runBlocking {
        val source = image(240, 160)
        val originalLength = source.length()
        val wide = BlinkChatPhotoEditor.edit(context, Uri.fromFile(source),
            BlinkChatPhotoEditor.Edit.CENTER_LANDSCAPE)
        val tall = BlinkChatPhotoEditor.edit(context, Uri.fromFile(source),
            BlinkChatPhotoEditor.Edit.CENTER_PORTRAIT)
        try {
            val w = BitmapFactory.Options().apply { inJustDecodeBounds=true }
            val h = BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(wide.path,w)
            BitmapFactory.decodeFile(tall.path,h)
            assertTrue(w.outWidth > w.outHeight)
            assertTrue(h.outHeight > h.outWidth)
            assertEquals(originalLength,source.length())
        } finally {
            File(requireNotNull(wide.path)).delete()
            File(requireNotNull(tall.path)).delete()
            source.delete()
        }
    }

    @Test fun annotationsAreExportedAsNewPhotoWithoutChangingSource() = runBlocking {
        val source = image(180,120)
        val bytesBefore = source.readBytes()
        val result = BlinkChatPhotoEditor.overlay(
            context,Uri.fromFile(source),
            strokes=listOf(BlinkChatPhotoEditor.Stroke(
                listOf(.1f to .1f, .8f to .8f), android.graphics.Color.YELLOW)),
            labels=listOf(BlinkChatPhotoEditor.Label("BLINK",.5f,.5f))
        )
        val out=File(requireNotNull(result.path))
        try {
            assertTrue(out.isFile && out.length()>0)
            assertTrue(out.canonicalPath!=source.canonicalPath)
            assertTrue(source.readBytes().contentEquals(bytesBefore))
        } finally {out.delete();source.delete()}
    }

    @Test fun liveAdjustmentsDoNotMutateOriginal() = runBlocking {
        val source=image(130,100)
        val before=source.readBytes()
        val result=BlinkChatPhotoEditor.adjust(
            context,Uri.fromFile(source),.2f,1.2f,.9f
        )
        val out=File(requireNotNull(result.path))
        try {
            assertTrue(out.exists() && out.length()>0)
            assertTrue(source.readBytes().contentEquals(before))
        } finally {out.delete();source.delete()}
    }

}
