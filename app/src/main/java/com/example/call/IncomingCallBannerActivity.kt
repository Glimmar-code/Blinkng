package com.example.call

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lightweight in-app call surface used only while Blink is already visible and unlocked.
 * Background/locked-screen delivery remains on Android's CallStyle notification path.
 */
class IncomingCallBannerActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PEER_ID = "blink_banner_peer_id"
        private const val EXTRA_PEER_USERNAME = "blink_banner_peer_username"
        private const val EXTRA_PEER_NAME = "blink_banner_peer_name"
        private const val EXTRA_PEER_AVATAR = "blink_banner_peer_avatar"
        private const val EXTRA_CONVERSATION_ID = "blink_banner_conversation_id"
        private const val EXTRA_CALL_TYPE = "blink_banner_call_type"
        private const val RING_TIMEOUT_MS = 50_000L

        @Volatile
        private var activeCallId: String? = null

        @Volatile
        private var activeInstance: IncomingCallBannerActivity? = null

        fun show(
            context: Context,
            callId: String,
            callType: CallType,
            peerId: String,
            peerUsername: String,
            peerName: String,
            peerAvatar: String,
            conversationId: String
        ) {
            if (callId.isBlank() || activeCallId == callId) return
            activeCallId = callId
            context.startActivity(
                Intent(context, IncomingCallBannerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(CallActivity.EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_CALL_TYPE, callType.wireValue)
                    putExtra(EXTRA_PEER_ID, peerId)
                    putExtra(EXTRA_PEER_USERNAME, peerUsername)
                    putExtra(EXTRA_PEER_NAME, peerName)
                    putExtra(EXTRA_PEER_AVATAR, peerAvatar)
                    putExtra(EXTRA_CONVERSATION_ID, conversationId)
                }
            )
        }

        fun dismiss(callId: String) {
            if (callId.isBlank() || activeCallId != callId) return
            activeInstance?.runOnUiThread {
                if (activeInstance?.callId == callId) activeInstance?.finish()
            }
            if (activeInstance == null) activeCallId = null
        }
    }

    private var callId: String = ""
    private var callType: CallType = CallType.AUDIO
    private var peerId: String = ""
    private var peerUsername: String = ""
    private var peerName: String = ""
    private var peerAvatar: String = ""
    private var conversationId: String = ""
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var actionInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        readIntent(intent)
        if (callId.isBlank()) {
            finish()
            return
        }
        activeInstance = this
        activeCallId = callId

        setContent {
            BlinkTheme {
                IncomingCallBanner(
                    peerName = peerName.ifBlank { "Blink user" },
                    peerUsername = peerUsername,
                    callType = callType,
                    onAnswer = ::answer,
                    onDecline = ::decline
                )
            }
        }

        startRinging()
        lifecycleScope.launch {
            delay(RING_TIMEOUT_MS)
            if (!isFinishing && !actionInProgress) {
                val expired = CallRepository().expireCall(callId).getOrNull()
                if (expired?.status == CallStatus.MISSED) {
                    IncomingCallNotification.showMissed(
                        context = this@IncomingCallBannerActivity,
                        callId = callId,
                        callType = callType,
                        peerName = peerName
                    )
                }
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingId = intent.getStringExtra(CallActivity.EXTRA_CALL_ID).orEmpty()
        if (incomingId.isNotBlank() && incomingId != callId) {
            stopRinging()
            readIntent(intent)
            activeCallId = callId
            startRinging()
        }
    }

    override fun onDestroy() {
        stopRinging()
        if (activeInstance === this) activeInstance = null
        if (activeCallId == callId) activeCallId = null
        super.onDestroy()
    }

    private fun configureWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        window.attributes = window.attributes.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            y = 18
        }
        setFinishOnTouchOutside(false)
    }

    private fun readIntent(source: Intent) {
        callId = source.getStringExtra(CallActivity.EXTRA_CALL_ID).orEmpty()
        callType = CallType.fromWire(source.getStringExtra(EXTRA_CALL_TYPE))
        peerId = source.getStringExtra(EXTRA_PEER_ID).orEmpty()
        peerUsername = source.getStringExtra(EXTRA_PEER_USERNAME).orEmpty()
        peerName = source.getStringExtra(EXTRA_PEER_NAME).orEmpty()
        peerAvatar = source.getStringExtra(EXTRA_PEER_AVATAR).orEmpty()
        conversationId = source.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
    }

    private fun answer() {
        if (actionInProgress) return
        actionInProgress = true
        stopRinging()
        IncomingCallNotification.cancel(this, callId)
        startActivity(
            CallActivity.incomingIntent(
                context = this,
                callId = callId,
                peerId = peerId,
                peerUsername = peerUsername,
                peerName = peerName,
                peerAvatar = peerAvatar,
                callType = callType,
                conversationId = conversationId,
                answerImmediately = true
            )
        )
        finish()
    }

    private fun decline() {
        if (actionInProgress) return
        actionInProgress = true
        stopRinging()
        IncomingCallNotification.cancel(this, callId)
        lifecycleScope.launch {
            val repository = CallRepository()
            repository.declineCall(callId)
            repository.dispatchPush(callId, "declined")
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRinging() {
        stopRinging()
        CallSoundPreferences.ringtoneUri(this, callType)?.let { uri ->
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }

        if (CallSoundPreferences.vibrateEnabled(this)) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            val pattern = longArrayOf(0L, 500L, 350L, 500L, 350L, 500L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }
}

@Composable
private fun IncomingCallBanner(
    peerName: String,
    peerUsername: String,
    callType: CallType,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(peerName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (peerUsername.isNotBlank()) {
                        Text("@${peerUsername.removePrefix("@")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (callType == CallType.VIDEO) "Incoming video call" else "Incoming voice call",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Decline")
                }
                Button(
                    onClick = onAnswer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Answer")
                }
            }
        }
    }
}
