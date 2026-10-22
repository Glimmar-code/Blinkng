package com.blinkng.shared.notification

/** Photo decorations are local, predictable and intentionally allowlisted. */
object BlinkStatusPhotoDecorPolicy {
    const val ORIGINAL = "Original"
    const val MONO = "Mono"
    const val WARM = "Warm"
    const val COOL = "Cool"
    val filters = listOf(ORIGINAL, MONO, WARM, COOL)
    val stickers = listOf("", "⭐", "❤️", "🎓", "🔥", "🎉", "💜")

    data class Position(val x: Float, val y: Float)

    fun filter(id: String): String = id.takeIf { it in filters } ?: ORIGINAL
    fun sticker(value: String): String = value.takeIf { it in stickers } ?: ""
    fun position(x: Float, y: Float): Position = Position(
        if (x.isFinite()) x.coerceIn(0.10f, 0.90f) else 0.50f,
        if (y.isFinite()) y.coerceIn(0.10f, 0.90f) else 0.50f
    )
}
