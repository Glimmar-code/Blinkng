package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }
private val authInk = Color(0xFF080808)
private val authHint = Color(0xFF777777)
private val authLine = Color(0xFFDFDFDF)

/**
 * Desktop equivalent of the Android BLINK reference: white form, bold BLINK
 * wordmark, black submit button and the same real Supabase auth handlers.
 * Uses a centered desktop card instead of stretching the phone mock-up.
 */
@Composable
fun BlinkAuthScreen(state: DesktopAppState) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var showWelcome by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val textColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = authInk,
        unfocusedTextColor = authInk,
        focusedLabelColor = authInk,
        unfocusedLabelColor = authHint,
        cursorColor = authInk,
        focusedBorderColor = authInk,
        unfocusedBorderColor = authLine,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF1F1F1))
            .verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).padding(horizontal = 18.dp),
            shape = RoundedCornerShape(25.dp),
            color = Color.White,
            contentColor = authInk,
            border = BorderStroke(1.dp, authLine),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showWelcome) {
                    Text("B L I N K", color = authInk,
                        fontSize = 31.sp, fontWeight = FontWeight.Black)
                    Text("Welcome to BLINK", color = authInk,
                        fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        buildAnnotatedString {
                            append("Connect, discover and grow ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                                append("Beyond LINK")
                            }
                        },
                        color = authHint, fontSize = 15.sp
                    )
                    Text(
                        "Meet Students • Join Communities • Attend Events • Buy & Sell on Campus",
                        color = authHint, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showWelcome = false; mode = AuthMode.SIGN_IN; state.clearError() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = authInk, contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Log In") }
                    OutlinedButton(
                        onClick = { showWelcome = false; mode = AuthMode.SIGN_UP; state.clearError() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        border = BorderStroke(1.dp, authInk),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Create Account", color = authInk) }
                    OutlinedButton(
                        onClick = {
                            state.clearError()
                            scope.launch { runCatching { state.signInWithGoogle() } }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        border = BorderStroke(1.dp, authLine),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
                        Text("  Continue with Google", color = authInk)
                    }
                    Text("Already a student? Join your campus community today.",
                        color = authHint, fontSize = 12.sp)
                } else {
                Text("B L I N K", color = authInk,
                    fontSize = 31.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    when (mode) {
                        AuthMode.SIGN_IN -> "Welcome Back"
                        AuthMode.SIGN_UP -> "Create Your Student Account"
                        AuthMode.RESET -> "Reset Your Password"
                    },
                    fontSize = 26.sp, fontWeight = FontWeight.Black,
                )
                Text(
                    when (mode) {
                        AuthMode.SIGN_IN -> "Log in to your BLINK account"
                        AuthMode.SIGN_UP -> "Join a brighter campus community of real students."
                        AuthMode.RESET -> "Enter the email linked to your BLINK account."
                    },
                    color = authHint, fontSize = 13.sp,
                )

                Spacer(Modifier.height(8.dp))
                if (mode == AuthMode.SIGN_UP) {
                    OutlinedTextField(
                        value = fullName, onValueChange = { fullName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, colors = textColors,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Full Name", color = authHint) },
                    )
                }

                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, colors = textColors,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(if (mode == AuthMode.SIGN_IN) "Email or Username" else "Email", color = authHint)
                    },
                )

                if (mode != AuthMode.RESET) {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, colors = textColors,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Password", color = authHint) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show", color = authInk)
                            }
                        },
                    )
                }
                if (mode == AuthMode.SIGN_UP) {
                    Text(
                        "Choose your university when completing your campus profile.",
                        color = authHint, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode == AuthMode.SIGN_IN) {
                    TextButton(
                        onClick = { mode = AuthMode.RESET; notice = null; state.clearError() },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Forgot Password?", color = authInk,
                            textDecoration = TextDecoration.Underline)
                    }
                }

                state.errorMessage?.let {
                    Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
                }
                notice?.let {
                    Text(it, color = Color(0xFF3B3B3B), fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        state.clearError()
                        notice = null
                        scope.launch {
                            runCatching {
                                when (mode) {
                                    AuthMode.SIGN_IN -> state.signIn(email, password)
                                    AuthMode.SIGN_UP -> state.signUp(email, password, fullName)
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
                        containerColor = authInk, contentColor = Color.White,
                        disabledContainerColor = Color(0xFF6E6E6E),
                        disabledContentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White, strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            when (mode) {
                                AuthMode.SIGN_IN -> "Log In"
                                AuthMode.SIGN_UP -> "Create Account"
                                AuthMode.RESET -> "Send Reset Email"
                            }, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.ArrowForward, contentDescription = null)
                    }
                }

                if (mode != AuthMode.RESET) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = authLine)
                        Text("OR", color = authHint, modifier = Modifier.padding(horizontal = 15.dp))
                        HorizontalDivider(Modifier.weight(1f), color = authLine)
                    }
                    OutlinedButton(
                        onClick = {
                            state.clearError()
                            scope.launch { runCatching { state.signInWithGoogle() } }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, authInk),
                    ) {
                        Text("Continue with Google", color = authInk)
                    }
                }

                when (mode) {
                    AuthMode.SIGN_IN -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Don't have an account?", color = authHint, fontSize = 12.sp)
                            TextButton(onClick = { mode = AuthMode.SIGN_UP; state.clearError() }) {
                                Text("Sign Up", color = authInk,
                                    textDecoration = TextDecoration.Underline)
                            }
                        }
                    }
                    AuthMode.SIGN_UP -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Already have an account?", color = authHint, fontSize = 12.sp)
                            TextButton(onClick = { mode = AuthMode.SIGN_IN; state.clearError() }) {
                                Text("Log In", color = authInk,
                                    textDecoration = TextDecoration.Underline)
                            }
                        }
                    }
                    AuthMode.RESET -> {
                        TextButton(onClick = { mode = AuthMode.SIGN_IN; state.clearError() }) {
                            Text("Back to Log In", color = authInk,
                                textDecoration = TextDecoration.Underline)
                        }
                    }
                }
                }
            }
        }
    }
}
