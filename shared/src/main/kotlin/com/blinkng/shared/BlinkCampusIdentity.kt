package com.blinkng.shared

/** Campus matching for already-published university names with punctuation variation.
 * Never group blank/unknown universities into one campus leaderboard.
 */
fun sameBlinkCampus(left: String?, right: String?): Boolean {
    fun campusKey(name: String?): String {
        val value = name?.trim()?.lowercase().orEmpty()
        if (value.isEmpty() || value == "null") return ""
        return value.replace(Regex("[^a-z0-9]"), "")
    }
    val leftKey = campusKey(left)
    return leftKey.isNotEmpty() && leftKey == campusKey(right)
}
