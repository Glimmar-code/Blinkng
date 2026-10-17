package com.example.ui.components

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blinkng.shared.discovery.BlinkDiscoveryPolicy
import com.blinkng.shared.discovery.BlinkGuideStep
import kotlinx.coroutines.delay

/**
 * Phase 1: local, per-account guide progress. This does NOT claim cross-device sync or
 * remote announcements; those will require an authenticated Supabase contract.
 */
@Composable
fun BlinkDiscoveryGuideHost(
    userId: String,
    safeToShow: Boolean,
    onNavigate: (String) -> Unit,
) {
    if (userId.isBlank()) return
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("blink_discovery_v1", Context.MODE_PRIVATE)
    }
    val accountKey = remember(userId) { "seen:" + userId }
    var seen by remember(accountKey) {
        mutableStateOf(preferences.getStringSet(accountKey, emptySet()).orEmpty().toSet())
    }
    var activeStep by remember(accountKey) { mutableStateOf<BlinkGuideStep?>(null) }
    var shownThisSession by remember(accountKey) { mutableIntStateOf(0) }
    val sessionStart = remember(accountKey) { SystemClock.elapsedRealtime() }
    var lastShownAt by remember(accountKey) { mutableLongStateOf(0L) }

    fun complete(step: BlinkGuideStep, route: String? = null) {
        val updated = seen + step.id
        preferences.edit().putStringSet(accountKey, updated).apply()
        seen = updated
        activeStep = null
        route?.let(onNavigate)
    }

    LaunchedEffect(accountKey, safeToShow, seen, activeStep, shownThisSession) {
        while (safeToShow && activeStep == null) {
            val now = SystemClock.elapsedRealtime()
            val candidate = BlinkDiscoveryPolicy.next(
                completed = seen,
                elapsedSessionMs = now - sessionStart,
                safeToShow = safeToShow,
                promptsShownThisSession = shownThisSession,
                elapsedSinceLastPromptMs = if (lastShownAt == 0L) Long.MAX_VALUE else now - lastShownAt,
            )
            if (candidate != null) {
                activeStep = candidate
                shownThisSession++
                lastShownAt = now
                return@LaunchedEffect
            }
            delay(1_000)
        }
    }

    val step = activeStep ?: return
    Dialog(
        onDismissRequest = { complete(step) },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            color = Color(0xFF111111),
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (step.id == "welcome") "BLINK • GUIDE" else "DID YOU KNOW?",
                    color = Color(0xFFBBBBBB),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = step.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = step.description,
                    color = Color(0xFFE6E6E6),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { complete(step, step.routeId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Text(step.actionLabel) }
                TextButton(
                    onClick = {
                        if (step.id == "welcome") {
                            seen = BlinkDiscoveryPolicy.allIds()
                            preferences.edit().putStringSet(accountKey, seen).apply()
                            activeStep = null
                        } else {
                            complete(step)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (step.id == "welcome") "Skip introduction" else "Maybe later",
                        color = Color(0xFFCCCCCC),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
