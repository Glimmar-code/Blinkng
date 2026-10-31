package com.example.ui.components

/**
 * Post composer tags may also occur in the caption. Display each hashtag only
 * once without mutating the saved caption or the actual posting payload.
 * Prevents reference previews from duplicating "#FUTA" already in the text.
 */
internal object BlinkPostTagPresentation {
    private val tagName = Regex("^[\\p{L}\\p{N}_]+$")
    private val captionTag = Regex("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]+)")

    fun extraTags(caption: String, tags: List<String>): List<String> {
        val visible = captionTag.findAll(caption)
            .map { it.groupValues[1].lowercase() }.toMutableSet()
        return buildList {
            tags.forEach { raw ->
                val clean = raw.trim().removePrefix("#").trim()
                val key = clean.lowercase()
                if (clean.isNotEmpty() && tagName.matches(clean) && visible.add(key)) {
                    add("#$clean")
                }
            }
        }
    }
}
