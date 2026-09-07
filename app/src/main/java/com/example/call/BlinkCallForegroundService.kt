package com.example.call

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

class BlinkCallForegroundService : Service() {
    companion object {
        const val EXTRA_PEER_NAME = "blink_call_peer_name"
        const val EXTRA_CALL_TYPE = "blink_call_type"
        const val EXTRA_STATUS = "blink_call_status"

        @Volatile
        var activeCallId: String? = null
            private set

        fun startIntent(
            context: android.content.Context,
            callId: String,
            peerName: String,
            callType: CallType,
            status: String
        ): Intent = Intent(context, BlinkCallForegroundService::class.java).apply {
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
            putExtra(EXTRA_PEER_NAME, peerName)
            putExtra(EXTRA_CALL_TYPE, callType.wireValue)
            putExtra(EXTRA_STATUS, status)
        }
    }

    override fun onCreate() {
        super.onCreate()
        IncomingCallNotification.createChannels(this)
    }

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(CallActivity.EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        activeCallId = callId
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty()
        val callType = CallType.fromWire(intent?.getStringExtra(EXTRA_CALL_TYPE))
        val status = intent?.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "In call" }
        val notification = IncomingCallNotification.ongoingNotification(
            context = this,
            callId = callId,
            peerName = peerName,
            callType = callType,
            status = status
        )

        // Android 14+ validates the runtime foreground-service types and their
        // corresponding while-in-use permissions. Audio calls must not request the
        // camera service type, while video calls need both microphone and camera.
        val foregroundServiceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
            if (callType == CallType.VIDEO) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        ServiceCompat.startForeground(
            this,
            IncomingCallNotification.FOREGROUND_NOTIFICATION_ID,
            notification,
            foregroundServiceTypes
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (activeCallId != null) activeCallId = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
