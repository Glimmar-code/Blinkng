package com.example.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.example.MainActivity
import com.example.data.supabase.SupabaseService

/**
 * Receives the Supabase OAuth redirect:
 * blink://auth/callback
 *
 * Supabase can return OAuth values in the URI fragment (implicit flow),
 * while some configurations return values in the query string. Handle both
 * so the Google flow does not appear to stop after returning to the app.
 */
class GoogleAuthCallbackActivity : Activity() {

    companion object {
        private const val TAG = "GoogleAuthCallback"
        private const val ACCESS_TOKEN = "access_token"
        private const val REFRESH_TOKEN = "refresh_token"
        private const val ERROR = "error"
        private const val ERROR_DESCRIPTION = "error_description"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data
        if (uri == null) {
            Log.e(TAG, "OAuth callback received without a URI")
            returnToMain()
            return
        }

        Log.d(TAG, "OAuth callback received: scheme=${uri.scheme}, host=${uri.host}, path=${uri.path}")

        val values = mutableMapOf<String, String>()

        // Implicit OAuth flow: Supabase returns tokens in the fragment.
        parsePairs(uri.fragment).forEach { (key, value) -> values[key] = value }

        // Be defensive: also accept query parameters if the provider/auth flow
        // returns them there instead of in the fragment.
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { values[key] = it }
        }

        getSharedPreferences("blink_auth_prefs", MODE_PRIVATE)
            .edit()
            .putLong("google_oauth_started_at", 0L)
            .apply()

        val error = values[ERROR]
        val errorDescription = values[ERROR_DESCRIPTION]
        if (!error.isNullOrBlank() || !errorDescription.isNullOrBlank()) {
            val message = errorDescription ?: error ?: "Google authentication failed."
            Log.e(TAG, "Google OAuth failed: $message")
            getSharedPreferences("blink_auth_prefs", MODE_PRIVATE)
                .edit()
                .putString("google_oauth_error", message)
                .apply()
            returnToMain()
            return
        }

        val accessToken = values[ACCESS_TOKEN].orEmpty()
        val refreshToken = values[REFRESH_TOKEN].orEmpty()

        if (accessToken.isBlank()) {
            Log.e(TAG, "Google OAuth callback contained no access token")
            getSharedPreferences("blink_auth_prefs", MODE_PRIVATE)
                .edit()
                .putString("google_oauth_error", "Google sign-in did not return a session. Please try again.")
                .apply()
            returnToMain()
            return
        }

        // Store the real Supabase Auth session. Never replace this with the
        // project's anon/publishable key.
        SupabaseService.saveSession(
            accessToken = accessToken,
            refreshToken = refreshToken.ifBlank { null }
        )

        getSharedPreferences("blink_auth_prefs", MODE_PRIVATE)
            .edit()
            .remove("google_oauth_error")
            .apply()

        Log.d(TAG, "Google OAuth session saved successfully")

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }

    private fun parsePairs(fragment: String?): Map<String, String> {
        if (fragment.isNullOrBlank()) return emptyMap()

        return fragment
            .removePrefix("#")
            .split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val parts = pair.split('=', limit = 2)
                if (parts.size != 2) return@mapNotNull null

                val key = Uri.decode(parts[0])
                val value = Uri.decode(parts[1])
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun returnToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
