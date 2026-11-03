package com.blinkng.shared

/**
 * Backwards-compatible representation of a chat contact shared by older clients.
 * The wire payload remains human-readable text; both desktop and Android can now
 * render it as a contact card without a database migration or a second transport.
 */
data class BlinkChatContactCard(val displayName: String, val phoneNumber: String)

object BlinkChatContactCardPolicy {
    private const val NAME_PREFIX = "👤 Contact: "
    private const val PHONE_PREFIX = "Phone: "

    fun parse(text: String): BlinkChatContactCard? {
        val lines = text.trim().lines()
        if (lines.size != 2 || !lines[0].startsWith(NAME_PREFIX) ||
            !lines[1].startsWith(PHONE_PREFIX)) return null
        val name = lines[0].removePrefix(NAME_PREFIX).trim()
        val phone = lines[1].removePrefix(PHONE_PREFIX).trim()
        if (name.isBlank() || name.length > 100 || phone.length !in 3..60 ||
            phone.none(Char::isDigit) ||
            name.any(Char::isISOControl) || phone.any(Char::isISOControl)) return null
        return BlinkChatContactCard(displayName = name, phoneNumber = phone)
    }

    fun encode(displayName: String, phoneNumber: String): String {
        val safeName = displayName.replace("\n", " ").replace("\r", " ").trim().take(100)
        val safePhone = phoneNumber.replace("\n", " ").replace("\r", " ").trim().take(60)
        require(safeName.isNotBlank() && safePhone.isNotBlank() && safePhone.any(Char::isDigit)) {
            "A contact must have a name and a phone number."
        }
        return "$NAME_PREFIX$safeName\n$PHONE_PREFIX$safePhone"
    }
}
