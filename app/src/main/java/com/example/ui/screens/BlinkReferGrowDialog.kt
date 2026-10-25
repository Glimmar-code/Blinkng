package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralStats
import com.example.data.supabase.BlinkReferralClient

@Composable
internal fun BlinkReferGrowDialog(username: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<BlinkReferralStats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username) {
        runCatching { BlinkReferralClient.dashboard() }
            .onSuccess { stats = it }
            .onFailure { error = "Referral statistics are unavailable until the server update is ready." }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BLINK Refer & Grow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(BlinkReferralPolicy.PROMO_MESSAGE)
                if (stats == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                stats?.let {
                    Text("Lifetime confirmed referrals: ${it.total}")
                    Text("Achievement: ${it.achievementTier}")
                    Text(BlinkReferralPolicy.milestoneLabel(it))
                    Text("Active referrals (last 30 days): ${it.activeLast30Days}")
                    Text("Current visibility: ${it.visibilityLabel} (${it.visibilityPoints} points)")
                    Text("Each referral adds 3 points for 10 days, then 2 for 10 days, then 1 for 10 days. The boost expires after 30 days, but achievements remain.")
                    Text("No guaranteed views, follows or Coins.")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Share your invite link. A new account must be confirmed to count.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = username.isNotBlank(),
                onClick = {
                    val link = runCatching { BlinkReferralPolicy.inviteUrl(username) }.getOrNull()
                        ?: return@TextButton
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${BlinkReferralPolicy.INVITATION_MESSAGE}\n$link")
                    }
                    context.startActivity(Intent.createChooser(intent, "Invite to BLINK"))
                },
            ) { Text("Invite friends") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val url = runCatching { BlinkReferralPolicy.inviteUrl(username) }.getOrNull()
                        ?: return@TextButton
                    clipboard.setText(AnnotatedString(url))
                    copied = true
                }) { Text(if (copied) "Copied!" else "Copy link") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
