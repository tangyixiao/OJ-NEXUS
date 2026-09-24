package com.ojnexus.core.data.restore

/** Durable phases written to the small restore journal. */
enum class RestoreStage {
    STAGED,
    SWAPPING,
    APPLIED,
    ROLLED_BACK,
    REJECTED,
    ;

    fun canTransitionTo(next: RestoreStage): Boolean = when (this) {
        STAGED -> next == SWAPPING || next == REJECTED
        SWAPPING -> next == APPLIED || next == ROLLED_BACK
        APPLIED, ROLLED_BACK, REJECTED -> false
    }
}

/** Stable, user-safe categories. Raw SQLite or filesystem messages never cross this boundary. */
enum class RestoreFailure {
    INVALID_SQLITE,
    SCHEMA_MISMATCH,
    REQUIRED_TABLE_MISSING,
    INTEGRITY_FAILURE,
    FILE_REPLACEMENT_FAILURE,
    ROLLBACK_FAILURE,
}

sealed interface RestoreOutcome {
    data class Staged(val generation: String) : RestoreOutcome
    data class Applied(val generation: String) : RestoreOutcome
    data class RolledBack(val generation: String, val failure: RestoreFailure) : RestoreOutcome
    data class Rejected(val failure: RestoreFailure) : RestoreOutcome
    data object NothingToRestore : RestoreOutcome
}

data class RestoreJournal(
    val stage: RestoreStage,
    val generation: String,
    val detail: RestoreFailure?,
)

data class RestoreFileState(
    val target: Boolean,
    val candidate: Boolean,
    val rollback: Boolean,
    val staged: Boolean,
)

enum class RestoreRecoveryAction {
    APPLY_CANDIDATE,
    VALIDATE_TARGET,
    RESTORE_ROLLBACK,
    REJECT_STAGING,
    CLEAR_COMPLETED_ARTIFACTS,
    NO_ACTION,
}

enum class RestoreWorkDecision {
    PROCEED,
    STALE,
    LEGACY,
}

fun decideRestoreWorkGeneration(expected: String?, current: String): RestoreWorkDecision = when {
    expected == null -> RestoreWorkDecision.LEGACY
    expected == current -> RestoreWorkDecision.PROCEED
    else -> RestoreWorkDecision.STALE
}

/**
 * Decides how startup should converge after an import or an interrupted replacement.
 * This function deliberately knows nothing about files and is therefore safe to exhaustively test.
 */
fun decideRestoreRecovery(
    journal: RestoreJournal?,
    files: RestoreFileState,
): RestoreRecoveryAction {
    if (journal == null) {
        return if (files.staged) {
            RestoreRecoveryAction.APPLY_CANDIDATE
        } else {
            RestoreRecoveryAction.NO_ACTION
        }
    }

    return when (journal.stage) {
        RestoreStage.STAGED -> if (files.staged) {
            RestoreRecoveryAction.APPLY_CANDIDATE
        } else {
            RestoreRecoveryAction.REJECT_STAGING
        }

        RestoreStage.SWAPPING -> when {
            files.target && files.rollback -> RestoreRecoveryAction.VALIDATE_TARGET
            !files.target && files.rollback -> RestoreRecoveryAction.RESTORE_ROLLBACK
            files.candidate -> RestoreRecoveryAction.APPLY_CANDIDATE
            else -> RestoreRecoveryAction.REJECT_STAGING
        }

        RestoreStage.APPLIED,
        RestoreStage.ROLLED_BACK,
        RestoreStage.REJECTED,
        -> RestoreRecoveryAction.CLEAR_COMPLETED_ARTIFACTS
    }
}
