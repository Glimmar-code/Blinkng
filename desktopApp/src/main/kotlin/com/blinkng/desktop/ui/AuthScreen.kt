package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }

@Composable
fun BlinkAuthScreen(state: DesktopAppState) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val authFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color(0xFFB8B8B8),
        cursorColor = Color.White,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color(0xFF454545),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )
    val authOutline = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF080808),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BlinkDesktopLogo(size = 54.dp, showText = false)
                    Text(
                        "BLINK",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    when (mode) {
                        AuthMode.SIGN_IN -> "Sign in to your Blink account"
                        AuthMode.SIGN_UP -> "Create your Blink account"
                        AuthMode.RESET -> "Reset your password"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (mode == AuthMode.SIGN_UP) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = authFieldColors,
                        label = { Text("Full name") },
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = authFieldColors,
                        label = { Text("Username") },
                        prefix = { Text("@") },
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = authFieldColors,
                    label = { Text("Email") },
                )

                if (mode != AuthMode.RESET) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = authFieldColors,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }

                state.errorMessage?.let {
                    Text(it, color = Color(0xFFFF7A7A), fontSize = 13.sp)
                }
                notice?.let {
                    Text(it, color = Color(0xFFD0D0D0), fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        state.clearError()
                        notice = null
                        scope.launch {
                            runCatching {
                                when (mode) {
                                    AuthMode.SIGN_IN -> state.signIn(email, password)
                                    AuthMode.SIGN_UP -> state.signUp(email, password, username, fullName)
                                    AuthMode.RESET -> {
                                        state.sendPasswordReset(email)
                                        notice = "Password reset email sent. Check your inbox."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF333333),
                        disabledContentColor = Color(0xFF8A8A8A),
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            when (mode) {
                                AuthMode.SIGN_IN -> "Sign in"
                                AuthMode.SIGN_UP -> "Create account"
                                AuthMode.RESET -> "Send reset email"
                            },
                        )
                    }
                }

                if (mode != AuthMode.RESET) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(
                            onClick = {
                                state.clearError()
                                scope.launch { runCatching { state.signInWithGoogle() } }
                            },
                            enabled = !state.busy,
                            colors = OutlinedButtonDefaults.colors(contentColor = Color.White),
                            border = authOutline,
                        ) {
                            Text("Continue with Google")
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    when (mode) {
                        AuthMode.SIGN_IN -> {
                            OutlinedButton(
                                onClick = { mode = AuthMode.SIGN_UP; state.clearError() },
                                colors = OutlinedButtonDefaults.colors(contentColor = Color.White),
                                border = authOutline,
                            ) { Text("Create account") }
                            OutlinedButton(
                                onClick = { mode = AuthMode.RESET; state.clearError() },
                                colors = OutlinedButtonDefaults.colors(contentColor = Color.White),
                                border = authOutline,
                            ) { Text("Forgot password") }
                        }
                        AuthMode.SIGN_UP -> {
                            OutlinedButton(
                                onClick = { mode = AuthMode.SIGN_IN; state.clearError() },
                                colors = OutlinedButtonDefaults.colors(contentColor = Color.White),
                                border = authOutline,
                            ) { Text("Back to sign in") }
                            Spacer(Modifier.weight(1f))
                        }
                        AuthMode.RESET -> {
                            OutlinedButton(
                                onClick = { mode = AuthMode.SIGN_IN; state.clearError() },
                                colors = OutlinedButtonDefaults.colors(contentColor = Color.White),
                                border = authOutline,
                            ) { Text("Back to sign in") }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    "Blinkng for Windows uses the same account, posts, messages and Supabase backend as the Android app.",
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
