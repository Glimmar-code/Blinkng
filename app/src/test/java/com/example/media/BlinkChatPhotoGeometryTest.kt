package com.example.media

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BlinkChatPhotoGeometryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun precisionCropProducesNewSquareWithoutChangingOriginal() = runBlocking {
        val input = File.createTempFile("blink-geometry-test-", ".jpg", context.cacheDir)
        val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        try {
            input.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG,90,it) }
        } finally { bmp.recycle() }
        val old = input.readBytes()
        val output = BlinkChatPhotoGeometry.edit(
            context, Uri.fromFile(input), 1f, 0f, .8f
        )
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path,opts)
            assertEquals(opts.outWidth, opts.outHeight)
            assertTrue(opts.outWidth in 1..200)
            assertArrayEquals(old,input.readBytes())
        } finally {
            output.path?.let { File(it).delete() }
            input.delete()
        }
    }

    @Test fun invalidGeometryIsRejectedBeforeFileWrite() = runBlocking {
        val source = Uri.parse("file:///this-file-does-not-exist.jpg")
        val result = runCatching {
            BlinkChatPhotoGeometry.edit(context,source,9f,0f,1f)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
