package com.nevoit.cresto.data.learning

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nevoit.cresto.data.routine.HabitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Uploads the study log; runs once the device is online and retries transient failures. */
class StudyLogSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        return try {
            val logs = HabitRepository.get(applicationContext).studyLogsSnapshot()
            when (StudyPlanRepository.get(applicationContext).pushStudyLog(logs)) {
                StudyLogPushOutcome.Retry -> Result.retry()
                else -> Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.retry()
        }
    }
}

object StudyLogSyncScheduler {
    private const val WORK_NAME = "study_log_sync"
    private const val SETTLE_SECONDS = 120L

    /**
     * Each edit restarts the wait, so typing a note makes one upload about two minutes after the
     * last keystroke instead of one per character.
     */
    fun scheduleAfterEdit(context: Context) = enqueue(context, SETTLE_SECONDS)

    fun syncNow(context: Context) = enqueue(context, 0L)

    /** The pending or running upload, or null when nothing is queued. */
    fun observe(context: Context): Flow<WorkInfo.State?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .map { infos -> infos.firstOrNull { !it.state.isFinished }?.state }

    private fun enqueue(context: Context, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<StudyLogSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
