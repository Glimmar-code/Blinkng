package com.example.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkBugReport
import com.example.data.supabase.BlinkBugReportService
import kotlinx.coroutines.launch

object BlinkBugPrefs {
    const val FILE = "blink_bug_bounty_settings"
    const val SHAKE_ENABLED = "shake_enabled"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkBugReportSheet(onDismiss: () -> Unit, initialSurface: String = "app") {
    val context = LocalContext.current
    val service = remember { BlinkBugReportService() }
    val scope = rememberCoroutineScope()
    var description by rememberSaveable { mutableStateOf("") }
    var screenshot by remember { mutableStateOf<Uri?>(null) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf<List<BlinkBugReport>>(emptyList()) }
    var shakeEnabled by remember {
        mutableStateOf(context.getSharedPreferences(BlinkBugPrefs.FILE, 0)
            .getBoolean(BlinkBugPrefs.SHAKE_ENABLED, true))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        screenshot = it
    }
    LaunchedEffect(history) {
        if (history) {
            service.myReports().onSuccess { reports = it }
                .onFailure { status = it.message ?: "Could not load report history." }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BugReport, "Bug report", modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Report a bug & earn coins", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Text("Found something wrong with BLINK? Tell our admins. Useful verified reports can earn Blink Coins; rewards are not automatic.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 2000) description = it },
                label = { Text("Describe the problem") },
                placeholder = { Text("What happened? Which page? What should happen instead?") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                minLines = 4,
                maxLines = 8,
                enabled = !working,
                supportingText = { Text("${description.length}/2000 (at least 20 characters)") }
            )
            OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !working) {
                Icon(Icons.Outlined.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text(if (screenshot == null) "Attach screenshot (optional)" else "Change screenshot")
            }
            if (screenshot != null) {
                AsyncImage(
                    model = screenshot,
                    contentDescription = "Selected screenshot preview",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 165.dp)
                )
                TextButton(onClick = { screenshot = null }, enabled = !working) { Text("Remove screenshot") }
            }
            Button(
                onClick = {
                    working = true
                    status = null
                    scope.launch {
                        val version = runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "unknown"
                        service.submit(context, description, initialSurface, version, screenshot)
                            .onSuccess { result ->
                                description = ""
                                screenshot = null
                                status = "Report sent to BLINK Admin (#${result.id.take(8)}). " +
                                    (result.screenshotWarning ?: "We'll review it and award coins if eligible.")
                                history = true
                            }
                            .onFailure { status = it.message ?: "Could not send your report. Try again." }
                        working = false
                    }
                },
                enabled = !working && description.trim().length in 20..2000,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Send report to Admin")
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Shake phone to report a bug", fontWeight = FontWeight.SemiBold)
                    Text("Works while BLINK is open", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = shakeEnabled,
                    onCheckedChange = {
                        shakeEnabled = it
                        context.getSharedPreferences(BlinkBugPrefs.FILE, 0).edit()
                            .putBoolean(BlinkBugPrefs.SHAKE_ENABLED, it).apply()
                    }
                )
            }
            TextButton(onClick = { history = !history }) {
                Text(if (history) "Hide my reports" else "View my reports and rewards")
            }
            if (history) {
                if (reports.isEmpty()) {
                    Text("No reports yet.", style = MaterialTheme.typography.bodySmall)
                } else reports.take(20).forEach { report ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(report.status.uppercase() +
                                    if (report.rewardCoins > 0) " • +${report.rewardCoins} Blink Coins" else "",
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(report.description, maxLines = 3)
                            if (report.note.isNotBlank()) Text("Admin: ${report.note}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
