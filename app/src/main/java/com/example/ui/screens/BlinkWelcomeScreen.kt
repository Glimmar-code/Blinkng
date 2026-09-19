package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BlinkMark
import com.example.ui.theme.DarkTextSecondary

@Composable
fun BlinkWelcomeScreen(
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
    onGoogle: (String) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BlinkMark(size = 96.dp, showText = false)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "BLINK",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Connect. Share. Discover.",
                    color = DarkTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                GoogleSignInButton(
                    text = "Continue with Google",
                    onClick = { onGoogle("") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_google")
                )

                PremiumAuthButton(
                    text = "Create account",
                    icon = androidx.compose.material.icons.Icons.Default.PersonAdd,
                    onClick = onCreateAccount,
                    primary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_create_account")
                )

                PremiumAuthButton(
                    text = "Log in",
                    icon = androidx.compose.material.icons.Icons.Default.Login,
                    onClick = onLogin,
                    primary = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_login")
                )

                Text(
                    text = "By continuing, you agree to BLINK's Terms and Privacy Policy.",
                    color = DarkTextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                )

                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.blink.com.ng/privacy")
                                )
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Privacy", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}
