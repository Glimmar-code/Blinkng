package com.blinkng.shared.discovery

/**
 * Contact discovery is opt-in. A local contact chooser may return ONE selected
 * contact to the operating system's SMS composer; no phone numbers, emails,
 * hashes or address books are sent to BLINK or retained by this policy.
 *
 * Automatic phone matching must remain disabled until there is a verified-number,
 * mutual opt-in, rate-limited privacy-preserving backend contract.
 */
object BlinkFriendDiscoveryPolicy {
    const val CONTACT_MATCHING_ENABLED = false
    const val MAX_SUGGESTIONS = 12

    data class Candidate(
        val id: String,
        val username: String,
        val university: String,
    )

    fun campusSuggestions(
        currentId: String,
        university: String?,
        profiles: List<Candidate>,
    ): List<Candidate> {
        val campus = university.orEmpty().trim()
        if (currentId.isBlank() || campus.isBlank() || campus.equals("null", ignoreCase = true))
            return emptyList()
        return profiles.asSequence()
            .filter { it.id.isNotBlank() && it.id != currentId && it.username.isNotBlank() }
            .filter { it.university.trim().equals(campus, ignoreCase = true) }
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.username })
            .take(MAX_SUGGESTIONS)
            .toList()
    }

    fun inviteMessage(link: String): String =
        "Join me on BLINK! Find your campus community and connect with friends: ${link.trim()}"
}
