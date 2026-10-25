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

    @Test fun extensionsAndNamesAreSafe() {
        assertEquals("jpg", BlinkChatAttachmentKind.IMAGE.extension("image/jpeg"))
        assertEquals("docx", BlinkChatAttachmentKind.DOCUMENT.extension(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals("report_.pdf", safeChatFilename("../../report?.pdf"))
        assertEquals("Document", safeChatFilename("../.."))
    }
}
