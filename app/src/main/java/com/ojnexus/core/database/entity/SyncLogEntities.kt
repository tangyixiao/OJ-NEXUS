package com.ojnexus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ojnexus.core.sync.SyncOpKind

/**
 * Phase 118 — the sync op-log model (RFC 0001 §3), additive tables only.
 *
 * An [SyncLogOperationEntity] is one device-produced batch of row operations. Its payload is
 * encrypted client-side before it ever reaches an endpoint (Phase 119); the local ledger stores
 * the plaintext op list so merge/conflict resolution is inspectable on-device.
 *
 * Schema constraints from the RFC:
 * - never references `judge_accounts` or credential stores (they do not sync);
 * - lamport + device id give the total order; opId is a client-generated ULID;
 * - status tracks the local lifecycle only (PENDING → SEALED → UPLOADED), never the server's.
 */
enum class SyncOpStatus { PENDING, SEALED, UPLOADED, CANCELLED }

@Entity(
    tableName = "sync_log_operations",
    indices = [
        Index(value = ["deviceId", "lamport"]),
        Index(value = ["status"]),
    ],
)
data class SyncLogOperationEntity(
    @PrimaryKey val opId: String,
    val deviceId: String,
    val lamport: Long,
    val createdAtEpochMs: Long,
    val schemaVersion: Int,
    val status: String = SyncOpStatus.PENDING.name,
    val encryptedPayloadPath: String? = null,
)

@Entity(
    tableName = "sync_log_ops",
    indices = [
        Index(value = ["opId"]),
        Index(value = ["tableName", "rowKey"]),
    ],
)
data class SyncLogOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opId: String,
    val tableName: String,
    val rowKey: String,
    val kind: String, // SyncOpKind.UPSERT / DELETE name
    val payloadJson: String, // encrypted-at-rest in the outbound payload; plaintext locally
)

@Entity(
    tableName = "sync_conflict_log",
    indices = [
        Index(value = ["tableName", "rowKey"]),
        Index(value = ["resolvedAtEpochMs"]),
    ],
)
data class SyncConflictLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val rowKey: String,
    val winnerOpId: String,
    val winnerDeviceId: String,
    val winnerLamport: Long,
    val resolvedAtEpochMs: Long,
    val reason: String, // SyncMergeKernel.MergeOutcome.resolved
)
