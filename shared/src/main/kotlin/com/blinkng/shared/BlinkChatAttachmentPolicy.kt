package com.blinkng.shared

/**
 * Shared Android/Windows attachment validation. These are app-level limits;
 * Supabase private-bucket and RLS rules still apply on the server.
 */
enum class BlinkChatAttachmentKind(
    val wireType: String,
    val maxBytes: Long,
    val title: String
) {
    IMAGE("image", 15L * 1024 * 1024, "Image"),
    VIDEO("video", 80L * 1024 * 1024, "Video"),
    VOICE("voice", 15L * 1024 * 1024, "Voice note"),
    DOCUMENT("document", 25L * 1024 * 1024, "Document");

    fun accepts(mime: String): Boolean = mime.lowercase() in when (this) {
        IMAGE -> setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif")
        VIDEO -> setOf("video/mp4", "video/webm", "video/quicktime", "video/3gpp")
        VOICE -> setOf("audio/mp4", "audio/m4a", "audio/aac", "audio/3gpp", "audio/mpeg")
        DOCUMENT -> setOf(
            "application/pdf", "text/plain", "text/csv",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }

    fun verify(mime: String, size: Long) {
        require(accepts(mime)) { "Unsupported ${title.lowercase()} format. Choose a supported file." }
        require(size > 0L) { "The selected file is empty." }
        require(size <= maxBytes) { "${title} is too large (maximum ${maxBytes / (1024 * 1024)} MB)." }
    }

    fun extension(mime: String): String = when (mime.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/3gpp", "audio/3gpp" -> "3gp"
        "audio/mp4", "audio/m4a" -> "m4a"
        "audio/aac" -> "aac"
        "audio/mpeg" -> "mp3"
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        "text/csv" -> "csv"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        else -> throw IllegalArgumentException("Unsupported attachment type.")
    }

    companion object {
        fun fromWireType(value: String): BlinkChatAttachmentKind? =
            entries.firstOrNull { it.wireType == value.lowercase() }
    }
}

fun safeChatFilename(raw: String): String {
    val basename = raw.substringAfterLast('/').substringAfterLast('\\')
    return basename.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        .trim().trim('.').take(96).ifBlank { "Document" }
}
