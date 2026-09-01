package com.ojnexus.judge.luogu.open

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ojnexus.OjNexusApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal data class LuoguResultWorkSpec(
    val requestId: String,
    val uniqueWorkName: String,
    val inputData: Map<String, String>,
    val requiresConnectedNetwork: Boolean,
    val initialDelayMillis: Long,
    val backoffDelayMillis: Long,
    val backoffPolicy: BackoffPolicy,
    val existingWorkPolicy: ExistingWorkPolicy,
)

internal object LuoguResultWorkRequestFactory {
    const val REQUEST_ID_KEY = "request_id"
    const val UNIQUE_WORK_PREFIX = "luogu-result:"
    const val IMMEDIATE_WORK_PREFIX = "luogu-result-manual:"
    const val INITIAL_DELAY_MILLIS = 10_000L
    const val BACKOFF_DELAY_MILLIS = 30_000L

    fun spec(requestId: String): LuoguResultWorkSpec? {
        return createSpec(
            requestId = requestId,
            uniqueWorkPrefix = UNIQUE_WORK_PREFIX,
            initialDelayMillis = INITIAL_DELAY_MILLIS,
        )
    }

    fun immediateSpec(requestId: String): LuoguResultWorkSpec? {
        return createSpec(
            requestId = requestId,
            uniqueWorkPrefix = IMMEDIATE_WORK_PREFIX,
            initialDelayMillis = 0L,
        )
    }

    private fun createSpec(
        requestId: String,
        uniqueWorkPrefix: String,
        initialDelayMillis: Long,
    ): LuoguResultWorkSpec? {
        val trimmed = requestId.trim().takeIf { it.isNotEmpty() } ?: return null
        return LuoguResultWorkSpec(
            requestId = trimmed,
            uniqueWorkName = "$uniqueWorkPrefix$trimmed",
            inputData = mapOf(REQUEST_ID_KEY to trimmed),
            requiresConnectedNetwork = true,
            initialDelayMillis = initialDelayMillis,
            backoffDelayMillis = BACKOFF_DELAY_MILLIS,
            backoffPolicy = BackoffPolicy.EXPONENTIAL,
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
        )
    }

    fun request(spec: LuoguResultWorkSpec): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<LuoguOpenResultWorker>()
            .setInputData(workDataOf(*spec.inputData.toList().toTypedArray()))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(spec.initialDelayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                spec.backoffPolicy,
                spec.backoffDelayMillis,
                TimeUnit.MILLISECONDS,
            )
            .build()
}

interface LuoguResultWorkScheduler {
    fun enqueue(requestId: String)

    fun enqueueNow(requestId: String) = enqueue(requestId)
}

class WorkManagerLuoguResultScheduler(context: Context) : LuoguResultWorkScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue(requestId: String) {
        enqueue(LuoguResultWorkRequestFactory.spec(requestId))
    }

    override fun enqueueNow(requestId: String) {
        enqueue(LuoguResultWorkRequestFactory.immediateSpec(requestId))
    }

    private fun enqueue(spec: LuoguResultWorkSpec?) {
        spec ?: return
        workManager.enqueueUniqueWork(
            spec.uniqueWorkName,
            spec.existingWorkPolicy,
            LuoguResultWorkRequestFactory.request(spec),
        )
    }
}

class LuoguOpenResultWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val requestId = inputData.getString(LuoguResultWorkRequestFactory.REQUEST_ID_KEY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return ListenableWorker.Result.failure()
        val application = applicationContext as? OjNexusApplication
            ?: return ListenableWorker.Result.failure()

        return try {
            LuoguResultWorkPolicy.decide(
                application.container.luoguSubmissionRepository.refreshResult(requestId),
                runAttemptCount,
            ).toWorkerResult()
        } catch (error: LuoguOpenApiError) {
            LuoguResultWorkPolicy.decide(error, runAttemptCount).toWorkerResult()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ListenableWorker.Result.failure()
        }
    }
}

internal fun LuoguResultWorkDecision.toWorkerResult(): ListenableWorker.Result = when (this) {
    LuoguResultWorkDecision.Success -> ListenableWorker.Result.success()
    LuoguResultWorkDecision.Retry -> ListenableWorker.Result.retry()
    LuoguResultWorkDecision.Failure -> ListenableWorker.Result.failure()
}
