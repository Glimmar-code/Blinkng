package com.example.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.data.supabase.SupabaseService
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.atomic.AtomicBoolean

class CallActivity : ComponentActivity() {
    companion object {
        const val ACTION_OUTGOING = "com.example.blink.call.OUTGOING"
        const val ACTION_INCOMING = "com.example.blink.call.INCOMING"
        const val ACTION_ANSWER = "com.example.blink.call.ANSWER"
        const val ACTION_RESTORE = "com.example.blink.call.RESTORE"

        const val EXTRA_CALL_ID = "blink_call_id"
        const val EXTRA_PEER_ID = "blink_call_peer_id"
        const val EXTRA_PEER_USERNAME = "blink_call_peer_username"
        const val EXTRA_PEER_NAME = "blink_call_peer_name"
        const val EXTRA_PEER_AVATAR = "blink_call_peer_avatar"
        const val EXTRA_CALL_TYPE = "blink_call_type"
        const val EXTRA_CONVERSATION_ID = "blink_call_conversation_id"

        fun outgoingIntent(
            context: Context,
            callId: String,
            peerId: String,
            peerUsername: String,
            peerName: String,
            peerAvatar: String,
            callType: CallType
        ): Intent = Intent(context, CallActivity::class.java).apply {
            action = ACTION_OUTGOING
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_PEER_ID, peerId)
            putExtra(EXTRA_PEER_USERNAME, peerUsername)
            putExtra(EXTRA_PEER_NAME, peerName)
            putExtra(EXTRA_PEER_AVATAR, peerAvatar)
            putExtra(EXTRA_CALL_TYPE, callType.wireValue)
        }

