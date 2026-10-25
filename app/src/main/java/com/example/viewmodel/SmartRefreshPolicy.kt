package com.example.viewmodel

/**
 * A manual refresh is a new server snapshot, not a union with every stale cached row.
 * For ranked lanes, make newly discovered IDs visible first while retaining the server's
 * relative order within the new and previously known groups. Following stays chronological.
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
    prioritizeNew: Boolean
): SmartRefreshPage<T> {
    val previousIds = previous.mapTo(HashSet()) { idOf(it) }
    val unique = fetched.distinctBy(idOf)
    val newlyDiscovered = unique.filter { idOf(it) !in previousIds }
    val sorted = if (prioritizeNew && newlyDiscovered.isNotEmpty()) {
        newlyDiscovered + unique.filter { idOf(it) in previousIds }
    } else {
        unique
    }
    return SmartRefreshPage(sorted, newlyDiscovered.size)
}
