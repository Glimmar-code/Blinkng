package com.blinkng.shared

/**
 * Shared, locale-safe text filtering used by the Android menu and available to
 * the Windows client without duplicating navigation search semantics.
 */
object MenuSearch {
    fun matches(query: String, vararg values: String): Boolean {
        val normalized = query.trim()
        return normalized.isEmpty() ||
            values.any { value -> value.contains(normalized, ignoreCase = true) }
    }
}
