package com.example.ui.screens

import com.example.data.models.ChatMessage

/** Display filenames that came from actual attachment metadata, not raw URLs. */
internal object BlinkChatDocumentPresentation {
    private val supported = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt",
        "pptx", "txt", "csv", "rtf", "odt", "ods", "zip")
    fun filename(message: ChatMessage): String {
        val raw = sequenceOf(message.attachmentName, message.attachedDocumentName,
            message.text)
            .filterNotNull().map { it.trim() }
            .firstOrNull {
                it.isNotBlank() && it.lowercase() !in setOf(
                    "document", "file", "attachment", "document.pdf", "file.bin"
                )
            } ?: "Document"
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

    /** Short, safe document badge derived only from the validated filename/MIME. */
    fun typeLabel(name: String, mime: String?): String = when (extension(name, mime)) {
        "pdf" -> "PDF"
        "doc", "docx", "odt", "rtf" -> "DOC"
        "xls", "xlsx", "csv", "ods" -> "XLS"
        "ppt", "pptx", "odp" -> "PPT"
        "txt" -> "TXT"
        "zip" -> "ZIP"
        else -> when {
            mime?.startsWith("audio/", ignoreCase = true) == true -> "AUD"
            else -> "FILE"
        }
    }

    /** Stable ARGB values keep document cards recognizable without changing chat themes. */
    fun typeColorArgb(name: String, mime: String?): Long = when (typeLabel(name, mime)) {
        "PDF" -> 0xFFE5484D
        "DOC" -> 0xFF2B6CD9
        "XLS" -> 0xFF1F9D55
        "PPT" -> 0xFFE8742A
        "TXT" -> 0xFF7A7A82
        "ZIP" -> 0xFFB58B2A
        "AUD" -> 0xFF8B5CF6
        else -> 0xFF5B6270
    }
}
