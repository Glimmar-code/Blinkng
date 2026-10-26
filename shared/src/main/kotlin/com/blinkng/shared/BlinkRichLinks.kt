package com.blinkng.shared

import java.net.URI

/** Shared URL recognition for Android, Windows and BLINK's link-preview adapter. */
data class BlinkRichLink(val url: String, val host: String, val internal: Boolean)

object BlinkRichLinks {
    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val trailing = charArrayOf('.', ',', '!', '?', ';', ':', ')', ']', '}')
    private val blinkHosts = setOf("blink.com.ng", "www.blink.com.ng")

    /** Returns distinct links, bounded to avoid spam, excess requests and long cards. */
    fun all(text: String, maxLinks: Int = 3): List<BlinkRichLink> {
        if (text.length > 100_000) return emptyList()
        return urlPattern.findAll(text)
            .mapNotNull { match -> parse(match.value) }
            .distinctBy { it.url }
            .take(maxLinks.coerceIn(0, 3))
            .toList()
    }

    fun first(text: String): BlinkRichLink? = all(text, 1).firstOrNull()

    fun parse(raw: String): BlinkRichLink? {
        val value = raw.trim().trimEnd(*trailing)
        if (value.length !in 10..2048 || value.any { it.code < 32 }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.rawUserInfo != null || uri.port !in -1..65535) return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        if (host.length !in 3..253 || !host.contains('.') ||
            host.any { !it.isLetterOrDigit() && it != '.' && it != '-' }) return null
        return BlinkRichLink(value, host, host in blinkHosts)
    }
}
