package com.ojnexus.core.data.sync

import androidx.room.withTransaction
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.SyncConflictLogEntity
import com.ojnexus.core.database.entity.SyncLogOpEntity
import com.ojnexus.core.database.entity.SyncLogOperationEntity
import com.ojnexus.core.database.entity.SyncOpStatus
import com.ojnexus.core.sync.SyncMergeKernel
import com.ojnexus.core.sync.SyncOp
import com.ojnexus.core.sync.SyncOpKind
import java.util.UUID

/**
 * Phase 118 (RFC 0001 §3) — writes the device-local op-log and resolves incoming op batches
 * through the [SyncMergeKernel], appending every deterministic resolution to the conflict log.
 *
 * Credentials never touch this repository: it accepts only explicit row operations and has no
 * API surface that can name judge_accounts or the Luogu credential store.
 */
class SyncLogRepository(private val database: OjNexusDatabase) {

    /** Appends one local batch of row ops as a new ledger operation with an advanced lamport. */
    suspend fun appendLocalBatch(
        deviceId: String,
        schemaVersion: Int,
        createdAtEpochMs: Long,
        ops: List<SyncLogOpEntity>,
    ): SyncLogOperationEntity {
        require(ops.isNotEmpty()) { "an op batch must carry at least one row op" }
        val lamport = (database.syncLogOperationDao().maxLamport() ?: 0L) + 1L
        val operation = SyncLogOperationEntity(
            opId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            lamport = lamport,
            createdAtEpochMs = createdAtEpochMs,
            schemaVersion = schemaVersion,
            status = SyncOpStatus.PENDING.name,
        )
        database.withTransaction {
            database.syncLogOperationDao().upsert(operation)
            database.syncLogOpDao().insertAll(ops.map { it.copy(opId = operation.opId) })
        }
        return operation
    }

    /**
     * Merges a remote batch into the local ledger view: per (table, rowKey) resolution via the
     * kernel, every displaced op recorded in the conflict log. Returns the winners in stable
     * key order.
     */
    suspend fun resolveRemoteBatch(
        remoteOp: SyncLogOperationEntity,
        remoteOps: List<SyncOp>,
        localOps: List<SyncOp>,
        resolvedAtEpochMs: Long,
    ): Map<String, com.ojnexus.core.sync.MergeOutcome> {
        val merged = SyncMergeKernel.mergeByRow(localOps + remoteOps)
        database.withTransaction {
            for ((_, outcome) in merged) {
                for (loser in outcome.conflicts) {
                    database.syncConflictLogDao().insert(
                        SyncConflictLogEntity(
                            tableName = loser.table,
                            rowKey = loser.rowKey,
                            winnerOpId = outcome.winner.opId,
                            winnerDeviceId = outcome.winner.deviceId,
                            winnerLamport = outcome.winner.lamport,
                            resolvedAtEpochMs = resolvedAtEpochMs,
                            reason = outcome.resolved,
                        ),
                    )
                }
            }
        }
        return merged
    }

    /** Marks a batch sealed (payload written, ready to upload). */
    suspend fun markSealed(opId: String, payloadPath: String) {
        database.syncLogOperationDao().findById(opId)
            ?: throw IllegalArgumentException("unknown sync op batch: $opId")
        database.syncLogOperationDao().upsert(
            database.syncLogOperationDao().findById(opId)!!.copy(
                status = SyncOpStatus.SEALED.name,
                encryptedPayloadPath = payloadPath,
            ),
        )
    }

    suspend fun markUploaded(opId: String) =
        database.syncLogOperationDao().updateStatus(opId, SyncOpStatus.UPLOADED.name)
}
