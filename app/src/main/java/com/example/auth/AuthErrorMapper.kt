package com.example.auth

/** Converts provider/network errors into stable, user-safe authentication messages. */
object AuthErrorMapper {
    fun friendly(message: String?, fallback: String = "Authentication failed. Please try again."): String {
        val raw = message.orEmpty().trim()
        if (raw.isBlank()) return fallback
        val lower = raw.lowercase()
        return when {
            "invalid login credentials" in lower ||
                "invalid email or password" in lower ||
                "invalid email/username or password" in lower -> "The email/username or password is incorrect."
            "email not confirmed" in lower || "email_not_confirmed" in lower ->
                "Confirm your email address before signing in."
            "too many requests" in lower || "rate limit" in lower || "over_request_rate_limit" in lower ->
                "Too many attempts. Please wait a little and try again."
            "network" in lower || "timeout" in lower || "timed out" in lower ||
                "unable to resolve host" in lower || "connection" in lower ->
                "Blink could not reach the server. Check your connection and try again."
            "already registered" in lower || "already exists" in lower ->
                "An account already uses that email address."
            "username" in lower && ("taken" in lower || "exists" in lower) ->
                "That username is already taken."
            "google" in lower && ("configured" in lower || "client" in lower) ->
                "Google Sign-In is not configured correctly for this build."
            else -> fallback
        }
    }
}
