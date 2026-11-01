package com.ojnexus.core.data.restore

import java.io.IOException

/** Durable phases written to the small restore journal. */
enum class RestoreStage {
    STAGED,
    SWAPPING,
    SAVING_ROLLBACK,
    INSTALLING,
    ROLLING_BACK,
    APPLIED,
    ROLLED_BACK,
    REJECTED,
    ;

    fun canTransitionTo(next: RestoreStage): Boolean = when (this) {
        STAGED -> next == SWAPPING || next == SAVING_ROLLBACK || next == REJECTED
        SWAPPING -> next == APPLIED || next == ROLLED_BACK || next == SAVING_ROLLBACK || next == INSTALLING || next == ROLLING_BACK
        SAVING_ROLLBACK -> next == INSTALLING || next == ROLLED_BACK || next == REJECTED
        INSTALLING -> next == APPLIED || next == ROLLING_BACK
        ROLLING_BACK -> next == ROLLED_BACK
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
    STORAGE_FULL,
    STORAGE_IO,
}

sealed interface RestoreOutcome {
    data class Staged(val generation: String) : RestoreOutcome
    data class Applied(val generation: String) : RestoreOutcome
    data class RolledBack(val generation: String, val failure: RestoreFailure) : RestoreOutcome
    data class Rejected(val failure: RestoreFailure) : RestoreOutcome
    data object NothingToRestore : RestoreOutcome
    data class Blocked(val failure: RestoreFailure, val recovery: RecoveryDisposition) : RestoreOutcome
}

enum class RecoveryDisposition { RECONCILE_FORWARD_ON_NEXT_START, INSPECTION_REQUIRED }

sealed interface RestoreReadiness {
    data class Ready(val outcome: RestoreOutcome, val generation: String) : RestoreReadiness
    data class Blocked(val failure: RestoreFailure, val recovery: RecoveryDisposition) : RestoreReadiness
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
    BLOCK_RECOVERY,
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

private val ENOSPC_MARKERS = listOf("no space left on device", "enospc", "not enough space", "disk full")

/**
 * Classifies an import I/O failure (RV004–RV006): ENOSPC-flavoured failures are
 * [RestoreFailure.STORAGE_FULL], any other I/O failure is [RestoreFailure.STORAGE_IO].
 * Non-I/O throwables return null so callers keep their own fallback category.
 */
fun classifyImportIoFailure(failure: Throwable): RestoreFailure? {
    var cause: Throwable? = failure
    while (cause != null) {
        if (cause is IOException) {
            val message = cause.message?.lowercase().orEmpty()
            return if (ENOSPC_MARKERS.any { message.contains(it) }) {
                RestoreFailure.STORAGE_FULL
            } else {
                RestoreFailure.STORAGE_IO
            }
        }
        cause = cause.cause
    }
    return null
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

        RestoreStage.SWAPPING, RestoreStage.INSTALLING -> when {
            files.target && files.rollback -> RestoreRecoveryAction.VALIDATE_TARGET
            !files.target && files.rollback -> RestoreRecoveryAction.RESTORE_ROLLBACK
            files.candidate -> RestoreRecoveryAction.APPLY_CANDIDATE
            else -> RestoreRecoveryAction.REJECT_STAGING
        }

        RestoreStage.APPLIED,
        RestoreStage.ROLLED_BACK,
        RestoreStage.REJECTED,
        -> RestoreRecoveryAction.CLEAR_COMPLETED_ARTIFACTS

        RestoreStage.SAVING_ROLLBACK, RestoreStage.ROLLING_BACK -> RestoreRecoveryAction.BLOCK_RECOVERY
    }
}
