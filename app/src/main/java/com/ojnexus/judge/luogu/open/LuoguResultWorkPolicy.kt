package com.ojnexus.judge.luogu.open

sealed interface LuoguResultWorkDecision {
    data object Success : LuoguResultWorkDecision
    data object Retry : LuoguResultWorkDecision
    data object Failure : LuoguResultWorkDecision
}

internal object LuoguResultWorkPolicy {
    const val MAX_RESULT_ATTEMPTS = 20

    fun decide(result: LuoguOpenResult, runAttemptCount: Int): LuoguResultWorkDecision = when (result) {
        is LuoguOpenResult.Ready -> LuoguResultWorkDecision.Success
        is LuoguOpenResult.InProgress,
        LuoguOpenResult.Pending,
        -> retryOrStop(runAttemptCount)
    }

    fun decide(error: LuoguOpenApiError, runAttemptCount: Int): LuoguResultWorkDecision = when {
        error.isRetryableResultError() -> retryOrStop(runAttemptCount)
        else -> LuoguResultWorkDecision.Failure
    }

    private fun retryOrStop(runAttemptCount: Int): LuoguResultWorkDecision =
        if (runAttemptCount >= MAX_RESULT_ATTEMPTS) {
            LuoguResultWorkDecision.Success
        } else {
            LuoguResultWorkDecision.Retry
        }
}

internal fun LuoguOpenApiError.isRetryableResultError(): Boolean = when (this) {
    is LuoguOpenApiError.Network -> true
    is LuoguOpenApiError.Http -> status == 408 || status == 425 || status == 429 || status in 500..599
    else -> false
}
