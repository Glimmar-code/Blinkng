package com.example.call

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.supabase.SupabaseService
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Last-resort durable call termination. Local camera/microphone close
 * immediately; WorkManager attempts to synchronize the server later.
 *
 * No tokens, shared secrets, or other private credentials are persisted.
 */
class CallEndRetryWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val callId = inputData.getString(KEY_CALL_ID).orEmpty()
        val expectedAccount = inputData.getString(KEY_ACCOUNT).orEmpty()
        val action = CallEndRecoveryPolicy.Action.fromWire(inputData.getString(KEY_ACTION))
            ?: return Result.success()
        if (!isValidCallId(callId) || expectedAccount.isBlank()) return Result.success()

        SupabaseService.initialize(applicationContext)
        val repository = CallRepository()
        if (repository.currentUserId() != expectedAccount) {
            // An old phone push or unfinished call must never act on another
            // user's session after BLINK account switching.
            return Result.success()
        }

        val current = repository.fetchCall(callId).getOrElse { error ->
            return retryOrStop(error)
        }
        if (!CallEndRecoveryPolicy.canApply(action, current.status)) {
            return Result.success()
        }

        val outcome = when (action) {
            CallEndRecoveryPolicy.Action.DECLINE -> repository.declineCall(callId)
            CallEndRecoveryPolicy.Action.END -> repository.endCall(callId, "ended")
            CallEndRecoveryPolicy.Action.FAIL -> repository.endCall(callId, "failed")
        }
        val ended = outcome.getOrElse { error -> return retryOrStop(error) }
        if (ended.status.isTerminal) {
            repository.dispatchPush(callId, ended.status.wireValue)
                .onFailure { Log.w(TAG, "Ending call update could not be delivered") }
            IncomingCallNotification.handleCallUpdate(
                applicationContext, callId, ended.status.wireValue
            )
        }
        return Result.success()
    }

    private fun retryOrStop(error: Throwable): Result {
        val status = (error as? CallApiException)?.statusCode
        if (!CallEndRecoveryPolicy.shouldRetry(runAttemptCount, status)) {
            Log.w(TAG, "Unable to reconcile call termination")
            return Result.failure()
        }
        return Result.retry()
    }

    companion object {
        private const val TAG = "BlinkCallEndRetry"
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_ACCOUNT = "account_id"
        private const val KEY_ACTION = "action"
        private const val WORK_PREFIX = "blink_end_call_"

        private fun isValidCallId(callId: String): Boolean =
            runCatching { UUID.fromString(callId).toString().equals(callId, ignoreCase = true) }
                .getOrDefault(false)

        internal fun schedule(
            context: Context,
            callId: String,
            accountId: String,
            action: CallEndRecoveryPolicy.Action
        ) {
            if (!isValidCallId(callId) || accountId.isBlank()) return
            val data = Data.Builder()
                .putString(KEY_CALL_ID, callId)
                .putString(KEY_ACCOUNT, accountId)
                .putString(KEY_ACTION, action.wire)
                .build()
            val request = OneTimeWorkRequestBuilder<CallEndRetryWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_PREFIX + accountId + "_" + callId,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
