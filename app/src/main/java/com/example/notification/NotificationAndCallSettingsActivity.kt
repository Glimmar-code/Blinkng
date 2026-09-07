package com.example.notification

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.CallHistoryActivity
import com.example.call.CallSoundPreferences
import com.example.call.CallType
import com.example.call.IncomingCallNotification
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotificationAndCallSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlinkTheme {
                NotificationAndCallSettingsScreen(onBack = ::finish)
            }
        }
    }
}

private data class ChannelSetting(
    val title: String,
    val subtitle: String,
    val channelId: String,
    val callChannel: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationAndCallSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationManager = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    var previewRingtone by remember { mutableStateOf<Ringtone?>(null) }
    var pendingToneType by remember { mutableStateOf<CallType?>(null) }
    var voiceRingEnabled by remember { mutableStateOf(CallSoundPreferences.ringEnabled(context, CallType.AUDIO)) }
    var videoRingEnabled by remember { mutableStateOf(CallSoundPreferences.ringEnabled(context, CallType.VIDEO)) }
    var vibrationEnabled by remember { mutableStateOf(CallSoundPreferences.vibrateEnabled(context)) }
    var voiceToneLabel by remember { mutableStateOf(CallSoundPreferences.ringtoneLabel(context, CallType.AUDIO)) }
    var videoToneLabel by remember { mutableStateOf(CallSoundPreferences.ringtoneLabel(context, CallType.VIDEO)) }

    fun refreshCallSoundState() {
        voiceRingEnabled = CallSoundPreferences.ringEnabled(context, CallType.AUDIO)
        videoRingEnabled = CallSoundPreferences.ringEnabled(context, CallType.VIDEO)
        vibrationEnabled = CallSoundPreferences.vibrateEnabled(context)
        voiceToneLabel = CallSoundPreferences.ringtoneLabel(context, CallType.AUDIO)
        videoToneLabel = CallSoundPreferences.ringtoneLabel(context, CallType.VIDEO)
        IncomingCallNotification.createChannels(context)
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val type = pendingToneType
        pendingToneType = null
        if (type == null || result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }

        // Android's ringtone picker returns null when the user explicitly chooses Silent.
        if (pickedUri == null) {
            CallSoundPreferences.setRingEnabled(context, type, false)
        } else {
            CallSoundPreferences.setRingtoneUri(context, type, pickedUri)
            CallSoundPreferences.setRingEnabled(context, type, true)
        }
        refreshCallSoundState()
    }

    fun chooseRingtone(type: CallType) {
        pendingToneType = type
        val existing = CallSoundPreferences.ringtoneUri(context, type)
        ringtonePicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                    if (type == CallType.VIDEO) "Choose Blink video-call ringtone" else "Choose Blink voice-call ringtone"
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
        )
    }

    fun notificationsEnabled(): Boolean = BlinkNotificationHelper.areNotificationsEnabled(context)

    fun fullScreenAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

    fun previewUri(uri: Uri?) {
        previewRingtone?.stop()
        if (uri == null) return
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        previewRingtone = ringtone
        ringtone.play()
        scope.launch {
            delay(2_500)
            if (previewRingtone === ringtone) {
                ringtone.stop()
                previewRingtone = null
            }
        }
    }

    fun preview(type: Int) {
        previewUri(RingtoneManager.getDefaultUri(type))
    }

    DisposableEffect(Unit) {
        onDispose {
            previewRingtone?.stop()
            previewRingtone = null
        }
    }

    val channels = listOf(
        ChannelSetting(
            "Direct messages",
            "Message sound, vibration, lock-screen visibility and importance",
            BlinkNotificationHelper.CHANNEL_MESSAGES
        ),
        ChannelSetting(
            "Campus activity",
            "Likes and general Blink activity",
            BlinkNotificationHelper.CHANNEL_SOCIAL
        ),
        ChannelSetting(
            "Mentions",
            "When another user mentions you",
            BlinkNotificationHelper.CHANNEL_MENTIONS
        ),
        ChannelSetting(
            "Comments & replies",
            "Comments and replies on your posts",
            BlinkNotificationHelper.CHANNEL_COMMENTS
        ),
        ChannelSetting(
            "Followers",
            "New followers and follow activity",
            BlinkNotificationHelper.CHANNEL_FOLLOWS
        ),
        ChannelSetting(
            "Marketplace",
            "Buyer, seller and listing activity",
            BlinkNotificationHelper.CHANNEL_MARKET
        ),
        ChannelSetting(
            "Market orders",
            "Important order-status alerts",
            BlinkNotificationHelper.CHANNEL_MARKET_ORDERS
        ),
        ChannelSetting(
            "Voice-call Android channel",
            "System-level importance, lock-screen and interruption controls",
            IncomingCallNotification.incomingChannelId(context, CallType.AUDIO),
            callChannel = true
        ),
        ChannelSetting(
            "Video-call Android channel",
            "System-level importance, lock-screen and interruption controls",
            IncomingCallNotification.incomingChannelId(context, CallType.VIDEO),
            callChannel = true
        ),
        ChannelSetting(
            "Missed calls",
            "Missed Blink voice and video calls",
            IncomingCallNotification.CHANNEL_MISSED_CALLS,
            callChannel = true
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Calls & notifications", fontWeight = FontWeight.Bold)
                        Text("Sounds, ringing and alert controls", fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 10.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                StatusCard(
                    title = "Notification delivery",
                    value = if (notificationsEnabled()) "Enabled" else "Needs attention",
                    detail = if (notificationsEnabled()) {
                        "Blink is allowed to post notifications on this device."
                    } else {
                        "Enable notifications so messages and incoming calls can alert you."
                    },
                    healthy = notificationsEnabled()
                )
            }

            item {
                StatusCard(
                    title = "Full-screen incoming calls",
                    value = if (fullScreenAllowed()) "Available" else "Permission off",
                    detail = if (fullScreenAllowed()) {
                        "Locked-screen calls can use Android's incoming-call surface when the system permits it."
                    } else {
                        "Android can still show a heads-up call alert, but full-screen ringing is currently disabled."
                    },
                    healthy = fullScreenAllowed()
                )
            }

            item {
                Text(
                    "Blink call sounds",
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Voice and video calls can use different ringtones. Changes apply to the next incoming call.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            item {
                CallToneCard(
                    icon = Icons.Default.Call,
                    title = "Voice-call ringtone",
                    toneLabel = voiceToneLabel,
                    ringingEnabled = voiceRingEnabled,
                    onToggle = { enabled ->
                        CallSoundPreferences.setRingEnabled(context, CallType.AUDIO, enabled)
                        refreshCallSoundState()
                    },
                    onChoose = { chooseRingtone(CallType.AUDIO) },
                    onPreview = { previewUri(CallSoundPreferences.ringtoneUri(context, CallType.AUDIO)) }
                )
            }

            item {
                CallToneCard(
                    icon = Icons.Default.Videocam,
                    title = "Video-call ringtone",
                    toneLabel = videoToneLabel,
                    ringingEnabled = videoRingEnabled,
                    onToggle = { enabled ->
                        CallSoundPreferences.setRingEnabled(context, CallType.VIDEO, enabled)
                        refreshCallSoundState()
                    },
                    onChoose = { chooseRingtone(CallType.VIDEO) },
                    onPreview = { previewUri(CallSoundPreferences.ringtoneUri(context, CallType.VIDEO)) }
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Vibration, contentDescription = null, tint = BlinkPink)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Call vibration", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Vibrate for incoming voice and video calls",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { enabled ->
                                CallSoundPreferences.setVibrateEnabled(context, enabled)
                                refreshCallSoundState()
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { preview(RingtoneManager.TYPE_NOTIFICATION) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Preview alert")
                    }
                    Button(
                        onClick = { preview(RingtoneManager.TYPE_RINGTONE) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Device ring")
                    }
                }
            }

            item {
                SettingsAction(
                    icon = Icons.Default.Notifications,
                    title = "All Blink notification settings",
                    subtitle = "Master permission, badges, lock-screen alerts and interruption settings"
                ) {
                    BlinkNotificationHelper.openNotificationSettings(context)
                }
            }

            item {
                SettingsAction(
                    icon = Icons.Default.PhoneInTalk,
                    title = "Full-screen call permission",
                    subtitle = "Manage Android's permission for full-screen incoming-call alerts"
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }.onFailure { BlinkNotificationHelper.openNotificationSettings(context) }
                    } else {
                        BlinkNotificationHelper.openNotificationSettings(context)
                    }
                }
            }

            item {
                SettingsAction(
                    icon = Icons.Default.Settings,
                    title = "Phone sound & Do Not Disturb",
                    subtitle = "Open Android sound settings for volume, silent mode and DND"
                ) {
                    context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                }
            }

            item {
                SettingsAction(
                    icon = Icons.Default.History,
                    title = "Call history",
                    subtitle = "Missed, incoming and outgoing Blink calls with callback shortcuts"
                ) {
                    context.startActivity(Intent(context, CallHistoryActivity::class.java))
                }
            }

            item {
                Text(
                    "Notification categories",
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Choose a category to change its Android sound, vibration, importance and lock-screen behavior.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            items(channels, key = { it.channelId }) { channel ->
                SettingsAction(
                    icon = if (channel.callChannel) Icons.Default.Call else Icons.Default.Notifications,
                    title = channel.title,
                    subtitle = channel.subtitle
                ) {
                    BlinkNotificationHelper.createNotificationChannels(context)
                    IncomingCallNotification.createChannels(context)
                    BlinkNotificationHelper.openChannelSettings(context, channel.channelId)
                }
            }
        }
    }
}

@Composable
private fun CallToneCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    toneLabel: String,
    ringingEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onChoose: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = BlinkPink)
                Spacer(Modifier.size(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ringingEnabled) toneLabel else "Silent",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(checked = ringingEnabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChoose, modifier = Modifier.weight(1f)) {
                    Text("Choose tone")
                }
                Button(onClick = onPreview, enabled = ringingEnabled, modifier = Modifier.weight(1f)) {
                    Text("Preview")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    detail: String,
    healthy: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (healthy) Icons.Default.CheckCircle else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (healthy) BlinkPink else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(value, fontSize = 12.sp, color = if (healthy) BlinkPink else MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = BlinkPink)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
        }
    }
}
