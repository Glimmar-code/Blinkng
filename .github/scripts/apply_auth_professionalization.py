from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected exactly one marker, found {count}')
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# New auth primitives
# -----------------------------------------------------------------------------
write(
    'app/src/main/java/com/example/auth/GoogleAuthLaunchGate.kt',
    '''package com.example.auth

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide state for the explicit Google credential flow. */
object GoogleAuthLaunchGate {
    private val started = AtomicBoolean(false)
    private val _inFlight = MutableStateFlow(false)
    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    fun tryStart(): Boolean {
        if (!started.compareAndSet(false, true)) return false
        _inFlight.value = true
        return true
    }

    fun end() {
        started.set(false)
        _inFlight.value = false
    }
}
'''
)

write(
    'app/src/main/java/com/example/auth/AuthErrorMapper.kt',
    '''package com.example.auth

/** Converts provider/network errors into stable, user-safe authentication messages. */
object AuthErrorMapper {
    fun friendly(message: String?, fallback: String = "Authentication failed. Please try again."): String {
        val raw = message.orEmpty().trim()
        if (raw.isBlank()) return fallback
        val lower = raw.lowercase()
        return when {
            "invalid login credentials" in lower ||
                "invalid email or password" in lower ||
                "invalid email/username or password" in lower -> "The email/username or password is incorrect."
            "email not confirmed" in lower || "email_not_confirmed" in lower ->
                "Confirm your email address before signing in."
            "too many requests" in lower || "rate limit" in lower || "over_request_rate_limit" in lower ->
                "Too many attempts. Please wait a little and try again."
            "network" in lower || "timeout" in lower || "timed out" in lower ||
                "unable to resolve host" in lower || "connection" in lower ->
                "Blink could not reach the server. Check your connection and try again."
            "already registered" in lower || "already exists" in lower ->
                "An account already uses that email address."
            "username" in lower && ("taken" in lower || "exists" in lower) ->
                "That username is already taken."
            "google" in lower && ("configured" in lower || "client" in lower) ->
                "Google Sign-In is not configured correctly for this build."
            else -> fallback
        }
    }
}
'''
)

write(
    'app/src/main/java/com/example/auth/PasswordRecoveryLinkParser.kt',
    '''package com.example.auth

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parsed payload from the Supabase recovery redirect back into Blink. */
data class PasswordRecoveryLink(
    val accessToken: String = "",
    val refreshToken: String = "",
    val error: String? = null
)

object PasswordRecoveryLinkParser {
    fun parse(rawUrl: String?): PasswordRecoveryLink? {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isBlank()) return null

        val isBlinkRecovery = raw.startsWith("blink://reset-password", ignoreCase = true)
        val isHttpsRecovery = Regex("^https://[^/]+/.*/?auth/reset-password", RegexOption.IGNORE_CASE)
            .containsMatchIn(raw) || raw.contains("/auth/reset-password", ignoreCase = true)
        if (!isBlinkRecovery && !isHttpsRecovery) return null

        val params = linkedMapOf<String, String>()
        fun parsePart(part: String) {
            part.split('&').forEach { pair ->
                if (pair.isBlank()) return@forEach
                val pieces = pair.split('=', limit = 2)
                val key = decode(pieces.getOrElse(0) { "" })
                val value = decode(pieces.getOrElse(1) { "" })
                if (key.isNotBlank()) params[key] = value
            }
        }

        raw.substringAfter('?', "").substringBefore('#').takeIf { it.isNotBlank() }?.let(::parsePart)
        raw.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::parsePart)

        val providerError = params["error_description"]
            ?: params["error"]
            ?: params["message"]

        return PasswordRecoveryLink(
            accessToken = params["access_token"].orEmpty(),
            refreshToken = params["refresh_token"].orEmpty(),
            error = providerError?.takeIf { it.isNotBlank() }
        )
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
'''
)

