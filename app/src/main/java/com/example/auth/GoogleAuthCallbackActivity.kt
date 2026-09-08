package com.example.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.R
import com.example.data.repository.AuthRepository
import com.example.data.supabase.SupabaseService
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Native Android Sign in with Google entry point.
 *
 * This activity is opened only after the user explicitly taps Blink's
 * "Continue with Google" button. Google recommends GetSignInWithGoogleOption
 * for that explicit button flow; GetGoogleIdOption is intended for the
 * Credential Manager bottom-sheet/automatic account discovery flow.
 *
 * Google Credential Manager returns an ID token directly to Blink. Supabase Auth
 * validates that token and creates the normal Supabase access/refresh session used
 * by the rest of the app.
 *
 * The raw nonce is kept by Blink for Supabase validation while its SHA-256 form is
 * sent to Google, matching Supabase's recommended native Google sign-in flow.
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
            failAndReturnToSignIn("Google Sign-In is not configured for this build.")
            return
        }

        // One cryptographically-random nonce is used for both the Google request and
        // the Supabase ID-token exchange so replay protection remains intact.
        val rawNonce = GoogleNonce.generate()
        val googleNonce = GoogleNonce.sha256Hex(rawNonce)

        // This is an explicit Sign in with Google button, so use Google's dedicated
        // button option. Using GetGoogleIdOption here can result in Credential Manager
        // failing before the account chooser opens on some devices/emulators.
        val googleOption = GetSignInWithGoogleOption.Builder(
            serverClientId = webClientId
        )
            .setNonce(googleNonce)
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
                failAndReturnToSignIn("Google returned an unsupported credential. Please try again.")
                return
            }

            val googleCredential = try {
                GoogleIdTokenCredential.createFrom(customCredential.data)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to parse Google ID token", error)
                failAndReturnToSignIn("Google returned an invalid sign-in response. Please try again.")
                return
            }

            val idToken = googleCredential.idToken
            if (idToken.isBlank()) {
                failAndReturnToSignIn("Google did not return an ID token. Please try again.")
                return
            }

            val result = authRepository.signInWithGoogleIdToken(
                idToken = idToken,
                nonce = rawNonce
            )

            if (!result.isSuccess) {
                failAndReturnToSignIn(result.errorMessage ?: "Google authentication failed. Please try again.")
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
        } catch (error: GetCredentialCancellationException) {
            Log.i(TAG, "Google credential flow cancelled by user")
            failAndReturnToSignIn("Google sign-in was cancelled. Tap Continue with Google to try again.")
        } catch (error: GetCredentialException) {
            // Keep the provider error type in Logcat. It is especially useful for
            // distinguishing a device/Play-services problem from a package/SHA OAuth
            // registration mismatch without exposing implementation details to users.
            Log.e(TAG, "Google Credential Manager failed: ${error.type}", error)
            failAndReturnToSignIn(
                "Google sign-in could not start. Update Google Play services, make sure you have internet, then try again."
            )
        } catch (error: Exception) {
            Log.e(TAG, "Native Google sign-in failed", error)
            failAndReturnToSignIn(error.message ?: "Unable to complete Google sign-in. Please try again.")
        }
    }

    /**
     * A failed Google attempt must never expose the stale Sign Up screen underneath.
     * Reset the partial auth attempt, force a fresh Sign In destination, and rebuild
     * MainActivity from a clean task. A later successful Google login clears the
     * sign-in-required flag again in AuthRepository.persistSession().
     */
    private fun failAndReturnToSignIn(message: String) {
        Log.w(TAG, "Returning to Blink sign-in after Google auth failure: $message")
        SupabaseService.clearSession()
        AccountSessionStore.setSignInRequired(applicationContext, true)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }

    override fun onDestroy() {
        if (isFinishing) GoogleAuthLaunchGate.end()
        super.onDestroy()
    }
}
