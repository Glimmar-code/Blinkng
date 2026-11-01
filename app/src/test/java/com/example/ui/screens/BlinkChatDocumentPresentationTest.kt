package com.example.ui.screens

import com.example.data.models.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkChatDocumentPresentationTest {
    @Test fun choosesTheRealAttachmentNameInsteadOfGenericMessageText() {
        val m = ChatMessage(text = "Document",
            attachedDocumentName = "Project_Proposal.pdf")
        assertEquals("Project_Proposal.pdf", BlinkChatDocumentPresentation.filename(m))
        assertEquals("Project_Proposal.pdf", BlinkChatDocumentPresentation.filename(
            m.copy(attachmentName = "Document")
        ))
        assertTrue(BlinkChatDocumentPresentation.isPdf(
            BlinkChatDocumentPresentation.filename(m), null))
    }
    @Test fun doesNotExposeUntrustedPathsOrControlCharacters() {
        val m = ChatMessage(attachmentName = "../../private\\draft\nreport.docx")
        assertEquals("draftreport.docx", BlinkChatDocumentPresentation.filename(m))
        assertEquals("docx", BlinkChatDocumentPresentation.extension(
            BlinkChatDocumentPresentation.filename(m), null))
    }
    @Test fun classifiesCommonDocumentTypesForTheChatCard() {
        assertEquals("PDF", BlinkChatDocumentPresentation.typeLabel("report.pdf", null))
        assertEquals("DOC", BlinkChatDocumentPresentation.typeLabel("brief.docx", null))
        assertEquals("XLS", BlinkChatDocumentPresentation.typeLabel("budget.xlsx", null))
        assertEquals("PPT", BlinkChatDocumentPresentation.typeLabel("slides.pptx", null))
        assertEquals("TXT", BlinkChatDocumentPresentation.typeLabel("notes.txt", "text/plain"))
        assertEquals("ZIP", BlinkChatDocumentPresentation.typeLabel("archive.zip", null))
        assertEquals("AUD", BlinkChatDocumentPresentation.typeLabel("voice.bin", "audio/mpeg"))
        assertEquals("FILE", BlinkChatDocumentPresentation.typeLabel("unknown.bin", null))
        assertEquals(0xFFE5484D, BlinkChatDocumentPresentation.typeColorArgb("report.pdf", null))
    }

    @Test fun usesRealMimeAndSafeExtensions() {
        assertEquals("pdf", BlinkChatDocumentPresentation.extension("attachment", "application/pdf"))
        assertEquals("xlsx", BlinkChatDocumentPresentation.extension("budget.xlsx", null))
        assertEquals("bin", BlinkChatDocumentPresentation.extension("unknown.apk", null))
        assertEquals("Document", BlinkChatDocumentPresentation.filename(ChatMessage(text="File")))
    }
}
