package com.blinkng.shared

/**
 * Device-independent profile edit validation. Both save buttons share this policy
 * so user input is checked before image uploads or network mutations.
 */
object ProfileEditValidation {
    fun validate(
        fullName: String,
        username: String,
        email: String,
        phone: String,
        whatsapp: String,
        graduationYear: String,
        links: List<String>
    ): String? {
        val handle = username.trim().lowercase().removePrefix("@")
        if (!handle.matches(Regex("^[a-z0-9][a-z0-9._-]{1,29}$"))) {
            return "Username must contain 2–30 letters, numbers, dots, dashes or underscores."
        }
        if (fullName.trim().length !in 2..60 || fullName.trim().equals("Blink User", ignoreCase = true)) {
            return "Display name must contain 2–60 characters."
        }
        if (email.isNotBlank() &&
            !email.trim().matches(Regex("^[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE))) {
            return "Enter a valid email address."
        }
        val phonePattern = Regex("^\\+?[0-9()\\s-]{7,20}$")
        if (phone.isNotBlank() && !phone.trim().matches(phonePattern)) return "Enter a valid phone number."
        if (whatsapp.isNotBlank() && !whatsapp.trim().matches(phonePattern)) return "Enter a valid WhatsApp number."
        if (graduationYear.isNotBlank()) {
            val year = graduationYear.trim().toIntOrNull() ?: return "Graduation year must be a number."
            if (year !in 1990..2150) return "Enter a valid graduation year."
        }
        links.forEach { raw ->
            val link = raw.trim()
            if (link.isNotEmpty() &&
                (link.contains(Regex("\\s")) ||
                    link.startsWith("javascript:", ignoreCase = true) ||
                    link.startsWith("intent:", ignoreCase = true) ||
                    link.startsWith("file:", ignoreCase = true) ||
                    link.startsWith("content:", ignoreCase = true))) {
                return "Enter a valid public website or social link."
            }
        }
        return null
    }
}
