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
    fun albumCaptionsSurviveQueueReloadAndStayWithinOwnerAccount() {
        val owner = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val conversation = UUID.randomUUID().toString()
        val albumId = UUID.randomUUID().toString()
        val original = File.createTempFile("blink-album-photo-", ".jpg")
        original.writeBytes(ByteArray(4096) { (it % 239).toByte() })
        try {
            val queued = (0 until 3).map { position ->
                DesktopChatImageOutbox.enqueue(
                    ownerId = owner,
                    conversationId = conversation,
                    messageId = UUID.randomUUID().toString(),
                    original = original,
                    caption = "Caption $position",
                    quality = BlinkChatImageQuality.ORIGINAL,
                    album = BlinkChatPhotoAlbum.of(albumId, position, 3)
                )
            }
            // Simulates app restart: reconstruct all records from durable disk metadata.
            val recovered = DesktopChatImageOutbox.pending(owner)
                .associateBy { it.messageId }
            assertEquals(3, recovered.size)
            queued.forEach { expected ->
                val actual = recovered[expected.messageId]
                    ?: throw AssertionError("Missing queued photo after reload")
                assertEquals(expected.caption, actual.caption)
                assertEquals(albumId, actual.album.id)
                assertEquals(expected.album.position, actual.album.position)
                assertEquals(3, actual.album.size)
                assertEquals(BlinkChatImageQuality.ORIGINAL, actual.quality)
            }
            assertTrue(DesktopChatImageOutbox.pending(other).isEmpty())
            DesktopChatImageOutbox.complete(queued.first())
            assertEquals(2, DesktopChatImageOutbox.pending(owner).size)
        } finally {
            original.delete()
            DesktopChatImageOutbox.discardOwner(owner)
            DesktopChatImageOutbox.discardOwner(other)
        }
    }

    @Test
    fun rejectsMalformedAccountIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopChatImageOutbox.pending("../unauthorized")
        }
    }
}
