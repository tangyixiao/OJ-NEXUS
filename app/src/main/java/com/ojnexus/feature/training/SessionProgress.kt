package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem

/** Counts the actionable states shown by the live session board. */
data class SessionProgressPulse(
    val total: Int,
    val solved: Int,
    val attempted: Int,
    val pending: Int,
)

fun deriveSessionProgressPulse(problems: List<SessionProblem>): SessionProgressPulse {
    val solved = problems.count { it.solved }
    val attempted = problems.count { it.attempts > 0 }
    return SessionProgressPulse(
        total = problems.size,
        solved = solved,
        attempted = attempted,
        pending = problems.count { it.attempts == 0 },
    )
}
