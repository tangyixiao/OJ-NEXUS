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
import com.ojnexus.core.data.restore.RestoreWorkDecision
import com.ojnexus.core.data.restore.decideRestoreWorkGeneration
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
    val dataGeneration: String? = null,
)

internal object LuoguResultWorkRequestFactory {
    const val REQUEST_ID_KEY = "request_id"
    const val DATA_GENERATION_KEY = "data_generation"
    const val UNIQUE_WORK_PREFIX = "luogu-result:"
    const val IMMEDIATE_WORK_PREFIX = "luogu-result-manual:"
    const val INITIAL_DELAY_MILLIS = 10_000L
    const val BACKOFF_DELAY_MILLIS = 30_000L

    fun spec(requestId: String, dataGeneration: String? = null): LuoguResultWorkSpec? {
        return createSpec(
            requestId = requestId,
            uniqueWorkPrefix = UNIQUE_WORK_PREFIX,
            initialDelayMillis = INITIAL_DELAY_MILLIS,
            dataGeneration = dataGeneration,
        )
    }

    fun immediateSpec(requestId: String, dataGeneration: String? = null): LuoguResultWorkSpec? {
        return createSpec(
            requestId = requestId,
            uniqueWorkPrefix = IMMEDIATE_WORK_PREFIX,
            initialDelayMillis = 0L,
            dataGeneration = dataGeneration,
        )
    }

    private fun createSpec(
        requestId: String,
        uniqueWorkPrefix: String,
        initialDelayMillis: Long,
        dataGeneration: String?,
    ): LuoguResultWorkSpec? {
        val trimmed = requestId.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedGeneration = dataGeneration?.trim()?.takeIf { it.isNotEmpty() }
        return LuoguResultWorkSpec(
            requestId = trimmed,
            uniqueWorkName = "$uniqueWorkPrefix$trimmed",
            inputData = buildMap {
                put(REQUEST_ID_KEY, trimmed)
                normalizedGeneration?.let { put(DATA_GENERATION_KEY, it) }
            },
            requiresConnectedNetwork = true,
            initialDelayMillis = initialDelayMillis,
            backoffDelayMillis = BACKOFF_DELAY_MILLIS,
            backoffPolicy = BackoffPolicy.EXPONENTIAL,
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            dataGeneration = normalizedGeneration,
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

    fun enqueue(requestId: String, dataGeneration: String) = enqueue(requestId)

    fun enqueueNow(requestId: String) = enqueue(requestId)

    fun enqueueNow(requestId: String, dataGeneration: String) = enqueueNow(requestId)
}

class WorkManagerLuoguResultScheduler(context: Context) : LuoguResultWorkScheduler {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun enqueue(requestId: String) {
        enqueue(requestId, currentDataGeneration())
    }

    override fun enqueue(requestId: String, dataGeneration: String) {
        enqueue(LuoguResultWorkRequestFactory.spec(requestId, dataGeneration))
    }

    override fun enqueueNow(requestId: String) {
        enqueueNow(requestId, currentDataGeneration())
    }

    override fun enqueueNow(requestId: String, dataGeneration: String) {
        enqueue(LuoguResultWorkRequestFactory.immediateSpec(requestId, dataGeneration))
    }

    private fun enqueue(spec: LuoguResultWorkSpec?) {
        spec ?: return
        workManager.enqueueUniqueWork(
            spec.uniqueWorkName,
            spec.existingWorkPolicy,
            LuoguResultWorkRequestFactory.request(spec),
        )
    }

    private fun currentDataGeneration(): String =
        com.ojnexus.core.data.restore.DatabaseRestoreCoordinator(appContext)
            .currentDataGeneration()
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
        when (decideRestoreWorkGeneration(
            inputData.getString(LuoguResultWorkRequestFactory.DATA_GENERATION_KEY),
            application.container.currentDataGeneration(),
        )) {
            RestoreWorkDecision.STALE ->
                return ListenableWorker.Result.success(
                    workDataOf("result" to "STALE_DATA_GENERATION"),
                )
            RestoreWorkDecision.PROCEED,
            RestoreWorkDecision.LEGACY,
            -> Unit
        }

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
