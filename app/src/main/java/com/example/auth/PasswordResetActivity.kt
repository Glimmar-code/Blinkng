package com.example.auth

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
import com.example.util.startActivitySafely
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
        // Password recovery uses its own one-use access token and never imports it into
        // SupabaseService. Therefore cancellation/completion must not erase an unrelated
        // account that was already signed in before the recovery link was opened.
        val hasExistingSession =
            !SupabaseService.accessToken().isNullOrBlank() ||
                !SupabaseService.refreshToken().isNullOrBlank()
        AccountSessionStore.setSignInRequired(applicationContext, !hasExistingSession)

        if (showSuccess) {
            val message = if (hasExistingSession) {
                "Password updated."
            } else {
                "Password updated. Sign in with your new password."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        if (
            startActivitySafely(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                "Unable to reopen Blink."
            )
        ) {
            finish()
        }
    }
}
