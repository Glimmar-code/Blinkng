package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlinkBlack
import com.example.ui.theme.BlinkCream
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkTextMuted

@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onCreateAccount: (String, String, String) -> Unit,
    onGoogle: (String) -> Unit,
    onLogin: () -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    val ready = fullName.trim().length >= 2 &&
        email.contains("@") &&
        isStrongBlinkPassword(password) &&
        acceptedTerms

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 36.dp)
        ) {
            item {
                AuthTopBar(
                    onBack = onBack,
                    title = "Create account",
                    subtitle = "Start your BLINK profile"
                )

                Spacer(Modifier.height(18.dp))

                GoogleSignInButton(
                    text = "Continue with Google",
                    onClick = { onGoogle("") },
                    modifier = Modifier.testTag("create_account_google")
                )

                Spacer(Modifier.height(16.dp))
                AuthDivider(text = "OR CREATE WITH EMAIL")
                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut()
                ) {
                    AuthMessageCard(
                        message = error.orEmpty(),
                        success = false,
                        onDismiss = { error = null }
                    )
                }

                AuthField(
                    value = fullName,
                    onValueChange = {
                        fullName = it.take(60)
                        error = null
                    },
                    label = "Full name",
                    icon = Icons.Default.Person,
                    testTag = "create_account_name"
                )

                Spacer(Modifier.height(12.dp))

                AuthField(
                    value = email,
                    onValueChange = {
                        email = it.take(120)
                        error = null
                    },
                    label = "Email address",
                    icon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email,
                    testTag = "create_account_email"
                )

                Spacer(Modifier.height(12.dp))

                AuthPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    visible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                    onFocus = {},
                    testTag = "create_account_password"
                )

                PasswordStrengthBar(password = password)

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acceptedTerms,
                        onCheckedChange = { acceptedTerms = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BlinkGold,
                            checkmarkColor = BlinkBlack,
                            uncheckedColor = DarkTextMuted
                        )
                    )
                    Text(
                        "I agree to BLINK's terms and community guidelines.",
                        color = Color.White.copy(alpha = .70f),
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        error = when {
                            fullName.trim().length < 2 -> "Enter your full name."
                            !email.contains("@") -> "Enter a valid email address."
                            !isStrongBlinkPassword(password) ->
                                "Use at least 8 characters with uppercase, lowercase, a number, and a symbol."
                            !acceptedTerms -> "Please accept BLINK's terms to continue."
                            else -> null
                        }

                        if (error == null) {
                            submitting = true
                            onCreateAccount(
                                fullName.trim(),
                                email.trim().lowercase(),
                                password
                            )
                        }
                    },
                    enabled = ready && !submitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlinkCream,
                        contentColor = BlinkBlack,
                        disabledContainerColor = DarkSurfaceElevated,
                        disabledContentColor = DarkTextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp)
                        .testTag("create_account_submit")
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Create account", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Already have an account? Log in")
                }
            }
        }
    }
}