write(
    'app/src/main/java/com/example/ui/screens/ResetPasswordScreen.kt',
    '''package com.example.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlinkCream
import com.example.ui.theme.BlinkBlack
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkTextSecondary

@Composable
fun ResetPasswordScreen(
    onSubmit: (String, (Boolean, String) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val strong = password.length >= 8 &&
        password.any(Char::isLowerCase) &&
        password.any(Char::isUpperCase) &&
        password.any(Char::isDigit) &&
        password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    val matches = password.isNotBlank() && password == confirmation

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create a new password",
                color = BlinkCream,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Use at least 8 characters with uppercase, lowercase, a number and a symbol.",
                color = DarkTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; message = null },
                modifier = Modifier.fillMaxWidth().testTag("reset_password_field"),
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors()
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it; message = null },
                modifier = Modifier.fillMaxWidth().testTag("reset_password_confirm_field"),
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors()
            )

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    when {
                        !strong -> message = "Use at least 8 characters with uppercase, lowercase, a number and a symbol."
                        !matches -> message = "The passwords do not match."
                        else -> {
                            isSubmitting = true
                            onSubmit(password) { success, resultMessage ->
                                isSubmitting = false
                                if (!success) message = resultMessage
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlinkCream,
                    contentColor = BlinkBlack,
                    disabledContainerColor = DarkSurfaceElevated,
                    disabledContentColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("reset_password_submit")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Update password", fontWeight = FontWeight.Bold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onCancel) {
                    Text("Back to sign in", color = DarkTextSecondary)
                }
            }
        }
    }
}
'''
)

# -----------------------------------------------------------------------------
# AuthRepository: friendly errors, unique username only, safe defaults, launch gate
# -----------------------------------------------------------------------------
path = 'app/src/main/java/com/example/data/repository/AuthRepository.kt'
text = read(path)
if 'import com.example.auth.AuthErrorMapper' not in text:
    text = text.replace(
        'import com.example.auth.AccountSessionStore\n',
        'import com.example.auth.AccountSessionStore\nimport com.example.auth.AuthErrorMapper\nimport com.example.auth.GoogleAuthLaunchGate\n'
    )
