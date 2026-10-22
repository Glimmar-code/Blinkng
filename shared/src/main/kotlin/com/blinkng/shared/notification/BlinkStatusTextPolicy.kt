package com.blinkng.shared.notification

/** Keep text-status colours and limits server-compatible and predictable. */
object BlinkStatusTextPolicy {
    const val MAX_LENGTH = 240
    val backgrounds = listOf("#171717", "#214870", "#57317A", "#9C385B", "#135C48")

    fun text(input: String): String? = input.trim()
        .takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }

    fun background(input: String): String = input
        .takeIf { it in backgrounds } ?: backgrounds.first()
}
