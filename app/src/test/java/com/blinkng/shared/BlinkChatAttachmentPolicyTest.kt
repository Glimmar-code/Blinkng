package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkChatAttachmentPolicyTest {
    @Test fun exactlyFourSupportedTypes() {
        assertEquals(listOf("image", "video", "voice", "document"),
            BlinkChatAttachmentKind.entries.map { it.wireType })
    }

    @Test fun rejectsExecutableAndOversizeContent() {
        assertFalse(BlinkChatAttachmentKind.DOCUMENT.accepts("application/x-msdownload"))
        assertFalse(BlinkChatAttachmentKind.DOCUMENT.accepts("text/html"))
        assertFalse(BlinkChatAttachmentKind.IMAGE.accepts("image/svg+xml"))
        assertFalse(BlinkChatAttachmentKind.VIDEO.accepts("image/jpeg"))
        assertTrue(runCatching {
            BlinkChatAttachmentKind.IMAGE.verify("image/png", 16L * 1024L * 1024L)
        }.isFailure)
        assertTrue(runCatching {
            BlinkChatAttachmentKind.DOCUMENT.verify("application/pdf", 0L)
        }.isFailure)
        assertTrue(runCatching {
            BlinkChatAttachmentKind.VIDEO.verify("video/mp4", 1L)
        }.isSuccess)
    }

    @Test fun uploadProgressClampsAndHandlesUnknownOrEmptyTotals() {
        assertEquals(0, BlinkChatUploadProgress.percent(10L, 0L))
        assertEquals(0, BlinkChatUploadProgress.percent(-20L, 100L))
        assertEquals(45, BlinkChatUploadProgress.percent(45L, 100L))
        assertEquals(100, BlinkChatUploadProgress.percent(200L, 100L))
        assertEquals(100, BlinkChatUploadProgress.percent(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun albumMetadataValidatesLimitsAndKeepsSinglesBackwardCompatible() {
        val id = "f4ba69ec-814a-41e7-ac11-14d994d4f402"
        assertFalse(BlinkChatPhotoAlbum.single().grouped)
        assertTrue(BlinkChatPhotoAlbum.of(id, 0, 2).grouped)
        assertTrue(BlinkChatPhotoAlbum.of(id, 9, 10).grouped)
        assertThrows(IllegalArgumentException::class.java) {
            BlinkChatPhotoAlbum.of(id, 10, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlinkChatPhotoAlbum.of(id, 0, 11)
        }
    }

    @Test fun photoQualitySupportsStandardAndOriginalWithoutNewMediaType() {
        assertEquals(2, BlinkChatImageQuality.entries.size)
        assertTrue(BlinkChatImageQuality.STANDARD != BlinkChatImageQuality.ORIGINAL)
        assertEquals("image", BlinkChatAttachmentKind.IMAGE.wireType)
        assertTrue(BlinkChatAttachmentKind.IMAGE.accepts("image/jpeg"))
        assertTrue(BlinkChatAttachmentKind.IMAGE.accepts("image/heic"))
        assertFalse(BlinkChatAttachmentKind.IMAGE.accepts("application/pdf"))
    }

    @Test fun extensionsAndNamesAreSafe() {
        assertEquals("jpg", BlinkChatAttachmentKind.IMAGE.extension("image/jpeg"))
        assertEquals("docx", BlinkChatAttachmentKind.DOCUMENT.extension(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals("report_.pdf", safeChatFilename("../../report?.pdf"))
        assertEquals("Document", safeChatFilename("../.."))
    }
}
