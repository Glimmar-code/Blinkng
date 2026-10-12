package com.blinkng.shared

/** Shared Android/Windows Connect freshness messaging. Never present unavailable results as an empty directory. */
object BlinkConnectHealth {
    fun warning(failedSections: Collection<String>): String? {
        val names = failedSections.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return null
        val detail = if (names.size <= 3) " (" + names.joinToString(", ") + ")" else ""
        return "Some Connect features could not refresh" + detail + ". " +
            "Showing available or previously loaded information."
    }
}
