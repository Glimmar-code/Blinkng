package com.example.call

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.util.startActivitySafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object BlinkCallLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val starting = AtomicBoolean(false)

    fun startOutgoing(
        context: Context,
        conversationId: String,
        calleeId: String,
        calleeUsername: String,
        calleeName: String,
        calleeAvatar: String,
        type: CallType
    ) {
        if (conversationId.isBlank() || conversationId.startsWith("local_")) {
            Toast.makeText(context, "Open the conversation online before starting a call.", Toast.LENGTH_SHORT).show()
            return
        }
        if (calleeId.isBlank()) {
            Toast.makeText(context, "This contact is still loading. Try the call again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!starting.compareAndSet(false, true)) return

        scope.launch {
            try {
                val repository = CallRepository()
                val result = repository.startCall(conversationId, calleeId, type)
                val call = result.getOrElse { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "Unable to start the call.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val intent = CallActivity.outgoingIntent(
                    context = context,
                    callId = call.id,
                    peerId = calleeId,
                    peerUsername = calleeUsername,
                    peerName = calleeName,
                    peerAvatar = calleeAvatar,
                    callType = type
                )
                // Deliver the incoming alert before loading JNI in CallActivity.
                // Even a native WebRTC startup abort must not swallow the invite.
                val pushOutcome = repository.dispatchPush(call.id, "invite")
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!context.startActivitySafely(intent, "Unable to open the Blink call screen.")) {
                    val ended = repository.endCall(call.id, "failed").getOrNull()
                    if (ended != null) repository.dispatchPush(call.id, ended.status.wireValue)
                    return@launch
                }

                pushOutcome.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "Call started, but the other phone may not receive an alert.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                starting.set(false)
            }
        }
    }
}
