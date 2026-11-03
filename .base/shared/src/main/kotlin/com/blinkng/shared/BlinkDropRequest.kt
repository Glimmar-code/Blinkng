package com.blinkng.shared

data class BlinkDropRequest(
    val targetType: String,
    val targetId: String,
    val action: String,
    val reward: Int,
    val recipients: Int,
    val audience: String,
    val hours: Int,
) {
    fun fingerprint(): String = listOf(targetType, targetId, action, reward.toString(),
        recipients.toString(), audience, hours.toString()).joinToString("|")
    fun isValid(): Boolean = targetId.isNotBlank() && reward in 100..100_000 && reward % 100 == 0 &&
        recipients in 1..500 && reward.toLong() * recipients <= 10_000_000 &&
        hours in setOf(1, 6, 12, 24, 72, 168) && audience in setOf("MY_CAMPUS", "ALL_CAMPUSES") &&
        ((targetType in setOf("POST", "REEL") && action in setOf("LIKE", "COMMENT", "REPOST")) ||
            (targetType == "LISTING" && action == "SAVE_LISTING"))
}
