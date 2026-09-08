package com.example.call

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.supabase.SupabaseService
import java.util.concurrent.TimeUnit

/**
 * Receiver-side safety net for unanswered calls.
 *
 * The caller normally expires a ringing call, but that is not reliable if the caller app
 * is killed or loses connectivity. Scheduling this when the incoming FCM invite arrives
 * guarantees that the callee also asks Supabase to turn an expired ringing call into a
 * durable MISSED record that can appear in Call History.
 */
class CallTimeoutWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val callId = inputData.getString(KEY_CALL_ID).orEmpty()
        if (callId.isBlank()) return Result.success()

        val peerName = inputData.getString(KEY_PEER_NAME).orEmpty().ifBlank { "Blink user" }
        val callType = CallType.fromWire(inputData.getString(KEY_CALL_TYPE))

        return runCatching {
            SupabaseService.initialize(applicationContext)
            val call = CallRepository().expireCall(callId).getOrThrow()
            if (call.status == CallStatus.MISSED) {
                IncomingCallNotification.cancel(applicationContext, callId)
                IncomingCallNotification.showMissed(
                    context = applicationContext,
                    callId = callId,
                    callType = callType,
                    peerName = peerName
                )
            }
            Result.success()
        }.getOrElse {
            // A temporary network failure should not erase the chance to persist the missed call.
            Result.retry()
        }
    }

    companion object {
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_CALL_TYPE = "call_type"
        private const val KEY_PEER_NAME = "peer_name"
        private const val WORK_PREFIX = "blink_call_timeout_"

        fun schedule(context: Context, callId: String, callType: CallType, peerName: String) {
            if (callId.isBlank()) return
            val data = Data.Builder()
                .putString(KEY_CALL_ID, callId)
                .putString(KEY_CALL_TYPE, callType.wireValue)
                .putString(KEY_PEER_NAME, peerName)
                .build()
            val work = OneTimeWorkRequestBuilder<CallTimeoutWorker>()
                .setInitialDelay(60, TimeUnit.SECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_PREFIX + callId,
                ExistingWorkPolicy.REPLACE,
                work
            )
        }

        fun cancel(context: Context, callId: String) {
            if (callId.isBlank()) return
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_PREFIX + callId)
        }
    }
}
