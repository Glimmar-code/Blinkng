package com.blinkng.desktop.data

import com.blinkng.shared.BlinkChatImageQuality
import com.blinkng.shared.BlinkChatPhotoAlbum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

/** No network, no real BLINK users, and no Supabase credentials. */
class DesktopChatImageOutboxTest {
    @Test
    fun photoOutboxIsDurableAndStrictlyOwnerScoped() {
        val owner = UUID.randomUUID().toString()
        val stranger = UUID.randomUUID().toString()
        val conversation = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        val original = File.createTempFile("blink-photo-regression-", ".jpg")
        original.writeBytes(ByteArray(2048) { (it % 251).toByte() })
        try {
            val record = DesktopChatImageOutbox.enqueue(
                owner, conversation, messageId, original, "Test caption",
                BlinkChatImageQuality.ORIGINAL, BlinkChatPhotoAlbum.single()
            )
            assertTrue(record.file.isFile)
            assertEquals(messageId, record.messageId)
            assertEquals("Test caption", record.caption)
            assertEquals(1, DesktopChatImageOutbox.pending(owner).size)
            assertTrue(DesktopChatImageOutbox.pending(stranger).isEmpty())

            DesktopChatImageOutbox.progress(owner, messageId, 145)
            assertEquals(100, DesktopChatImageOutbox.pendingProgress(owner))

            assertThrows(IllegalArgumentException::class.java) {
                DesktopChatImageOutbox.enqueue(
                    owner, conversation, messageId, original, "Duplicate",
                    BlinkChatImageQuality.ORIGINAL
                )
            }
            DesktopChatImageOutbox.complete(record)
            assertFalse(record.file.exists())
            assertTrue(DesktopChatImageOutbox.pending(owner).isEmpty())
            assertEquals(null, DesktopChatImageOutbox.pendingProgress(owner))
        } finally {
            original.delete()
            DesktopChatImageOutbox.discardOwner(owner)
            DesktopChatImageOutbox.discardOwner(stranger)
        }
    }

    @Test
    fun rejectsMalformedAccountIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopChatImageOutbox.pending("../unauthorized")
        }
    }
}