text = text.replace('faculty: String = "SIMME"', 'faculty: String = ""', 1)
text = re.sub(
    r'\n\s*if \(availability\?\.fullNameAvailable == false\) \{\n\s*return@withContext AuthResult\.failure\("That display name is already in use\. Add a middle name or another identifier\."\)\n\s*\}',
    '',
    text,
    count=1
)
text = text.replace(
    'if (result.isSuccess) { val profile = result.getOrThrow(); persistSession(profile); _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken()); AuthResult.success(profile) } else AuthResult.failure(result.exceptionOrNull()?.message ?: "Sign up failed.")',
    'if (result.isSuccess) { val profile = result.getOrThrow(); persistSession(profile); _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken()); AuthResult.success(profile) } else AuthResult.failure(AuthErrorMapper.friendly(result.exceptionOrNull()?.message, "Sign up failed."))',
    1
)
text = text.replace(
    '} catch (e: Exception) { Log.e("AuthRepository", "signUpWithEmail error", e); AuthResult.failure(e.message ?: "Sign up failed.") }',
    '} catch (e: Exception) { Log.e("AuthRepository", "signUpWithEmail error", e); AuthResult.failure(AuthErrorMapper.friendly(e.message, "Sign up failed.")) }',
    1
)
text = text.replace(
    'if (result.isSuccess) { val profile = result.getOrThrow(); persistSession(profile); _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken()); AuthResult.success(profile) } else AuthResult.failure(result.exceptionOrNull()?.message ?: "Invalid email/username or password.")',
    'if (result.isSuccess) { val profile = result.getOrThrow(); persistSession(profile); _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken()); AuthResult.success(profile) } else AuthResult.failure(AuthErrorMapper.friendly(result.exceptionOrNull()?.message, "Unable to sign in."))',
    1
)
text = text.replace(
    '} catch (e: Exception) { Log.e("AuthRepository", "signInWithEmail error", e); AuthResult.failure(e.message ?: "Connection error. Please try again.") }',
    '} catch (e: Exception) { Log.e("AuthRepository", "signInWithEmail error", e); AuthResult.failure(AuthErrorMapper.friendly(e.message, "Unable to sign in.")) }',
    1
)
old_google = '''    suspend fun signInWithGoogle(email: String): AuthResult = withContext(Dispatchers.Main) {
        try {
            context.startActivity(
                Intent(context, GoogleAuthCallbackActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            AuthResult.failure("GOOGLE_OAUTH_STARTED")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Unable to launch native Google sign-in", e)
            AuthResult.failure("Unable to open Google sign-in on this device.")
        }
    }
'''
new_google = '''    suspend fun signInWithGoogle(email: String): AuthResult = withContext(Dispatchers.Main) {
        if (!GoogleAuthLaunchGate.tryStart()) {
            return@withContext AuthResult.failure("GOOGLE_OAUTH_STARTED")
        }
        try {
            context.startActivity(
                Intent(context, GoogleAuthCallbackActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            AuthResult.failure("GOOGLE_OAUTH_STARTED")
        } catch (e: Exception) {
            GoogleAuthLaunchGate.end()
            Log.e("AuthRepository", "Unable to launch native Google sign-in", e)
            AuthResult.failure(AuthErrorMapper.friendly(e.message, "Unable to open Google sign-in on this device."))
        }
    }
'''
text = replace_once(text, old_google, new_google, 'AuthRepository Google launch')
text = text.replace(
    'AuthResult.failure(e.message ?: "Google authentication failed.")',
    'AuthResult.failure(AuthErrorMapper.friendly(e.message, "Google authentication failed."))',
    1
)
write(path, text)

# -----------------------------------------------------------------------------
# Google callback releases the real in-flight state only when the flow finishes
# -----------------------------------------------------------------------------
path = 'app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt'
text = read(path)
if 'override fun onDestroy()' not in text:
    marker = '    private fun generateSecureRandomNonce(byteLength: Int = 32): String {'
    insertion = '''    override fun onDestroy() {
        if (isFinishing) GoogleAuthLaunchGate.end()
        super.onDestroy()
    }

'''
    text = replace_once(text, marker, insertion + marker, 'Google callback onDestroy')
write(path, text)

# -----------------------------------------------------------------------------
# Supabase password recovery redirect + update-password endpoint + safe defaults
# -----------------------------------------------------------------------------
path = 'app/src/main/java/com/example/data/supabase/SupabaseService.kt'
text = read(path)
text = text.replace('faculty: String = "SIMME",\n        university: String = "University of Lagos"', 'faculty: String = "",\n        university: String = ""', 1)
text = text.replace(
    '"/auth/v1/recover",',
    '"/auth/v1/recover?redirect_to=${URLEncoder.encode("blink://reset-password", StandardCharsets.UTF_8.name())}",',
    1
)
if 'suspend fun updatePassword(' not in text:
    method = '''

    /** Updates the authenticated recovery session password. */
    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val strongPassword = newPassword.length >= 8 &&
            newPassword.any(Char::isLowerCase) &&
            newPassword.any(Char::isUpperCase) &&
            newPassword.any(Char::isDigit) &&
            newPassword.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (!strongPassword) {
            return@withContext Result.failure(
                Exception("Use at least 8 characters with uppercase, lowercase, a number, and a symbol.")
            )
        }
        try {
            val body = JSONObject().put("password", newPassword)
            val request = newRequestBuilder("/auth/v1/user", authenticated = true)
                .put(body.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(request).use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(parseSupabaseError(responseBody, "Unable to update password."))
                    )
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AUTH_UPDATE_PASSWORD exception", e)
            Result.failure(Exception(e.message ?: "Unable to update password."))
        }
    }
'''
    idx = text.rfind('\n}')
    if idx < 0:
        raise RuntimeError('SupabaseService final class marker not found')
    text = text[:idx] + method + text[idx:]
