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

enum class AccountVerificationState { VERIFIED, UNVERIFIED }

data class AccountBinding(
    val storedHandle: String,
    val canonicalHandle: String,
    val verificationState: AccountVerificationState,
    val reliability: DataSourceReliability,
)

sealed class AccountBindingError(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {
    class InvalidHandle : AccountBindingError("invalid handle")
    class NotFound(message: String?) : AccountBindingError(message)
    class Unavailable(message: String?, cause: Throwable? = null) : AccountBindingError(message, cause)
}

/** Judge-specific public-handle validation behind a shared connection lifecycle. */
interface JudgeAccountConnector {
    val judgeId: JudgeId
    suspend fun bind(rawHandle: String): AccountBinding
}
