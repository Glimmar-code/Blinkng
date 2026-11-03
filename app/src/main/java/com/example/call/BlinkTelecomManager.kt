package com.example.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLINK's bridge to Android Telecom.
 *
 * WebRTC still transports media and Supabase still owns BLINK call state. Telecom is the
 * device-level coordinator so Bluetooth/wearables/Android Auto/system calls can cooperate
 * with BLINK instead of competing for the microphone and audio route.
 */
object BlinkTelecomManager {
    private const val TAG = "BlinkTelecom"
    private const val CONTROL_READY_TIMEOUT_MS = 6_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = ConcurrentHashMap<String, Session>()

    private data class PeerMetadata(
        val peerId: String,
        val peerUsername: String,
        val peerName: String,
        val peerAvatar: String,
        val conversationId: String,
        val callType: CallType,
        val incoming: Boolean
    )

    private class Session(
        val metadata: PeerMetadata
    ) {
        val controlReady = CompletableDeferred<CallControlScope>()
        val suppressNextSystemDisconnect = AtomicBoolean(false)
        val locallyAnswered = AtomicBoolean(false)
        @Volatile var control: CallControlScope? = null
        @Volatile var job: Job? = null
        @Volatile var onSystemSetActive: (suspend () -> Unit)? = null
        @Volatile var onSystemSetInactive: (suspend () -> Unit)? = null
        @Volatile var onEndpointChanged: ((CallEndpointCompat) -> Unit)? = null
        @Volatile var lastNonSpeakerEndpoint: CallEndpointCompat? = null
    }

    fun registerApplication(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            CallsManager(context.applicationContext).registerAppWithTelecom(
                CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to register BLINK with Android Telecom", error)
        }
    }

    /**
     * Register a BLINK call exactly once with Telecom. This is safe to call from both the
     * incoming notification path and CallActivity; duplicate calls are coalesced by call id.
     */
    fun ensureCallRegistered(
        context: Context,
        callId: String,
        peerId: String,
        peerUsername: String,
        peerName: String,
        peerAvatar: String,
        conversationId: String,
        callType: CallType,
        incoming: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || callId.isBlank()) return
        if (sessions.containsKey(callId)) return

        val metadata = PeerMetadata(
            peerId = peerId,
            peerUsername = peerUsername,
            peerName = peerName,
            peerAvatar = peerAvatar,
            conversationId = conversationId,
            callType = callType,
            incoming = incoming
        )
        val session = Session(metadata)
        if (sessions.putIfAbsent(callId, session) != null) return