write(path, text)

# -----------------------------------------------------------------------------
# ViewModel routes recovery links and owns the password update transition
# -----------------------------------------------------------------------------
path = 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
text = read(path)
if 'import com.example.auth.AuthErrorMapper' not in text:
    text = text.replace(
        'import com.example.auth.AccountSessionStore\n',
        'import com.example.auth.AccountSessionStore\nimport com.example.auth.AuthErrorMapper\nimport com.example.auth.PasswordRecoveryLinkParser\n'
    )
text = text.replace(
    'enum class AppDestination { SPLASH, ONBOARDING, SIGN_IN, SIGN_UP, PROFILE_SETUP, MAIN }',
    'enum class AppDestination { SPLASH, ONBOARDING, SIGN_IN, SIGN_UP, RESET_PASSWORD, PROFILE_SETUP, MAIN }',
    1
)
if 'fun handleAuthDeepLink(' not in text:
    anchor = '''    fun handleDeepLink(link: AppDeepLink) {
        pendingDeepLink = link
        val state = _uiState.value
        if (state.destination == AppDestination.MAIN && state.myProfile.id.isNotBlank()) {
            pendingDeepLink = null
            routeDeepLink(link)
        }
    }
'''
    addition = anchor + '''
    /** Returns true when the incoming URI is an authentication/recovery route. */
    fun handleAuthDeepLink(uri: Uri?): Boolean {
        val recovery = PasswordRecoveryLinkParser.parse(uri?.toString()) ?: return false
        if (!recovery.error.isNullOrBlank()) {
            SupabaseService.clearSession()
            AccountSessionStore.setSignInRequired(appContext, true)
            _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            showToast("That password reset link is invalid or expired. Request a new one.")
            return true
        }
        if (recovery.accessToken.isBlank()) {
            SupabaseService.clearSession()
            AccountSessionStore.setSignInRequired(appContext, true)
            _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            showToast("That password reset link is incomplete or expired. Request a new one.")
            return true
        }
        SupabaseService.saveSession(
            accessToken = recovery.accessToken,
            refreshToken = recovery.refreshToken.ifBlank { null }
        )
        AccountSessionStore.setSignInRequired(appContext, false)
        _uiState.value = _uiState.value.copy(destination = AppDestination.RESET_PASSWORD)
        return true
    }

    fun updateRecoveredPassword(newPassword: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = supabaseService.updatePassword(newPassword)
            if (result.isSuccess) {
                SupabaseService.clearSession()
                AccountSessionStore.setSignInRequired(appContext, true)
                prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
                authPrefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
                val message = "Password updated. Sign in with your new password."
                showToast(message)
                onResult(true, message)
            } else {
                val message = AuthErrorMapper.friendly(
                    result.exceptionOrNull()?.message,
                    "Unable to update password. Request a new reset link and try again."
                )
                onResult(false, message)
            }
        }
    }

    fun cancelPasswordRecovery() {
        SupabaseService.clearSession()
        AccountSessionStore.setSignInRequired(appContext, true)
        _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
    }
'''
    text = replace_once(text, anchor, addition, 'BlinkViewModel auth deep link insertion')
text = text.replace(
    'val msg = if (success) "Password reset instructions sent to $email." else "Could not send password reset email."',
    'val msg = if (success) "If an account exists for that email, a password reset link has been sent." else "Could not send the password reset email. Check your connection and try again."',
    1
)
write(path, text)

