package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AccountSessionStore
import com.example.auth.GoogleAuthLaunchGate
import com.example.auth.RememberedLoginStore
import com.example.data.models.NigerianUniversities

/**
 * Real phone layouts from the supplied BLINK login/sign-up references.
 * Reference hero and three different student portrait crops ship in offline WebP;
 * no more sharing a tiny low-quality campus JPEG across all subjects.
 * Compact Android shows the functional phone-screen content; wide screens add
 * poster framing without turning interactive controls into a static picture.
 * All authentication is handled by the existing callbacks / Supabase flow.
 */
private val Ink = Color(0xFF080808)
private val Hint = Color(0xFF777777)
private val Line = Color(0xFFDFDFDF)

internal object SignupCampusPreference {
    private const val PREFS = "blink_signup_campus"
    private const val CAMPUS = "pending_university"

    fun set(context: Context, university: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CAMPUS, university).apply()
    }

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CAMPUS, "").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(CAMPUS).apply()
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier, color: Color = Ink) {
    Text(
        text = "B L I N K",
        color = color,
        fontSize = if (LocalDensity.current.fontScale > 1.35f) 22.sp else 27.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier,
        maxLines = 1
    )
}

@Composable
private fun ReferenceInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    visible: Boolean = false,
    onToggle: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
        singleLine = true,
        placeholder = { Text(label, color = Hint, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = Ink, modifier = Modifier.size(21.dp)) },
        trailingIcon = if (password) {
            {
                IconButton(onClick = onToggle) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (visible) "Hide password" else "Show password", tint = Hint)
                }
            }
        } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation()
            else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        shape = RoundedCornerShape(12.dp),
        textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink, unfocusedTextColor = Ink,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedBorderColor = Ink, unfocusedBorderColor = Line,
            cursorColor = Ink
        )
    )
}

@Composable
private fun SolidAuthButton(
    title: String,
    enabled: Boolean = true,
    working: Boolean = false,
    tag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black, contentColor = Color.White,
            disabledContainerColor = Color(0xFF666666), disabledContentColor = Color.White
        )
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp
                )
            } else {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ArrowForward, null,
                    modifier = Modifier.align(Alignment.CenterEnd).size(21.dp))
            }
        }
    }
}

@Composable
private fun OrSeparator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f), color = Line)
        Text("OR", color = Hint, fontSize = 13.sp)
        HorizontalDivider(Modifier.weight(1f), color = Line)
    }
}

@Composable
private fun GoogleOutlineButton(loading: Boolean, onClick: () -> Unit, tag: String) {
    OutlinedButton(
        onClick = onClick, enabled = !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(tag),
        border = BorderStroke(1.2.dp, Ink),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
        } else {
            GoogleLogoVector(Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(if (loading) "Opening Google..." else "Continue with Google",
            fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}


/**
 * Compact Android phones render the exact usable UI INSIDE the reference phone,
 * rather than shrinking a marketing poster (and its input targets) to illegibility.
 * Tablets/desktop-width Android screens include the poster headline and monochrome
 * framing outside a centered, 430dp-wide interactive screen.
 *
 * Never scale touch targets or font sizes down based on pixel density: Material's
 * dp/sp sizing, natural wrapping and independent vertical scrolling remain intact.
 */
@Composable
private fun AuthAdaptiveShell(
    posterTitle: String,
    posterSubtitle: String,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White)
            .testTag("auth_adaptive_stage")
    ) {
        val expanded = maxWidth >= 700.dp
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0B)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Image(
                        painter = painterResource(R.drawable.blink_auth_reference_poster_people),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.46f,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth().fillMaxHeight(0.66f)
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.0f to Color(0xFF0B0B0B),
                                0.40f to Color(0xE60B0B0B),
                                1.0f to Color.Transparent
                            )
                        )
                    )
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 36.dp, end = 22.dp, top = 45.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(posterTitle, color = Color.White, fontWeight = FontWeight.Black,
                            fontSize = if (maxWidth < 850.dp) 30.sp else 42.sp,
                            lineHeight = if (maxWidth < 850.dp) 33.sp else 43.sp)
                    Spacer(Modifier.height(24.dp))
                    Text("B L I N K", color = Color.White,
                        fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(posterSubtitle, color = Color(0xFFE5E5E5),
                        fontSize = 17.sp, lineHeight = 25.sp)
                    Spacer(Modifier.height(45.dp))
                    Text("REAL STUDENTS  ✦  NEW FRIENDS  ✦  START NOW",
                        color = Color(0xFFB7B7B7), fontSize = 13.sp)
                    }
                }
                Surface(
                    modifier = Modifier.widthIn(min = 330.dp, max = 450.dp)
                        .fillMaxHeight().padding(end = 20.dp, top = 12.dp, bottom = 12.dp)
                        .testTag("auth_reference_phone"),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White, contentColor = Ink,
                    border = BorderStroke(3.dp, Color(0xFF575757))
                ) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(25.dp))) { content() }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().testTag("auth_compact_phone")) { content() }
        }
    }
}


