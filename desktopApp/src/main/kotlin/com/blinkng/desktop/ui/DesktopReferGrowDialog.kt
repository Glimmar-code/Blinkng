package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralStats

@Composable
internal fun DesktopReferGrowDialog(state: DesktopAppState, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var stats by remember { mutableStateOf<BlinkReferralStats?>(null) }
    var error by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(state.session?.userId) {
        runCatching { state.client.referralDashboard() }
            .onSuccess { stats = it }
            .onFailure { error = true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BLINK Refer & Grow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(BlinkReferralPolicy.PROMO_MESSAGE)
                if (stats == null && !error) LinearProgressIndicator(Modifier.fillMaxWidth())
                stats?.let {
                    Text("Successful referrals: ${it.total}")
                    Text("Active in 30 days: ${it.activeLast30Days}")
                    Text("Visibility level: ${it.visibilityLabel}")
                    Text("Referral discovery points: ${it.visibilityPoints}")
                }
                if (error) Text("Referrals are unavailable until the server update is ready.")
                Text("Referral visibility increases discovery opportunities, not guaranteed followers.")
                if (copied) Text("Invitation copied to clipboard.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.profile?.username?.isNotBlank() == true,
                onClick = {
                    val username = state.profile?.username ?: return@TextButton
                    val link = runCatching { BlinkReferralPolicy.inviteUrl(username) }.getOrNull()
                        ?: return@TextButton
                    clipboard.setText(AnnotatedString("${BlinkReferralPolicy.INVITATION_MESSAGE}\n$link"))
                    copied = true
                }
            ) { Text("Copy invitation") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