        fun incomingIntent(
            context: Context,
            callId: String,
            peerId: String,
            peerUsername: String,
            peerName: String,
            peerAvatar: String,
            callType: CallType,
            conversationId: String,
            answerImmediately: Boolean
        ): Intent = Intent(context, CallActivity::class.java).apply {
            action = if (answerImmediately) ACTION_ANSWER else ACTION_INCOMING
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_PEER_ID, peerId)
            putExtra(EXTRA_PEER_USERNAME, peerUsername)
            putExtra(EXTRA_PEER_NAME, peerName)
            putExtra(EXTRA_PEER_AVATAR, peerAvatar)
            putExtra(EXTRA_CALL_TYPE, callType.wireValue)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }

        fun restoreIntent(context: Context, callId: String): Intent =
            Intent(context, CallActivity::class.java).apply {
                action = ACTION_RESTORE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CALL_ID, callId)
            }
    }

    private val repository = CallRepository()
    private var call: BlinkCall? = null
    private var realtime: CallRealtimeChannel? = null
    private var rtcClient: WebRtcCallClient? = null
    private var pollJob: Job? = null
    private var durationJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastFetchedSignalId = 0L
    private val processedSignalIds = mutableSetOf<Long>()
    private val pendingSignals = mutableListOf<CallSignal>()
    private val signalSendMutex = Mutex()
    private val ending = AtomicBoolean(false)
    private var mediaRequestedForIncomingAnswer = false
    private var mediaStarted = false
    private var connectedMarked = false
    private var isCaller = false
    private var connectingSinceMillis = 0L

    private var statusText by mutableStateOf("Loading call…")
    private var callType by mutableStateOf(CallType.AUDIO)
    private var peerName by mutableStateOf("Blink user")
    private var peerUsername by mutableStateOf("")
    private var peerAvatar by mutableStateOf("")
    private var incomingRinging by mutableStateOf(false)
    private var mediaReady by mutableStateOf(false)
    private var microphoneOn by mutableStateOf(true)
    private var cameraOn by mutableStateOf(true)
    private var speakerOn by mutableStateOf(false)
    private var elapsedSeconds by mutableIntStateOf(0)
    private var errorText by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        val cameraGranted = callType != CallType.VIDEO ||
            (results[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA))
        if (audioGranted && cameraGranted) {
            lifecycleScope.launch { startMediaAfterPermission() }
        } else {
            errorText = if (!audioGranted) {
                "Microphone permission is required for calls."
            } else {
                "Camera permission is required for video calls."
            }
            lifecycleScope.launch { failAndFinish("permission_denied") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@CallActivity, "Use End call to leave the call.", Toast.LENGTH_SHORT).show()
            }
        })

        setContent {
            BlinkTheme {
                CallScreen(
                    callType = callType,
                    peerName = peerName,
                    peerUsername = peerUsername,
                    peerAvatar = peerAvatar,
                    statusText = statusText,
                    errorText = errorText,
                    incomingRinging = incomingRinging,
                    mediaReady = mediaReady,
                    microphoneOn = microphoneOn,
                    cameraOn = cameraOn,
                    speakerOn = speakerOn,
                    elapsedSeconds = elapsedSeconds,
                    rtcClient = rtcClient,
                    onAnswer = { answerIncoming() },
                    onDecline = { declineIncoming() },
                    onEnd = { endFromUi() },
                    onToggleMic = {
                        microphoneOn = rtcClient?.toggleMicrophone() ?: microphoneOn
                    },
                    onToggleCamera = {
                        cameraOn = rtcClient?.toggleCamera() ?: cameraOn
                    },
                    onSwitchCamera = { rtcClient?.switchCamera() },
                    onToggleSpeaker = {
                        speakerOn = rtcClient?.toggleSpeaker() ?: speakerOn
                    }
                )
            }
        }

        lifecycleScope.launch { loadCall(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_ANSWER && incomingRinging) answerIncoming()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        durationJob?.cancel()
        reconnectJob?.cancel()
        realtime?.close()
        rtcClient?.release()
        rtcClient = null
        mediaReady = false

        val active = call
        if (isFinishing && active != null && !active.status.isTerminal && !ending.get()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val ended = repository.endCall(active.id, "ended").getOrNull()
                    ended?.status?.wireValue?.let { repository.dispatchPush(active.id, it) }
                }
            }
        }
        stopService(Intent(this, BlinkCallForegroundService::class.java))
        super.onDestroy()
    }

    private suspend fun loadCall(sourceIntent: Intent) {
        val callId = sourceIntent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) {
            errorText = "Call information is missing."
            delay(900)
            finish()
            return
        }

        val loaded = repository.fetchCall(callId).getOrElse { error ->
            errorText = error.message ?: "Unable to load this call."
            delay(1_000)
            finish()
            return
        }
        call = loaded
        callType = loaded.type
        isCaller = loaded.callerId == repository.currentUserId()
        if (loaded.status.isTerminal) {
            statusText = terminalLabel(loaded.status)
            IncomingCallNotification.cancel(this, loaded.id)
            delay(900)
            finish()
            return
        }

        val suppliedPeerId = sourceIntent.getStringExtra(EXTRA_PEER_ID).orEmpty()
        val peerId = suppliedPeerId.ifBlank { loaded.otherUserId(repository.currentUserId()) }
        peerName = sourceIntent.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Blink user" }
        peerUsername = sourceIntent.getStringExtra(EXTRA_PEER_USERNAME).orEmpty()
        peerAvatar = sourceIntent.getStringExtra(EXTRA_PEER_AVATAR).orEmpty()
        if (peerId.isNotBlank() && (peerName == "Blink user" || peerUsername.isBlank())) {
            repository.fetchPeer(peerId).getOrNull()?.let { peer ->
                peerName = peer.name
                peerUsername = peer.username
                peerAvatar = peer.avatar
            }
        }

        incomingRinging = !isCaller && loaded.status == CallStatus.RINGING
        statusText = when {
            incomingRinging -> if (callType == CallType.VIDEO) "Incoming video call" else "Incoming voice call"
            loaded.status == CallStatus.CONNECTED -> "Connected"
            loaded.status == CallStatus.CONNECTING -> "Connecting…"
            else -> "Calling…"
        }
        speakerOn = callType == CallType.VIDEO
        cameraOn = callType == CallType.VIDEO

        startRealtime(loaded.id)
        startReconciliationLoop(loaded.id)
        synchronizeSignals()

        when {
            sourceIntent.action == ACTION_ANSWER && incomingRinging -> answerIncoming()
            isCaller -> requestMediaPermissions(incomingAnswer = false)
            loaded.status in setOf(CallStatus.CONNECTING, CallStatus.CONNECTED) -> {
                mediaRequestedForIncomingAnswer = true
                requestMediaPermissions(incomingAnswer = true)
            }
        }
    }

    private fun requestMediaPermissions(incomingAnswer: Boolean) {
        mediaRequestedForIncomingAnswer = incomingAnswer
        val needed = buildList {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (callType == CallType.VIDEO && !hasPermission(Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
        }
        if (needed.isEmpty()) {
            lifecycleScope.launch { startMediaAfterPermission() }
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun answerIncoming() {
        if (!incomingRinging || ending.get()) return
        statusText = "Connecting…"
        requestMediaPermissions(incomingAnswer = true)
    }

    private suspend fun startMediaAfterPermission() {
        if (mediaStarted || ending.get()) return
        val active = call ?: return

        if (mediaRequestedForIncomingAnswer && !isCaller && active.status == CallStatus.RINGING) {
            val answered = repository.answerCall(active.id).getOrElse { error ->
                errorText = error.message ?: "Unable to answer this call."
                return
            }
            call = answered
            incomingRinging = false
            connectingSinceMillis = System.currentTimeMillis()
            IncomingCallNotification.cancel(this, active.id)
        }

        mediaStarted = true
        statusText = if (isCaller && call?.status == CallStatus.RINGING) "Calling…" else "Connecting…"
        connectingSinceMillis = if (connectingSinceMillis == 0L) System.currentTimeMillis() else connectingSinceMillis

        rtcClient = try {
            WebRtcCallClient(this, callType, rtcListener)
        } catch (error: Exception) {
            mediaStarted = false
            errorText = "Unable to initialize call media."
            failAndFinish("failed")
            return
        }
        mediaReady = true
        speakerOn = rtcClient?.setSpeakerEnabled(callType == CallType.VIDEO) ?: speakerOn
        startForegroundCall(active.id)

        val queued = pendingSignals.sortedBy { it.id }.toList()
        pendingSignals.clear()
        queued.forEach { processSignal(it) }
        synchronizeSignals()

        if (isCaller && queued.none { it.kind == "answer" }) {
            rtcClient?.createOffer()
        }
    }

    private fun startRealtime(callId: String) {
        realtime?.close()
        realtime = CallRealtimeChannel(
            callId = callId,
            onSignal = { signal -> runOnUiThread { processSignal(signal) } },
            onCallUpdate = { update -> runOnUiThread { applyCallUpdate(update) } },
            onConnection = { connected ->
                if (!connected) lifecycleScope.launch { synchronizeSignals() }
            }
        ).also { it.connect() }
    }

    private fun startReconciliationLoop(callId: String) {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive && !ending.get()) {
                delay(1_500L)
                synchronizeSignals()
                val refreshed = repository.expireCall(callId).getOrNull()
                    ?: repository.fetchCall(callId).getOrNull()
                if (refreshed != null) applyCallUpdate(refreshed)
                if (
                    refreshed?.status == CallStatus.CONNECTING &&
                    connectingSinceMillis > 0L &&
                    System.currentTimeMillis() - connectingSinceMillis > 35_000L
                ) {
                    failAndFinish("connection_timeout")
                    break
                }
            }
        }
    }

    private suspend fun synchronizeSignals() {
        val active = call ?: return
        val signals = repository.fetchSignals(active.id, lastFetchedSignalId)
            .getOrNull()
            ?.sortedBy { it.id }
            .orEmpty()
        for (signal in signals) {
            // Only ordered REST reconciliation advances the cursor. Realtime can arrive out of
            // order, so letting a high realtime id move this cursor could permanently skip SDP.
            lastFetchedSignalId = maxOf(lastFetchedSignalId, signal.id)
            processSignal(signal)
        }
    }

    private fun processSignal(signal: CallSignal) {
        val active = call ?: return
        if (signal.callId != active.id) return
        if (signal.senderId == repository.currentUserId()) return
        val rtc = rtcClient
        if (rtc == null) {
            if (pendingSignals.none { it.id == signal.id }) pendingSignals.add(signal)
            return
        }
        if (!processedSignalIds.add(signal.id)) return
        when (signal.kind) {
            "offer", "answer" -> {
                val sdp = signal.payload.optString("sdp")
                rtc.applyRemoteDescription(signal.kind, sdp)
            }
            "ice" -> {
                rtc.addRemoteIceCandidate(
                    sdpMid = signal.payload.optString("sdpMid").takeIf { it.isNotBlank() },
                    lineIndex = signal.payload.optInt("sdpMLineIndex", 0),
                    candidate = signal.payload.optString("candidate")
                )
            }
            "hangup" -> lifecycleScope.launch { refreshAndMaybeFinish() }
        }
    }

    private fun applyCallUpdate(updated: BlinkCall) {
        val previous = call
        if (previous != null && previous.id != updated.id) return
        call = updated
        when (updated.status) {
            CallStatus.RINGING -> statusText = if (isCaller) "Calling…" else "Incoming call"
            CallStatus.CONNECTING -> {
                incomingRinging = false
                statusText = "Connecting…"
                if (connectingSinceMillis == 0L) connectingSinceMillis = System.currentTimeMillis()
                if (!isCaller && !mediaStarted) requestMediaPermissions(incomingAnswer = true)
            }
            CallStatus.CONNECTED -> {
                incomingRinging = false
                statusText = "Connected"
                if (!mediaStarted) requestMediaPermissions(incomingAnswer = !isCaller)
                startDurationTimer()
            }
            else -> if (updated.status.isTerminal) finishTerminal(updated.status)
        }
    }

    private fun startDurationTimer() {
        if (durationJob?.isActive == true) return
        durationJob = lifecycleScope.launch {
            while (isActive && !ending.get()) {
                delay(1_000L)
                elapsedSeconds += 1
            }
        }
    }

    private val rtcListener = object : WebRtcCallClient.Listener {
        override fun onLocalDescription(kind: String, description: SessionDescription) {
            val active = call ?: return
            lifecycleScope.launch {
                signalSendMutex.withLock {
                    repository.sendSignal(
                        active.id,
                        kind,
                        JSONObject().put("type", description.type.canonicalForm()).put("sdp", description.description)
                    )
                }
            }
        }

        override fun onLocalIceCandidate(candidate: IceCandidate) {
            val active = call ?: return
            lifecycleScope.launch {
                signalSendMutex.withLock {
                    repository.sendSignal(
                        active.id,
                        "ice",
                        JSONObject()
                            .put("sdpMid", candidate.sdpMid ?: JSONObject.NULL)
                            .put("sdpMLineIndex", candidate.sdpMLineIndex)
                            .put("candidate", candidate.sdp)
                    )
                }
            }
        }

        override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) {
            runOnUiThread {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        statusText = "Connected"
                        if (!connectedMarked) {
                            connectedMarked = true
                            lifecycleScope.launch {
                                call?.let { active ->
                                    repository.markConnected(active.id).getOrNull()?.let { applyCallUpdate(it) }
                                }
                            }
                        }
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        statusText = "Reconnecting…"
                        if (isCaller) scheduleIceRestart()
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        lifecycleScope.launch { failAndFinish("failed") }
                    }
                    PeerConnection.PeerConnectionState.CLOSED -> Unit
                    else -> if (!incomingRinging) statusText = "Connecting…"
                }
            }
        }

        override fun onError(message: String) {
            runOnUiThread { errorText = message }
        }
    }

    private fun scheduleIceRestart() {
        reconnectJob?.cancel()
        reconnectJob = lifecycleScope.launch {
            delay(2_500L)
            if (!ending.get() && statusText.startsWith("Reconnect")) {
                rtcClient?.restartIce()
                rtcClient?.createOffer()
            }
        }
    }

    private fun startForegroundCall(callId: String) {
        val serviceIntent = BlinkCallForegroundService.startIntent(
            context = this,
            callId = callId,
            peerName = peerName,
            callType = callType,
            status = statusText
        )
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun declineIncoming() {
        val active = call ?: return
        if (!ending.compareAndSet(false, true)) return
        lifecycleScope.launch {
            repository.declineCall(active.id)
            repository.dispatchPush(active.id, "declined")
            finishCallUi(CallStatus.DECLINED)
        }
    }

    private fun endFromUi() {
        val active = call ?: run { finish(); return }
        if (!ending.compareAndSet(false, true)) return
        lifecycleScope.launch {
            val ended = repository.endCall(active.id, "ended").getOrNull()
            val event = ended?.status?.wireValue ?: "ended"
            repository.dispatchPush(active.id, event)
            finishCallUi(ended?.status ?: CallStatus.ENDED)
        }
    }

    private suspend fun failAndFinish(reason: String) {
        val active = call ?: run { finish(); return }
        if (!ending.compareAndSet(false, true)) return
        val ended = repository.endCall(active.id, "failed").getOrNull()
        repository.dispatchPush(active.id, "failed")
        errorText = if (reason == "connection_timeout") "The call could not connect." else errorText
        finishCallUi(ended?.status ?: CallStatus.FAILED, delayMillis = 1_200L)
    }

    private suspend fun refreshAndMaybeFinish() {
        val active = call ?: return
        repository.fetchCall(active.id).getOrNull()?.let { applyCallUpdate(it) }
    }

    private fun finishTerminal(status: CallStatus) {
        if (!ending.compareAndSet(false, true)) return
        lifecycleScope.launch { finishCallUi(status) }
    }

    private suspend fun finishCallUi(status: CallStatus, delayMillis: Long = 650L) {
        statusText = terminalLabel(status)
        incomingRinging = false
        call?.id?.let { IncomingCallNotification.cancel(this, it) }
        stopService(Intent(this, BlinkCallForegroundService::class.java))
        delay(delayMillis)
        finish()
    }

    private fun terminalLabel(status: CallStatus): String = when (status) {
        CallStatus.DECLINED -> "Call declined"
        CallStatus.BUSY -> "User is busy"
        CallStatus.MISSED -> "No answer"
        CallStatus.CANCELLED -> "Call cancelled"
        CallStatus.FAILED -> "Call failed"
        else -> "Call ended"
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun CallScreen(
    callType: CallType,
    peerName: String,
    peerUsername: String,
    peerAvatar: String,
    statusText: String,
    errorText: String?,
    incomingRinging: Boolean,
    mediaReady: Boolean,
    microphoneOn: Boolean,
    cameraOn: Boolean,
    speakerOn: Boolean,
    elapsedSeconds: Int,
    rtcClient: WebRtcCallClient?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (callType == CallType.VIDEO && mediaReady) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> SurfaceViewRenderer(context) },
                    update = { renderer -> rtcClient?.attachRemoteRenderer(renderer) }
                )
                if (cameraOn) {
                    AndroidView(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp, end = 18.dp)
                            .size(width = 112.dp, height = 158.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                        factory = { context -> SurfaceViewRenderer(context) },
                        update = { renderer -> rtcClient?.attachLocalRenderer(renderer) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!(callType == CallType.VIDEO && mediaReady)) {
                        AsyncImage(
                            model = peerAvatar,
                            contentDescription = peerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(118.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.size(16.dp))
                    }
                    Text(peerName.ifBlank { "Blink user" }, style = MaterialTheme.typography.headlineSmall)
                    if (peerUsername.isNotBlank()) {
                        Text("@$peerUsername", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (statusText == "Connected" && elapsedSeconds > 0) formatDuration(elapsedSeconds) else statusText,
                        color = if (errorText == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                    if (!errorText.isNullOrBlank()) {
                        Spacer(Modifier.size(4.dp))
                        Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }

                if (incomingRinging) {
                    Row(horizontalArrangement = Arrangement.spacedBy(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        CallRoundButton(
                            icon = Icons.Default.CallEnd,
                            label = "Decline",
                            container = MaterialTheme.colorScheme.error,
                            onClick = onDecline
                        )
                        CallRoundButton(
                            icon = Icons.Default.Call,
                            label = "Answer",
                            container = Color(0xFF22C55E),
                            onClick = onAnswer
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CallControlButton(
                                icon = if (microphoneOn) Icons.Default.Mic else Icons.Default.MicOff,
                                label = if (microphoneOn) "Mute" else "Unmute",
                                onClick = onToggleMic
                            )
                            CallControlButton(
                                icon = if (speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                label = "Speaker",
                                onClick = onToggleSpeaker
                            )
                            if (callType == CallType.VIDEO) {
                                CallControlButton(
                                    icon = if (cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    label = "Camera",
                                    onClick = onToggleCamera
                                )
                                CallControlButton(
                                    icon = Icons.Default.Cameraswitch,
                                    label = "Flip",
                                    onClick = onSwitchCamera
                                )
                            }
                        }
                        Spacer(Modifier.size(22.dp))
                        CallRoundButton(
                            icon = Icons.Default.CallEnd,
                            label = "End",
                            container = MaterialTheme.colorScheme.error,
                            onClick = onEnd
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            IconButton(onClick = onClick, modifier = Modifier.size(54.dp)) {
                Icon(icon, contentDescription = label)
            }
        }
        Spacer(Modifier.size(5.dp))
        Text(label, fontSize = 10.sp)
    }
}

@Composable
private fun CallRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    container: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = container),
            modifier = Modifier.size(66.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(Modifier.size(6.dp))
        Text(label, fontSize = 11.sp)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
