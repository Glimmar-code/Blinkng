package com.example.auth

import com.blinkng.shared.BlinkAuthFeedback

/** Android adapter for the shared BLINK authentication message contract. */
object AuthErrorMapper {
    fun friendly(
        message: String?,
        fallback: String = "Authentication failed. Please try again."
    ): String = BlinkAuthFeedback.friendly(message, fallback)
}
