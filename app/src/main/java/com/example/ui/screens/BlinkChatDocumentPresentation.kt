package com.example.ui.screens

import com.example.data.models.ChatMessage

/** Display filenames that came from actual attachment metadata, not raw URLs. */
internal object BlinkChatDocumentPresentation {
    private val supported = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt",
        "pptx", "txt", "csv", "rtf", "odt", "ods", "zip")
    fun filename(message: ChatMessage): String {
        val raw = sequenceOf(message.attachmentName, message.attachedDocumentName,
            message.text.takeUnless { it.equals("document", true) || it.equals("file", true) })
            .filterNotNull().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: "Document"
        return raw.replace('\\', '/').substringAfterLast('/')
            .filter { it >= ' ' && it != '\u007f' }
            .take(120).ifBlank { "Document" }
    }
    fun extension(name: String, mime: String?): String {
        if (mime?.equals("application/pdf", true) == true) return "pdf"
        return name.substringAfterLast('.', "").lowercase()
            .takeIf { it in supported } ?: "bin"
    }
    fun isPdf(name: String, mime: String?): Boolean = extension(name, mime) == "pdf"
}
