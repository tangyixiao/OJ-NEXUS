package com.ojnexus.core.data.sync

import com.ojnexus.core.model.JudgeId

/**
 * Sync pipeline stages, executed in order by the coordinator. A stage failure marks the
 * run PARTIAL (already-persisted data stays); only a full failure marks ERROR.
 */
enum class SyncStage {
    PROFILE,
    RATING,
    SUBMISSIONS,
    CONTESTS,
    PROBLEMS,
    DONE,
}

/** Persisted per-judge sync run state; survives process death. */
enum class SyncPhase {
    IDLE,
    QUEUED,
    SYNCING,
    SUCCESS,
    PARTIAL,
    ERROR,
}

/** Outcome of one stage. */
data class StageOutcome(
    val stage: SyncStage,
    val ok: Boolean,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val itemsProcessed: Int = 0,
)

/** Aggregate result of one sync run. */
data class SyncReport(
    val outcomes: List<StageOutcome>,
) {
    val failures: List<StageOutcome> get() = outcomes.filterNot { it.ok }
    val allOk: Boolean get() = outcomes.isNotEmpty() && failures.isEmpty()
    val anyOk: Boolean get() = outcomes.any { it.ok }
    val submissionsImported: Int
        get() = outcomes.firstOrNull { it.stage == SyncStage.SUBMISSIONS }?.itemsProcessed ?: 0

    fun phase(): SyncPhase = when {
        allOk -> SyncPhase.SUCCESS
        anyOk -> SyncPhase.PARTIAL
        else -> SyncPhase.ERROR
    }
}

/** Context passed to a coordinator when its caller wants durable module receipts. */
data class SyncRunContext(
    val operationId: Long,
    val dataGeneration: String,
    private val moduleRecorder: suspend (StageOutcome) -> Unit,
) {
    suspend fun recordModule(outcome: StageOutcome) = moduleRecorder(outcome)
}

/** Identity-bound request for retrying one failed stage or falling back to a full sync. */
data class SyncRetryRequest(
    val judge: JudgeId,
    val accountId: Long,
    val operationId: Long,
    val stage: SyncStage,
    val dataGeneration: String,
)
