package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.BlinkMark
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================================================================
// SPLASH SCREEN
// ================================================================

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    val infinite = rememberInfiniteTransition(
        label = "splash_animation"
    )

    val logoScale by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    val glowAlpha by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(Unit) {
        delay(1800)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF37104B),
                        Color(0xFF1B0929),
                        DarkBackground
                    ),
                    radius = 1100f
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(220.dp)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        listOf(
                            BlinkPink,
                            BlinkPurple,
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier.scale(logoScale)
            ) {
                BlinkMark(
                    size = 82.dp,
                    showText = false
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "BLINK",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 7.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = "CAMPUS • COMMUNITY • MARKET",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp,
                color = BlinkPink
            )

            Spacer(modifier = Modifier.height(42.dp))

            LoadingDots()
        }
    }
}

// ================================================================
// ONBOARDING
// ================================================================

@Composable
fun OnboardingScreen(
    onSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onGoogleSignIn: (String) -> Unit
) {
    var currentFeature by remember {
        mutableIntStateOf(0)
    }

    val features = remember {
        listOf(
            Triple(
                Icons.Default.PlayCircle,
                "Campus Reels",
                "Short videos, stories and live campus moments."
            ),
            Triple(
                Icons.Default.Storefront,
                "ALUTA Market",
                "Buy, sell and discover things around campus."
            ),
            Triple(
                Icons.Default.EmojiEvents,
                "Campus Rankings",
                "Discover trending students, creators and events."
            ),
            Triple(
                Icons.Default.Groups,
                "Student Community",
                "Connect with people in your university."
            )
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2800)
            currentFeature =
                (currentFeature + 1) % features.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF321143),
                        Color(0xFF180A26),
                        DarkBackground
                    ),
                    radius = 950f
                )
            )
    ) {

        DecorativeAuthBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal = 22.dp,
                    vertical = 14.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                BlinkMark(
                    size = 38.dp,
                    showText = true
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                PremiumCampusOrb()

                Spacer(
                    modifier = Modifier.height(23.dp)
                )

                Text(
                    text = "Your university life.\nAmplified.",
                    fontSize = 29.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )

                Spacer(
                    modifier = Modifier.height(11.dp)
                )

                Text(
                    text = "One place for campus conversations, Reels, Stories, rankings and ALUTA Market.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = DarkTextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                AnimatedContent(
                    targetState = currentFeature,
                    label = "feature_rotation"
                ) { index ->

                    PremiumFeatureCard(
                        icon = features[index].first,
                        title = features[index].second,
                        description = features[index].third
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Row {
                    repeat(features.size) { index ->

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(
                                    if (index == currentFeature)
                                        7.dp
                                    else
                                        5.dp
                                )
                                .background(
                                    if (index == currentFeature)
                                        BlinkPink
                                    else
                                        Color.White.copy(alpha = 0.25f),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // REAL GOOGLE AUTH ENTRY POINT
                GoogleSignInButton(
                    text = "Continue with Google",
                    onClick = {
                        // Empty string intentionally.
                        // AuthRepository opens the Android Google account chooser.
                        onGoogleSignIn("")
                    },
                    modifier = Modifier
                        .testTag("onboarding_google_btn")
                )

                PremiumAuthButton(
                    text = "Create Student Account",
                    icon = Icons.Default.PersonAdd,
                    onClick = onSignUpClick,
                    primary = true,
                    modifier = Modifier
                        .testTag("onboarding_signup_btn")
                )

                PremiumAuthButton(
                    text = "Sign In with Email",
                    icon = Icons.Default.Login,
                    onClick = onSignInClick,
                    primary = false,
                    modifier = Modifier
                        .testTag("onboarding_signin_btn")
                )

                Text(
                    text = "By continuing, you agree to Blink's community guidelines.",
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    color = DarkTextSecondary
                )
            }
        }
    }
}

// ================================================================
// SIGN IN
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    initialIdentifier: String = "",
    onBack: () -> Unit,
    onSignInWithCredentials: (
        emailOrUsername: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) -> Unit,
    onGoogleSignIn: (String) -> Unit,
    onForgotPassword: (
        email: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit,
    onSwitchToSignUp: () -> Unit
) {

    var emailOrUsername by rememberSaveable(initialIdentifier) {
        mutableStateOf(initialIdentifier)
    }

    var password by remember {
        mutableStateOf("")
    }

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    var isSubmitting by remember {
        mutableStateOf(false)
    }

    var googleLoading by remember {
        mutableStateOf(false)
    }

    var authError by remember {
        mutableStateOf<String?>(null)
    }

    var showForgotPasswordDialog by remember {
        mutableStateOf(false)
    }

    var rememberMe by remember {
        mutableStateOf(true)
    }

    val coroutineScope =
        rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 34.dp
            )
        ) {

            item {

                AuthTopBar(
                    onBack = onBack,
                    title = "Sign in",
                    subtitle = "Welcome back to Blink"
                )

                Spacer(
                    modifier = Modifier.height(17.dp)
                )

                AuthHeroBadge(
                    icon = Icons.Default.LockOpen,
                    title = "Welcome back 👋",
                    subtitle = "Your campus feed, communities and marketplace are waiting."
                )

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                // REAL GOOGLE ACCOUNT PICKER ENTRY POINT
                GoogleSignInButton(
                    text = if (googleLoading)
                        "Opening Google..."
                    else
                        "Continue with Google",
                    onClick = {

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
                    modifier = Modifier
                        .testTag("signin_google_btn")
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                AuthDivider(
                    text = "OR CONTINUE WITH EMAIL"
                )

                Spacer(
                    modifier = Modifier.height(17.dp)
                )

                AnimatedVisibility(
                    visible = authError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut()
                ) {

                    AuthMessageCard(
                        message = authError ?: "",
                        success = false,
                        onDismiss = {
                            authError = null
                        }
                    )
                }

                AuthField(
                    value = emailOrUsername,
                    onValueChange = {
                        emailOrUsername = it
                        authError = null
                    },
                    label = "University Email or Username",
                    icon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email,
                    testTag = "signin_email_field"
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                AuthPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        authError = null
                    },
                    visible = passwordVisible,
                    onToggleVisibility = {
                        passwordVisible =
                            !passwordVisible
                    },
                    onFocus = {},
                    testTag = "signin_password_field"
                )

                PasswordStrengthBar(
                    password = password
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = {
                                rememberMe = it
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BlinkPink,
                                uncheckedColor = DarkTextSecondary
                            )
                        )

                        Text(
                            "Keep me signed in",
                            color = DarkTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }

                    TextButton(
                        onClick = {
                            showForgotPasswordDialog = true
                        }
                    ) {

                        Text(
                            "Forgot password?",
                            color = BlinkLavender,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                Button(
                    onClick = {

                        when {

                            emailOrUsername.isBlank() -> {
                                authError =
                                    "Enter your university email or username."
                            }

                            password.isBlank() -> {
                                authError =
                                    "Enter your password."
                            }

                            else -> {

                                isSubmitting = true
                                authError = null

                                onSignInWithCredentials(
                                    emailOrUsername.trim(),
                                    password
                                ) { success, errorMessage ->

                                    isSubmitting = false

                                    if (!success) {
                                        authError =
                                            errorMessage
                                                ?: "Unable to sign in."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlinkPink
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp)
                        .testTag("signin_submit_btn")
                ) {

                    if (isSubmitting) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )

                    } else {

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                "Sign In to Campus",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(
                                modifier = Modifier.width(7.dp)
                            )

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(17.dp)
                )

                SecurityNotice()

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        "New to Blink?",
                        color = DarkTextSecondary,
                        fontSize = 12.5.sp
                    )

                    TextButton(
                        onClick = onSwitchToSignUp
                    ) {

                        Text(
                            "Create account",
                            color = BlinkPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showForgotPasswordDialog) {

            ForgotPasswordDialog(
                initialEmail =
                    if (emailOrUsername.contains("@"))
                        emailOrUsername
                    else
                        "",
                onDismiss = {
                    showForgotPasswordDialog = false
                },
                onSendReset = onForgotPassword
            )
        }
    }
}

// ================================================================
// SIGN UP
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onSuccess: (
        fullName: String,
        username: String,
        email: String,
        password: String,
        faculty: String
    ) -> Unit,
    onGoogleSignUp: (String) -> Unit,
    onSwitchToSignIn: () -> Unit
) {

    var fullName by remember {
        mutableStateOf("")
    }

    var username by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    var faculty by remember {
        mutableStateOf("SIMME")
    }

    var validationError by remember {
        mutableStateOf<String?>(null)
    }

    var googleLoading by remember {
        mutableStateOf(false)
    }

    var acceptTerms by remember {
        mutableStateOf(false)
    }

    val coroutineScope = rememberCoroutineScope()

    val isFormReady =
        fullName.trim().length >= 2 &&
                username.trim().length >= 3 &&
                email.contains("@") &&
                password.length >= 6 &&
                acceptTerms

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 36.dp
            )
        ) {

            item {

                AuthTopBar(
                    onBack = onBack,
                    title = "Create account",
                    subtitle = "Join your campus"
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                AuthHeroBadge(
                    icon = Icons.Default.School,
                    title = "Join Blink Campus 🎓",
                    subtitle = "Create your identity, find your people and become part of the campus conversation."
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                // REAL GOOGLE ACCOUNT PICKER
                GoogleSignInButton(
                    text = if (googleLoading)
                        "Opening Google..."
                    else
                        "Sign up with Google",
                    onClick = {

                        if (googleLoading) {
                            return@GoogleSignInButton
                        }

                        googleLoading = true
                        validationError = null

                        onGoogleSignUp("")

                        // Only controls button presentation.
                        // AuthRepository controls the actual Google flow.
                        coroutineScope.launch {
                            delay(1200)
                            googleLoading = false
                        }
                    },
                    modifier = Modifier.testTag(
                        "signup_google_btn"
                    )
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                AuthDivider(
                    text = "OR CREATE WITH EMAIL"
                )

                Spacer(
                    modifier = Modifier.height(17.dp)
                )

                AnimatedVisibility(
                    visible = validationError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut()
                ) {

                    AuthMessageCard(
                        message = validationError ?: "",
                        success = false,
                        onDismiss = {
                            validationError = null
                        }
                    )
                }

                AuthField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        validationError = null
                    },
                    label = "Full name",
                    icon = Icons.Default.Person,
                    testTag = "signup_name_field"
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                AuthField(
                    value = username,
                    onValueChange = {
                        username =
                            it.filter { char ->
                                char.isLetterOrDigit() ||
                                        char == '_' ||
                                        char == '.'
                            }.take(25)

                        validationError = null
                    },
                    label = "Campus username",
                    icon = Icons.Default.AlternateEmail,
                    testTag = "signup_username_field"
                )

                if (username.isNotBlank()) {

                    UsernamePreview(
                        username = username
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                AuthField(
                    value = email,
                    onValueChange = {
                        email = it
                        validationError = null
                    },
                    label = "University / Gmail address",
                    icon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email,
                    testTag = "signup_email_field"
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                AuthPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationError = null
                    },
                    visible = passwordVisible,
                    onToggleVisibility = {
                        passwordVisible =
                            !passwordVisible
                    },
                    onFocus = {},
                    testTag = "signup_password_field"
                )

                PasswordStrengthBar(
                    password = password
                )

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                Text(
                    "Choose your faculty",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextSecondary
                )

                Spacer(
                    modifier = Modifier.height(7.dp)
                )

                val faculties =
                    listOf(
                        "SIMME",
                        "ENGINEERING",
                        "LAW",
                        "ARTS",
                        "SCIENCE"
                    )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {

                    items(faculties) { fac ->

                        val selected =
                            faculty == fac

                        PremiumChoiceChip(
                            text = fac,
                            selected = selected,
                            onClick = {
                                faculty = fac
                            }
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(15.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Checkbox(
                        checked = acceptTerms,
                        onCheckedChange = {
                            acceptTerms = it
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BlinkPink
                        )
                    )

                    Text(
                        "I agree to Blink's community guidelines and terms.",
                        color = DarkTextSecondary,
                        fontSize = 10.5.sp
                    )
                }

                Spacer(
                    modifier = Modifier.height(13.dp)
                )

                Button(
                    onClick = {

                        when {

                            fullName.isBlank() ->
                                validationError =
                                    "Enter your full name."

                            username.length < 3 ->
                                validationError =
                                    "Your username should contain at least 3 characters."

                            !email.contains("@") ->
                                validationError =
                                    "Enter a valid email address."

                            password.length < 6 ->
                                validationError =
                                    "Your password should contain at least 6 characters."

                            !acceptTerms ->
                                validationError =
                                    "Please accept the community guidelines and terms."

                            else ->
                                onSuccess(
                                    fullName.trim(),
                                    username.trim(),
                                    email.trim(),
                                    password,
                                    faculty
                                )
                        }
                    },
                    enabled = isFormReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlinkPink,
                        disabledContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp)
                        .testTag("signup_submit_btn")
                ) {

                    Text(
                        "Continue to onboarding",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(
                        modifier = Modifier.width(7.dp)
                    )

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.height(17.dp)
                )

                SignupProgressIndicator(
                    ready = isFormReady
                )

                Spacer(
                    modifier = Modifier.height(15.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        "Already on Blink?",
                        color = DarkTextSecondary,
                        fontSize = 12.5.sp
                    )

                    TextButton(
                        onClick = onSwitchToSignIn
                    ) {

                        Text(
                            "Sign In",
                            color = BlinkPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ================================================================
// PROFILE SETUP
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupOnboardingScreen(
    studentName: String,
    studentUsername: String,
    onComplete: (
        university: String,
        department: String,
        level: String,
        bio: String,
        skills: List<String>
    ) -> Unit
) {

    val universities = listOf(
        "University of Lagos",
        "University of Benin",
        "Obafemi Awolowo University",
        "University of Nigeria Nsukka",
        "Ahmadu Bello University",
        "Federal University of Technology Akure",
        "University of Ibadan",
        "Lagos State University",
        "Covenant University"
    )

    val levels = listOf(
        "100 Level",
        "200 Level",
        "300 Level",
        "400 Level",
        "500 Level",
        "Postgraduate"
    )

    val interests = listOf(
        "Product Design",
        "Coding",
        "ALUTA Market",
        "Photography",
        "Gaming",
        "Content Creation",
        "Campus Politics",
        "Afrobeats",
        "Fashion",
        "Sports",
        "Events",
        "Tech"
    )

    var selectedUniversity by remember {
        mutableStateOf(
            "Federal University of Technology Akure"
        )
    }

    var department by remember {
        mutableStateOf("")
    }

    var selectedLevel by remember {
        mutableStateOf("200 Level")
    }

    var bio by remember {
        mutableStateOf("")
    }

    var currentStep by remember {
        mutableIntStateOf(0)
    }

    val selectedInterests =
        remember {
            mutableStateListOf<String>()
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 40.dp
            )
        ) {

            item {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    BlinkMark(
                        size = 32.dp,
                        showText = true
                    )

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = BlinkPink.copy(alpha = 0.10f)
                    ) {

                        Text(
                            text = "STEP ${currentStep + 1}/4",
                            color = BlinkPink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            )
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                LinearProgressIndicator(
                    progress = {
                        when (currentStep) {
                            0 -> 0.25f
                            1 -> 0.50f
                            2 -> 0.75f
                            else -> 1f
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = BlinkPink,
                    trackColor = DarkBorder
                )

                Spacer(
                    modifier = Modifier.height(22.dp)
                )

                AnimatedContent(
                    targetState = currentStep,
                    label = "onboarding_step"
                ) { step ->

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        when (step) {

                            0 -> {

                                OnboardingStepHeader(
                                    title = "Where do you study? 🎓",
                                    subtitle = "This helps Blink personalize your campus experience."
                                )

                                Spacer(
                                    modifier = Modifier.height(17.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {

                                    items(universities) { university ->

                                        PremiumChoiceChip(
                                            text = university,
                                            selected =
                                                selectedUniversity ==
                                                        university,
                                            onClick = {
                                                selectedUniversity =
                                                    university
                                            }
                                        )
                                    }
                                }
                            }

                            1 -> {

                                OnboardingStepHeader(
                                    title = "Tell us about your studies",
                                    subtitle = "Your academic profile helps organize your campus communities."
                                )

                                Spacer(
                                    modifier = Modifier.height(18.dp)
                                )

                                PremiumOnboardingField(
                                    value = department,
                                    onValueChange = {
                                        department = it
                                    },
                                    label = "Department / Course",
                                    icon = Icons.Default.School
                                )

                                Spacer(
                                    modifier = Modifier.height(18.dp)
                                )

                                Text(
                                    "Academic level",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )

                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {

                                    items(levels) { level ->

                                        PremiumChoiceChip(
                                            text = level,
                                            selected =
                                                selectedLevel == level,
                                            onClick = {
                                                selectedLevel = level
                                            }
                                        )
                                    }
                                }
                            }

                            2 -> {

                                OnboardingStepHeader(
                                    title = "What are you into? 🔥",
                                    subtitle = "Pick a few interests to improve your recommendations."
                                )

                                Spacer(
                                    modifier = Modifier.height(17.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {

                                    items(interests) { interest ->

                                        val selected =
                                            selectedInterests.contains(
                                                interest
                                            )

                                        PremiumChoiceChip(
                                            text = interest,
                                            selected = selected,
                                            onClick = {

                                                if (selected) {
                                                    selectedInterests.remove(
                                                        interest
                                                    )
                                                } else if (
                                                    selectedInterests.size < 6
                                                ) {
                                                    selectedInterests.add(
                                                        interest
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            else -> {

                                OnboardingStepHeader(
                                    title = "Give your profile a voice ✨",
                                    subtitle = "A short bio helps people know what you're about."
                                )

                                Spacer(
                                    modifier = Modifier.height(18.dp)
                                )

                                ProfilePreviewCard(
                                    name = studentName,
                                    username = studentUsername,
                                    university = selectedUniversity,
                                    department = department,
                                    level = selectedLevel
                                )

                                Spacer(
                                    modifier = Modifier.height(15.dp)
                                )

                                OutlinedTextField(
                                    value = bio,
                                    onValueChange = {
                                        bio =
                                            it.take(180)
                                    },
                                    label = {
                                        Text("Campus bio")
                                    },
                                    placeholder = {
                                        Text(
                                            "Student, creator, builder..."
                                        )
                                    },
                                    minLines = 4,
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BlinkPink,
                                            unfocusedBorderColor = DarkBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedLabelColor = BlinkPink,
                                            unfocusedLabelColor = DarkTextSecondary,
                                            focusedContainerColor = DarkSurface,
                                            unfocusedContainerColor = DarkSurface
                                        ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    "${bio.length}/180",
                                    fontSize = 9.sp,
                                    color = DarkTextSecondary,
                                    modifier = Modifier.align(
                                        Alignment.End
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(28.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {

                    if (currentStep > 0) {

                        OutlinedButton(
                            onClick = {
                                currentStep--
                            },
                            modifier = Modifier
                                .weight(0.35f)
                                .height(52.dp),
                            border = BorderStroke(
                                1.dp,
                                DarkBorder
                            )
                        ) {

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous"
                            )
                        }
                    }

                    Button(
                        onClick = {

                            if (currentStep < 3) {

                                currentStep++

                            } else {

                                onComplete(
                                    selectedUniversity,
                                    department,
                                    selectedLevel,
                                    bio,
                                    selectedInterests.toList()
                                )
                            }
                        },
                        enabled =
                            when (currentStep) {
                                0 ->
                                    selectedUniversity.isNotBlank()

                                1 ->
                                    department.isNotBlank()

                                2 ->
                                    selectedInterests.isNotEmpty()

                                else ->
                                    true
                            },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BlinkPink,
                                disabledContainerColor = DarkSurface
                            ),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .weight(
                                if (currentStep > 0)
                                    0.65f
                                else
                                    1f
                            )
                            .height(52.dp)
                            .testTag(
                                "onboarding_continue_btn"
                            )
                    ) {

                        Text(
                            if (currentStep < 3)
                                "Continue"
                            else
                                "Launch Blink 🚀",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(
                            modifier = Modifier.width(6.dp)
                        )

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

// ================================================================
// FORGOT PASSWORD
// ================================================================

@Composable
fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSendReset: (
        email: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit
) {

    var email by remember {
        mutableStateOf(initialEmail)
    }

    var sending by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    var success by remember {
        mutableStateOf(false)
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {

            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Surface(
                    shape = CircleShape,
                    color = BlinkPink.copy(alpha = 0.10f)
                ) {

                    Icon(
                        Icons.Default.LockReset,
                        contentDescription = "Password reset",
                        tint = BlinkPink,
                        modifier = Modifier.padding(15.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.height(15.dp)
                )

                Text(
                    "Reset your password",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    "Enter the email linked to your Blink account.",
                    color = DarkTextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                AnimatedVisibility(
                    visible = message != null,
                    enter = fadeIn() + expandVertically()
                ) {

                    AuthMessageCard(
                        message = message ?: "",
                        success = success,
                        onDismiss = {
                            message = null
                        }
                    )
                }

                if (!success) {

                    AuthField(
                        value = email,
                        onValueChange = {
                            email = it
                        },
                        label = "Email address",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )

                    Spacer(
                        modifier = Modifier.height(17.dp)
                    )

                    Button(
                        onClick = {

                            if (!email.contains("@")) {

                                message =
                                    "Enter a valid email address."

                                success = false

                                return@Button
                            }

                            sending = true
                            message = null

                            onSendReset(email.trim()) {
                                    ok,
                                    result ->

                                sending = false
                                success = ok
                                message = result
                            }
                        },
                        enabled =
                            !sending &&
                                    email.isNotBlank(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BlinkPink
                            ),
                        shape =
                            RoundedCornerShape(100.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(49.dp)
                    ) {

                        if (sending) {

                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )

                        } else {

                            Text(
                                "Send reset link",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                } else {

                    Button(
                        onClick = onDismiss,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BlinkPink
                            ),
                        shape =
                            RoundedCornerShape(100.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(49.dp)
                    ) {

                        Text(
                            "Back to Sign In",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                TextButton(
                    onClick = onDismiss
                ) {

                    Text(
                        "Cancel",
                        color = DarkTextSecondary
                    )
                }
            }
        }
    }
}

// ================================================================
// GOOGLE BUTTON
// ================================================================

@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            GoogleLogoVector(
                modifier = Modifier.size(20.dp)
            )

            Spacer(
                modifier = Modifier.width(11.dp)
            )

            Text(
                text,
                color = Color(0xFF202124),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ================================================================
// AUTH FIELD
// ================================================================

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    testTag: String? = null
) {

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        leadingIcon = {

            Icon(
                icon,
                contentDescription = null,
                tint = BlinkPink,
                modifier = Modifier.size(19.dp)
            )
        },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType
            ),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BlinkPink,
            unfocusedBorderColor = DarkBorder,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = BlinkPink,
            unfocusedLabelColor = DarkTextSecondary,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (testTag != null)
                    Modifier.testTag(testTag)
                else
                    Modifier
            )
    )
}

// ================================================================
// PASSWORD
// ================================================================

@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onFocus: () -> Unit,
    testTag: String? = null
) {

    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChange(it)
            onFocus()
        },
        label = {
            Text("Password")
        },
        leadingIcon = {

            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = BlinkPink,
                modifier = Modifier.size(19.dp)
            )
        },
        trailingIcon = {

            IconButton(
                onClick = onToggleVisibility
            ) {

                Icon(
                    imageVector =
                        if (visible)
                            Icons.Default.Visibility
                        else
                            Icons.Default.VisibilityOff,
                    contentDescription = "Toggle password visibility",
                    tint = DarkTextSecondary
                )
            }
        },
        visualTransformation =
            if (visible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BlinkPink,
            unfocusedBorderColor = DarkBorder,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = BlinkPink,
            unfocusedLabelColor = DarkTextSecondary,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (testTag != null)
                    Modifier.testTag(testTag)
                else
                    Modifier
            )
    )
}

// ================================================================
// PASSWORD STRENGTH
// ================================================================

@Composable
private fun PasswordStrengthBar(
    password: String
) {

    if (password.isBlank()) {
        return
    }

    val score =
        listOf(
            password.length >= 6,
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() }
        ).count { it }

    val progress = score / 4f

    val color =
        when (score) {
            4 -> Color(0xFF22C55E)
            3 -> Color(0xFFF59E0B)
            2 -> Color(0xFFFF8A00)
            else -> BlinkPink
        }

    Column(
        modifier = Modifier.padding(top = 6.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text =
                    when (score) {
                        4 -> "Strong password"
                        3 -> "Good password"
                        2 -> "Weak password"
                        else -> "Very weak password"
                    },
                color = color,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Text(
                "$score/4",
                color = DarkTextSecondary,
                fontSize = 9.sp
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        LinearProgressIndicator(
            progress = {
                progress
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = color,
            trackColor = DarkBorder
        )
    }
}

// ================================================================
// AUTH TOP BAR
// ================================================================

@Composable
private fun AuthTopBar(
    onBack: () -> Unit,
    title: String,
    subtitle: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(
                    Color.White.copy(alpha = 0.07f),
                    CircleShape
                )
        ) {

            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Column {

            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Text(
                subtitle,
                color = DarkTextSecondary,
                fontSize = 9.5.sp
            )
        }
    }
}

// ================================================================
// AUTH HERO
// ================================================================

@Composable
private fun AuthHeroBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color = BlinkPink.copy(alpha = 0.11f)
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = BlinkPink,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = Color.White
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                subtitle,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = DarkTextSecondary
            )
        }
    }
}

// ================================================================
// DIVIDER
// ================================================================

@Composable
private fun AuthDivider(
    text: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(DarkBorder)
        )

        Text(
            text,
            color = DarkTextSecondary,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(DarkBorder)
        )
    }
}

// ================================================================
// MESSAGE CARD
// ================================================================

@Composable
private fun AuthMessageCard(
    message: String,
    success: Boolean,
    onDismiss: () -> Unit
) {

    val color =
        if (success)
            Color(0xFF22C55E)
        else
            Color(0xFFFF4D4D)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(15.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            color
        )
    ) {

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                if (success)
                    Icons.Default.CheckCircle
                else
                    Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(21.dp)
            )

            Spacer(
                modifier = Modifier.width(9.dp)
            )

            Text(
                message,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {

                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = DarkTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ================================================================
// SECURITY NOTICE
// ================================================================

@Composable
private fun SecurityNotice() {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = Color(0xFF22C55E).copy(alpha = 0.07f)
    ) {

        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(18.dp)
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                "Your sign-in is handled through your configured authentication backend.",
                color = DarkTextSecondary,
                fontSize = 9.5.sp,
                lineHeight = 13.sp
            )
        }
    }
}

// ================================================================
// USERNAME PREVIEW
// ================================================================

@Composable
private fun UsernamePreview(
    username: String
) {

    Row(
        modifier = Modifier.padding(
            top = 5.dp,
            bottom = 3.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF22C55E),
            modifier = Modifier.size(12.dp)
        )

        Spacer(
            modifier = Modifier.width(4.dp)
        )

        Text(
            "@$username",
            color = Color(0xFF22C55E),
            fontSize = 9.5.sp
        )
    }
}

// ================================================================
// SIGNUP PROGRESS
// ================================================================

@Composable
private fun SignupProgressIndicator(
    ready: Boolean
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        repeat(5) { index ->

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(
                        RoundedCornerShape(100.dp)
                    )
                    .background(
                        if (ready || index < 2)
                            BlinkPink
                        else
                            DarkBorder
                    )
            )
        }
    }
}

// ================================================================
// CHOICE CHIP
// ================================================================

@Composable
private fun PremiumChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        shape = RoundedCornerShape(100.dp),
        color =
            if (selected)
                BlinkPink
            else
                DarkSurface,
        border =
            BorderStroke(
                1.dp,
                if (selected)
                    BlinkPink
                else
                    DarkBorder
            ),
        modifier = Modifier.clickable {
            onClick()
        }
    ) {

        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (selected) {

                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )

                Spacer(
                    modifier = Modifier.width(4.dp)
                )
            }

            Text(
                text,
                color =
                    if (selected)
                        Color.White
                    else
                        DarkTextSecondary,
                fontSize = 10.sp,
                fontWeight =
                    if (selected)
                        FontWeight.Bold
                    else
                        FontWeight.Medium
            )
        }
    }
}

// ================================================================
// ONBOARDING FIELD
// ================================================================

@Composable
private fun PremiumOnboardingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        leadingIcon = {

            Icon(
                icon,
                contentDescription = null,
                tint = BlinkPink
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BlinkPink,
            unfocusedBorderColor = DarkBorder,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = BlinkPink,
            unfocusedLabelColor = DarkTextSecondary,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ================================================================
// ONBOARDING HEADER
// ================================================================

@Composable
private fun OnboardingStepHeader(
    title: String,
    subtitle: String
) {

    Column {

        Text(
            title,
            fontSize = 23.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        Text(
            subtitle,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = DarkTextSecondary
        )
    }
}

// ================================================================
// PROFILE PREVIEW
// ================================================================

@Composable
private fun ProfilePreviewCard(
    name: String,
    username: String,
    university: String,
    department: String,
    level: String
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        )
    ) {

        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(55.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                BlinkPink,
                                BlinkPurple,
                                BlinkGold
                            )
                        ),
                        CircleShape
                    )
                    .padding(2.dp)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            DarkBackground,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text =
                            name.firstOrNull()
                                ?.uppercase()
                                ?: "B",
                        fontSize = 19.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(
                modifier = Modifier.width(11.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "@$username",
                    color = BlinkPink,
                    fontSize = 9.5.sp
                )

                Text(
                    "$department • $level",
                    color = DarkTextSecondary,
                    fontSize = 9.5.sp
                )

                Text(
                    university,
                    color = DarkTextSecondary,
                    fontSize = 9.sp
                )
            }

            Icon(
                Icons.Default.Verified,
                contentDescription = "Student badge",
                tint = BlinkPink,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

// ================================================================
// PREMIUM CAMPUS ORB
// ================================================================

@Composable
private fun PremiumCampusOrb() {

    val infinite =
        rememberInfiniteTransition(
            label = "orb_animation"
        )

    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                9000,
                easing = LinearEasing
            )
        ),
        label = "orb_rotation"
    )

    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                1100,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    Box(
        modifier = Modifier
            .size(112.dp)
            .scale(pulse)
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation
                }
                .background(
                    Brush.sweepGradient(
                        listOf(
                            BlinkPink,
                            BlinkPurple,
                            BlinkGold,
                            BlinkPink
                        )
                    ),
                    CircleShape
                )
                .padding(3.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        DarkSurface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    Icons.Default.School,
                    contentDescription = "Campus",
                    tint = BlinkPink,
                    modifier = Modifier.size(49.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    Color(0xFF22C55E),
                    CircleShape
                )
                .border(
                    3.dp,
                    DarkBackground,
                    CircleShape
                )
                .align(Alignment.BottomEnd)
        )
    }
}

// ================================================================
// FEATURE CARD
// ================================================================

@Composable
private fun PremiumFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.08f)
        )
    ) {

        Row(
            modifier = Modifier.padding(
                horizontal = 13.dp,
                vertical = 11.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Surface(
                shape = CircleShape,
                color = BlinkPink.copy(alpha = 0.10f)
            ) {

                Icon(
                    icon,
                    contentDescription = null,
                    tint = BlinkPink,
                    modifier = Modifier.padding(9.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(9.dp)
            )

            Column {

                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )

                Text(
                    description,
                    color = DarkTextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

// ================================================================
// PREMIUM AUTH BUTTON
// ================================================================

@Composable
private fun PremiumAuthButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier
) {

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color =
            if (primary)
                BlinkPink
            else
                Color.Transparent,
        border =
            if (primary)
                null
            else
                BorderStroke(
                    1.dp,
                    BlinkPurple
                ),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )

            Spacer(
                modifier = Modifier.width(7.dp)
            )

            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
        }
    }
}

// ================================================================
// DECORATIVE BACKGROUND
// ================================================================

@Composable
private fun DecorativeAuthBackground() {

    val infinite =
        rememberInfiniteTransition(
            label = "background"
        )

    val y by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                2500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "background_y"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Box(
            modifier = Modifier
                .size(190.dp)
                .offset(
                    x = (-70).dp,
                    y = y.dp
                )
                .alpha(0.10f)
                .background(
                    BlinkPink,
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(
                    x = 275.dp,
                    y = 250.dp
                )
                .alpha(0.08f)
                .background(
                    BlinkPurple,
                    CircleShape
                )
        )
    }
}

// ================================================================
// LOADING DOTS
// ================================================================

@Composable
private fun LoadingDots() {

    val infinite =
        rememberInfiniteTransition(
            label = "loading_dots"
        )

    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                1200
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_phase"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        repeat(3) { index ->

            val active =
                phase > index * 0.25f

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(
                        if (active) 7.dp else 5.dp
                    )
                    .background(
                        if (active)
                            BlinkPink
                        else
                            Color.White.copy(alpha = 0.25f),
                        CircleShape
                    )
            )
        }
    }
}

// ================================================================
// GOOGLE LOGO
// ================================================================

@Composable
fun GoogleLogoVector(
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
    ) {

        val w = size.width
        val cx = w / 2f
        val cy = size.height / 2f
        val radius = w * 0.46f

        val blue = Color(0xFF4285F4)
        val green = Color(0xFF34A853)
        val yellow = Color(0xFFFBBC05)
        val red = Color(0xFFEA4335)

        drawArc(
            color = red,
            startAngle = 220f,
            sweepAngle = 70f,
            useCenter = true,
            topLeft = Offset(
                cx - radius,
                cy - radius
            ),
            size = Size(
                radius * 2,
                radius * 2
            )
        )

        drawArc(
            color = yellow,
            startAngle = 145f,
            sweepAngle = 78f,
            useCenter = true,
            topLeft = Offset(
                cx - radius,
                cy - radius
            ),
            size = Size(
                radius * 2,
                radius * 2
            )
        )

        drawArc(
            color = green,
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = true,
            topLeft = Offset(
                cx - radius,
                cy - radius
            ),
            size = Size(
                radius * 2,
                radius * 2
            )
        )

        drawArc(
            color = blue,
            startAngle = -35f,
            sweepAngle = 80f,
            useCenter = true,
            topLeft = Offset(
                cx - radius,
                cy - radius
            ),
            size = Size(
                radius * 2,
                radius * 2
            )
        )

        drawCircle(
            color = Color.White,
            radius = radius * 0.58f,
            center = Offset(cx, cy)
        )

        drawRect(
            color = blue,
            topLeft = Offset(
                cx - radius * 0.05f,
                cy - radius * 0.19f
            ),
            size = Size(
                radius * 0.95f,
                radius * 0.38f
            )
        )
    }
}