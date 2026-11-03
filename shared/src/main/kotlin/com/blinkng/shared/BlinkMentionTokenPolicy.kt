package com.blinkng.shared

/** Text-tail @mention editing shared by Android, Windows and comment composers. */
object BlinkMentionTokenPolicy {
    data class Token(val start: Int, val query: String)

    fun active(text: String): Token? {
        val index = text.lastIndexOf('@')
        if (index < 0 || (index > 0 && !text[index - 1].isWhitespace())) return null
        val query = text.substring(index + 1)
        if (query.length > 30 || query.any { !it.isLetterOrDigit() && it !in "_.-" }) return null
        return Token(index, query)
    }

    fun replace(text: String, token: Token, username: String): String {
        if (token.start < 0 || token.start >= text.length || text[token.start] != '@') return text
        val clean = username.trim().removePrefix("@")
        if (!clean.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{1,29}$"))) return text
        return text.substring(0, token.start) + "@$clean "
    }
}
