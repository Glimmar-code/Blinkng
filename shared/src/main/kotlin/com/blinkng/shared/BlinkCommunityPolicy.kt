package com.blinkng.shared

/** Shared Android/Windows input and visibility validation for real communities. */
object BlinkCommunityPolicy {
    const val MAX_RULES = 15
    const val MAX_NAME = 80
    const val MAX_DESCRIPTION = 1500
    const val MAX_RULE_LENGTH = 300

    enum class Visibility(val wire: String) {
        PUBLIC("public"), PRIVATE("private");
        companion object {
            fun fromWire(value: String): Visibility? = entries.firstOrNull { it.wire == value }
        }
    }

    fun validatedName(name: String): String = name.trim().also {
        require(it.length in 3..MAX_NAME) { "Community name must have 3–80 characters." }
    }

    fun validatedDescription(description: String): String = description.trim().also {
        require(it.length <= MAX_DESCRIPTION) { "Description is too long." }
    }

    fun validatedField(field: String, label: String, max: Int = 150): String = field.trim().also {
        require(it.length in 2..max) { "$label must have 2–$max characters." }
    }

    fun validatedRules(values: List<String>): List<String> {
        val clean = values.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        require(clean.size <= MAX_RULES) { "Only 15 community rules are supported." }
        require(clean.all { it.length <= MAX_RULE_LENGTH }) { "Each rule must be 300 characters or less." }
        return clean
    }
}
