package com.example.data.supabase

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.blinkng.shared.BlinkChatAttachmentKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InterruptedIOException

/**
 * Pure local failure-path tests. Never contact Supabase or send user media.
 * Two-account live delivery/RLS checks belong in isolated integration testing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlinkChatPrivateMediaSafetyTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun cancelledPhotoUploadAbortsBeforeCheckingSessionOrSending() = runBlocking {
        val result = MessageMediaService.sendAttachment(
            context = context,
            receiverUsername = "testing-only",
            uri = Uri.parse("content://never-opened/cancelled"),
            kind = BlinkChatAttachmentKind.IMAGE,
            shouldCancel = { true }
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InterruptedIOException)
    }

    @Test
    fun emptyConversationPageNeedsNoPrivateMediaLookups() = runBlocking {
        val result = MessageMediaService.hydrateViewOnceFlags(emptyList(), "")
        assertTrue(result.isEmpty())
    }
}
