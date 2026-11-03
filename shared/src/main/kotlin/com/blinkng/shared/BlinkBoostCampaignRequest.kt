package com.blinkng.shared

/** Immutable inputs bind a preview and a retry key to the same campaign. */
data class BlinkBoostCampaignRequest(
    val targetType: String,
    val targetId: String,
    val power: Int,
    val objective: String,
    val audience: String,
    val duration: Int,
    val university: String?,
) {
    fun fingerprint(): String = listOf(targetType, targetId, power.toString(), objective,
        audience, duration.toString(), university.orEmpty()).joinToString("|")
}
