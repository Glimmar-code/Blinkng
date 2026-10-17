package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.shared.notification.BlinkLoginAnnouncementPolicy
import com.blinkng.shared.notification.BlinkLoginAnnouncement
import com.example.data.models.ActivityItem

/**
 * Displays at most one **already delivered** official campaign announcement per login.
 * No fake remote delivery, no local repeat timer, and no interference with critical
 * website APK updates. Dismissal is account-scoped.
 */
@Composable
fun BlinkLoginAnnouncementHost(
    accountId: String,
    activities: List<ActivityItem>,
    safeToShow: Boolean,
    onOpenNotifications: () -> Unit,
    onShowingChanged: (Boolean) -> Unit = {},
) {
    if (accountId.isBlank()) return
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("blink_login_announcement_v1", Context.MODE_PRIVATE)
    }
    val accountKey = remember(accountId) { "seen:$accountId" }
    var seen by remember(accountKey) {
        mutableStateOf(
            prefs.getString(accountKey, "").orEmpty().split("\n")
                .filter(String::isNotBlank)
        )
    }
    var initialized by remember(accountKey) { mutableStateOf(prefs.getBoolean("baseline:$accountId", false)) }
    var shownThisSession by remember(accountKey) { mutableStateOf(false) }
    var active by remember(accountKey) { mutableStateOf<BlinkLoginAnnouncement?>(null) }

    val candidates = activities.mapNotNull { row ->
        BlinkLoginAnnouncementPolicy.fromCampaignActivity(
            id = row.id,
            targetType = row.targetType,
            title = row.action,
            body = row.previewText,
            isUnread = row.isUnread,
        )
    }

    // First install shouldn't replay months of historical announcements as new dialogs.
    LaunchedEffect(accountKey, activities) {
        if (!initialized && candidates.isNotEmpty()) {
            seen = candidates.map { it.id }.take(BlinkLoginAnnouncementPolicy.MAX_REMEMBERED_IDS)
            prefs.edit()
                .putString(accountKey, seen.joinToString("\n"))
                .putBoolean("baseline:$accountId", true)
                .apply()
            initialized = true
        }
    }

    LaunchedEffect(accountKey, safeToShow, candidates, seen, shownThisSession, initialized) {
        if (!initialized || !safeToShow || active != null) return@LaunchedEffect
        active = BlinkLoginAnnouncementPolicy.nextUnseen(
            candidates = candidates,
            seenIds = seen.toSet(),
            safeToShow = safeToShow,
            shownThisSession = shownThisSession,
        )
        if (active != null) {
            shownThisSession = true
        }
    }

    LaunchedEffect(active) { onShowingChanged(active != null) }

    val popup = active ?: return
    fun acknowledge(open: Boolean) {
        seen = BlinkLoginAnnouncementPolicy.remember(seen, popup.id)
        prefs.edit().putString(accountKey, seen.joinToString("\n")).apply()
        active = null
        if (open) onOpenNotifications()
    }

    Dialog(onDismissRequest = { acknowledge(false) }) {
        Surface(
            color = Color(0xFF101010),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("OFFICIAL BLINK UPDATE", color = Color(0xFFCCCCCC), fontWeight = FontWeight.SemiBold)
                Text(popup.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (popup.content.isNotBlank()) Text(popup.content, color = Color(0xFFE5E5E5))
                Button(
                    onClick = { acknowledge(true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Text("View in Notifications") }
                TextButton(onClick = { acknowledge(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Got it", color = Color(0xFFDDDDDD))
                }
            }
        }
    }
}
