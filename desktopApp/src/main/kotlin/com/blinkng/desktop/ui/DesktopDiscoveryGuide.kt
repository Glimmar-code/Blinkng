package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.shared.discovery.BlinkDiscoveryPolicy
import com.blinkng.shared.discovery.BlinkGuideStep
import kotlinx.coroutines.delay
import java.util.prefs.Preferences

/** Windows keyboard/mouse equivalent of the Android guide; shared sequencing rules. */
@Composable
fun DesktopDiscoveryGuide(
    userId: String,
    safeToShow: Boolean,
    onNavigate: (String) -> Unit,
) {
    if (userId.isBlank()) return
    val prefs = remember { Preferences.userNodeForPackage(BlinkDiscoveryPolicy::class.java) }
    val key = remember(userId) { "seen:" + userId }
    var seen by remember(key) {
        mutableStateOf(prefs.get(key, "").split(",").filter { it.isNotBlank() }.toSet())
    }
    var active by remember(key) { mutableStateOf<BlinkGuideStep?>(null) }
    var shown by remember(key) { mutableIntStateOf(0) }
    var lastShownAt by remember(key) { mutableLongStateOf(0L) }
    val startedAt = remember(key) { System.nanoTime() / 1_000_000L }

    fun complete(step: BlinkGuideStep, routeId: String? = null) {
        seen = seen + step.id
        prefs.put(key, seen.joinToString(","))
        active = null
        routeId?.let(onNavigate)
    }

    LaunchedEffect(key, safeToShow, active, seen, shown) {
        while (safeToShow && active == null) {
            val now = System.nanoTime() / 1_000_000L
            val candidate = BlinkDiscoveryPolicy.next(
                completed = seen,
                elapsedSessionMs = now - startedAt,
                safeToShow = safeToShow,
                promptsShownThisSession = shown,
                elapsedSinceLastPromptMs = if (lastShownAt == 0L) Long.MAX_VALUE else now - lastShownAt,
            )
            if (candidate != null) {
                active = candidate
                shown++
                lastShownAt = now
                return@LaunchedEffect
            }
            delay(1_000)
        }
    }

    val step = active ?: return
    Dialog(onCloseRequest = { complete(step) }, title = "BLINK Guide") {
        Surface(
            color = Color(0xFF111111),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("BLINK • GUIDE", color = Color(0xFFBBBBBB))
                Text(step.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(step.description, color = Color.White)
                Button(
                    onClick = { complete(step, step.routeId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Text(step.actionLabel) }
                TextButton(onClick = {
                    if (step.id == "welcome") {
                        seen = BlinkDiscoveryPolicy.allIds()
                        prefs.put(key, seen.joinToString(","))
                        active = null
                    } else {
                        complete(step)
                    }
                }) {
                    Text(if (step.id == "welcome") "Skip introduction" else "Maybe later", color = Color(0xFFCCCCCC))
                }
            }
        }
    }
}
