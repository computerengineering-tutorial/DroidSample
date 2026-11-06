package com.example.droidsample.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Encapsulates constraint configurations for WorkManager tasks.
 */
data class WorkSchedulerConstraints(
    val networkType: NetworkType = NetworkType.CONNECTED,
    val requiresCharging: Boolean = false,
    val requiresDeviceIdle: Boolean = false,
    val requiresBatteryNotLow: Boolean = true,
    val requiresStorageNotLow: Boolean = false
) {
    fun toWorkConstraints(): Constraints {
        val builder = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresCharging(requiresCharging)
            .setRequiresBatteryNotLow(requiresBatteryNotLow)
            .setRequiresStorageNotLow(requiresStorageNotLow)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            builder.setRequiresDeviceIdle(requiresDeviceIdle)
        }

        return builder.build()
    }
}

/**
 * Worker implementation executing sample background sync tasks.
 * Publishes progress and logs execution to [SystemBroadcastReceiver].
 */
class SampleBackgroundWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskName = inputData.getString(KEY_TASK_NAME) ?: "Default Task"

        SystemBroadcastReceiver.recordEvent(
            SystemBroadcastEvent(
                id = System.currentTimeMillis(),
                timestamp = System.currentTimeMillis(),
                action = "androidx.work.STARTED",
                summary = "WorkManager worker started: $taskName [ID: ${id}]",
                category = EventCategory.SYSTEM,
                extras = mapOf("taskId" to id.toString(), "taskName" to taskName)
            )
        )

        // Simulate multi-step processing with progress
        for (step in 1..5) {
            val progressPercent = step * 20
            setProgress(workDataOf(KEY_PROGRESS to progressPercent))
            delay(500)
        }

        SystemBroadcastReceiver.recordEvent(
            SystemBroadcastEvent(
                id = System.currentTimeMillis(),
                timestamp = System.currentTimeMillis(),
                action = "androidx.work.COMPLETED",
                summary = "WorkManager worker finished: $taskName successfully",
                category = EventCategory.SYSTEM,
                extras = mapOf("taskId" to id.toString(), "taskName" to taskName, "result" to "SUCCESS")
            )
        )

        return Result.success(workDataOf(KEY_OUTPUT_RESULT to "Task $taskName processed successfully"))
    }

    companion object {
        const val KEY_TASK_NAME = "key_task_name"
        const val KEY_PROGRESS = "key_progress"
        const val KEY_OUTPUT_RESULT = "key_output_result"
    }
}

/**
 * Helper utility to manage WorkManager enqueueing, queries, and cancellations.
 */
object WorkSchedulerHelper {

    const val TAG_ONE_TIME_SYNC = "tag_one_time_sync"
    const val TAG_PERIODIC_SYNC = "tag_periodic_sync"
    const val UNIQUE_PERIODIC_WORK_NAME = "sample_periodic_work"

    /**
     * Enqueue a one-time background job with the specified constraints and delay.
     */
    fun enqueueOneTimeWork(
        context: Context,
        taskName: String = "One-Time Sync Job",
        constraints: WorkSchedulerConstraints = WorkSchedulerConstraints(),
        initialDelaySeconds: Long = 0
    ): UUID {
        val workManager = WorkManager.getInstance(context)

        val request = OneTimeWorkRequestBuilder<SampleBackgroundWorker>()
            .setConstraints(constraints.toWorkConstraints())
            .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            .addTag(TAG_ONE_TIME_SYNC)
            .setInputData(workDataOf(SampleBackgroundWorker.KEY_TASK_NAME to taskName))
            .build()

        workManager.enqueue(request)
        return request.id
    }

    /**
     * Enqueue a recurring periodic work request (min 15 minutes).
     */
    fun enqueuePeriodicWork(
        context: Context,
        taskName: String = "Periodic Health Sync",
        intervalMinutes: Long = 15,
        constraints: WorkSchedulerConstraints = WorkSchedulerConstraints()
    ) {
        val workManager = WorkManager.getInstance(context)

        val request = PeriodicWorkRequestBuilder<SampleBackgroundWorker>(
            intervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints.toWorkConstraints())
            .addTag(TAG_PERIODIC_SYNC)
            .setInputData(workDataOf(SampleBackgroundWorker.KEY_TASK_NAME to taskName))
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Get real-time flow of WorkInfo for tags.
     */
    fun getWorkInfosByTagFlow(context: Context, tag: String): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosByTagFlow(tag)
    }

    /**
     * Cancel work by tag.
     */
    fun cancelWorkByTag(context: Context, tag: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
    }

    /**
     * Cancel periodic work.
     */
    fun cancelPeriodicWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    }

    /**
     * Cancel all queued and running work.
     */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