/** Preserve readable account navigation at 130-200% font scaling. */
@Composable
private fun AuthAccountSwitcher(prompt: String, link: String, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val stacked = maxWidth < 350.dp || LocalDensity.current.fontScale >= 1.3f
        if (stacked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(prompt, color = Hint, fontSize = 12.sp, textAlign = TextAlign.Center)
                TextButton(onClick = onClick) {
                    Text(link, color = Ink, fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prompt, color = Hint, fontSize = 12.sp)
                TextButton(onClick = onClick) {
                    Text(link, color = Ink, fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline)
                }
            }
        }
    }
}

@Composable
private fun AuthSocialCircle(
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        modifier = Modifier.size(65.dp).testTag(tag),
        shape = CircleShape,
        onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White, contentColor = Ink,
            disabledContainerColor = Color.White, disabledContentColor = Hint
        ),
        content = content
    )
}

@Composable
fun BlinkReferenceSignInScreen(
    initialIdentifier: String = "",
    onBack: () -> Unit,
    onSignInWithCredentials: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onGoogleSignIn: (String) -> Unit,
    onForgotPassword: (String, (Boolean, String) -> Unit) -> Unit,
    onSwitchToSignUp: () -> Unit
) {
    val context = LocalContext.current
    val stored = remember { RememberedLoginStore.load(context) }
    var identifier by rememberSaveable { mutableStateOf(stored?.identifier?.ifBlank { initialIdentifier } ?: initialIdentifier) }
    var password by remember { mutableStateOf(stored?.password.orEmpty()) }
    val rememberCredentials = stored != null
    var visible by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    val googleLoading by GoogleAuthLaunchGate.inFlight.collectAsState()

    AuthAdaptiveShell(
        posterTitle = "Log In And\nJump Back In",
        posterSubtitle = "Secure access to your profile, messages, coins and campus activity."
    ) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().height(
            (screenHeight * 0.36f).coerceIn(210f, 315f).dp
        )) {
            Image(
                painter = painterResource(R.drawable.blink_auth_reference_login_photo),
                contentDescription = "Students together on campus",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Wordmark(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
                color = Ink
            )
            // The extracted reference scene already includes its hand-lettered art.
            // Do not paint a duplicate phrase over the original photograph.
        }
        Column(
            modifier = Modifier.fillMaxWidth().offset(y = (-28).dp)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color.White)
                .padding(horizontal = 22.dp).padding(top = 28.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome Back", color = Ink, fontSize = 31.sp,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(3.dp))
            Text("Log in to your BLINK account", color = Hint, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            error?.let {
                Text(it, color = Color(0xFFBC2525), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            }
            ReferenceInput(
                identifier, { identifier = it; error = null }, "Email or Username",
                Icons.Default.Email, Modifier.testTag("signin_email_field"),
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(10.dp))
            ReferenceInput(
                password, { password = it; error = null }, "Password", Icons.Default.Lock,
                Modifier.testTag("signin_password_field"), password = true,
                visible = visible, onToggle = { visible = !visible }
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { forgot = true }) {
                    Text("Forgot Password?", color = Ink, textDecoration = TextDecoration.Underline,
                        fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            SolidAuthButton("Log In", enabled = !submitting, working = submitting,
                tag = "signin_submit_btn") {
                if (identifier.isBlank() || password.isBlank()) {
                    error = "Enter your email or username and password."
                } else {
                    submitting = true
                    error = null
                    onSignInWithCredentials(identifier.trim(), password) { success, message ->
                        submitting = false
                        if (success) {
                            AccountSessionStore.setSignInRequired(context, !rememberCredentials)
                            if (rememberCredentials) RememberedLoginStore.save(context, identifier.trim(), password)
                            else RememberedLoginStore.clear(context)
                        } else {
                            error = message ?: "Unable to log in. Please try again."
                        }
                    }
                }
            }
            OrSeparator()
            GoogleOutlineButton(googleLoading, { onGoogleSignIn("") }, "signin_google_btn")
            Spacer(Modifier.height(21.dp))
            AuthAccountSwitcher("Don't have an account?", "Sign Up", onSwitchToSignUp)
        }
    }
    }
    if (forgot) {
        ForgotPasswordDialog(
            initialEmail = identifier.takeIf { it.contains("@") }.orEmpty(),
            onDismiss = { forgot = false },
            onSendReset = onForgotPassword
        )
    }
}

@Composable
private fun CampusDialog(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val results = remember(search) {
        NigerianUniversities.all.filter { it.contains(search.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose your university", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    singleLine = true, label = { Text("Search universities") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(results) { university ->
                        Text(
                            university, color = Ink,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(university) }
                                .padding(vertical = 11.dp, horizontal = 5.dp),
                            fontWeight = if (selected == university) FontWeight.Bold else FontWeight.Normal
                        )
                        HorizontalDivider(color = Line)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } },
        containerColor = Color.White
    )
}

@Composable
fun BlinkReferenceCreateAccountScreen(
    onBack: () -> Unit,
    onCreateAccount: (String, String, String) -> Unit,
    onGoogle: (String) -> Unit,
    onLogin: () -> Unit
) {
    val context = LocalContext.current
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var university by rememberSaveable { mutableStateOf("") }
    var pickingUniversity by remember { mutableStateOf(false) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var confirmTerms by remember { mutableStateOf(false) }
    val emailFocus = remember { FocusRequester() }
    var error by remember { mutableStateOf<String?>(null) }
    val googleLoading by GoogleAuthLaunchGate.inFlight.collectAsState()

    AuthAdaptiveShell(
        posterTitle = "Create Your\nStudent Account",
        posterSubtitle = "Sign up in moments and unlock your campus community, market and messages."
    ) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Wordmark()
            Spacer(Modifier.height(6.dp))
            Text(
                "Join a brighter campus community\nof real students.",
                color = Hint, fontSize = 14.sp, lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            // Size against actual available width: no clipping at 320dp or 200% fonts.
            BoxWithConstraints(Modifier.fillMaxWidth().height(142.dp)) {
                val side = (maxWidth.value * 0.27f).coerceIn(66f, 94f).dp
                val center = (maxWidth.value * 0.34f).coerceIn(82f, 124f).dp
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy((-10).dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = listOf(Color(0xFFE8D7FA), Color(0xFFFFE4A9), Color(0xFFDCEEFF))
                    repeat(3) { i ->
                        Box(
                            Modifier.size(if (i == 1) center else side)
                                .background(colors[i], CircleShape)
                                .clip(CircleShape).border(1.dp, Color.White, CircleShape)
                        ) {
                            Image(
                                painter = painterResource(listOf(
                                    R.drawable.blink_auth_reference_student_left,
                                    R.drawable.blink_auth_reference_student_center,
                                    R.drawable.blink_auth_reference_student_right
                                )[i]),
                                contentDescription = if (i == 1) "Campus students" else null,
                                contentScale = ContentScale.Crop,
                                alignment = when (i) {
                                    0 -> Alignment.CenterStart
                                    1 -> Alignment.Center
                                    else -> Alignment.CenterEnd
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            error?.let {
                Text(it, color = Color(0xFFBC2525), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            }
            ReferenceInput(fullName, { fullName = it.take(60); error = null }, "Full Name",
                Icons.Default.Person, Modifier.testTag("create_account_name"))
            Spacer(Modifier.height(10.dp))
            ReferenceInput(email, { email = it.take(120); error = null }, "Email",
                Icons.Default.Email, Modifier.testTag("create_account_email").focusRequester(emailFocus), KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { pickingUniversity = true },
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("create_account_university"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Line),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) {
                Icon(Icons.Default.School, null, tint = Ink, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(17.dp))
                Text(
                    university.ifBlank { "University" },
                    modifier = Modifier.weight(1f), color = if (university.isBlank()) Hint else Ink,
                    fontSize = 14.sp, maxLines = 1
                )
                Icon(Icons.Default.ExpandMore, "Select university", tint = Ink)
            }
            Spacer(Modifier.height(10.dp))
            ReferenceInput(password, { password = it; error = null }, "Password",
                Icons.Default.Lock, Modifier.testTag("create_account_password"), password = true,
                visible = visible, onToggle = { visible = !visible })
            Spacer(Modifier.height(6.dp))
            SolidAuthButton("Create Account", enabled = true,
                tag = "create_account_submit") {
                error = when {
                    fullName.trim().length < 2 -> "Enter your full name."
                    !email.contains("@") -> "Enter a valid email address."
                    university.isBlank() -> "Select your university."
                    !isStrongBlinkPassword(password) -> "Use at least 8 characters with uppercase, lowercase, a number, and a symbol."
                     else -> null
                }
                if (error == null && !acceptedTerms) {
                    confirmTerms = true
                } else if (error == null) {
                    // Save only this campus choice for the existing onboarding screen;
                    // passwords stay with the current Supabase sign-up callback.
                    SignupCampusPreference.set(context, university)
                    onCreateAccount(fullName.trim(), email.trim().lowercase(), password)
                }
            }
            OrSeparator()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthSocialCircle(
                    tag = "create_account_google",
                    enabled = !googleLoading,
                    onClick = { onGoogle("") }
                ) {
                    if (googleLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.dp)
                    else GoogleLogoVector(Modifier.size(23.dp))
                }
                Spacer(Modifier.width(24.dp))
                // Apple login must remain disabled until a real provider is configured.
                AuthSocialCircle(
                    tag = "create_account_apple_unavailable",
                    enabled = false, onClick = {}
                ) { Text("Apple", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(24.dp))
                AuthSocialCircle(
                    tag = "create_account_email_focus",
                    onClick = { emailFocus.requestFocus() }
                ) { Icon(Icons.Default.MailOutline, contentDescription = "Sign up with email") }
            }
            Spacer(Modifier.height(13.dp))
            AuthAccountSwitcher("Already have an account?", "Log In", onLogin)
        }
    }
    }
    if (confirmTerms) {
        AlertDialog(
            onDismissRequest = { confirmTerms = false },
            title = { Text("BLINK terms and community guidelines", color = Ink) },
            text = { Text("By creating an account you agree to follow BLINK's terms and community guidelines.", color = Ink) },
            confirmButton = {
                TextButton(onClick = {
                    acceptedTerms = true
                    confirmTerms = false
                    SignupCampusPreference.set(context, university)
                    onCreateAccount(fullName.trim(), email.trim().lowercase(), password)
                }) { Text("Agree & Create Account", color = Ink) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTerms = false }) { Text("Cancel", color = Ink) }
            },
            containerColor = Color.White
        )
    }
    if (pickingUniversity) {
        CampusDialog(
            university, { pickingUniversity = false },
            { university = it; pickingUniversity = false; error = null }
        )
    }
}
