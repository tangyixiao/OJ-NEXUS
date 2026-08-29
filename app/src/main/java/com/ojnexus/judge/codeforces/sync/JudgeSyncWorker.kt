package com.ojnexus.judge.codeforces.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ojnexus.OjNexusApplication
import com.ojnexus.core.data.sync.SyncPhase
import java.util.concurrent.TimeUnit

/**
 * WorkManager entry point for judge syncs. The worker owns lifecycle only — the sync
 * business lives in [CodeforcesSyncCoordinator].
 *
 * Uniqueness: manual syncs use unique work named after the accountId, so tapping
 * SYNC NOW ten times enqueues one run; periodic background sync uses its own unique name
 * with KEEP policy. Cancellation on disconnect cancels both.
 */
class JudgeSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OjNexusApplication).container
        val accountId = inputData.getLong(KEY_ACCOUNT_ID, -1L)
        if (accountId <= 0L) return Result.failure()

        val account = container.problemRepository.let { } // keep reference local below
        val repo = container.judgeAccountRepository
        val found = repo.findById(accountId) ?: return Result.failure()
        if (!found.enabled) return Result.failure()

        val force = inputData.getBoolean(KEY_FORCE, false)
        return try {
            val report = container.syncCoordinator.syncAccount(accountId, force)
            when {
                report == null -> Result.failure()
                report.allOk -> Result.success()
                // Partial success: data persisted, failures recorded; transient errors
                // may retry within WorkManager's own budget.
                report.phase() == SyncPhase.PARTIAL &&
                    report.failures.any { it.errorType?.contains("RateLimited") == true } ->
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
                report.phase() == SyncPhase.PARTIAL -> Result.success()
                else -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // disconnected mid-sync — do not swallow, do not retry
        }
    }

    companion object {
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_FORCE = "force"
        const val MAX_ATTEMPTS = 3

        private fun manualName(accountId: Long) = "judge-sync-manual-$accountId"
        private fun periodicName(accountId: Long) = "judge-sync-periodic-$accountId"

        /** Manual SYNC NOW: unique one-time work; KEEP prevents duplicate concurrent runs. */
        fun enqueueManual(context: Context, accountId: Long, force: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<JudgeSyncWorker>()
                .setInputData(workDataOf(KEY_ACCOUNT_ID to accountId, KEY_FORCE to force))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                manualName(accountId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Periodic background refresh; fresh modules no-op inside the coordinator. */
        fun enqueuePeriodic(context: Context, accountId: Long) {
            val request = PeriodicWorkRequestBuilder<JudgeSyncWorker>(
                com.ojnexus.judge.codeforces.SyncPolicy.PERIODIC_SYNC_MINUTES,
                TimeUnit.MINUTES,
            )
                .setInputData(workDataOf(KEY_ACCOUNT_ID to accountId, KEY_FORCE to false))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    com.ojnexus.judge.codeforces.SyncPolicy.WORK_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                periodicName(accountId),
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Disconnect: stop manual + periodic work for this account immediately. */
        fun cancelFor(context: Context, accountId: Long) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(manualName(accountId))
            manager.cancelUniqueWork(periodicName(accountId))
        }
    }
}
