package com.example.ui.screens

import com.example.R
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blinkng.shared.broadcast.BroadcastNotificationPrefs
import com.example.viewmodel.BlinkBroadcastUiState
import com.example.viewmodel.BlinkBroadcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkNotificationSettingsScreen(
    state: BlinkBroadcastUiState,
    viewModel: BlinkBroadcastViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = (state as? BlinkBroadcastUiState.Success)?.preferences ?: BroadcastNotificationPrefs()
    var permissionOn by remember(context) {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var editingQuietTime by rememberSaveable { mutableStateOf("") }
    val selectedTime = if (editingQuietTime == "start") prefs.quietHoursStart else prefs.quietHoursEnd
    val timeParts = selectedTime.split(":")
    val timeState = key(editingQuietTime, selectedTime) {
        rememberTimePickerState(
            initialHour = timeParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 22,
            initialMinute = timeParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
            is24Hour = true,
        )
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.broadcast_settings)) },
            navigationIcon = { IconButton(onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.broadcast_back))
            } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state is BlinkBroadcastUiState.Loading) BlinkBroadcastSkeleton()
            if (state is BlinkBroadcastUiState.Error) BlinkBroadcastEmptyOrError(
                stringResource(R.string.broadcast_error),
                if (state.offline) stringResource(R.string.broadcast_offline) else state.message,
                stringResource(R.string.broadcast_retry), viewModel::refresh,
            )
            if (!permissionOn) {
                BlinkBroadcastSurfaceCard {
                    Text(stringResource(R.string.broadcast_os_permission_denied))
                    BlinkBroadcastPrimaryButton(stringResource(R.string.broadcast_enable_settings)) {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
            }
            PrefSwitch(stringResource(R.string.broadcast_push_master), prefs.pushEnabled) {
                viewModel.updatePrefs(prefs.copy(pushEnabled = it))
            }
            HorizontalDivider()
            PrefSwitch(stringResource(R.string.broadcast_announcement_toggle), prefs.announcements) {
                viewModel.updatePrefs(prefs.copy(announcements = it))
            }
            PrefSwitch(stringResource(R.string.broadcast_campaign_toggle), prefs.campaigns) {
                viewModel.updatePrefs(prefs.copy(campaigns = it))
            }
            PrefSwitch(stringResource(R.string.broadcast_offer_toggle), prefs.offers) {
                viewModel.updatePrefs(prefs.copy(offers = it))
            }
            PrefSwitch(stringResource(R.string.broadcast_email_toggle), prefs.emailEnabled) {
                viewModel.updatePrefs(prefs.copy(emailEnabled = it))
            }
            HorizontalDivider()
            PrefSwitch(stringResource(R.string.broadcast_quiet_hours), prefs.quietHoursEnabled) {
                viewModel.updatePrefs(prefs.copy(quietHoursEnabled = it))
            }
            if (prefs.quietHoursEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editingQuietTime = "start" },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.broadcast_quiet_start) + " · " + prefs.quietHoursStart)
                    }
                    OutlinedButton(onClick = { editingQuietTime = "end" },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.broadcast_quiet_end) + " · " + prefs.quietHoursEnd)
                    }
                }
            }
        }
    }
    if (editingQuietTime.isNotBlank()) AlertDialog(
        onDismissRequest = { editingQuietTime = "" },
        title = { Text(stringResource(R.string.broadcast_pick_quiet_time)) },
        text = { TimePicker(timeState) },
        confirmButton = {
            TextButton(onClick = {
                val time = "%02d:%02d".format(timeState.hour, timeState.minute)
                viewModel.updatePrefs(if (editingQuietTime == "start")
                    prefs.copy(quietHoursStart = time) else prefs.copy(quietHoursEnd = time))
                editingQuietTime = ""
            }) { Text(stringResource(R.string.broadcast_save_time)) }
        },
        dismissButton = {
            TextButton(onClick = { editingQuietTime = "" }) {
                Text(stringResource(R.string.broadcast_cancel))
            }
        },
    )
}

@Composable
private fun PrefSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(vertical = 15.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange, modifier = Modifier.padding(start = 12.dp))
    }
}
