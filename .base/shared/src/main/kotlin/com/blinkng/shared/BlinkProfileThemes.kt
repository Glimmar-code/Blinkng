package com.blinkng.shared

/**
 * Public, owner-selected profile appearance. This is separate from the viewer's app theme.
 * Keep IDs stable because they are stored on profiles and read by Android and Windows.
 */
data class BlinkProfileThemeSpec(
    val id: String,
    val label: String,
    val topArgb: Long,
    val bottomArgb: Long,
    val cardArgb: Long,
    val accentArgb: Long,
)

object BlinkProfileThemes {
    const val BLACK = "black"
    const val ROYAL_PURPLE = "royal_purple"
    const val SAPPHIRE = "sapphire"
    const val EMERALD = "emerald"
    const val ROSE = "rose"
    const val AMBER = "amber"

    val all: List<BlinkProfileThemeSpec> = listOf(
        BlinkProfileThemeSpec(BLACK, "Pure Black", 0xFF000000, 0xFF000000, 0xFF111111, 0xFFFFFFFF),
        BlinkProfileThemeSpec(ROYAL_PURPLE, "Royal Purple", 0xFF52146B, 0xFF160B23, 0xFF391744, 0xFFD16FFF),
        BlinkProfileThemeSpec(SAPPHIRE, "Sapphire Blue", 0xFF123F88, 0xFF081326, 0xFF132C52, 0xFF75B7FF),
        BlinkProfileThemeSpec(EMERALD, "Emerald", 0xFF126252, 0xFF081F1A, 0xFF16443A, 0xFF7AE7BE),
        BlinkProfileThemeSpec(ROSE, "Rose", 0xFF76254E, 0xFF260E1D, 0xFF51203C, 0xFFFF91C9),
        BlinkProfileThemeSpec(AMBER, "Amber", 0xFF77501A, 0xFF261909, 0xFF4D3415, 0xFFFFCD75),
    )

    fun normalize(id: String?): String =
        all.firstOrNull { it.id == id?.trim()?.lowercase() }?.id ?: BLACK

    fun resolve(id: String?): BlinkProfileThemeSpec =
        all.first { it.id == normalize(id) }
}
