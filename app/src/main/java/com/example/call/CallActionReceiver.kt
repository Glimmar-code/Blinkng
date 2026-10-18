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

class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DECLINE = "com.example.blink.call.DECLINE"
        const val ACTION_END = "com.example.blink.call.END"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallActivity.EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank() ||
            intent.action !in setOf(ACTION_DECLINE, ACTION_END)) return
        IncomingCallNotification.cancel(context, callId)
        IncomingCallBannerActivity.dismiss(callId)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SupabaseService.initialize(context.applicationContext)
                val repository = CallRepository()
                val event = when (intent.action) {
                    ACTION_DECLINE -> repository.declineCall(callId).getOrNull()
                        ?.status?.wireValue
                    ACTION_END -> repository.endCall(callId, "ended").getOrNull()
                        ?.status?.wireValue
                    else -> null
                }
                if (event != null) {
                    CallNotificationLedger.markUpdated(
                        context, repository.currentUserId(), callId, event
                    )
                    repository.dispatchPush(callId, event)
                } else {
                    // A notification action must not be lost just because the
                    // network disappeared before the call status was saved.
                    val action = when (intent.action) {
                        ACTION_DECLINE -> CallEndRecoveryPolicy.Action.DECLINE
                        else -> CallEndRecoveryPolicy.Action.END
                    }
                    CallEndRetryWorker.schedule(
                        context, callId, repository.currentUserId(), action
                    )
                }
                if (BlinkCallForegroundService.activeCallId == callId) {
                    context.stopService(Intent(context, BlinkCallForegroundService::class.java))
                }
            } catch (error: Exception) {
                Log.w("BlinkCalls", "Call action could not be completed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
