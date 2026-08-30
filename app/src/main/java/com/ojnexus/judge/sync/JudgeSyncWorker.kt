package com.ojnexus.judge.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ojnexus.OjNexusApplication
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.model.JudgeId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** One worker entry point; judge-specific stage plans are resolved through the registry. */
class JudgeSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getLong(KEY_ACCOUNT_ID, -1)
        val judge = inputData.getString(KEY_JUDGE_ID)?.let(JudgeId::fromId)
        if (accountId <= 0 || judge == null) return Result.failure()
        val force = inputData.getBoolean(KEY_FORCE, false)
        return try {
            val report = (applicationContext as OjNexusApplication).container.syncDispatcher
                .sync(judge, accountId, force)
                ?: return Result.failure()
            when {
                report.allOk -> Result.success()
                report.phase() == SyncPhase.PARTIAL && report.failures.any { it.isTransient } ->
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
                report.phase() == SyncPhase.PARTIAL -> Result.success()
                else -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    companion object {
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_JUDGE_ID = "judge_id"
        private const val KEY_FORCE = "force"
        const val MAX_ATTEMPTS = 3

        fun enqueueManual(context: Context, judge: JudgeId, accountId: Long, force: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<JudgeSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_JUDGE_ID to judge.id,
                        KEY_ACCOUNT_ID to accountId,
                        KEY_FORCE to force,
                    ),
                )
                .setConstraints(connectedConstraint())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                JudgeWorkNames.manual(judge, accountId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueuePeriodic(context: Context, judge: JudgeId, accountId: Long) {
            val request = PeriodicWorkRequestBuilder<JudgeSyncWorker>(6, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_JUDGE_ID to judge.id, KEY_ACCOUNT_ID to accountId, KEY_FORCE to false))
                .setConstraints(connectedConstraint())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                JudgeWorkNames.periodic(judge, accountId),
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelFor(context: Context, judge: JudgeId, accountId: Long) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(JudgeWorkNames.manual(judge, accountId))
            manager.cancelUniqueWork(JudgeWorkNames.periodic(judge, accountId))
        }

        private fun connectedConstraint() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private val com.ojnexus.core.data.sync.StageOutcome.isTransient: Boolean
            get() = listOf("RateLimited", "Network", "Timeout", "ServerError")
                .any { marker -> errorType?.contains(marker, ignoreCase = true) == true }
    }
}
