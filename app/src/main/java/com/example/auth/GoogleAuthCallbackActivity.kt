package com.example.auth

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.R
import com.example.data.repository.AuthRepository
import com.example.data.supabase.SupabaseService
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Native Android Sign in with Google entry point.
 *
 * The old implementation received a browser redirect at blink://auth/callback.
 * Blink now uses Android Credential Manager instead: Google returns an ID token
 * directly to the app, and Supabase Auth validates that token and creates the
 * normal Supabase access/refresh session used everywhere else in Blink.
 */
class GoogleAuthCallbackActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BlinkGoogleAuth"
    }

    private val credentialManager by lazy { CredentialManager.create(this) }
    private val authRepository by lazy { AuthRepository(applicationContext, SupabaseService()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)

        if (savedInstanceState == null) {
            lifecycleScope.launch { startNativeGoogleSignIn() }
        }
    }

    private suspend fun startNativeGoogleSignIn() {
        val webClientId = runCatching { getString(R.string.default_web_client_id) }
            .getOrDefault("")
            .trim()

        if (webClientId.isBlank()) {
            failAndFinish("Google Sign-In is not configured for this build.")
            return
        }

        val rawNonce = generateSecureRandomNonce()
        val hashedNonce = sha256Hex(rawNonce)

        val googleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        try {
            val response = credentialManager.getCredential(
                context = this,
                request = request
            )

            val customCredential = response.credential as? CustomCredential
            if (
                customCredential == null ||
                customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                failAndFinish("Google returned an unsupported credential.")
                return
            }

            val googleCredential = try {
                GoogleIdTokenCredential.createFrom(customCredential.data)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to parse Google ID token", error)
                failAndFinish("Google returned an invalid sign-in response.")
                return
            }

            val idToken = googleCredential.idToken
            if (idToken.isBlank()) {
                failAndFinish("Google did not return an ID token.")
                return
            }

            val result = authRepository.signInWithGoogleIdToken(
                idToken = idToken,
                nonce = rawNonce
            )

            if (!result.isSuccess) {
                failAndFinish(result.errorMessage ?: "Google authentication failed.")
                return
            }

            Log.d(TAG, "Native Google authentication completed successfully")
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        } catch (error: GetCredentialException) {
            Log.i(TAG, "Google credential flow closed: ${error.type}")
            // Cancellation is normal; return silently to Blink. For other credential
            // errors the user can tap Continue with Google again.
            finish()
        } catch (error: Exception) {
            Log.e(TAG, "Native Google sign-in failed", error)
            failAndFinish(error.message ?: "Unable to complete Google sign-in.")
        }
    }

    private fun failAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
}
