package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Call actions must not report completion when Supabase rejected a state change. */
class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DECLINE = "com.example.blink.call.DECLINE"
        const val ACTION_END = "com.example.blink.call.END"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallActivity.EXTRA_CALL_ID).orEmpty()
        val action = when (intent.action) {
            ACTION_DECLINE -> CallEndRecoveryPolicy.Action.DECLINE
            ACTION_END -> CallEndRecoveryPolicy.Action.END
            else -> return
        }
        if (callId.isBlank()) return

        val appContext = context.applicationContext
        IncomingCallNotification.cancel(appContext, callId)
        IncomingCallBannerActivity.dismiss(callId)

        // An Android BroadcastReceiver is only given a short execution window.
        // Persist a delayed WorkManager fallback BEFORE launching the best-effort
        // fast RPC; the OS may terminate goAsync() before OkHttp times out.
        runCatching {
            SupabaseService.initialize(appContext)
            val userId = CallRepository().currentUserId()
            CallEndRetryWorker.schedule(
                appContext, callId, userId, action, initialDelaySeconds = 15L
            )
        }.onFailure {
            Log.w("BlinkCalls", "Could not queue durable call action", it)
        }

        // Stop local ringing / Telecom promptly, even when the network call
        // finishes after the receiver's OS background execution window.
        BlinkTelecomManager.disconnect(
            callId,
            if (action == CallEndRecoveryPolicy.Action.DECLINE)
                android.telecom.DisconnectCause.REJECTED
            else android.telecom.DisconnectCause.LOCAL
        )
        if (BlinkCallForegroundService.activeCallId == callId) {
            appContext.stopService(Intent(appContext, BlinkCallForegroundService::class.java))
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SupabaseService.initialize(appContext)
                val repository = CallRepository()
                val userId = repository.currentUserId()
                val outcome = when (action) {
                    CallEndRecoveryPolicy.Action.DECLINE -> repository.declineCall(callId)
                    else -> repository.endCall(callId, "ended")
                }
                val persisted = outcome.getOrNull()
                if (persisted != null) {
                    repository.dispatchPush(callId, persisted.status.wireValue)
                        .onFailure { Log.w("BlinkCalls", "Call status push failed") }
                } else {
                    CallEndRetryWorker.schedule(appContext, callId, userId, action)
                }
            } catch (error: Exception) {
                Log.w("BlinkCalls", "Could not complete call notification action", error)
                runCatching {
                    SupabaseService.initialize(appContext)
                    CallEndRetryWorker.schedule(
                        appContext, callId, CallRepository().currentUserId(), action
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
