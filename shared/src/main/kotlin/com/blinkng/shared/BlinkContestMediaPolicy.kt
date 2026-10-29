package com.blinkng.shared

object BlinkContestMediaPolicy {
    fun maxBytes(mime: String): Long = when (mime) {
        "image/jpeg", "image/png", "image/webp" -> 8_388_608L
        "video/mp4" -> 26_214_400L
        else -> throw IllegalArgumentException("Choose a JPG, PNG, WebP or MP4 file.")
    }
    fun validate(mime: String, bytes: Long) {
        require(bytes in 1..maxBytes(mime)) { "Choose a nonempty image up to 8 MB or MP4 up to 25 MB." }
    }
}
