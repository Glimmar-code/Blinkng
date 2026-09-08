package com.example.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.rememberCoroutineScope
import com.example.MainActivity
import com.example.data.supabase.SupabaseService
import com.example.ui.screens.ResetPasswordScreen
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.launch

/** Receives blink://auth/reset-password links from Supabase recovery emails. */
class PasswordResetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)
        val recovery = PasswordRecoveryClient.parseRecoveryLink(intent?.data)

        setContent {
            BlinkTheme {
                val scope = rememberCoroutineScope()
                val recoverySession = recovery.getOrNull()

                ResetPasswordScreen(
                    onSubmit = { password, onResult ->
                        if (recoverySession == null) {
                            onResult(
                                false,
                                recovery.exceptionOrNull()?.message
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "This reset link cannot be used. Request a fresh link from the sign-in screen."
                            )
                        } else {
                            scope.launch {
                                PasswordRecoveryClient.updatePassword(
                                    accessToken = recoverySession.accessToken,
                                    newPassword = password
                                ).fold(
                                    onSuccess = {
                                        onResult(true, "Password updated.")
                                        finishResetAndRequireFreshSignIn()
                                    },
                                    onFailure = { failure ->
                                        onResult(
                                            false,
                                            failure.message ?: "Password could not be updated."
                                        )
                                    }
                                )
                            }
                        }
                    },
                    onCancel = {
                        finishResetAndRequireFreshSignIn(showSuccess = false)
                    }
                )
            }
        }
    }

    private fun finishResetAndRequireFreshSignIn(showSuccess: Boolean = true) {
        // A recovery token is intentionally not imported as the app's durable session.
        // Require one clean sign-in so no old account/token state can be mixed in.
        SupabaseService.clearSession()
        AccountSessionStore.setSignInRequired(applicationContext, true)
        getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("blink_user_session", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", false)
            .apply()

        if (showSuccess) {
            Toast.makeText(this, "Password updated. Sign in with your new password.", Toast.LENGTH_LONG).show()
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
