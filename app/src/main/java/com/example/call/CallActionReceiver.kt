package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        if (callId.isBlank()) return
        IncomingCallNotification.cancel(context, callId)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = CallRepository()
                when (intent.action) {
                    ACTION_DECLINE -> {
                        repository.declineCall(callId)
                        repository.dispatchPush(callId, "declined")
                    }
                    ACTION_END -> {
                        val result = repository.endCall(callId, "ended")
                        val event = result.getOrNull()?.status?.wireValue ?: "ended"
                        repository.dispatchPush(callId, event)
                    }
                }
                context.stopService(Intent(context, BlinkCallForegroundService::class.java))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
