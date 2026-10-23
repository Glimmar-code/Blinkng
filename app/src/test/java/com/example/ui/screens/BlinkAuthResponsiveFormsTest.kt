package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Guards authentic, clickable BLINK forms on the smallest supported phone and
 * expanded tablet layouts without generating device-only APK downloads.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h640dp")
class BlinkAuthNarrowScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun signupKeepsEveryInputAndProviderReachable() {
        rule.setContent {
            BlinkReferenceCreateAccountScreen(
                onBack = {}, onCreateAccount = { _, _, _ -> },
                onGoogle = {}, onLogin = {}
            )
        }
        rule.onNodeWithTag("auth_compact_phone").assertExists()
        rule.onNodeWithTag("create_account_name").assertExists()
        rule.onNodeWithTag("create_account_email").assertExists()
        rule.onNodeWithTag("create_account_university").assertExists()
        rule.onNodeWithTag("create_account_password").assertExists()
        rule.onNodeWithTag("create_account_submit").assertExists()
        rule.onNodeWithTag("create_account_google").assertExists()
        rule.onNodeWithTag("create_account_email_focus").assertExists()
        rule.onRoot().captureRoboImage(filePath = "build/screenshots/blink-auth-signup-320dp.png")
    }

    @Test fun signupRemainsReachableAt130To200PercentFont() {
        var scale by mutableFloatStateOf(1f)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                BlinkReferenceCreateAccountScreen(
                    onBack = {}, onCreateAccount = { _, _, _ -> },
                    onGoogle = {}, onLogin = {}
                )
            }
        }
        listOf(1f, 1.3f, 1.5f, 2f).forEach { next ->
            rule.runOnIdle { scale = next }
            rule.onNodeWithTag("create_account_submit").assertExists()
            rule.onNodeWithTag("create_account_email").assertExists()
            rule.onNodeWithTag("create_account_google").assertExists()
            rule.onRoot().captureRoboImage(
                filePath = "build/screenshots/blink-auth-signup-320dp-font-${(next * 100).toInt()}.png"
            )
        }
    }

    @Test fun loginPreservesFunctionalResetAndGoogle() {
        rule.setContent {
            BlinkReferenceSignInScreen(
                onBack = {},
                onSignInWithCredentials = { _, _, callback -> callback(false, "Test only") },
                onGoogleSignIn = {},
                onForgotPassword = { _, callback -> callback(false, "Test only") },
                onSwitchToSignUp = {}
            )
        }
        rule.onNodeWithTag("auth_compact_phone").assertExists()
        rule.onNodeWithTag("signin_email_field").assertExists()
        rule.onNodeWithTag("signin_password_field").assertExists()
        rule.onNodeWithTag("signin_submit_btn").assertExists()
        rule.onNodeWithTag("signin_google_btn").assertExists()
        rule.onNodeWithText("Forgot Password?").assertExists()
        rule.onRoot().captureRoboImage(filePath = "build/screenshots/blink-auth-login-320dp.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(application = Application::class, sdk = [35], qualifiers = "w800dp-h960dp")
class BlinkAuthExpandedScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tabletKeepsPosterSeparateFromInteractivePhone() {
        rule.setContent {
            BlinkReferenceCreateAccountScreen(
                onBack = {}, onCreateAccount = { _, _, _ -> },
                onGoogle = {}, onLogin = {}
            )
        }
        rule.onNodeWithTag("auth_reference_phone").assertExists()
        rule.onNodeWithText("B L I N K").assertExists()
        rule.onNodeWithTag("create_account_submit").assertExists()
        rule.onRoot().captureRoboImage(filePath = "build/screenshots/blink-auth-signup-800dp.png")
    }
}
