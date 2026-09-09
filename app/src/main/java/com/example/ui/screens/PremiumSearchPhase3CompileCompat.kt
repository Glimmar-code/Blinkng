package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.DiscoveryResult
import com.example.ui.theme.BlinkThemeTokens
import kotlin.reflect.KFunction0

/**
 * Compose-typed overload used by the Phase 3 hero transition.
 *
 * Kotlin's standard `let` expects a regular function type, so a direct reference to
 * an @Composable function is not applicable. This narrow overload keeps the existing
 * call site type-safe without weakening the hero composable or changing its behavior.
 */
@Composable
internal inline fun DiscoveryResult?.let(block: @Composable (DiscoveryResult) -> Unit) {
    if (this != null) block(this)
}

/**
 * Callable-reference overload for ViewModel toggle commands.
 *
 * Following/Saved expose zero-argument toggle functions, while the normal row in
 * PremiumSearchPhase3Screen accepts `(Boolean) -> Unit` setters. KFunction0 keeps
 * method references unambiguous without capturing ordinary lambdas such as autoplay.
 */
@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: KFunction0<Unit>,
) {
    val colors = BlinkThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(subtitle, fontSize = 9.sp, color = colors.textMuted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = { requested ->
                if (requested != checked) onToggle()
            },
        )
    }
}
