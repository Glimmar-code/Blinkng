package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkAuthReferenceLandingTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun referenceCopyAndLoginRouteWork() {
        var clicked = ""
        rule.setContent {
            BlinkAuthReferenceLanding(
                onSignInClick = { clicked = "login" },
                onSignUpClick = { clicked = "signup" },
                onGoogleSignIn = { clicked = "google" },
            )
        }
        rule.onNodeWithText("Welcome to BLINK").assertExists()
        rule.onNodeWithText("Connect, discover and grow Beyond LINK").assertExists()
        rule.onNodeWithTag("onboarding_signin_btn").performClick()
        rule.runOnIdle { assertEquals("login", clicked) }
        rule.onNodeWithTag("onboarding_signup_btn").performClick()
        rule.runOnIdle { assertEquals("signup", clicked) }
        rule.onNodeWithTag("onboarding_google_btn").performClick()
        rule.runOnIdle { assertEquals("google", clicked) }
    }

    @Test
    fun referenceLayoutsAtLargeFontScales() {
        val scales = listOf(1f, 1.3f, 1.5f, 2f)
        scales.forEach { scale ->
            rule.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    BlinkAuthReferenceLanding(
                        onSignInClick = {},
                        onSignUpClick = {},
                        onGoogleSignIn = {},
                    )
                }
            }
            rule.onNodeWithTag("onboarding_signin_btn").assertExists()
            rule.onNodeWithTag("onboarding_signup_btn").assertExists()
            rule.onNodeWithTag("onboarding_google_btn").assertExists()
            rule.onRoot().captureRoboImage(
                filePath = "build/screenshots/blink-auth-reference-${(scale * 100).toInt()}.png"
            )
        }
    }
}
