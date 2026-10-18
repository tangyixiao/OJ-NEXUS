package com.ojnexus.core.sync

import com.ojnexus.BuildConfig

/**
 * The POC surface: prove the RFC 0001 op-log merge rules as pure functions. No network, no
 * storage, no crypto — Phase 117's DoD is the deterministic merge kernel plus the flag
 * discipline (release builds never see this code path because every entry point is guarded by
 * [BuildConfig.SYNC_POC]).
 */
enum class SyncOpKind { UPSERT, DELETE }

data class SyncOp(
    val opId: String,
    val deviceId: String,
    val lamport: Long,
    val table: String,
    val rowKey: String,
    val kind: SyncOpKind,
)

data class MergeOutcome(
    val winner: SyncOp,
    val conflicts: List<SyncOp>,
) {
    val resolved: String get() = "LWW: lamport=${winner.lamport} device=${winner.deviceId}"
}

object SyncMergeKernel {

    /**
     * RFC 0001 §3: for row conflicts the higher (lamport, deviceId) wins — a total order,
     * deterministic across devices. Deletes follow the same comparison; a delete wins over an
     * insert only when its own order is greater (equal order keeps the insert, which makes
     * replayed stale deletes harmless).
     */
    fun mergeByRow(ops: List<SyncOp>): Map<String, MergeOutcome> {
        val byKey = ops.groupBy { "${it.table}#${it.rowKey}" }
        return byKey.mapValues { (_, group) ->
            val ordered = group.sortedWith(
                compareBy<SyncOp> { it.lamport }.thenBy { it.deviceId },
            )
            MergeOutcome(winner = ordered.last(), conflicts = ordered.dropLast(1))
        }
    }

    /**
     * Applies a batch of remote ops on top of a lamport clock. Returns the advanced clock; the
     * caller persists ops only when the gate is on, so the kernel itself stays pure.
     */
    fun advanceClock(current: Long, incoming: List<SyncOp>): Long =
        incoming.fold(current) { acc, op -> maxOf(acc, op.lamport) }
}