# -----------------------------------------------------------------------------
# MainActivity: auth deep links have priority over public content links + reset route
# -----------------------------------------------------------------------------
path = 'app/src/main/java/com/example/MainActivity.kt'
text = read(path)
old = '''    private fun handleIncomingIntent(intent: Intent?) {
        handleNotificationIntent(intent)
        DeepLinkRouter.parse(intent?.data)?.let { deepLink ->
            viewModel.handleDeepLink(deepLink)
            intent?.data = null
        }
    }
'''
new = '''    private fun handleIncomingIntent(intent: Intent?) {
        handleNotificationIntent(intent)
        if (viewModel.handleAuthDeepLink(intent?.data)) {
            intent?.data = null
            return
        }
        DeepLinkRouter.parse(intent?.data)?.let { deepLink ->
            viewModel.handleDeepLink(deepLink)
            intent?.data = null
        }
    }
'''
text = replace_once(text, old, new, 'MainActivity incoming auth route')
needle = '''                                AppDestination.SIGN_UP -> {
                                    SignUpScreen(
                                        onBack = { viewModel.setDestination(AppDestination.ONBOARDING) },
                                        onSuccess = { name, user, email, pass, fac ->
                                            viewModel.signUp(name, user, email, pass, fac)
                                        },
                                        onGoogleSignUp = { email -> viewModel.loginWithGoogle(email) },
                                        onSwitchToSignIn = { viewModel.setDestination(AppDestination.SIGN_IN) }
                                    )
                                }

                                AppDestination.PROFILE_SETUP -> {
'''
replacement = '''                                AppDestination.SIGN_UP -> {
                                    SignUpScreen(
                                        onBack = { viewModel.setDestination(AppDestination.ONBOARDING) },
                                        onSuccess = { name, user, email, pass, fac ->
                                            viewModel.signUp(name, user, email, pass, fac)
                                        },
                                        onGoogleSignUp = { email -> viewModel.loginWithGoogle(email) },
                                        onSwitchToSignIn = { viewModel.setDestination(AppDestination.SIGN_IN) }
                                    )
                                }

                                AppDestination.RESET_PASSWORD -> {
                                    ResetPasswordScreen(
                                        onSubmit = { password, onResult ->
                                            viewModel.updateRecoveredPassword(password, onResult)
                                        },
                                        onCancel = { viewModel.cancelPasswordRecovery() }
                                    )
                                }

                                AppDestination.PROFILE_SETUP -> {
'''
text = replace_once(text, needle, replacement, 'MainActivity reset destination')
write(path, text)

# -----------------------------------------------------------------------------
# Manifest: route the Supabase recovery redirect into MainActivity
# -----------------------------------------------------------------------------
path = 'app/src/main/AndroidManifest.xml'
text = read(path)
if 'android:host="reset-password"' not in text:
    marker = '''            <!-- Temporary/fallback app-only links let the web preview hand an
                 interaction to Blink before the production domain is available. -->
            <intent-filter>
'''
    auth_filter = '''            <!-- Supabase password-recovery redirect. Add blink://reset-password
                 to Supabase Auth > URL Configuration > Redirect URLs. -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="blink" android:host="reset-password" />
            </intent-filter>

'''
    text = replace_once(text, marker, auth_filter + marker, 'Manifest recovery deep link')
write(path, text)

# -----------------------------------------------------------------------------
# Sign-in UX: real Google loading, meaningful remember-me, no strength meter on login
# -----------------------------------------------------------------------------
path = 'app/src/main/java/com/example/ui/screens/AuthScreens.kt'
text = read(path)
if 'import com.example.auth.GoogleAuthLaunchGate' not in text:
    text = text.replace(
        'import com.example.auth.RememberedLoginStore\n',
        'import com.example.auth.RememberedLoginStore\nimport com.example.auth.AccountSessionStore\nimport com.example.auth.GoogleAuthLaunchGate\n'
    )
