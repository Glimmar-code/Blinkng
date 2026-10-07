package com.example.ui.components

import java.net.URI
import java.util.Locale

internal object PostComposerRules {
    private val tagRegex = Regex("""(?<![A-Za-z0-9_])#([A-Za-z0-9_]{1,50})""")
    private val mentionRegex = Regex("""(?<![A-Za-z0-9._])@([A-Za-z0-9._]{2,32})""")

    fun normalizedLink(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val hasExplicitScheme = trimmed.contains("://")
        val isHttpScheme =
            trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true)
        if (hasExplicitScheme && !isHttpScheme) return null

        val candidate = if (isHttpScheme) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase(Locale.US)
        if (scheme != "https" && scheme != "http") return null
        return candidate.takeIf { !parsed.host.isNullOrBlank() }
    }

    fun tags(text: String): List<String> =
        tagRegex.findAll(text)
            .map { it.groupValues[1].lowercase(Locale.US) }
            .distinct()
            .take(20)
            .toList()

    fun mentions(text: String): List<String> =
        mentionRegex.findAll(text)
            .map { it.groupValues[1] }
            .distinctBy { it.lowercase(Locale.US) }
            .take(20)
            .toList()

    fun validPoll(question: String, options: List<String>): Boolean {
        val cleaned = options.map(String::trim).filter(String::isNotBlank)
        if (question.isBlank() || cleaned.size < 2) return false
        return cleaned
            .map { it.lowercase(Locale.US) }
            .distinct()
            .size == cleaned.size
    }
}
