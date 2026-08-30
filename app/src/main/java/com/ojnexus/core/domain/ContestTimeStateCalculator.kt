package com.ojnexus.core.domain

enum class ContestTimeState { UPCOMING, LIVE, ENDED }

/** Judge-independent contest phase derived from epoch-second boundaries. */
object ContestTimeStateCalculator {
    fun calculate(startTimeSeconds: Long, durationSeconds: Long, nowSeconds: Long): ContestTimeState =
        when {
            nowSeconds < startTimeSeconds -> ContestTimeState.UPCOMING
            nowSeconds < startTimeSeconds + durationSeconds -> ContestTimeState.LIVE
            else -> ContestTimeState.ENDED
        }
}
