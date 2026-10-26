package com.example.data.local

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk=[34])
class BlinkStickerLibraryTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun stickerSurvivesReloadAndNeverCrossesAccountFolders() = runBlocking {
        val own = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val src = File.createTempFile("sticker-input-", ".png", ctx.cacheDir)
        val bitmap = Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        try {
            src.outputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
            }
            val sticker = BlinkStickerLibrary.create(ctx,own,Uri.fromFile(src))
            try {
                assertTrue(sticker.isFile)
                assertEquals(1,BlinkStickerLibrary.saved(ctx,own).count { it == sticker })
                assertTrue(BlinkStickerLibrary.saved(ctx,other).isEmpty())
                assertTrue(!BlinkStickerLibrary.remove(ctx,other,sticker))
                assertTrue(sticker.exists())
                assertTrue(BlinkStickerLibrary.remove(ctx,own,sticker))
                assertTrue(BlinkStickerLibrary.saved(ctx,own).isEmpty())
            } finally { sticker.delete() }
        } finally {bitmap.recycle();src.delete()}
    }

    @Test fun invalidAccountFolderAndUnsupportedFormatAreRejected() = runBlocking {
        val fake = File.createTempFile("unsupported-sticker-", ".gif", ctx.cacheDir)
        try {
            fake.writeText("not an image")
            assertTrue(runCatching {
                BlinkStickerLibrary.create(ctx,UUID.randomUUID().toString(),Uri.fromFile(fake))
            }.isFailure)
            assertTrue(runCatching {
                BlinkStickerLibrary.saved(ctx,"../../other")
            }.getOrDefault(emptyList()).isEmpty())
        } finally {fake.delete()}
    }
}
