package com.example.auth

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parsed payload from the Supabase recovery redirect back into Blink. */
data class PasswordRecoveryLink(
    val accessToken: String = "",
    val refreshToken: String = "",
    val error: String? = null
)

object PasswordRecoveryLinkParser {
    fun parse(rawUrl: String?): PasswordRecoveryLink? {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isBlank()) return null

        val isBlinkRecovery = raw.startsWith("blink://reset-password", ignoreCase = true)
        val isHttpsRecovery = Regex("^https://[^/]+/.*/?auth/reset-password", RegexOption.IGNORE_CASE)
            .containsMatchIn(raw) || raw.contains("/auth/reset-password", ignoreCase = true)
        if (!isBlinkRecovery && !isHttpsRecovery) return null

        val params = linkedMapOf<String, String>()
        fun parsePart(part: String) {
            part.split('&').forEach { pair ->
                if (pair.isBlank()) return@forEach
                val pieces = pair.split('=', limit = 2)
                val key = decode(pieces.getOrElse(0) { "" })
                val value = decode(pieces.getOrElse(1) { "" })
                if (key.isNotBlank()) params[key] = value
            }
        }

        raw.substringAfter('?', "").substringBefore('#').takeIf { it.isNotBlank() }?.let(::parsePart)
        raw.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::parsePart)

        val providerError = params["error_description"]
            ?: params["error"]
            ?: params["message"]

        return PasswordRecoveryLink(
            accessToken = params["access_token"].orEmpty(),
            refreshToken = params["refresh_token"].orEmpty(),
            error = providerError?.takeIf { it.isNotBlank() }
        )
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
