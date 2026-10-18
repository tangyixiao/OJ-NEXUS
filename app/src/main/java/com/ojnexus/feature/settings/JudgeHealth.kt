package com.ojnexus.feature.settings

import com.ojnexus.core.database.dao.SyncOperationWithModules

/**
 * Read-only projection of the sync ledger for one judge: the terminal status of the most
 * recent finished run plus its wall-clock latency. Nothing here probes the network — the
 * ledger already records what happened, and this only makes it visible.
 */
data class JudgeHealthSnapshot(
    val lastRunStatus: String?,
    val lastRunErrorType: String?,
    val lastRunLatencyMs: Long?,
)

fun deriveJudgeHealth(operations: List<SyncOperationWithModules>): JudgeHealthSnapshot {
    val finished = operations
        .filter { it.operation.finishedAt != null }
        .maxByOrNull { it.operation.startedAt }
        ?: return JudgeHealthSnapshot(lastRunStatus = null, lastRunErrorType = null, lastRunLatencyMs = null)
    val operation = finished.operation
    return JudgeHealthSnapshot(
        lastRunStatus = operation.status,
        lastRunErrorType = operation.lastErrorType,
        lastRunLatencyMs = (operation.finishedAt!! - operation.startedAt).takeIf { it >= 0 },
    )
}

/** Honest latency rendering: milliseconds under one second, one-decimal seconds above. */
fun formatJudgeHealthLatency(latencyMs: Long): String = when {
    latencyMs < 0 -> "?"
    latencyMs < 1_000 -> "$latencyMs ms"
    else -> String.format(java.util.Locale.US, "%.1f s", latencyMs / 1_000.0)
}

/** The status line shown in the connector center: terminal status, plus failure type when present. */
fun judgeHealthStatusLabel(snapshot: JudgeHealthSnapshot): String? {
    val status = snapshot.lastRunStatus ?: return null
    val errorType = snapshot.lastRunErrorType
    return if (errorType.isNullOrBlank()) status else "$status/$errorType"
}
