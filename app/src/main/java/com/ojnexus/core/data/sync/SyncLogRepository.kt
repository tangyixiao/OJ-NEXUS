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

    /**
     * Phase 119 (RFC §4 storage side) — encodes the batch's row ops, seals them with AAD
     * `deviceId|lamport`, writes the payload under <filesDir>/sync-payload/, and marks the
     * operation SEALED. No network I/O; the sealer is injected (production AEAD is a later
     * decision). Unknown batch → IllegalArgumentException.
     */
    suspend fun sealBatch(
        opId: String,
        filesDir: java.io.File,
        sealer: com.ojnexus.core.sync.SyncPayloadSealer,
    ): String {
        val operation = database.syncLogOperationDao().findById(opId)
            ?: throw IllegalArgumentException("unknown sync op batch: $opId")
        val rows = database.syncLogOpDao().findByOpId(opId).map {
            com.ojnexus.core.sync.SyncLogOpRow(
                tableName = it.tableName,
                rowKey = it.rowKey,
                kind = it.kind,
                payloadJson = it.payloadJson,
            )
        }
        val plaintext = com.ojnexus.core.sync.SyncPayloadCodec.encode(rows)
        val aad = "${operation.deviceId}|${operation.lamport}".toByteArray(Charsets.UTF_8)
        val sealed = sealer.seal(plaintext.toByteArray(Charsets.UTF_8), aad)
        val dir = java.io.File(filesDir, "sync-payload")
        if (!dir.exists()) dir.mkdirs()
        val payload = java.io.File(dir, "$opId.bin")
        payload.writeBytes(sealed)
        markSealed(opId, payload.absolutePath)
        return payload.absolutePath
    }

    /**
     * Opens a sealed payload back into row ops. Tampered payload or AAD mismatch → null
     * (verification failure, never a crash). Unknown batch → IllegalArgumentException.
     */
    suspend fun openBatch(
        opId: String,
        sealer: com.ojnexus.core.sync.SyncPayloadSealer,
    ): List<com.ojnexus.core.sync.SyncLogOpRow>? {
        val operation = database.syncLogOperationDao().findById(opId)
            ?: throw IllegalArgumentException("unknown sync op batch: $opId")
        val path = operation.encryptedPayloadPath ?: return null
        val payload = java.io.File(path)
        if (!payload.exists()) return null
        val aad = "${operation.deviceId}|${operation.lamport}".toByteArray(Charsets.UTF_8)
        val plaintext = sealer.open(payload.readBytes(), aad) ?: return null
        return com.ojnexus.core.sync.SyncPayloadCodec.decode(String(plaintext, Charsets.UTF_8))
    }

    /**
     * Phase 120 — pushes a batch to the transport's remote store (sealing first when the batch
     * is still PENDING). Returns the remote opId. Unknown batch → IllegalArgumentException.
     */
    suspend fun pushBatch(
        opId: String,
        transport: com.ojnexus.core.sync.SyncTransport,
        sealer: com.ojnexus.core.sync.SyncPayloadSealer,
        filesDir: java.io.File,
    ) {
        val operation = database.syncLogOperationDao().findById(opId)
            ?: throw IllegalArgumentException("unknown sync op batch: $opId")
        val payloadPath = operation.encryptedPayloadPath
            ?: sealBatch(opId, filesDir, sealer)
        transport.upload(opId, payloadPath)
    }

    /**
     * Phase 120 — pulls a remote payload into the local ledger: downloads, marks the batch
     * SEALED against the local file, and returns the decoded rows (null on tamper). The caller
     * must have created the batch row first (e.g. via [appendLocalBatch]) so the AAD
     * deviceId|lamport matches the remote batch's identity.
     */
    suspend fun pullBatch(
        opId: String,
        transport: com.ojnexus.core.sync.SyncTransport,
        sealer: com.ojnexus.core.sync.SyncPayloadSealer,
        filesDir: java.io.File,
    ): List<com.ojnexus.core.sync.SyncLogOpRow>? {
        val localPath = transport.download(opId, filesDir) ?: return null
        markSealed(opId, localPath)
        return openBatch(opId, sealer)
    }
}
