package com.blinkng.desktop.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

class DesktopPostDraftStoreTest {
    @TempDir lateinit var root: File
    // Test-only codec; production always uses Windows DPAPI.
    private fun codec(bytes: ByteArray) = bytes.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    private fun store() = DesktopPostDraftStore(root, ::codec, ::codec)

    @Test fun restoresTheExactPendingRequestWithoutCrossAccountAccess() {
        val owner = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val draft = DesktopPostDraft(kind = "Photo", text = "Private pending caption", photos = listOf("C:/photos/one.jpg"),
            audience = "Followers", location = "Campus", allowComments = false, submitted = true)
        store().save(owner, draft)
        assertEquals(draft, store().load(owner))
        assertNull(store().load(other))
        assertFalse(File(root, "$owner.draft").readText().contains(draft.text))
        assertEquals(1, root.listFiles()!!.size)
        store().clear(other)
        assertEquals(draft.requestId, store().load(owner)?.requestId)
    }

    @Test fun failedProtectionPreservesExistingDraftAndNeverWritesPlaintext() {
        val owner = UUID.randomUUID().toString()
        val original = DesktopPostDraft(text = "Original")
        store().save(owner, original)
        val before = File(root, "$owner.draft").readBytes()
        val failing = DesktopPostDraftStore(root, { throw IllegalStateException("DPAPI unavailable") }, ::codec)
        assertThrows(IllegalStateException::class.java) { failing.save(owner, original.copy(text = "Do not persist")) }
        assertArrayEquals(before, File(root, "$owner.draft").readBytes())
        assertEquals(original, store().load(owner))
        assertEquals(1, root.listFiles()!!.size)
    }

    @Test fun rejectsTraversalAndPreservesUnreadableDrafts() {
        assertThrows(IllegalArgumentException::class.java) { store().save("../stranger", DesktopPostDraft()) }
        val owner = UUID.randomUUID().toString()
        val file = File(root, "$owner.draft").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertThrows(Exception::class.java) { store().load(owner) }
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }
}
