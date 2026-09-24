package com.ojnexus.feature.settings

import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.judge.JudgeCapability

/** User-facing synchronization modules backed by independent persisted timestamps. */
enum class SyncReceiptModule {
    PROFILE,
    RATING,
    SUBMISSIONS,
    CONTESTS,
    PROBLEMSET,
}

data class SyncReceiptItem(
    val module: SyncReceiptModule,
    val syncedAt: Long?,
)

sealed interface SyncAge {
    data object NEVER : SyncAge
    data object JUST_NOW : SyncAge
    data class MINUTES_AGO(val value: Long) : SyncAge
    data class HOURS_AGO(val value: Long) : SyncAge
    data class DAYS_AGO(val value: Long) : SyncAge
}

private data class SyncReceiptMapping(
    val module: SyncReceiptModule,
    val capability: JudgeCapability,
    val timestamp: (SyncStateEntity?) -> Long?,
)

private val receiptMappings = listOf(
    SyncReceiptMapping(
        module = SyncReceiptModule.PROFILE,
        capability = JudgeCapability.PROFILE,
        timestamp = { it?.profileSyncedAt },
    ),
    SyncReceiptMapping(
        module = SyncReceiptModule.RATING,
        capability = JudgeCapability.RATING_HISTORY,
        timestamp = { it?.ratingSyncedAt },
    ),
    SyncReceiptMapping(
        module = SyncReceiptModule.SUBMISSIONS,
        capability = JudgeCapability.SUBMISSIONS,
        timestamp = { it?.submissionsSyncedAt },
    ),
    SyncReceiptMapping(
        module = SyncReceiptModule.CONTESTS,
        capability = JudgeCapability.CONTESTS,
        timestamp = { it?.contestsSyncedAt },
    ),
    SyncReceiptMapping(
        module = SyncReceiptModule.PROBLEMSET,
        capability = JudgeCapability.PROBLEM_CATALOG,
        timestamp = { it?.problemsetSyncedAt },
    ),
)

internal fun syncReceiptItems(
    capabilities: Set<JudgeCapability>,
    state: SyncStateEntity?,
): List<SyncReceiptItem> = receiptMappings
    .filter { it.capability in capabilities }
    .map { mapping ->
        SyncReceiptItem(
            module = mapping.module,
            syncedAt = mapping.timestamp(state),
        )
    }

internal fun formatSyncAge(now: Long, syncedAt: Long?): SyncAge {
    if (syncedAt == null) return SyncAge.NEVER
    val elapsed = (now - syncedAt).coerceAtLeast(0L)
    return when {
        elapsed < MINUTE_MS -> SyncAge.JUST_NOW
        elapsed < HOUR_MS -> SyncAge.MINUTES_AGO(elapsed / MINUTE_MS)
        elapsed < DAY_MS -> SyncAge.HOURS_AGO(elapsed / HOUR_MS)
        else -> SyncAge.DAYS_AGO(elapsed / DAY_MS)
    }
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
