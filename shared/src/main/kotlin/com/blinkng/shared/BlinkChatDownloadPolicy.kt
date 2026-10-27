package com.blinkng.shared

import java.net.URI

/**
 * Client-side defense in depth for received-picture downloads.
 * Server media authorization and conversation membership remain authoritative.
 */
object BlinkChatDownloadPolicy {
    const val MAX_IMAGE_BYTES = 30L * 1024 * 1024

    fun acceptsSource(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawFragment == null
    }.getOrDefault(false)

    fun acceptsMime(rawMime: String?): Boolean {
        val mime = rawMime?.substringBefore(';')?.trim()?.lowercase()
        return mime.isNullOrEmpty() ||
            mime.startsWith("image/") || mime == "application/octet-stream"
    }

    fun acceptsLength(bytes: Long): Boolean =
        bytes < 0 || bytes <= MAX_IMAGE_BYTES
}
