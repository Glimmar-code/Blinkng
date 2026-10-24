package com.blinkng.shared

/**
 * Same public, account-enumeration-safe authentication feedback on Android and Windows.
 * Never expose unrecognized provider internals or confirm whether an email exists.
 */
object BlinkAuthFeedback {
    const val BAD_CREDENTIALS = "The email/username or password is incorrect."

    fun friendly(message: String?, fallback: String = "Authentication failed. Please try again."): String {
        val lower = message.orEmpty().trim().lowercase()
        if (lower.isBlank()) return fallback
        return when {
            "invalid login credentials" in lower ||
                "invalid email or password" in lower ||
                "invalid email/username or password" in lower ||
                "invalid credentials" in lower ||
                "incorrect password" in lower ||
                "wrong password" in lower ||
                "user not found" in lower ||
                "username was not found" in lower -> BAD_CREDENTIALS
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
