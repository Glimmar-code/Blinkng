package com.example.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.data.supabase.SupabaseService
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
                val recoverySession = remember { recovery.getOrNull() }
                var password by remember { mutableStateOf("") }
                var confirmPassword by remember { mutableStateOf("") }
                var isSubmitting by remember { mutableStateOf(false) }
                var error by remember {
                    mutableStateOf(
                        recovery.exceptionOrNull()?.message
                            ?.takeIf { it.isNotBlank() }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Reset password", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (recoverySession != null) {
                            "Choose a new password for your Blink account."
                        } else {
                            "This reset link cannot be used. Request a fresh link from the sign-in screen."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(24.dp))

                    if (recoverySession != null) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                error = null
                            },
                            label = { Text("New password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                error = null
                            },
                            label = { Text("Confirm new password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            if (recoverySession == null) {
                                return@Button
                            }
                            when {
                                password.length < 8 -> error = "Password must be at least 8 characters."
                                password != confirmPassword -> error = "The passwords do not match."
                                else -> {
                                    isSubmitting = true
                                    error = null
                                    scope.launch {
                                        PasswordRecoveryClient.updatePassword(
                                            accessToken = recoverySession.accessToken,
                                            newPassword = password
                                        ).fold(
                                            onSuccess = {
                                                isSubmitting = false
                                                finishResetAndRequireFreshSignIn()
                                            },
                                            onFailure = { failure ->
                                                isSubmitting = false
                                                error = failure.message ?: "Password could not be updated."
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (recoverySession != null) "Update password" else "Link unavailable")
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = { finishResetAndRequireFreshSignIn(showSuccess = false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to sign in")
                    }
                }
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
