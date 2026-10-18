package com.ojnexus.core.data.sync

import com.ojnexus.core.database.entity.JudgeAccountEntity

/**
 * Stage-1 multi-handle planner (Phase 97 prototype — pure, NOT yet wired into production).
 *
 * Ground rules from docs/superpowers/specs/2026-10-18-multi-handle-design.md:
 * - `judge_accounts` unique index is (judge, canonical_handle), so multiple rows per judge are
 *   representable today.
 * - `sync_states` PK is judge, so per-handle cursors are NOT available yet (stage-2 migration).
 *   Until then a second handle is stored as a DISABLED account and switching resets the judge's
 *   cursor row — the incremental cursor belongs to the handle that produced it.
 * - Data generation stays global (Phase 73): restore staleness is whole-database, per-handle
 *   staleness is answered by the account id carried in work inputs and ledger rows.
 */
object MultiHandlePrototype {

    /** MAX_HANDLES is the deliberate stage-1 cap: current handle + one pending handle. */
    const val MAX_HANDLES = 2

    sealed class ConnectDecision {
        /** Same canonical handle re-binding: update the row in place, keep cursors. */
        data object ReuseExisting : ConnectDecision()

        /** New secondary handle below the cap: insert a disabled row, cursor resets on switch. */
        data object AddSecondary : ConnectDecision()

        /** Cap reached: the new handle replaces every other account of the judge. */
        data class ReplaceActive(val removedAccountIds: List<Long>) : ConnectDecision()
    }

    /**
     * Decide what connecting [requestedCanonical] to [judge] must do given the judge's current
     * accounts. Pure: no DAO calls, no clock, no network.
     */
    fun planConnect(
        accounts: List<JudgeAccountEntity>,
        judge: String,
        requestedCanonical: String,
    ): ConnectDecision {
        val sameHandle = accounts.firstOrNull {
            it.judge == judge && it.canonicalHandle == requestedCanonical
        }
        if (sameHandle != null) return ConnectDecision.ReuseExisting

        val others = accounts.filter { it.judge == judge }
        return if (others.size < MAX_HANDLES) {
            ConnectDecision.AddSecondary
        } else {
            ConnectDecision.ReplaceActive(others.map { it.id })
        }
    }

    /**
     * Whether a worker created for [workAccountId] must report stale-input for [judge]:
     * the account no longer exists (replaced/disconnected). The enabled flag alone is NOT
     * staleness — disabled secondary handles just never start work.
     */
    fun isWorkStaleForAccount(
        accounts: List<JudgeAccountEntity>,
        judge: String,
        workAccountId: Long,
    ): Boolean = accounts.none { it.judge == judge && it.id == workAccountId }

    /**
     * Rows to remove when switching the active handle of [judge] to [targetAccountId]:
     * every other account of the judge is removed together with the judge's sync-state cursor
     * (the caller deletes the cursor row in the same transaction). Current handle survives.
     */
    fun accountsToRemoveOnSwitch(
        accounts: List<JudgeAccountEntity>,
        judge: String,
        targetAccountId: Long,
    ): List<Long> = accounts
        .filter { it.judge == judge && it.id != targetAccountId }
        .map { it.id }
}
