package com.ojnexus.judge

import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncRetryRequest
import com.ojnexus.core.data.sync.SyncRunContext
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.dao.SyncOperationDao
import com.ojnexus.core.database.entity.SyncModuleStatus
import com.ojnexus.core.database.entity.SyncOperationEntity
import com.ojnexus.core.database.entity.SyncOperationModuleEntity
import com.ojnexus.core.database.entity.SyncOperationStatus
import com.ojnexus.core.model.JudgeId
import java.time.Clock

/** Validates worker input identity before routing to the registered judge coordinator. */
class JudgeSyncDispatcher(
    private val accountRepository: JudgeAccountRepository,
    private val registry: JudgeRegistry,
    private val syncOperationDao: SyncOperationDao? = null,
    private val currentDataGeneration: () -> String = { "" },
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun sync(
        judge: JudgeId,
        accountId: Long,
        force: Boolean,
        dataGeneration: String? = null,
    ): SyncReport? {
        val account = accountRepository.findById(accountId)
            ?.takeIf { it.enabled && it.judge == judge.id }
            ?: return null
        val ledger = syncOperationDao
        val generation = dataGeneration?.trim()?.takeIf { it.isNotEmpty() } ?: currentDataGeneration()
        val operationId = ledger?.openOperation(
            SyncOperationEntity(
                judge = judge.id,
                accountId = account.id,
                dataGeneration = generation,
                startedAt = clock.millis(),
            ),
        )
        val context = operationId?.let { id ->
            SyncRunContext(id, generation) { outcome ->
                ledger.appendCommittedModule(outcome.toModuleEntity(id, clock.millis()))
            }
        }
        return try {
            val coordinator = registry.syncCoordinator(judge)
            val report = if (context == null) {
                coordinator.syncAccount(account.id, force)
            } else {
                coordinator.syncAccount(account.id, force, context)
            }
            operationId?.let { id ->
                ledger.closeOperation(
                    operationId = id,
                    status = report.toOperationStatus().name,
                    finishedAt = clock.millis(),
                    errorType = report?.failures?.firstOrNull()?.errorType,
                )
            }
            report
        } catch (e: kotlinx.coroutines.CancellationException) {
            operationId?.let { id ->
                ledger.closeOperation(id, SyncOperationStatus.CANCELLED.name, clock.millis(), "CANCELLED")
            }
            throw e
        } catch (e: Exception) {
            operationId?.let { id ->
                ledger.closeOperation(id, SyncOperationStatus.ERROR.name, clock.millis(), e.javaClass.simpleName)
            }
            throw e
        }
    }

    /** Records a worker skipped because its captured restore generation is stale. */
    suspend fun recordStaleGeneration(
        judge: JudgeId,
        accountId: Long,
        staleGeneration: String?,
    ): Boolean {
        val account = accountRepository.findById(accountId)
            ?.takeIf { it.enabled && it.judge == judge.id }
            ?: return false
        val generation = staleGeneration?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (generation == currentDataGeneration()) return false
        val ledger = syncOperationDao ?: return false
        val now = clock.millis()
        val operationId = ledger.openOperation(
            SyncOperationEntity(
                judge = judge.id,
                accountId = account.id,
                dataGeneration = generation,
                startedAt = now,
            ),
        )
        ledger.closeOperation(
            operationId = operationId,
            status = SyncOperationStatus.STALE_GENERATION.name,
            finishedAt = now,
            errorType = "STALE_DATA_GENERATION",
        )
        return true
    }

    /** Validates the original operation identity; unsupported stage restarts fall back to full sync. */
    suspend fun retry(request: SyncRetryRequest): SyncReport? {
        val ledger = syncOperationDao ?: return null
        if (request.stage == SyncStage.DONE || request.dataGeneration != currentDataGeneration()) return null
        val account = accountRepository.findById(request.accountId)
            ?.takeIf { it.enabled && it.judge == request.judge.id }
            ?: return null
        val operation = ledger.findById(request.operationId)
            ?.takeIf {
                it.operation.judge == request.judge.id &&
                    it.operation.accountId == account.id &&
                    it.operation.dataGeneration == request.dataGeneration
            }
            ?: return null
        if (operation.modules.none { it.stage == request.stage.name && it.status != SyncModuleStatus.SUCCESS.name }) {
            return null
        }
        // No adapter currently declares a safe stage restart, so retry remains an explicit full sync.
        return sync(request.judge, request.accountId, force = true, dataGeneration = request.dataGeneration)
    }
}

private fun SyncReport?.toOperationStatus(): SyncOperationStatus = when (this?.phase()) {
    SyncPhase.SUCCESS -> SyncOperationStatus.SUCCESS
    SyncPhase.PARTIAL -> SyncOperationStatus.PARTIAL
    SyncPhase.ERROR,
    SyncPhase.IDLE,
    SyncPhase.QUEUED,
    SyncPhase.SYNCING,
    null,
    -> SyncOperationStatus.ERROR
}

private fun com.ojnexus.core.data.sync.StageOutcome.toModuleEntity(
    operationId: Long,
    completedAt: Long,
) = SyncOperationModuleEntity(
    operationId = operationId,
    stage = stage.name,
    status = when {
        ok -> SyncModuleStatus.SUCCESS.name
        itemsProcessed > 0 -> SyncModuleStatus.PARTIAL.name
        else -> SyncModuleStatus.ERROR.name
    },
    attemptedCount = itemsProcessed.coerceAtLeast(0),
    importedCount = if (ok) itemsProcessed.coerceAtLeast(0) else 0,
    updatedCount = 0,
    completedAt = completedAt,
    failureType = errorType,
)
