package com.blinkng.shared.discovery

/** Shared autocomplete rules used by mentions, signup and invitations. */
object BlinkUsernameSelectionPolicy {
    enum class Purpose { REFERRER, MENTION, GROUP_INVITE, COMMUNITY_INVITE }
    private val trailing = Regex("(?:^|\\s)@([A-Za-z0-9._-]{1,30})$")

    fun trailingMention(text: String): String? =
        trailing.find(text)?.groupValues?.getOrNull(1)

    fun insertMention(text: String, username: String): String {
        val match = trailing.find(text) ?: return text
        if (!username.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{1,29}"))) return text
        return text.replaceRange(match.range, match.value.replaceAfterLast('@', username)+" ")
    }

    fun addInvite(current: Set<String>, username: String, max: Int = 30): Set<String> {
        val name=username.trim().removePrefix("@").lowercase()
        if (name.isBlank() || current.size>=max) return current
        return current+name
    }
}
