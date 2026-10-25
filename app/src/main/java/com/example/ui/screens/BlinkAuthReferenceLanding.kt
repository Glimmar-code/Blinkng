package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.GoogleAuthLaunchGate
import com.blinkng.shared.BlinkAdaptiveLayoutPolicy

/** The real, clickable welcome screen from the approved BLINK authentication reference.
 * The surrounding promotional poster / phone bezel are not part of the in-app UI.
 */
@Composable
internal fun BlinkAuthReferenceLanding(
    onSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onGoogleSignIn: (String) -> Unit,
) {
    val googleLoading by GoogleAuthLaunchGate.inFlight.collectAsState()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding(),
    ) {
        // Keep the same photo/card proportions on normal phones; scroll on short/large-font screens.
        val heroHeight = if (maxHeight < 520.dp) {
            (maxHeight * 0.32f).coerceIn(150.dp, 230.dp)
        } else {
            (maxHeight * 0.43f).coerceIn(220.dp, 370.dp)
        }
        val compactFeatures = BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(
            maxWidth.value, LocalDensity.current.fontScale
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            // Reuse BLINK's already approved, larger 472px WebP source rather
            // than upscaling the tiny 240px JPEG baked with raster lettering.
            // Draw the exact wordmark separately so it remains sharp at any DPI.
            Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                Image(
                    painter = painterResource(R.drawable.blink_auth_reference_login_photo),
                    contentDescription = "BLINK students together on campus",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = "B L I N K",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 9.dp),
                    color = Color.Black,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-26).dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Color.White)
                    .padding(horizontal = 18.dp)
                    .padding(top = 26.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Welcome to BLINK",
                    color = Color.Black,
                    fontSize = 30.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Connect, discover and grow ")
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF202020),
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) { append("Beyond LINK") }
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF777777),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))
                // Four narrow feature columns clip badly at large Android text scales.
                // Present the same four approved actions in two balanced rows instead.
                val features = listOf(
                    AuthLandingFeature("Meet\nStudents", Icons.Default.People, Color(0xFF8738F5), Color(0xFFF4ECFE)),
                    AuthLandingFeature("Join\nCommunities", Icons.Default.Groups, Color(0xFF1676FF), Color(0xFFEDF5FF)),
                    AuthLandingFeature("Attend\nEvents", Icons.Default.CalendarMonth, Color(0xFF16BC72), Color(0xFFE9FAF1)),
                    AuthLandingFeature("Buy & Sell\non Campus", Icons.Default.ShoppingBag, Color(0xFFF5A400), Color(0xFFFFF5E7)),
                )
                if (compactFeatures) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        features.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { feature ->
                                    BlinkAuthFeature(
                                        title = feature.title,
                                        icon = feature.icon,
                                        color = feature.color,
                                        background = feature.background,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        features.forEach { feature ->
                            BlinkAuthFeature(
                                title = feature.title,
                                icon = feature.icon,
                                color = feature.color,
                                background = feature.background,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                BlinkAuthLandingButton(
                    text = "Log In",
                    onClick = onSignInClick,
                    modifier = Modifier.testTag("onboarding_signin_btn"),
                    icon = { Icon(Icons.Default.MailOutline, null, modifier = Modifier.size(23.dp), tint = Color.White) },
                    foreground = Color.White,
                    background = Color.Black,
                )
                Spacer(Modifier.height(10.dp))
                BlinkAuthLandingButton(
                    text = "Create Account",
                    onClick = onSignUpClick,
                    modifier = Modifier.testTag("onboarding_signup_btn"),
                    icon = { Icon(Icons.Default.PersonOutline, null, modifier = Modifier.size(23.dp), tint = Color.Black) },
                    foreground = Color.Black,
                    background = Color.White,
                    outlined = true,
                )
                Spacer(Modifier.height(10.dp))
                BlinkAuthLandingButton(
                    text = "Continue with Google",
                    onClick = { if (!googleLoading) onGoogleSignIn("") },
                    modifier = Modifier.testTag("onboarding_google_btn"),
                    icon = {
                        if (googleLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.DarkGray,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            GoogleLogoVector(Modifier.size(22.dp))
                        }
                    },
                    foreground = Color(0xFF363636),
                    background = Color(0xFFF2F2F2),
                )

                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Already a student?",
                    color = Color(0xFF818181),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Join your campus community today.",
                    color = Color(0xFF818181),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class AuthLandingFeature(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val background: Color
)

@Composable
private fun BlinkAuthFeature(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    background: Color,
    modifier: Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(49.dp).background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            color = Color(0xFF303030),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@Composable
private fun BlinkAuthLandingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    foreground: Color,
    background: Color,
    outlined: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(11.dp),
        color = background,
        border = if (outlined) androidx.compose.foundation.BorderStroke(1.4.dp, Color.Black) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 21.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(27.dp), contentAlignment = Alignment.CenterStart) { icon() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
