package com.example.devicetracker.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.devicetracker.data.remote.NonRetryableSyncException
import dagger.hilt.android.EntryPointAccessors

class SheetsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val fullSync = inputData.getBoolean(KEY_FULL_SYNC, false)
        Log.i(TAG, "Sync worker started, attempt=$runAttemptCount, fullSync=$fullSync")
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        val deviceLogRepository = entryPoint.deviceLogRepository()
        val hgtCheckRepository = entryPoint.hgtCheckRepository()

        val pushLogResult = deviceLogRepository.syncPending()
        if (pushLogResult.isFailure) {
            val error = pushLogResult.exceptionOrNull()
            val workerResult = if (error is NonRetryableSyncException) Result.failure() else Result.retry()
            Log.e(
                TAG,
                "Sync worker push failed, nonRetryable=${error is NonRetryableSyncException}, message=${error?.message}"
            )
            return workerResult
        }

        val pushHgtResult = hgtCheckRepository.syncPending()
        if (pushHgtResult.isFailure) {
            val error = pushHgtResult.exceptionOrNull()
            val workerResult = if (error is NonRetryableSyncException) Result.failure() else Result.retry()
            Log.e(
                TAG,
                "Sync worker push HGT failed, nonRetryable=${error is NonRetryableSyncException}, message=${error?.message}"
            )
            return workerResult
        }

        if (!fullSync) {
            Log.i(TAG, "Sync worker finished successfully (push-only quick mode)")
            return Result.success()
        }

        val pullLogResult = deviceLogRepository.refreshFromRemote()
        if (pullLogResult.isFailure) {
            val pullError = pullLogResult.exceptionOrNull()
            val workerResult = if (pullError is NonRetryableSyncException) Result.failure() else Result.retry()
            Log.e(
                TAG,
                "Sync worker pull logs failed, nonRetryable=${pullError is NonRetryableSyncException}, message=${pullError?.message}"
            )
            return workerResult
        }

        val pullHgtResult = hgtCheckRepository.refreshFromRemote()
        if (pullHgtResult.isSuccess) {
            Log.i(TAG, "Sync worker finished successfully (push + pull)")
            return Result.success()
        }

        val pullError = pullHgtResult.exceptionOrNull()
        val workerResult = if (pullError is NonRetryableSyncException) Result.failure() else Result.retry()
        Log.e(
            TAG,
            "Sync worker pull HGT failed, nonRetryable=${pullError is NonRetryableSyncException}, message=${pullError?.message}"
        )
        return workerResult
    }

    companion object {
        const val WORK_NAME = "sheet_sync_work"
        const val IMMEDIATE_WORK_NAME = "sheet_sync_work_immediate"
        const val IMMEDIATE_TAG = "sheet_sync_immediate"
        const val KEY_FULL_SYNC = "full_sync"
        private const val TAG = "SheetsSyncWorker"
    }
}
