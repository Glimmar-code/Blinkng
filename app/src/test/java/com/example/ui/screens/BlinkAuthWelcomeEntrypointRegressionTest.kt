package com.example.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approved welcome composable can pass isolated screenshot tests while
 * the actual Android app shell still routes users to the legacy dark screen.
 * Keep a small integration guard for the real app destination.
 */
class BlinkAuthWelcomeEntrypointRegressionTest {
    @Test
    fun onboardingDestinationDisplaysApprovedReferenceScreen() {
        val activity = listOf(
            File("src/main/java/com/example/MainActivity.kt"),
            File("app/src/main/java/com/example/MainActivity.kt"),
        ).firstOrNull { it.isFile }
            ?: error("MainActivity.kt source not found in the Gradle working directory")
        val route = activity.readText()
            .substringAfter("AppDestination.ONBOARDING -> {", missingDelimiterValue = "")
            .substringBefore("AppDestination.SIGN_IN -> {", missingDelimiterValue = "")

        assertTrue("The ONBOARDING route must exist", route.isNotBlank())
        assertTrue("The live route must use the approved OnboardingScreen", route.contains("OnboardingScreen("))
        assertFalse("Legacy dark welcome must not replace the approved design", route.contains("BlinkWelcomeScreen("))
    }
}