        val appContext = context.applicationContext
        session.job = scope.launch {
            val callsManager = CallsManager(appContext)
            val startingEndpoints = withTimeoutOrNull(1_500L) {
                callsManager.getAvailableStartingCallEndpoints().first()
            }.orEmpty()
            val preferredStartingEndpoint = if (callType == CallType.VIDEO) {
                startingEndpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_SPEAKER }
            } else {
                startingEndpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_BLUETOOTH }
                    ?: startingEndpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_WIRED_HEADSET }
                    ?: startingEndpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_EARPIECE }
            }

            val attributes = CallAttributesCompat(
                displayName = peerName.ifBlank { "Blink user" },
                address = telecomAddress(peerUsername, peerId, callId),
                direction = if (incoming) {
                    CallAttributesCompat.DIRECTION_INCOMING
                } else {
                    CallAttributesCompat.DIRECTION_OUTGOING
                },
                callType = telecomCallType(callType),
                // BLINK can temporarily pause media when Telecom gives another call priority.
                callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
                preferredStartingCallEndpoint = preferredStartingEndpoint,
                isLogExcluded = true
            )

            try {
                callsManager.addCall(
                    callAttributes = attributes,
                    onAnswer = { requestedType ->
                        handleSystemAnswer(appContext, callId, requestedType)
                    },
                    onDisconnect = { cause ->
                        handleSystemDisconnect(appContext, callId, cause)
                    },
                    onSetActive = {
                        sessions[callId]?.onSystemSetActive?.invoke()
                    },
                    onSetInactive = {
                        sessions[callId]?.onSystemSetInactive?.invoke()
                    }
                ) {
                    session.control = this
                    if (!session.controlReady.isCompleted) {
                        session.controlReady.complete(this)
                    }
                    launch {
                        currentCallEndpoint.collect { endpoint ->
                            if (endpoint.type != CallEndpointCompat.TYPE_SPEAKER) {
                                session.lastNonSpeakerEndpoint = endpoint
                            }
                            session.onEndpointChanged?.invoke(endpoint)
                        }
                    }
                }
            } catch (error: Exception) {
                if (!session.controlReady.isCompleted) {
                    session.controlReady.completeExceptionally(error)
                }
                Log.w(TAG, "Telecom rejected BLINK call $callId", error)
            } finally {
                sessions.remove(callId, session)
            }
        }
    }

    fun bindMediaLifecycle(
        callId: String,
        onSystemSetActive: suspend () -> Unit,
        onSystemSetInactive: suspend () -> Unit
    ) {
        sessions[callId]?.let { session ->
            session.onSystemSetActive = onSystemSetActive
            session.onSystemSetInactive = onSystemSetInactive
        }
    }

    fun bindEndpointLifecycle(
        callId: String,
        onEndpointChanged: (CallEndpointCompat) -> Unit
    ) {
        sessions[callId]?.onEndpointChanged = onEndpointChanged
    }

    suspend fun setSpeakerEnabled(callId: String, enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val session = sessions[callId] ?: return false
        val control = awaitControl(callId) ?: return false
        val available = withTimeoutOrNull(1_500L) {
            control.availableEndpoints.first()
        }.orEmpty()
        if (available.isEmpty()) return false

        val target = if (enabled) {
            available.firstOrNull { it.type == CallEndpointCompat.TYPE_SPEAKER }
        } else {
            session.lastNonSpeakerEndpoint?.takeIf { previous ->
                available.any { it.identifier == previous.identifier }
            } ?: available.firstOrNull { it.type == CallEndpointCompat.TYPE_WIRED_HEADSET }
                ?: available.firstOrNull { it.type == CallEndpointCompat.TYPE_BLUETOOTH }
                ?: available.firstOrNull { it.type == CallEndpointCompat.TYPE_EARPIECE }
        } ?: return false

        return when (runCatching { control.requestEndpointChange(target) }.getOrNull()) {
            is CallControlResult.Success -> true
            else -> false
        }
    }

    suspend fun answer(callId: String, callType: CallType): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val control = awaitControl(callId) ?: return false
        return when (runCatching { control.answer(telecomCallType(callType)) }.getOrNull()) {
            is CallControlResult.Success -> {
                sessions[callId]?.locallyAnswered?.set(true)
                true
            }
            else -> false
        }
    }

    suspend fun setActive(callId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val control = awaitControl(callId) ?: return false
        return when (runCatching { control.setActive() }.getOrNull()) {
            is CallControlResult.Success -> true
            else -> false
        }
    }

    /**
     * Remove a call from Telecom when BLINK has already committed the terminal server state.
     * The suppression flag prevents Telecom's disconnect callback from writing the state twice.
     */
    fun disconnect(callId: String, causeCode: Int = DisconnectCause.LOCAL) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || callId.isBlank()) return
        val session = sessions[callId] ?: return
        session.suppressNextSystemDisconnect.set(true)
        scope.launch {
            val control = awaitControl(callId)
            if (control != null) {
                runCatching { control.disconnect(DisconnectCause(causeCode)) }
                    .onFailure { Log.w(TAG, "Unable to remove BLINK call $callId from Telecom", it) }
            } else {
                session.job?.cancel()
                sessions.remove(callId, session)
            }
        }
    }

    fun clearMediaLifecycle(callId: String) {
        sessions[callId]?.let {
            it.onSystemSetActive = null
            it.onSystemSetInactive = null
            it.onEndpointChanged = null
        }
    }

    /**
     * The callee receives the "answered" push on every signed-in device. Keep Telecom alive
     * on the device that actually answered, but remove the stale ringing session elsewhere.
     */
    fun dismissIfAnsweredElsewhere(callId: String) {
        val session = sessions[callId] ?: return
        if (!session.locallyAnswered.get()) {
            disconnect(callId, DisconnectCause.REMOTE)
        }
    }

    private suspend fun awaitControl(callId: String): CallControlScope? {
        val session = sessions[callId] ?: return null
        session.control?.let { return it }
        return withTimeoutOrNull(CONTROL_READY_TIMEOUT_MS) {
            runCatching { session.controlReady.await() }.getOrNull()
        }
    }

    private suspend fun handleSystemAnswer(
        context: Context,
        callId: String,
        requestedType: Int
    ) {
        val session = sessions[callId] ?: return
        if (!session.metadata.incoming) return

        session.locallyAnswered.set(true)
        val repository = CallRepository()
        val answered = repository.answerCall(callId).getOrThrow()
        IncomingCallNotification.cancel(context, callId)
        IncomingCallBannerActivity.dismiss(callId)

        val requestedCallType = if (requestedType == CallAttributesCompat.CALL_TYPE_VIDEO_CALL) {
            CallType.VIDEO
        } else {
            answered.type
        }
        val metadata = session.metadata
        val intent = CallActivity.incomingIntent(
            context = context,
            callId = callId,
            peerId = metadata.peerId,
            peerUsername = metadata.peerUsername,
            peerName = metadata.peerName,
            peerAvatar = metadata.peerAvatar,
            callType = requestedCallType,
            conversationId = metadata.conversationId,
            answerImmediately = false
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    private suspend fun handleSystemDisconnect(
        context: Context,
        callId: String,
        cause: DisconnectCause
    ) {
        val session = sessions[callId] ?: return
        if (session.suppressNextSystemDisconnect.compareAndSet(true, false)) return

        val repository = CallRepository()
        val current = repository.fetchCall(callId).getOrNull()
        if (current == null || current.status.isTerminal) {
            IncomingCallNotification.cancel(context, callId)
            IncomingCallBannerActivity.dismiss(callId)
            return
        }

        // A Telecom MISSED callback can arrive before BLINK's authoritative ring timeout.
        // Leave the row ringing in that case; CallTimeoutWorker will transition it to MISSED
        // at timeout_at instead of incorrectly turning an unanswered call into DECLINED.
        val result = when {
            session.metadata.incoming &&
                current.status == CallStatus.RINGING &&
                cause.code == DisconnectCause.MISSED -> Result.success(current)
            session.metadata.incoming &&
                current.status == CallStatus.RINGING &&
                cause.code == DisconnectCause.REJECTED -> repository.declineCall(callId)
            else -> repository.endCall(
                callId = callId,
                reason = when (cause.code) {
                    DisconnectCause.REJECTED -> "declined"
                    DisconnectCause.REMOTE -> "remote"
                    else -> "system"
                }
            )
        }
        result.getOrNull()
            ?.takeIf { it.status.isTerminal }
            ?.let { ended -> repository.dispatchPush(callId, ended.status.wireValue) }
        IncomingCallNotification.cancel(context, callId)
        IncomingCallBannerActivity.dismiss(callId)
        context.stopService(Intent(context, BlinkCallForegroundService::class.java))
    }

    private fun telecomCallType(type: CallType): Int =
        if (type == CallType.VIDEO) {
            CallAttributesCompat.CALL_TYPE_VIDEO_CALL
        } else {
            CallAttributesCompat.CALL_TYPE_AUDIO_CALL
        }

    private fun telecomAddress(username: String, peerId: String, callId: String): Uri {
        val identity = username.trim().removePrefix("@")
            .ifBlank { peerId.ifBlank { callId } }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        // A SIP-form address keeps Core-Telecom compatible with Android 8.0/8.1.
        return Uri.parse("sip:$identity@blink.com.ng")
    }
}
