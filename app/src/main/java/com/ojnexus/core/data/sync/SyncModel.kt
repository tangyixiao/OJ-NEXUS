package com.ojnexus.core.data.sync

/**
 * Sync pipeline stages, executed in order by the coordinator. A stage failure marks the
 * run PARTIAL (already-persisted data stays); only a full failure marks ERROR.
 */
enum class SyncStage {
    PROFILE,
    RATING,
    SUBMISSIONS,
    CONTESTS,
    PROBLEMSET,
    DONE,
}

/** Persisted per-judge sync run state; survives process death. */
enum class SyncPhase {
    IDLE,
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
