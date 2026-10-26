package com.blinkng.shared

/**
 * Shared Android/Windows media selection model. Selection order is retained and
 * shown as 1..10; overflow cannot silently remove earlier selections.
 */
object BlinkChatMediaSelectionPolicy {
    const val MAX_ALBUM_PHOTOS = 10

    fun toggle(current: List<String>, mediaId: String): List<String> {
        if (mediaId.isBlank()) return current
        val unique = current.distinct()
        return when {
            mediaId in unique -> unique.filterNot { it == mediaId }
            unique.size < MAX_ALBUM_PHOTOS -> unique + mediaId
            else -> unique
        }
    }

    fun selectionNumber(selected: List<String>, mediaId: String): Int =
        selected.indexOf(mediaId).let { if (it == -1) 0 else it + 1 }

    fun canContinue(selected: List<String>): Boolean =
        selected.isNotEmpty() && selected.size <= MAX_ALBUM_PHOTOS &&
            selected.distinct().size == selected.size
}
