package com.blinkng.shared

/**
 * One hashtag contract for Android and Windows.
 * Backend normalizes again: client validation is only for immediate UX feedback.
 */
object BlinkSmartTags {
    const val MAX_TAGS = 5
    const val RECOMMENDED_TAGS = 3
    private val valid = Regex("^[a-z0-9_]{2,32}$")
    private val inline = Regex("(?<![A-Za-z0-9_])#([A-Za-z0-9_]{2,32})\\b")

    /** Return an unprefixed, canonical tag; never silently turn invalid input into a different tag. */
    fun normalize(raw: String): String? {
        val tag = raw.trim().removePrefix("#").lowercase(java.util.Locale.ROOT)
        return tag.takeIf { valid.matches(it) }
    }

    fun fromText(text: String): List<String> =
        inline.findAll(text).mapNotNull { normalize(it.groupValues[1]) }.distinct().toList()

    fun allTags(text: String, selected: List<String>): List<String> =
        (selected.mapNotNull(::normalize) + fromText(text)).distinct()

    fun merge(text: String, selected: List<String>): List<String> =
        allTags(text, selected).take(MAX_TAGS)

    fun add(selected: List<String>, raw: String): List<String> {
        val tag = normalize(raw) ?: return selected
        val existing = selected.mapNotNull(::normalize).distinct()
        return if (tag in existing || existing.size >= MAX_TAGS) existing else existing + tag
    }

    fun display(tag: String): String = "#${normalize(tag).orEmpty()}"
}
