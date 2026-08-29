package com.ojnexus.judge

import com.ojnexus.core.model.JudgeId

/** Feature switches exposed by a judge adapter only when backed by a real implementation. */
enum class JudgeCapability {
    ACCOUNT_BINDING,
    PROFILE,
    RATING,
    RATING_HISTORY,
    SUBMISSIONS,
    PROBLEM_CATALOG,
    PROBLEM_DIFFICULTY,
    CONTESTS,
    BACKGROUND_SYNC,
    INCREMENTAL_SYNC,
}

/** Provenance and stability of the adapter's primary data source. */
enum class DataSourceReliability {
    OFFICIAL,
    COMMUNITY,
    EXPERIMENTAL,
}

/** Runtime source health. This is deliberately separate from an account's sync phase. */
enum class AdapterStatus {
    AVAILABLE,
    DEGRADED,
    RATE_LIMITED,
    OFFLINE,
    ERROR,
    UNSUPPORTED,
}

/** Judge-level identity and diagnostics; optional operations are expressed as capabilities. */
interface JudgeAdapter {
    val id: JudgeId
    val capabilities: Set<JudgeCapability>
    val reliability: DataSourceReliability

    suspend fun status(): AdapterStatus
}
