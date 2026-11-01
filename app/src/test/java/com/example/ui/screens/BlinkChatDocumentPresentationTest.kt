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
    @Test fun usesRealMimeAndSafeExtensions() {
        assertEquals("pdf", BlinkChatDocumentPresentation.extension("attachment", "application/pdf"))
        assertEquals("xlsx", BlinkChatDocumentPresentation.extension("budget.xlsx", null))
        assertEquals("bin", BlinkChatDocumentPresentation.extension("unknown.apk", null))
        assertEquals("Document", BlinkChatDocumentPresentation.filename(ChatMessage(text="File")))
    }
}
