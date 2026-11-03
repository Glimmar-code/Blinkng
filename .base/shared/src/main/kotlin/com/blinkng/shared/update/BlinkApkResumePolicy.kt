package com.blinkng.shared.update

/** Pure Android/Windows-neutral policy for resuming an HTTPS APK transfer. */
object BlinkApkResumePolicy {
    data class Range(val start: Long, val end: Long, val total: Long)

    private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)")

    fun acceptedRange(header: String?, requestedOffset: Long, length: Long): Range? {
        if (requestedOffset <= 0) return null
        val pieces = CONTENT_RANGE.matchEntire(header.orEmpty())?.groupValues ?: return null
        val start = pieces[1].toLongOrNull() ?: return null
        val end = pieces[2].toLongOrNull() ?: return null
        val total = pieces[3].toLongOrNull() ?: return null
        if (start != requestedOffset || end < start || end >= total ||
            total > BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES ||
            (length >= 0L && length != end - start + 1)) return null
        return Range(start, end, total)
    }

    /** Full response after a Range request must discard previous partial bytes. */
    fun restartFromZero(statusCode: Int): Boolean = statusCode == 200
}
