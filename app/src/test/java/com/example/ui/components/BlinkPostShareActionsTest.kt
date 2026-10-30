package com.example.ui.components

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BlinkPostShareActionsTest {
    private val photoA = "content://com.android.providers.media.documents/document/image%3A123"
    private val photoB = "content://com.android.providers.media.documents/document/image%3A124"

    @Test fun captionUsesTextThenPollQuestion() {
        assertEquals("Campus update", BlinkPostShareActions.caption(" Campus update ", "Question"))
        assertEquals("Vote now", BlinkPostShareActions.caption(" ", " Vote now "))
        assertEquals("", BlinkPostShareActions.caption("", " "))
    }

    @Test fun singlePhotoSharesContentUriWithGrantAndCaption() {
        val share = BlinkPostShareActions.createIntent("Hello", listOf(photoA), null, "com.whatsapp")
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("image/*", share.type)
        assertEquals("com.whatsapp", share.`package`)
        assertEquals("Hello", share.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(Uri.parse(photoA), share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(1, share.clipData?.itemCount)
        assertEquals(Uri.parse(photoA), share.clipData?.getItemAt(0)?.uri)
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun photoAlbumUsesMultipleStreamAndGrantsEveryUri() {
        val share = BlinkPostShareActions.createIntent("Album", listOf(photoA, photoB, photoA), null)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, share.action)
        assertEquals("image/*", share.type)
        assertEquals(2, share.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.size)
        assertEquals(2, share.clipData?.itemCount)
        assertEquals(Uri.parse(photoB), share.clipData?.getItemAt(1)?.uri)
    }

    @Test fun reelSharesOnlyVideoUri() {
        val reel = "content://com.example.blinkng.chatfiles/reel_edits/reel_123.mp4"
        val share = BlinkPostShareActions.createIntent("Reel", listOf(photoA), reel)
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("video/*", share.type)
        assertEquals(Uri.parse(reel), share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(1, share.clipData?.itemCount)
    }

    @Test fun textOnlyAndUnshareablePrivateFilesDoNotForgeMediaGrants() {
        val textOnly = BlinkPostShareActions.createIntent("Campus", emptyList(), null)
        assertEquals("text/plain", textOnly.type)
        assertFalse(textOnly.hasExtra(Intent.EXTRA_STREAM))
        assertEquals(0, textOnly.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertFalse(BlinkPostShareActions.hasShareableContent("", listOf("file:///private/draft.jpg"), null))
        assertFalse(BlinkPostShareActions.hasShareableContent("", listOf("https://example.com/image.jpg"), null))
        assertTrue(BlinkPostShareActions.hasShareableContent("Text", emptyList(), null))
    }

    @Test fun staleUnshareableMediaDoesNotSilentlyBecomeTextOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(
            BlinkPostShareActions.share(
                context, "Photo post", listOf("file:///private/stale.jpg"), null, false
            )
        )
        assertFalse(
            BlinkPostShareActions.share(
                context, "Video post", emptyList(), "https://example.com/video.mp4", true
            )
        )
    }

    @Test fun copyCaptionWritesExactlyTheSelectedText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(BlinkPostShareActions.copyCaption(context, ""))
        assertTrue(BlinkPostShareActions.copyCaption(context, "My BLINK caption"))
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertNotNull(manager.primaryClip)
        assertEquals("My BLINK caption", manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
    }
}
