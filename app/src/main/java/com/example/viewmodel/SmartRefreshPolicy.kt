package com.example.viewmodel

/**
 * A manual refresh is a new server snapshot, not a union with every stale cached row.
 * For ranked lanes, put newly discovered IDs first while preserving server order
 * for existing rows. Optionally space repeated authors within the *new organic*
 * group; never move paid/promoted placements or change Following chronology.
 * Caching and error fallback are handled by the caller, not by mixing expired rows here.
 */
internal data class SmartRefreshPage<T>(
    val items: List<T>,
    val newlyDiscoveredCount: Int
)

internal fun <T> planSmartRefresh(
    previous: List<T>,
    fetched: List<T>,
    idOf: (T) -> String,
    prioritizeNew: Boolean,
    organicAuthorKey: ((T) -> String)? = null,
    isPromoted: (T) -> Boolean = { false }
): SmartRefreshPage<T> {
    val previousIds = previous.mapTo(HashSet()) { idOf(it) }
    val unique = fetched.distinctBy(idOf)
    val newlyDiscovered = unique.filter { idOf(it) !in previousIds }
    val sorted = if (prioritizeNew && newlyDiscovered.isNotEmpty()) {
        val front = organicAuthorKey?.let { authorKey ->
            diversifyNewOrganicAuthors(newlyDiscovered, authorKey, isPromoted)
        } ?: newlyDiscovered
        front + unique.filter { idOf(it) in previousIds }
    } else {
        unique
    }
    return SmartRefreshPage(sorted, newlyDiscovered.size)
}


/**
 * Reduce runs of three or more posts from the same author at the top of a
 * manually refreshed For You / Reels lane. Preserve all posts and their counts.
 * Move at most a nearby organic row; never jump past a promoted placement.
 * No change is made to the server's order of previously seen rows or Following.
 */
internal fun <T> diversifyNewOrganicAuthors(
    newest: List<T>,
    authorKey: (T) -> String,
    isPromoted: (T) -> Boolean,
    maxInARow: Int = 2
): List<T> {
    if (newest.size < 3) return newest
    val remaining = newest.toMutableList()
    val spaced = ArrayList<T>(newest.size)
    var lastAuthor = ""
    var consecutive = 0
    while (remaining.isNotEmpty()) {
        val first = remaining.first()
        val firstAuthor = authorKey(first).trim().lowercase()
        val repeated = !isPromoted(first) && firstAuthor.isNotBlank() &&
            firstAuthor == lastAuthor && consecutive >= maxInARow
        var index = 0
        if (repeated) {
            // Stop before any promotion so its rank and position remain fixed.
            val firstPromotion = remaining.indexOfFirst(isPromoted)
                .let { if (it < 0) remaining.size else it }
            val stop = minOf(firstPromotion, 9)
            for (candidateIndex in 1 until stop) {
                val candidate = remaining[candidateIndex]
                val candidateAuthor = authorKey(candidate).trim().lowercase()
                if (candidateAuthor.isNotBlank() && candidateAuthor != lastAuthor) {
                    index = candidateIndex
                    break
                }
            }
        }
        val chosen = remaining.removeAt(index)
        spaced.add(chosen)
        if (isPromoted(chosen)) {
            lastAuthor = ""
            consecutive = 0
        } else {
            val author = authorKey(chosen).trim().lowercase()
            consecutive = if (author.isNotBlank() && author == lastAuthor) consecutive + 1 else 1
            lastAuthor = author
        }
    }
    return spaced
}
