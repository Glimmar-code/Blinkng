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

    /**
     * Recommend only trending tags that match the user's draft or their explicit
     * university text. A tag's popularity by itself does not make it relevant.
     * Stable input order preserves the server's trend ranking for tied matches.
     */
    fun suggest(
        text: String,
        trending: List<String>,
        selected: List<String> = emptyList(),
        university: String? = null,
        limit: Int = 7
    ): List<String> {
        if (limit <= 0) return emptyList()
        val textTokens = Regex("[a-z0-9]+")
            .findAll(text.lowercase(java.util.Locale.ROOT))
            .map { it.value }
            .toSet()
        val campusTokens = Regex("[a-z0-9]+")
            .findAll(university.orEmpty().lowercase(java.util.Locale.ROOT))
            .map { it.value }
            .toSet()
        val existing = allTags(text, selected).toSet()

        return trending.asSequence()
            .mapNotNull(::normalize)
            .distinct()
            .filterNot { it in existing }
            .mapIndexedNotNull { index, tag ->
                val words = tag.split('_').filter { it.isNotBlank() }
                val textMatches = words.count { it in textTokens }
                val campusMatches = words.count { it in campusTokens }
                // Require a complete topical match; don't suggest unrelated viral tags.
                if (words.isEmpty() || (textMatches != words.size && campusMatches != words.size)) {
                    null
                } else {
                    val score = textMatches * 3 + campusMatches
                    Triple(tag, score, index)
                }
            }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }
                .thenBy { it.third })
            .take(limit.coerceAtMost(MAX_TAGS))
            .map { it.first }
            .toList()
    }

    fun display(tag: String): String = "#${normalize(tag).orEmpty()}"
}
