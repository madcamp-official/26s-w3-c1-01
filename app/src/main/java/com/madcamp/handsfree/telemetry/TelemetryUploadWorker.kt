package com.madcamp.handsfree.telemetry

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class TelemetryUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val queue = LocalTelemetryQueue(applicationContext)
        val events = queue.peek(MAX_UPLOAD_EVENTS)
        if (events.isEmpty()) return Result.success()

        val uploader: TelemetryUploader = if (inputData.getBoolean(KEY_USE_LOGCAT_UPLOADER, false)) {
            LogcatTelemetryUploader()
        } else {
            FirebaseTelemetryUploader(applicationContext)
        }

        val result = uploader.upload(events)
        return if (result.success) {
            queue.removeUploaded(result.uploadedEventIds)
            Log.i(TAG, "Uploaded ${result.uploadedEventIds.size} telemetry events; remaining=${queue.count()}")
            Result.success()
        } else {
            Log.w(TAG, "Telemetry upload failed: ${result.errorMessage}; queued=${queue.count()}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TelemetryUploadWorker"
        private const val UNIQUE_DAILY_UPLOAD = "daily_telemetry_upload_9am"
        private const val UNIQUE_MANUAL_UPLOAD = "manual_telemetry_upload"
        private const val KEY_USE_LOGCAT_UPLOADER = "use_logcat_uploader"
        private const val MAX_UPLOAD_EVENTS = 100

        fun scheduleDailyAt9(context: Context) {
            val request = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNext9amMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_DAILY_UPLOAD,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueManualTestUpload(context: Context, useLogcatUploader: Boolean = true) {
            val data = androidx.work.workDataOf(KEY_USE_LOGCAT_UPLOADER to useLogcatUploader)
            val request = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                .setInputData(data)
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_MANUAL_UPLOAD,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun networkConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }

        private fun delayUntilNext9amMillis(now: LocalDateTime = LocalDateTime.now()): Long {
            var next = now.with(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }
    }
}