text = text.replace(
    '''    var googleLoading by remember {
        mutableStateOf(false)
    }
''',
    '''    val googleLoading by GoogleAuthLaunchGate.inFlight.collectAsState()
''',
    1
)
text = text.replace(
    '''    var rememberMe by rememberSaveable {
        mutableStateOf(true)
    }

    val coroutineScope =
        rememberCoroutineScope()
''',
    '''    var rememberMe by rememberSaveable {
        mutableStateOf(rememberedLogin != null)
    }
''',
    1
)
old_click = '''                    onClick = {

                        if (googleLoading) {
                            return@GoogleSignInButton
                        }

                        googleLoading = true
                        authError = null

                        // Credential Manager / Google picker is triggered by AuthRepository.
                        onGoogleSignIn("")

                        coroutineScope.launch {
                            delay(1200)
                            googleLoading = false
                        }
                    },
'''
new_click = '''                    onClick = {
                        if (!googleLoading) {
                            authError = null
                            onGoogleSignIn("")
                        }
                    },
'''
text = replace_once(text, old_click, new_click, 'AuthScreens Google click')
text = text.replace('label = "University Email or Username"', 'label = "Email or username"', 1)
text = text.replace('"Enter your university email or username."', '"Enter your email or username."', 1)
text = text.replace(
    '''                PasswordStrengthBar(
                    password = password
                )

''',
    '',
    1
)
text = text.replace('"Remember username & password"', '"Keep me signed in"', 1)
callback_old = '''                                    isSubmitting = false

                                    if (!success) {
                                        authError =
                                            errorMessage
                                                ?: "Unable to sign in."
                                    }
'''
callback_new = '''                                    isSubmitting = false

                                    if (success) {
                                        AccountSessionStore.setSignInRequired(context, !rememberMe)
                                        if (rememberMe) {
                                            RememberedLoginStore.save(context, emailOrUsername.trim(), password)
                                        } else {
                                            RememberedLoginStore.clear(context)
                                        }
                                    } else {
                                        authError =
                                            errorMessage
                                                ?: "Unable to sign in."
                                    }
'''
text = replace_once(text, callback_old, callback_new, 'AuthScreens remember-me callback')
text = text.replace('fontSize = 9.sp', 'fontSize = 11.sp')
write(path, text)

# -----------------------------------------------------------------------------
# Tests
# -----------------------------------------------------------------------------
write(
    'app/src/test/java/com/example/auth/PasswordRecoveryLinkParserTest.kt',
    '''package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordRecoveryLinkParserTest {
    @Test
    fun parsesFragmentTokens() {
        val link = PasswordRecoveryLinkParser.parse(
            "blink://reset-password#access_token=abc123&refresh_token=refresh456&type=recovery"
        )
        assertNotNull(link)
        assertEquals("abc123", link?.accessToken)
        assertEquals("refresh456", link?.refreshToken)
        assertNull(link?.error)
    }

    @Test
    fun parsesProviderError() {
        val link = PasswordRecoveryLinkParser.parse(
            "blink://reset-password#error=access_denied&error_description=Link%20expired"
        )
        assertEquals("Link expired", link?.error)
    }

    @Test
    fun ignoresNonRecoveryLinks() {
        assertNull(PasswordRecoveryLinkParser.parse("blink://post/123"))
    }
}
'''
)

write(
    'app/src/test/java/com/example/auth/AuthErrorMapperTest.kt',
    '''package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun mapsInvalidCredentials() {
        assertEquals(
            "The email/username or password is incorrect.",
            AuthErrorMapper.friendly("Invalid login credentials")
        )
    }

    @Test
    fun mapsRateLimit() {
        assertEquals(
            "Too many attempts. Please wait a little and try again.",
            AuthErrorMapper.friendly("Too many requests")
        )
    }

    @Test
    fun hidesUnknownProviderDetails() {
        assertEquals(
            "Unable to sign in.",
            AuthErrorMapper.friendly("internal provider implementation detail", "Unable to sign in.")
        )
    }
}
'''
)

print('Auth professionalization patch applied.')
