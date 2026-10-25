package com.blinkng.shared

/**
 * Deterministic, exact-prefix-first ranking for user pickers.
 * The caller MUST supply only profiles visible to the authenticated viewer.
 * This policy never replaces server-side privacy, block or RLS filtering.
 */
object BlinkUserSuggestionPolicy {
    const val DEFAULT_PAGE_SIZE = 10

    fun score(query: String, username: String, displayName: String): Int {
        val q = query.trim().removePrefix("@").lowercase()
        val handle = username.trim().removePrefix("@").lowercase()
        val name = displayName.trim().lowercase()
        return when {
            q.isEmpty() -> 0
            handle == q -> 0
            handle.startsWith(q) -> 1
            name.startsWith(q) -> 2
            handle.contains(q) -> 3
            name.contains(q) -> 4
            else -> Int.MAX_VALUE
        }
    }

    fun <T> page(
        visibleCandidates: List<T>,
        query: String,
        offset: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        username: (T) -> String,
        displayName: (T) -> String,
    ): List<T> = visibleCandidates.asSequence()
        .filter { username(it).trim().removePrefix("@").isNotBlank() }
        .map { it to score(query, username(it), displayName(it)) }
        .filter { it.second != Int.MAX_VALUE }
        .sortedWith(compareBy<Pair<T, Int>> { it.second }
            .thenBy { username(it.first).lowercase() })
        .map { it.first }
        .distinctBy { username(it).trim().removePrefix("@").lowercase() }
        .drop(offset.coerceAtLeast(0))
        .take(pageSize.coerceIn(1, 100))
        .toList()
}
