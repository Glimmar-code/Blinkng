package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.discovery.BlinkCampusAccountType
import com.blinkng.shared.discovery.BlinkUniversitySearch
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }

@Composable
fun BlinkAuthScreen(state: DesktopAppState) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var showWelcome by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var universityQuery by remember { mutableStateOf("") }
    var chosenUniversity by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("") }
    var showUniversities by remember { mutableStateOf(false) }
    var showAccountTypes by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).heightIn(max = 760.dp).padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (showWelcome) {
                    BlinkDesktopLogo(size = 46.dp, showText = true)
                    Text("Welcome to BLINK", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Connect, discover and grow Beyond LINK",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Meet Students     •     Join Communities     •     Attend Events     •     Buy & Sell on Campus",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showWelcome = false; mode = AuthMode.SIGN_IN; state.clearError() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Log In") }
                    OutlinedButton(
                        onClick = { showWelcome = false; mode = AuthMode.SIGN_UP; state.clearError() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Create Account") }
                    OutlinedButton(
                        onClick = {
                            state.clearError()
                            scope.launch { runCatching { state.signInWithGoogle() } }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("G", color = androidx.compose.ui.graphics.Color(0xFF4285F4), fontWeight = FontWeight.Bold)
                        Text("  Continue with Google")
                    }
                    HorizontalDivider()
                    Text("Already a student?  Join your campus community today.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                BlinkDesktopLogo(size = 46.dp, showText = true)
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
                        label = { Text("Full name") },
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Username") },
                        prefix = { Text("@") },
                    )
                    OutlinedTextField(
                        value = universityQuery,
                        onValueChange = {
                            universityQuery = it
                            chosenUniversity = ""
                            showUniversities = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search and select your university") },
                    )
                    if (showUniversities) {
                        val universities = BlinkUniversitySearch.search(universityQuery, limit = 6)
                        if (universities.isEmpty()) {
                            Text("No matching institution. Try a full name or abbreviation.",
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                universities.forEach { name ->
                                    OutlinedButton(
                                        onClick = {
                                            chosenUniversity = name
                                            universityQuery = name
                                            showUniversities = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(name) }
                                }
                            }
                        }
                    }
                    Text(
                        if (chosenUniversity.isNotBlank()) "University selected: $"+"chosenUniversity"
                        else "A university from the directory is required.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Box {
                        OutlinedButton(onClick = { showAccountTypes = true }) {
                            Text(
                                "Account type: " + (BlinkCampusAccountType.fromWire(accountType)?.label ?: "Select")
                            )
                        }
                        DropdownMenu(
                            expanded = showAccountTypes,
                            onDismissRequest = { showAccountTypes = false },
                        ) {
                            BlinkCampusAccountType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        accountType = option.name
                                        showAccountTypes = false
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (mode == AuthMode.SIGN_IN) "Email or username" else "Email") },
                )

                if (mode != AuthMode.RESET) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }

                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                notice?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        state.clearError()
                        notice = null
                        scope.launch {
                            runCatching {
                                when (mode) {
                                    AuthMode.SIGN_IN -> state.signIn(email, password)
                                    AuthMode.SIGN_UP -> state.signUp(
                                        email, password, username, fullName,
                                        chosenUniversity, accountType,
                                    )
                                    AuthMode.RESET -> {
                                        state.sendPasswordReset(email)
                                        notice = "Password reset email sent. Check your inbox."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !state.busy &&
                        (mode != AuthMode.SIGN_UP || (chosenUniversity.isNotBlank() && accountType.isNotBlank())),
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
                        ) {
                            Text("Continue with Google")
                        }
                    }
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    when (mode) {
                        AuthMode.SIGN_IN -> {
                            OutlinedButton(onClick = { mode = AuthMode.SIGN_UP; state.clearError() }) { Text("Create account") }
                            OutlinedButton(onClick = { mode = AuthMode.RESET; state.clearError() }) { Text("Forgot password") }
                        }
                        AuthMode.SIGN_UP -> {
                            OutlinedButton(onClick = { mode = AuthMode.SIGN_IN; state.clearError() }) { Text("Back to sign in") }
                            Spacer(Modifier.weight(1f))
                        }
                        AuthMode.RESET -> {
                            OutlinedButton(onClick = { mode = AuthMode.SIGN_IN; state.clearError() }) { Text("Back to sign in") }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    "Blinkng for Windows uses the same account, posts, messages and Supabase backend as the Android app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                }
            }
        }
    }
}
